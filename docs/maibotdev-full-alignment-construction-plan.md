# MaidSoulCore Brain 对齐 maibotdev 完整施工方案

生成时间：2026-05-19

目标：把当前 Java 原型从“能跑稳的临时收敛版”继续改造成更接近 maibotdev 的会话核心。重点不是继续堆提示词，而是复刻 maibotdev 的运行时、工具链、上下文消息、回复后处理、回复效果追踪、记忆与用户画像注入方式。

## 0. 当前结论

当前 Java 原型已经解决了几个高危问题：

- 会话内单内部循环，避免并发请求互相抢话。
- 连续输入合批。
- 运行中收到新消息时 interrupt 当前模型链路。
- 旧轮过期后释放 `RUNNING`，避免卡死不回复。
- 可见回复改成完整生成后分段发送，避免半截旧输出泄漏。
- 对真实用户语义输入加了 `no_action` 覆盖保护，避免把用户反馈吞掉。

但它还不是 maibotdev 等价架构。当前仍有明显“原型兜底”痕迹：

- `ReasoningEngine` 里仍硬编码了 `query_memory`、`reply`、`no_action override` 等策略。
- `ReplyQualityGuard` 和 `fallbackForQuality` 偏临时，像安全闸，不是 maibotdev 的 reply effect 体系。
- `ChatMessage` 只有粗粒度 `USER / ASSISTANT / INTERNAL`，没有 maibotdev 的上下文消息分层。
- 底层流式没有被正确利用。现在是非流式完整生成，安全但慢；maibotdev 是底层可流式、可累积、用户侧完整后处理发送。
- 记忆和用户画像还没有按 maibotdev 的 `query_memory / query_person_profile / person_profile_injector` 拆开。
- reply 发出后的用户反馈没有被系统性记录、评分和回流。

## 1. maibotdev 关键参考点

### 1.1 Runtime

参考文件：

- `MaiBotDev/src/maisaka/runtime.py`
- `MaiBotDev/src/maisaka/chat_loop_service.py`
- `MaiBotDev/src/maisaka/builtin_tool/wait.py`

关键机制：

- 每个会话一个内部运行循环。
- 外部消息进入 cache，不是每条消息直接开模型请求。
- 内部 turn 队列串行消费：`message / timeout / proactive`。
- 运行中收到新消息时，设置 debounce required，并通过 interrupt flag 尝试中断。
- wait 是正式状态，到期后投递 timeout。

当前 Java 已部分对齐，但还需要继续拆清 tool loop 和 context。

### 1.2 Reply Tool

参考文件：

- `MaiBotDev/src/maisaka/builtin_tool/reply.py`
- `MaiBotDev/src/maisaka/builtin_tool/context.py`

maibotdev 链路：

```text
planner 调 reply tool
-> replyer.generate_reply_with_context(...)
-> 得到完整 reply_text
-> post_process_reply_message_sequences(reply_text)
-> 得到 reply_segments / MessageSequence
-> 逐段发送
-> track_reply_effect(...)
```

注意：maibotdev 底层支持 stream，但 reply tool 用户侧不是 token 直接外发。

### 1.3 Context Message

参考文件：

- `MaiBotDev/src/maisaka/context_messages.py`

关键类型：

- `SessionBackedMessage`
- `ComplexSessionMessage`
- `ReferenceMessage`
- `AssistantMessage`
- `ToolResultMessage`

每种消息都有：

- role
- source
- processed_plain_text
- count_in_context
- consume_once
- to_llm_message

当前 Java 的 `ChatMessage` 太粗，后续必须拆。

### 1.4 Reply Effect

参考文件：

- `MaiBotDev/src/maisaka/reply_effect/scoring.py`
- `MaiBotDev/src/maisaka/reply_effect/tracker.py`
- `MaiBotDev/src/maisaka/reply_effect/models.py`

maibotdev 已有规则：

```text
NEGATIVE_PATTERNS:
你没懂、没懂、不是这个意思、不是、别这样、好烦、烦死、算了、离谱、无语、你在说什么、听不懂、看不懂、错了、不对

REPAIR_PATTERNS:
我是说、我说的是、重新说、再说一遍、不是问、你理解错、你搞错、我问的是、纠正

POSITIVE_PATTERNS:
谢谢、感谢、懂了、明白了、可以、有用、不错、好耶、太好了
```

