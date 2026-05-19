# MaidSoulCore 聊天核心重构施工方案

目标：把当前 Java 原型的聊天核心按参考项目的会话级运行模型重构，优先解决连续输入、旧请求燃烧、主动节奏错乱、上下文断裂和临场补丁化问题。

## 当前判断

当前工程并不是没有 debounce、interrupt、version discard，而是这些机制的边界没有闭合。

现状问题：

- `ConversationRuntime` 使用 `newFixedThreadPool(2)`，允许多个模型链路并发。
- 新消息到来时 `CompletableFuture.cancel(true)` 不一定能真正取消 HTTP 请求。
- `version discard` 只能丢弃旧输出，不能阻止旧请求继续消耗 token、连接和时间。
- `messageDebounceMillis` 只挡住“请求启动前”的短时间输入，挡不住“模型运行中”的连续输入。
- `ChatSession.collectPendingMessages()` 在请求开始时就把 pending 标记为已处理，旧请求被丢弃时，这批消息已经进 history。
- 情绪和记忆逐条消息立即更新，但回复生成是异步滞后的，导致状态、history、输出不属于同一个对话切片。
- 当前没有稳定的对话状态层，很多“用户不满/冷场/修复关系”都交给 planner 临场判断，容易误判。

参考项目的关键机制：

- 每个会话只有一个内部运行循环。
- 外部消息进入缓存，不是每条消息直接启动一轮模型。
- 内部 turn 通过队列串行消费：`message`、`timeout`、`proactive`。
- 运行中收到新消息时，标记 debounce required，并通过 interrupt flag 尝试中断 planner。
- 连续打断有上限，避免疯狂 cancel/restart。
- LLM 客户端层会检查 interrupt flag，并真正取消底层 task。
- `wait` 是正式状态：进入 WAIT 后固定等待，到期投递 timeout。

## 总体架构

目标链路：

```text
外部消息 / 主动事件 / wait timeout
  -> Session Runtime
  -> InternalTurnQueue
  -> Pending Message Cache
  -> Context Builder
  -> Timing Gate
  -> Planner
  -> Tool Execution
  -> Reply Tool / Replyer
  -> 分句发送 / 写入历史 / 记忆情绪更新
  -> STOP / WAIT / 下一内部 round
```

我们自己的模块保留，但必须挂在固定阶段：

```text
Cognition / 视角摘要 -> context/reference message
Emotion / Affect -> dialogue state + prompt state
LifeMemory / UserProfile -> context + query tool
热回复池 -> pre-reply policy 或 reply tool 的一个实现
主动节奏 -> proactive event，进入同一 runtime
Forge/TLM 事件 -> external event adapter，不能绕过 runtime 发言
```

## 阶段一：Runtime 单循环重构

目标：彻底消除并发模型链路。

施工内容：

1. 新增内部事件类型：
   - `MESSAGE`
   - `TIMEOUT`
   - `PROACTIVE`

2. 新增或重写 `InternalTurnQueue`：
   - 串行队列。
   - 同一会话只允许一个 loop 消费。
   - 不允许外部消息直接启动模型请求。

3. 重写 `ConversationRuntime`：
   - 去掉 `Executors.newFixedThreadPool(2)`。
   - 改为单 worker / 单 internal loop。
   - `receiveUserMessage()` 只做缓存、状态标记、唤醒队列。
   - 不再每次新消息都 `CompletableFuture.runAsync`。

4. 保留并明确状态：
   - `RUNNING`
   - `WAIT`
   - `STOP`

5. 对齐运行时字段：
   - `messageCache`
   - `lastProcessedIndex`
   - `internalTurnQueue`
   - `messageTurnScheduled`
   - `messageDebounceRequired`
   - `lastMessageReceivedAt`
   - `oldestPendingMessageReceivedAt`
   - `waitTimeoutTask`
   - `plannerInterruptFlag`
   - `plannerInterruptRequested`
   - `plannerInterruptConsecutiveCount`

验收：

- 连续输入时不能并发出现多个 planner/replyer 请求。
- trace 中不应再出现大量旧请求完成后 discard 的情况。

## 阶段二：消息触发与合批

目标：把玩家连续输入识别为同一轮用户表达。

施工内容：

1. 重写 `scheduleMessageTurn()`：
   - WAIT 状态不触发。
   - 没有 pending 不触发。
   - 已 scheduled 不重复触发。
   - 强制触发直接入队。
   - 普通消息按阈值和静默窗口触发。

