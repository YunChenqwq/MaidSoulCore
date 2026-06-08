# MaidSoulCore 事件矩阵与裁决链路（面向 TLM 1.20 扩展）

> 目标：在**不改 TouhouLittleMaid 源码**前提下，做一个“持续在线、可主动互动、像真人陪伴”的女仆 AI 扩展。

## 1. 先明确：并不是“所有事件都丢给 LLM”

核心思路是三层裁决：

1. **本地硬规则层（毫秒级）**  
   生存安全、动作合法性、冷却与互斥，绝不等待网络。
2. **混合决策层（百毫秒~秒级）**  
   规则筛掉危险动作后，给 Planner 模型做结构化计划。
3. **表达层（秒级）**  
   Reply 模型负责“像人聊天”，但不能越权执行高风险动作。

这样做的结果：
- 战斗/受击/掉血不会“等 LLM 想半天”；
- 对话和陪伴仍然足够拟人；
- 系统成本和延迟可控。

---

## 2. TLM 1.20 可直接利用的事件源（已验证）

以下触发点都来自 TLM 源码，可作为 MaidSoulCore 订阅输入：

- `MaidTickEvent`：`EntityMaid.java:532`
- `InteractMaidEvent`：`EntityMaid.java:664`
- `MaidTamedEvent`：`EntityMaid.java:701`
- `MaidHurtTarget.Pre/Post`：`EntityMaid.java:915`、`EntityMaid.java:937`
- `MaidAttackEvent`（女仆被攻击）：`EntityMaid.java:973`
- `MaidDeathEvent`：`EntityMaid.java:1037`
- `MaidAfterEatEvent`：`EntityMaid.java:1666`
- 睡眠进入：`MaidBedTask.java:60`（`startSleeping`）
- 睡眠退出：`MaidClearSleepTask.java:17`（`stopSleeping`）

补充可做状态差分监听的 setter：
- `setHomeModeEnable`：`EntityMaid.java:2103`
- `setSchedule`：`EntityMaid.java:2285`
- `setTask`：`EntityMaid.java:2352`
- `setInSittingPose`：`EntityMaid.java:2364`

---

## 3. 事件标准化协议（给内部总线，不直接给 LLM）

所有外部事件先转成统一结构：

```json
{
  "event_id": "uuid",
  "maid_id": "entity_uuid",
  "owner_id": "player_uuid",
  "type": "maid.attacked | maid.feed | maid.sleep.enter ...",
  "ts": 1710000000000,
  "priority": "P0|P1|P2",
  "ttl_ms": 3000,
  "payload": {},
  "snapshot_ref": "blackboard/version"
}
```

必须做三件事：
- **去抖/合并**：1 秒内重复受击合并为一次战斗摘要；
- **预算控制**：每 N 秒最多向 LLM 发送 K 条事件；
- **过期丢弃**：超 `ttl_ms` 的事件不再触发远程裁决。

---

## 4. 事件分级矩阵（L0/L1/L2）

### L0：硬实时，本地规则闭环（不等 LLM）
- 典型事件：受击、低血、爆炸物接近、摔落风险。
- 处理：立即执行保命动作（闪避/回家/防御/跟随主人）。
- LLM 作用：事后总结和对话解释，不参与实时决策。

### L1：混合层（规则优先 + 可选 Planner）
- 典型事件：喂食、任务切换、坐下/跟随切换、捡拾冲突。
- 处理：先做合法性检查，再由 Planner 给“下一步动作序列”。
- LLM 作用：决定策略细节（例如优先护主还是先补给）。

### L2：叙事社交层（LLM 主导）
- 典型事件：闲聊、安抚、鼓励、日常建议、关系推进。
- 处理：Reply + Mood + Memory 联动生成拟人表达。
- 约束：不允许直接下发危险工具调用。

---

## 5. 你提到的“广播给 LLM 裁决”怎么做最稳

建议不是“广播原始事件”，而是“广播事件摘要包”：