并且不是简单关键词，而是构造：

- BehaviorSignals
- FrictionSignals
- RubricScores
- ReplyEffectScores
- ASI 分数

当前 Java 应直接迁移这套基础规则，再把酒狐特化词放扩展层。

### 1.5 Person Profile

参考文件：

- `MaiBotDev/src/maisaka/person_profile_injector.py`
- `MaiBotDev/src/maisaka/builtin_tool/query_person_profile.py`

关键机制：

- 根据当前消息对象收集画像候选。
- 私聊优先当前用户。
- 群聊还会考虑 recent speaker、at user、reply sender。
- 查询画像后注入内部参考消息。
- 画像是内部理解，不逐字复述。

当前 Java 只有粗粒度 `UserProfile.renderForPrompt()`，还不够。

## 2. 目标架构

最终结构：

```text
ConversationRuntime
  -> InternalTurnQueue
  -> ChatSession
  -> ContextStore
  -> ReasoningEngine
       -> TimingGate
       -> PlannerAgent
       -> ToolExecutor
            -> ReplyTool
                 -> ReplyComposer
                 -> ReplyPostProcessor
                 -> ReplyRiskPolicy
                 -> ReplySender
                 -> ReplyEffectTracker.recordReply
            -> WaitTool
            -> NoActionTool
            -> ContinueTool
            -> FinishTool
            -> QueryMemoryTool
            -> QueryPersonProfileTool
  -> ReplyEffectTracker.observeUserMessage
  -> StableTurnMemoryWriter
```

原则：

- 运行时只负责编排，不写角色策略。
- planner 只选工具，不直接生成可见回复。
- reply tool 才生成可见回复。
- 用户侧不接收 token 流式，只接收完整后处理后的分段消息。
- 底层模型可以流式，流式内容只进内部 buffer。
- 负反馈不靠临时 prompt，而靠 reply effect 记录和下一轮状态回流。

## 3. 施工阶段

## 阶段一：修正流式模型

目标：恢复底层流式能力，但禁止 token 直接可见外发。

当前问题：

- 之前 `StreamingSegmentEmitter` 会边收 delta 边 `output.accept()`，导致旧轮被打断后半截文本已经发给用户。
- 后来改成完全非流式，安全但慢。

施工内容：

1. 新增 `ReplyBuffer`：
   - 接收 delta。
   - 内部累积完整文本。
   - interrupt / stale version 时丢弃。
   - 不直接调用 `output.accept()`。

2. 修改 `ReplyComposer`：
   - 支持 `composeBufferedStreamingWithMeta(...)`。
   - 底层调用 `llm.chatStream(...)`。
   - 完成后返回完整 `LlmReply`。

3. 移除或废弃 `ConversationRuntime.StreamingSegmentEmitter` 的可见发送能力。

4. `ConversationRuntime.emitReply(...)` 仍负责最终分段发送。

验收：

- 新消息 interrupt 时不会出现半截旧回复。
- trace 能看到 stream 请求被 abort。
- 15 轮测试不再出现半句泄漏。

## 阶段二：工具执行器拆分

目标：从 `ReasoningEngine` 硬编码动作改成 maibotdev 式工具执行。

新增模块：

```text
tool/runtime/ToolExecutor.java
tool/runtime/ToolExecutionContext.java
tool/runtime/BuiltinToolRuntimeContext.java
tool/builtin/ReplyTool.java
tool/builtin/WaitTool.java
tool/builtin/NoActionTool.java
tool/builtin/ContinueTool.java
tool/builtin/FinishTool.java
tool/builtin/QueryMemoryTool.java
tool/builtin/QueryPersonProfileTool.java
```

施工内容：

1. `PlannerAgent` 只返回 tool calls。
2. `ReasoningEngine` 调 `ToolExecutor.execute(...)`。
3. `query_memory` 从 `ReasoningEngine` 分支移到 `QueryMemoryTool`。
4. `reply` 从 `ReasoningEngine` 直接调用 `ReplyComposer` 改成 `ReplyTool` 调用。
5. `wait/no_action/finish/continue` 全部工具化。

验收：

- `ReasoningEngine` 里不再出现 `if ("query_memory".equals(...))` 这类硬编码分支。
- 每个工具都有独立测试。
- 工具结果可写入上下文。