2. 重写 `collectPendingMessages()`：
   - 从 `lastProcessedIndex` 收集到最新。
   - 去重。
   - 一次性进入 history。
   - 更新 `lastProcessedIndex`。

3. 新增运行中静默窗口：
   - 运行中收到新消息只设置 `messageDebounceRequired=true`。
   - 等 `messageDebounceSeconds` 内没有新消息，再启动下一轮。

4. 增加触发策略：
   - 私聊直接命中时可以较快触发。
   - 连续短消息优先等静默窗口。
   - 高情绪消息可缩短静默窗口，但仍不并发开请求。

验收：

- 用户 1 秒内连发 5 条，只触发 1 次最终回复。
- 这 5 条在同一轮上下文里完整可见。

## 阶段三：真实 LLM Interrupt

目标：新消息到来时不只是丢弃旧输出，而是尽量取消旧请求。

施工内容：

1. 新增 `InterruptFlag`：
   - 内部用 `AtomicBoolean`。
   - 每轮 planner/replyer 持有当前 flag。

2. 修改 `OpenAiCompatibleClient`：
   - `sendAsync` 后循环等待。
   - 等待期间检查 interrupt flag。
   - flag set 后调用 `future.cancel(true)`。
   - 流式响应也要能提前关闭。
   - 抛出 `RequestAbortedException`。

3. `PlannerAgent`、`ReplyComposer` 接收 interrupt flag。

4. 连续打断控制：
   - 使用 `plannerInterruptMaxConsecutiveCount`。
   - 超过上限后不再打断当前请求，等它自然结束，再统一处理 pending。

验收：

- trace 能区分 `aborted` 和 `discarded`。
- 连续输入时旧请求不应继续大量占用后台。

## 阶段四：Timing Gate 对齐

目标：让 `wait/no_action/continue` 成为正式节奏层，而不是补丁。

施工内容：

1. 恢复独立 Timing Gate 标准阶段。

2. `wait` 语义：
   - 进入 WAIT。
   - 固定等待秒数。
   - wait 期间新消息进入 cache。
   - 不默认被新消息立即打断。
   - timeout 后投递 `TIMEOUT`。

3. `no_action` 语义：
   - 进入 STOP。
   - 等新消息唤醒。

4. `continue` 语义：
   - 当前内部 loop 继续下一 round。

5. 清理 timing 工具结果：
   - timing 工具结果不应污染 replyer 上下文。

验收：

- wait 到期后由 timeout 事件重进主循环。
- no_action 后不会主动继续烧模型。

## 阶段五：Planner / Tool Loop 对齐

目标：从简化的 `planner -> replyer` 改成工具式多轮推理。

施工内容：

1. 重写 `ReasoningEngine.runLoop()`：
   - 从 internal queue 取事件。
   - 每个事件最多 `maxInternalRounds`。
   - 每轮构建 context。
   - Timing Gate。
   - Planner。
   - Tool Execution。
   - 根据工具结果决定继续、等待、停止或回复。

2. 工具列表：
   - `reply`
   - `wait`
   - `no_action`
   - `continue`
   - `finish`
   - `query_memory`
   - `query_person_profile`
   - 后续接 Forge/TLM 工具。

3. 工具结果写回 history：
   - action 工具可作为 tool result。
   - timing 工具要按 request kind 过滤。

4. Planner 约束：
   - planner 不直接生成可见回复。
   - `reply` tool 触发 replyer。
   - `query_memory` 后可以继续 planner 或直接进入 reply，按工具结果配置。

验收：

- planner 输出工具调用。
- replyer 只由 reply tool 触发。
- query_memory 不会重复调用死循环。

## 阶段六：Context Message 分层

目标：上下文选择按请求类型过滤，而不是简单最近 N 条。

施工内容：

1. 新增 `ContextMessage` 抽象：
   - `UserMessage`
   - `AssistantMessage`
   - `ReferenceMessage`
   - `ToolResultMessage`
   - `SystemEventMessage`
   - `WorldObservationMessage`

2. Context 选择：
   - planner 可看工具链。
   - replyer 不看 timing gate 垃圾。
   - timing gate 可看最近聊天和必要状态。
   - 清理孤儿 tool result。
   - 保持 cache stability ratio，避免上下文边界剧烈变化。

3. 视角摘要接入：
   - 作为 `WorldObservationMessage`。
   - 不能直接触发可见发言。

验收：

- replyer prompt 里不出现 timing gate 内部说明。
- world observation 不被当成用户发言。

## 阶段七：Reply Tool 与 Replyer 对齐

目标：回复生成可监控、可重试、可后处理。