```text
raw events -> EventNormalizer -> PriorityQueue
           -> EventAggregator(1s/3s窗口)
           -> DecisionGate(预算/延迟/风险)
           -> PlannerInputBuilder
           -> LLM
```

`DecisionGate` 决策规则：
- `P0`（生存风险）→ 本地执行，LLM仅记录；
- `P1`（行为策略）→ 若网络可用则调用 Planner，否则本地 fallback；
- `P2`（陪伴叙事）→ 合并后批量送 Reply/Planner。

---

## 6. 睡觉/作息/状态切换：用“状态差分”补齐事件缺口

因为睡眠没有稳定的独立 API 事件，建议在 `MaidTickEvent` 中做差分：

- `isSleeping: false -> true` 触发 `maid.sleep.enter`
- `isSleeping: true -> false` 触发 `maid.sleep.exit`
- `schedule` 变化触发 `maid.schedule.changed`
- `task` 变化触发 `maid.task.changed`
- `sittingPose` 变化触发 `maid.pose.sit_changed`

这类差分事件属于 `L1`，可用于：
- 调整情绪（休息后 calm 上升）；
- 主动聊天（“我休息好了，可以继续帮你啦”）；
- 调整下一阶段行为策略。

---

## 7. “像一个人”的关键：Mood 不是装饰，是策略输入

建议用三轴情绪状态：
- `valence`（愉悦度）
- `arousal`（激活度）
- `bond`（亲密度）

并设更新源：
- 战斗压力、受击、饥饿、长期无互动、主人行为反馈。

Mood 直接影响两件事：
1. **动作倾向**：高 arousal 更容易选择警戒/护卫动作；
2. **语言风格**：高 bond 语气更亲近、主动频率更高。

---

## 8. 多模型协作（参考 MaiBot，但做成 MC 可运行版本）

最小可行协作拓扑：

1. **Planner 模型（结构化）**  
   输入事件摘要 + 黑板快照，输出 `ActionPlan(JSON)`。
2. **Reply 模型（拟人表达）**  
   只负责语言与情绪一致性，不直接定高风险动作。
3. **Vision 模型（异步）**  
   定时截图（例如 2~5 秒一次，战斗时提高频率），输出标签写入黑板。
4. **Summary 模型（低频）**  
   每 1~5 分钟压缩记忆，降低上下文成本。

---

## 9. 建议的模块拆分（MaidSoulCore 内部）

- `event-ingest`：Forge 事件订阅 + TLM 事件适配
- `state-diff`：Tick 差分检测（睡觉/任务/作息/姿态）
- `blackboard`：统一状态快照、TTL 缓存、版本号
- `decision-gate`：分级、预算、回退、熔断
- `planner-runtime`：Planner 提示词 + JSON schema 校验
- `tool-runtime`：工具注册、权限控制、幂等与冷却
- `mood-memory`：情绪更新、短期记忆、长期摘要
- `reply-runtime`：人设风格化输出与对齐

---

## 10. 第一阶段落地清单（建议 2~3 周）

### Week 1：事件底座
- 打通 `Interact/Attack/AfterEat/Tick/Death` 订阅。
- 完成 `MaidTickEvent` 差分事件生成。
- 完成事件标准化与本地队列。

### Week 2：裁决闭环
- 实现 `DecisionGate`（L0/L1/L2）。
- 接入 Planner（先一个模型）。
- 工具执行器加冷却/互斥/失败回退。

### Week 3：拟人化增强
- 接入 Mood 三轴与主动互动策略。
- 接入 Reply 模型（语气与人格一致）。
- 加入低频 Summary 和记忆压缩。

---

## 11. 最关键的工程原则

- **安全优先**：高风险动作必须本地可控，LLM 只能建议。
- **事件不是越多越好**：要合并、分级、预算化。
- **先能跑，再像人**：先做稳定闭环，再迭代情绪与表达。
- **独立模组边界清晰**：`MaidSoulCore` 只通过扩展接口接入，不侵入 TLM 本体。