## 阶段三：ContextMessage 分层

目标：替换粗糙 `ChatMessage.INTERNAL` 字符串过滤。

新增抽象：

```text
context/ContextMessage.java
context/SessionUserMessage.java
context/AssistantContextMessage.java
context/ReferenceContextMessage.java
context/ToolResultContextMessage.java
context/SystemEventContextMessage.java
context/ProactiveObservationMessage.java
context/ContextStore.java
context/ContextSelector.java
```

字段：

- id
- timestamp
- role
- source
- content
- countInContext
- visibleToPlanner
- visibleToReplyer
- visibleToTimingGate
- remainingUses

施工内容：

1. `ChatSession.history` 从 `List<ChatMessage>` 迁移到 `ContextStore`。
2. 用户消息用 `SessionUserMessage`。
3. 主动观察用 `ProactiveObservationMessage`。
4. 记忆/画像注入用 `ReferenceContextMessage`。
5. 工具返回用 `ToolResultContextMessage`。
6. `ContextWindow` 改成 `ContextSelector`，按 request kind 选择。

验收：

- replyer prompt 不靠字符串判断过滤 `[现场观察]`。
- timing/proactive 不污染 replyer。
- reference message 可一次性消费。

## 阶段四：Reply 后处理改成 maibotdev 风格

目标：区分“常规后处理”和“高风险质量检查”。

新增：

```text
reply/ReplyPostProcessor.java
reply/ReplySegment.java
reply/ReplyMessageSequence.java
reply/ReplyRiskPolicy.java
reply/ReplySender.java
```

常规后处理：

- 分句。
- 去空白。
- 去明显内部标签。
- 去括号动作。
- 预留 at 标记解析。
- 预留图片/表情组件。

高风险检查：

- 只在 `DialogueMode.REPAIR_NEEDED`、`COOLDOWN_AFTER_CONFLICT`、明确投诉、主动追话时启用。
- 不常态审判每条回复。

当前要调整：

- `ReplySanitizer` 改名并降级为 `ReplyPostProcessor` 的一部分。
- `ReplyQualityGuard` 改为 `ReplyRiskGuard`，只由 `ReplyRiskPolicy` 决定是否启用。
- `fallbackForQuality` 移到 `RiskFallbackPolicy`，不放在 `ReplyComposer`。

验收：

- 普通聊天不触发质量重试。
- 高风险回复失败时才重试或兜底。
- 无括号动作、无内部 JSON。

## 阶段五：Reply Effect 原样迁移

目标：照搬 maibotdev 的 reply effect 基础规则。

新增：

```text
reply/effect/ReplyEffectPatterns.java
reply/effect/FollowupMessageSnapshot.java
reply/effect/BehaviorSignals.java
reply/effect/FrictionSignals.java
reply/effect/RubricScores.java
reply/effect/ReplyEffectScores.java
reply/effect/ReplyEffectRecord.java
reply/effect/ReplyEffectScoring.java
reply/effect/ReplyEffectTracker.java
reply/effect/ReplyEffectStore.java
```

基础规则原样迁移：

```text
NEGATIVE_PATTERNS:
你没懂、没懂、不是这个意思、不是、别这样、好烦、烦死、算了、离谱、无语、你在说什么、听不懂、看不懂、错了、不对

REPAIR_PATTERNS:
我是说、我说的是、重新说、再说一遍、不是问、你理解错、你搞错、我问的是、纠正

POSITIVE_PATTERNS:
谢谢、感谢、懂了、明白了、可以、有用、不错、好耶、太好了
```

扩展规则：

```text
MaidSoulExtraNegativePatterns:
不可爱、不理我、谁家女仆、口癖、啧啧、老哼、乱编、没根据
```

注意：

- maibotdev 基础规则放 `ReplyEffectPatterns`。
- 酒狐扩展规则放 `MaidSoulReplyEffectPatterns`。
- 不要混在一起。

结算规则照搬：

- 目标用户明确负反馈 -> `explicit_negative`
- 目标用户修复循环 -> `repair_loop`
- 目标用户后续 2 条 -> `target_user_followups`
- 会话后续 5 条 -> `session_followups_limit`
- 超时 -> `window_timeout`

评分照搬：