施工内容：

1. `reply` tool 调用 `ReplyComposer`。

2. ReplyComposer 输入：
   - reply target。
   - planner reason。
   - selected context。
   - identity/style。
   - affect/memory/profile。

3. 输出后处理：
   - 去掉动作括号。
   - 去掉内部分析。
   - 去掉 JSON/工具残留。
   - 控制重复口癖。
   - 分句。
   - 写入 assistant history。

4. 后续加 reply effect judge：
   - 检查复读。
   - 检查没接用户情绪。
   - 检查编造事实。
   - 必要时重试一次。

验收：

- 用户抱怨“不可爱/冷淡/无语”时，replyer 不会继续强行嘴硬。
- 不输出括号动作。
- 不编造没有依据的书架、早餐、打扫等事实。

## 阶段八：Memory / Profile / Affect 融合

目标：长期状态不再每轮硬塞一堆，而是 context 与 tool 双通道。

施工内容：

1. 保留现有模块：
   - `AffectProfile`
   - `LifeMemory`
   - `UserProfile`
   - daily summary

2. 新增工具：
   - `query_memory`
   - `query_person_profile`
   - `write_memory_event`
   - `summarize_day`

3. 调整写入时机：
   - 用户消息先进入 session。
   - 用户 turn 稳定后统一更新 affect/memory。
   - assistant 回复成功发送后再写 assistant memory。

4. 注入策略：
   - 每轮固定注入当前 affect brief。
   - 相关人生记忆少量注入。
   - 明确依赖历史时让 planner 调工具查。

5. 对话状态层：
   - `NORMAL_CHAT`
   - `USER_COMPLAINING`
   - `REPAIR_NEEDED`
   - `MAID_HURT`
   - `COOLDOWN_AFTER_CONFLICT`
   - `WAITING_USER_FINISH`

验收：

- 情绪数值和回复属于同一个稳定用户 turn。
- “无语了/呵呵/。”在冲突上下文里能进入修复状态，而不是普通收束。

## 阶段九：主动聊天对齐

目标：主动事件不绕过主链路。

施工内容：

1. 主动事件只投递 `PROACTIVE`。

2. PROACTIVE 写入 reference/context message。

3. 进入同一个 timing/planner/tool/reply loop。

4. 主动频率状态控制：
   - 刚回复过不主动。
   - 用户不满时少主动，优先修复。
   - 用户沉默时逐级降低频率。
   - 已主动一次无人回应，停止。

验收：

- 主动回复不会刷屏。
- 主动不再直接输出固定文案。
- 主动不会在冲突后假装没事硬开新话题。

## 阶段十：测试补齐

必须补以下测试：

1. 连续输入测试：
   - 5 条用户消息 1 秒内输入。
   - 只能触发一次最终回复。
   - 上下文里能看到全部 5 条。

2. interrupt 测试：
   - planner 正在跑时输入新消息。
   - 旧请求 abort 或最多自然结束一次。
   - 不能并发开多个 replyer。

3. wait 测试：
   - wait 期间输入消息。
   - 消息进入 cache。
   - timeout 后合并处理。

4. 修复模式测试：
   - 用户说“你一点都不可爱 / 我生气了 / 无语了”。
   - 不能 no_action。
   - 不能嘴硬顶回去。
   - 要承认没接住。

5. 主动节奏测试：
   - 女仆回复后计时。
   - 用户未回只允许一次主动。
   - 后续停止。

6. 上下文过滤测试：
   - replyer prompt 不含 timing gate 工具结果。
   - internal observation 不被当作用户消息。

## 推荐施工顺序

1. `ConversationRuntime` 单循环重构。
2. LLM interrupt token。
3. `ChatSession` pending/cache 语义对齐。
4. Timing Gate 和 wait/no_action/continue 对齐。
5. `ReasoningEngine` 工具循环重构。
6. ContextMessage 分层。
7. Reply tool 化。
8. Memory/Profile/Affect 按用户 turn 重接。
9. Proactive 重新接入。
10. 补齐测试并跑 GUI。

## 第一刀

第一刀必须是 runtime，不是 prompt。

具体改动目标：

- 去掉当前会话内的并发 worker。
- 加内部单循环队列。
- 新消息只缓存和唤醒，不直接开新模型链路。
- 运行中收到新消息只标记 debounce + interrupt。
- 当前轮结束或中断后，再收集最新 pending 跑下一轮。

只有这一步完成，后面的人设、记忆、主动、情绪才不会继续被过期请求和碎片消息拖歪。