- `buildBehaviorSignals`
- `buildFrictionSignals`
- `calculateBehaviorScore`
- `calculateRelationalScore`
- `calculateFrictionScore`
- `calculateAsiScore`
- `estimateSentiment`

验收：

- 用户回复“无语/不是/你没懂/算了”能归因到上一条 bot 回复。
- 下一轮状态能读取最近 reply effect。
- 15 轮测试中用户投诉不再只靠当前关键词临时判断。

## 阶段六：DialogueState 接入 ReplyEffect

目标：让对话状态来自 reply effect，而不是当前消息关键词硬猜。

施工内容：

1. `DialogueStateTracker` 接收：
   - pending messages
   - recent context
   - latest reply effect summary

2. 优先级：

```text
explicit_negative / repair_loop -> REPAIR_NEEDED
high friction -> USER_COMPLAINING
recent apology after conflict -> COOLDOWN_AFTER_CONFLICT
attack words -> MAID_HURT
pending >= 3 -> WAITING_USER_FINISH
else NORMAL_CHAT
```

3. 删除大部分硬编码当前词。
4. 当前词仅作为辅助信号。

验收：

- 用户“无语了”如果是上一条回复后的反馈，会进入修复状态。
- 用户普通“嗯”不总是收束，要结合上一条 reply effect 和上下文。

## 阶段七：记忆与画像工具化

目标：对齐 maibotdev 的 `query_memory` 和 `query_person_profile`。

新增：

```text
memory/tool/QueryMemoryTool.java
memory/tool/QueryPersonProfileTool.java
memory/profile/PersonProfileInjector.java
memory/profile/PersonProfileCandidate.java
```

固定注入只保留：

- 当前情绪关系 brief。
- 最近 reply effect summary。
- 少量稳定画像摘要。

工具查询负责：

- 历史偏好。
- 长期记忆。
- 用户画像细节。
- 明确“还记得吗/之前/上次/我说过/我在意什么”等问题。

画像候选：

- 私聊：当前用户。
- 后续接群聊：recent speaker、at user、reply sender。

验收：

- “你记得我前面在意什么吗？”会触发 query_memory 或 query_person_profile。
- 普通情绪接话不滥查长期记忆。
- 用户画像记录“讨厌口癖/乱编/收束太快”等偏好。

## 阶段八：删除临时补丁味代码

需要迁移或删除：

- `ReasoningEngine.shouldOverrideNoActionForUserInput`
  - 改成 `NoActionPolicy`。

- `ReplyComposer.fallbackForQuality`
  - 改成 `RiskFallbackPolicy`。

- `ReplyQualityGuard`
  - 降级为高风险 `ReplyRiskGuard`。

- `ContextWindow` 字符串过滤
  - 改成 `ContextSelector` 可见性。

- `ConversationRuntime.StreamingSegmentEmitter`
  - 删除可见流式发送能力，改内部 buffer。

验收：

- `ReasoningEngine` 不再承担策略杂活。
- `ReplyComposer` 只生成回复，不负责风险兜底。
- Runtime 只做调度，不做人设判断。

## 阶段九：测试计划

### 离线测试

1. Runtime：
   - 连续输入合批。
   - 运行中 interrupt 后重跑最新 pending。
   - WAIT 期间缓存消息，timeout 后合并处理。
   - 主动事件只主动一次，无回应停止。

2. Tool：
   - planner 调 reply tool。
   - planner 调 wait tool。
   - planner 调 query_memory。
   - unknown tool 安全失败。

3. Context：
   - planner 能看到 tool result。
   - replyer 看不到 timing/proactive 内部观察。
   - reference remainingUses 生效。

4. Reply：
   - 内部流式 buffer 不外泄。
   - interrupt 丢弃 buffer。
   - post-process 去括号动作。
   - 高风险才触发 guard。

5. ReplyEffect：
   - 负反馈命中。
   - 修复循环命中。
   - 正反馈命中。
   - ASI 计算。
   - pending record finalize。

### 真实接口测试

固定四组：

1. 15 轮关系/口癖/记忆测试。
2. 连续快速输入打断测试。
3. 主动沉默测试。
4. 负反馈追踪测试：
   - bot 回复后，用户说“不是这个意思/无语/你没懂”。
   - 检查 reply effect 是否归因上一条回复。
   - 下一轮是否进入修复状态。

验收标准：

- 无半截旧输出。
- 无括号动作。
- 无重复口癖。
- 无语义用户输入被 no_action 吞掉。
- 负反馈可归因。
- 平均耗时可接受，普通聊天目标低于当前 17s。

## 4. 推荐施工顺序

1. 内部流式 buffer，外部完整分段发送。
2. ReplyPostProcessor 替换现有常态清洗。
3. ReplyTool 抽出。
4. ToolExecutor 抽出。
5. ContextMessage / ContextStore / ContextSelector。
6. ReplyEffectScoring 原样迁移。
7. ReplyEffectTracker 接入。
8. DialogueStateTracker 改读 reply effect。
9. QueryPersonProfileTool / PersonProfileInjector。
10. 降级或删除临时 guard / fallback / override。
11. 完整离线测试。
12. 四组真实接口测试。

## 5. 当前优先级

最高优先级：

1. 内部流式 buffer。
2. ReplyEffectScoring 原样迁移。
3. ReplyEffectTracker 接入。
4. ReplyTool 工具化。
5. ContextMessage 分层。

原因：

- 内部流式 buffer 能解决速度和半截泄漏的矛盾。
- ReplyEffect 能避免继续靠当前词和 prompt 补丁猜用户情绪。
- ReplyTool 和 ContextMessage 是对齐 maibotdev 的架构根基。

## 6. 风险提示

- 不要一次性全重写，否则 GUI 测试会断层。
- 每个阶段都要保持 `BrainGui` 可启动。
- 每阶段必须跑离线测试。
- 每两阶段至少跑一次 15 轮真实接口。
- 任何“为了立刻好看”的 prompt 规则，都要优先考虑能否落成状态、工具、上下文或 reply effect。

## 7. 完成定义

达到以下条件，才算本轮对齐完成：

- Java 原型具备 maibotdev 风格 tool loop。
- reply 链路为完整生成后处理发送，底层可流式累积。
- reply effect 可记录、评分、回流。
- ContextMessage 分层，不靠字符串过滤内部信息。
- 记忆和人物画像通过工具和内部参考注入。
- 普通聊天不跑重 guard，高风险才跑风险检查。
- 15 轮真实接口测试全 PASS，且平均耗时相比当前非流式版明显下降。

## 8. 2026-05-19 本轮落地进度

已完成：

- 新增 `ReplyBuffer`，底层 `chatStream` 只进入内部缓冲，不再边生成边可见发送。
- 移除 `ConversationRuntime.StreamingSegmentEmitter` 的可见发送路径，统一由 `emitReply(...)` 在完整后处理后分段发送。
- 新增 `ReplyPostProcessor`，把常规输出整理从“角色补丁”里剥离出来。
- 新增 `ReplyRiskPolicy`，`ReplyQualityGuard` 只在关系修复、冲突、口癖投诉、主动追话等高风险场景启用；普通聊天保留快速路径。
- 新增 `reply.effect` 包，源码级迁移 maibotdev 的行为信号、摩擦信号、ASI 评分和结算规则。
- 单独新增 `MaidSoulReplyEffectPatterns`，把酒狐原型暴露出来的“不可爱/不理我/口癖/乱编”等本地反馈放在扩展层，不污染 maibotdev 原始规则。
- `ConversationRuntime.receiveUserMessage(...)` 已观察后续用户反馈，`emitReply(...)` 后已登记回复效果记录。
- `DialogueStateTracker` 已读取最新 reply effect summary，负反馈和修复循环优先进入修复/抱怨状态。

本轮已验证：

```text
.\scripts\build.ps1
java -cp .\out\classes com.maidsoul.brain.RuntimeLoopSmokeTest
java -cp .\out\classes com.maidsoul.brain.ReplyQualityGuardSmokeTest
java -cp .\out\classes com.maidsoul.brain.ProactiveRhythmSmokeTest
java -cp .\out\classes com.maidsoul.brain.SmokeTest
```

下一步仍需继续拆的结构性工作：

- `ReplyTool / ToolExecutor` 还没有完全从 `ReasoningEngine` 中抽离。
- `ContextMessage / ContextStore / ContextSelector` 还没有替换当前粗粒度 `ChatMessage`。
- `QueryPersonProfileTool / PersonProfileInjector` 还需要继续对齐 maibotdev。
