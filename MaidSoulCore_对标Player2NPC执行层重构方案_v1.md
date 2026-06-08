# MaidSoulCore 对标 Player2NPC / PlayerEngine 的执行层重构方案 v1

## 1. 目的

这份方案只解决一个核心问题：

- 让 `MaidSoulCore` 的女仆从“聊天驱动动作”升级为“目标驱动执行”
- 让规划、聊天、动作执行三层彻底解耦
- 解决当前反复出现的几个顽疾：
  - 攻击锁定后不打
  - 多目标切换混乱
  - 动作执行慢、反馈慢
  - planner 像一次性决策器，不像持续规划器
  - 聊天链路和动作链路互相阻塞

---

## 2. 我们当前架构的问题

### 2.1 目前本质上还是“聊天触发动作”

当前链路更接近：

1. 主人发话
2. planner 解析意图
3. 生成一次动作决策
4. 执行器执行一次
5. 等待下一次聊天或下一次事件

这个模型的问题是：

- 动作没有独立生命周期
- planner 不持有持续中的目标状态
- 执行器更像“立即执行器”，不是“持续推进器”
- 聊天一旦拥塞，动作反馈也会变慢

### 2.2 攻击问题为什么一直反复出现

攻击问题不是单点 bug，而是执行层设计不完整：

- 没有稳定的“当前主目标”
- 没有明确的“次级待处理目标队列”
- 没有统一的目标丢失、超时、切换策略
- 没有单独的攻击推进 tick
- 没有把“寻路中 / 进入攻击距离 / 攻击挥手 / 命中确认 / 击杀确认”拆成状态机

所以你会看到：

- 女仆转圈
- 女仆站着不动
- 一直挥空刀
- 同时盯两个目标
- 清群体目标时无差别攻击

### 2.3 planner 现在不是“计划器”，只是“高层分类器”

目前 planner 的职责更像：

- 识别这句话是什么意图
- 要不要回复
- 要不要顺手触发一个动作

但它没有真正承担：

- 目标分解
- 子任务排序
- 执行中状态跟踪
- 失败恢复
- 执行结果回灌后再规划

所以你会感觉“planner 不聪明”，这是准确的。

### 2.4 聊天和动作执行互相污染

目前几类链路仍有耦合：

- 主人聊天链路
- 主动陪伴链路
- 视觉链路
- 动作执行链路

耦合后果：

- 聊天时视觉抢算力
- 动作执行中的反馈容易滞后
- 事件很多时聊天节奏发散
- planner 输出一次就结束，没有持续控制感

---

## 3. Player2NPC 值得借的不是代码，而是结构

`Player2NPC` 的价值不在于可以直接拷代码，而在于它的结构非常清楚：

### 3.1 LLM 只给高层目标

LLM 不直接负责底层动作细节，而是负责：

- 理解自然语言
- 给出高层命令
- 选择目标

### 3.2 控制器持续推进

真正推进动作的是控制器 / task system：

- 每 tick 推进一次
- 直到完成、失败或取消
- 不依赖聊天回调再次触发

### 3.3 实体层只做能力壳

实体本体只负责：

- 移动能力
- 交互能力
- 背包能力
- 攻击能力
- controller tick 接入

### 3.4 会话层和执行层解耦

聊天说什么、动作怎么做，是两套系统：

- 会话层负责“说”
- 执行层负责“做”

这正是我们现在缺的。

---

## 4. MaidSoulCore 重构后的目标架构

建议重构成五层。

### 4.1 第 1 层：感知层 `Perception Layer`

负责输入世界状态，不做决策。

建议保留并继续扩展：

- `MaidSoulVisionService`
- `MaidSoulEntityAwarenessService`
- `MaidSoulStateRegistry`

职责：

- 主人视角摘要
- 附近实体摘要
- 天气/时间/受击/任务变化事件
- 当前任务进度快照

输出统一写入：

- `World Snapshot`
- `Event Stream`

### 4.2 第 2 层：会话层 `Conversation Layer`

负责聊天，不直接驱动具体动作。

建议保留：

- `MaidSoulChatLoopRuntimeService`
- `MaidSoulChatRuntimeService`
- `MaidSoulPromptService`
- `MaidSoulReplyPostProcessor`

职责：

- 理解主人命令
- 生成回复
- 生成“意图对象”
- 生成“要不要插话”的决策

输出不再直接是动作执行，而是：

- `ConversationIntent`
- `SpeechPlan`
- `ActionRequest`

### 4.3 第 3 层：规划层 `Planning Layer`

这是当前最缺的一层。

建议新增：

- `MaidSoulPlannerService`
- `MaidSoulPlanContext`
- `MaidSoulExecutionPlan`
- `MaidSoulPlanStep`

职责：

- 把 `ActionRequest` 转成执行计划
- 根据当前执行状态决定下一步
- 在失败后重新规划
- 在目标丢失后回退

planner 不再直接“让女仆去打”，而是生成：

- 目标类型
- 优先级
- 步骤列表
- 成功条件
- 失败条件
- 超时条件

### 4.4 第 4 层：执行层 `Execution Layer`

这是这次最重要的重构目标。

建议新增：

- `MaidSoulExecutionController`
- `MaidSoulExecutionSession`
- `MaidSoulTaskDriver`
- `MaidSoulTaskResult`

按任务类型拆 driver：

- `FollowTaskDriver`
- `SitTaskDriver`
- `ScheduleTaskDriver`
- `SingleAttackTaskDriver`
- `GroupAttackTaskDriver`
- 后续可加：
  - `CollectTaskDriver`
  - `ProtectOwnerTaskDriver`
  - `GoToTaskDriver`

职责：

- 持续推进当前任务
- 每 tick 更新一次
- 自己维护状态机
- 自己判断完成/失败/超时
- 把执行反馈回写给 planner 和聊天层

### 4.5 第 5 层：反馈层 `Feedback Layer`

建议新增：

- `MaidSoulFeedbackOrchestrator`

职责：

- 任务开始时反馈给主人
- 任务切换时反馈给主人
- 条件不足时解释原因
- 执行超时时解释原因
- 任务完成时汇报结果

注意：

- 反馈层只发“高价值反馈”
- 不直接参与动作逻辑

---

## 5. 最关键的执行模型：从“动作调用”改成“执行会话”

### 5.1 新模型

每只女仆同一时刻只允许一个主执行会话：

- `ExecutionSession`
  - `sessionId`
  - `planId`
  - `currentStepIndex`
  - `status`
  - `startedAt`
  - `lastProgressAt`
  - `timeoutAt`
  - `lockedTarget`
  - `queuedTargets`
  - `lastFailureReason`

### 5.2 状态

执行状态建议统一为：

- `IDLE`
- `PREPARING`
- `RUNNING`
- `WAITING_PATH`
- `WAITING_COOLDOWN`
- `BLOCKED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

### 5.3 为什么这会解决现在的“慢”

因为动作不再依赖：

- 再次聊天
- 再次 planner 调用
- 再次 tool loop

而是：

- planner 只在开始时给出目标
- controller 每 tick 继续推进

这会让体感从：

- “说一句，等很久，动一下”

变成：

- “说一句，立即确认，然后动作持续推进”

---

## 6. 攻击系统重构方案

攻击必须单独讲，因为这是现在最不稳定的一块。

### 6.1 分成两种任务，不再混用

#### A. `SingleAttackTaskDriver`

用于：

- 攻击一只指定实体

输入：

- `entityId`

行为：

1. 锁定该实体
2. 若实体不存在 -> 失败
3. 若距离过远 -> 持续寻路接近
4. 若进入攻击距离 -> 才允许挥刀
5. 若实体死亡 -> 完成
6. 若超时 -> 失败

#### B. `GroupAttackTaskDriver`

用于：

- 攻击一类实体，比如“一群兔子”

输入：

- `entityType`
- `selectorRule`
- `maxCount`

行为：

1. 扫描候选目标
2. 先排序
3. 一次只锁一个目标
4. 当前目标死亡/失效后再切换到下一个
5. 全部清空后完成

### 6.2 群攻任务的关键规则

必须固定：

- 同一时刻只有一个 `currentLockedTarget`
- 其他候选目标只在 `queuedTargets`
- 不允许多个目标同时参与朝向/攻击判定

### 6.3 攻击状态机建议

单目标攻击状态：

- `ACQUIRE_TARGET`
- `APPROACH_TARGET`
- `FACE_TARGET`
- `ATTACK_WINDOW`
- `CONFIRM_HIT`
- `CONFIRM_KILL`
- `TARGET_LOST`
- `TIMEOUT`

### 6.4 挥刀条件必须严格收紧

只有同时满足以下条件才允许挥刀：

- 当前存在 `lockedTarget`
- `lockedTarget` 还活着
- 距离在攻击范围内
- 朝向误差在阈值内
- 当前不在路径重算中
- 攻击冷却已恢复

这能直接解决：

- 一直挥空刀
- 距离不够也挥刀
- 一边寻路一边乱挥

### 6.5 攻击超时

建议保留超时，但放到执行层里，不放在 planner 里。

建议：

- 默认 15 秒
- 配置化
- 超时后返回失败原因：
  - 路径阻塞
  - 目标跑太远
  - 没有命中窗口
  - 武器不足

---

## 7. planner 应该怎么改

### 7.1 planner 不再直接输出“立刻执行”

当前偏向：

- `action_type`
- `action_value`
- `target_entity_id`

建议升级为：

- `goal_type`
- `goal_payload`
- `execution_mode`
- `priority`
- `should_reply_now`
- `reply_style`

### 7.2 planner 的新职责

planner 负责：

- 决定是不是新目标
- 决定是否中断当前任务
- 决定目标优先级
- 决定是否需要给主人即时反馈

planner 不负责：

- 每一步怎么走
- 每 tick 怎么推进
- 什么时候挥刀

这些全部交给执行层。

### 7.3 planner 要有“中断策略”

建议明确：

- 高优先级事件可打断当前任务：
  - 主人直接命令
  - 女仆受击
  - 高危怪靠近
  - 当前任务失败
- 低优先级事件不能打断：
  - 小动物
  - 风景
  - 普通闲聊

---

## 8. 聊天层应该怎么配合执行层

### 8.1 主人下命令时

流程改成：

1. 会话层识别命令
2. planner 生成目标
3. 执行层创建会话
4. 反馈层立即说一句开始执行

这样就不会出现：

- 半天不回
- 做完才一口气说两句

### 8.2 执行中

执行层主动抛事件：

- `task.started`
- `task.progress.changed`
- `task.target.locked`
- `task.target.cleared`
- `task.blocked`
- `task.failed`
- `task.completed`

反馈层只挑必要事件发话。

### 8.3 执行完成后

执行层不自己说话，只抛结果：

- 完成
- 失败
- 超时
- 被中断

再由反馈层决定怎么说。

---

## 9. 这套重构对我们现有类的影响

### 9.1 建议保留

- `MaidSoulPromptService`
- `MaidSoulChatRuntimeService`
- `MaidSoulChatLoopRuntimeService`
- `MaidSoulVisionService`
- `MaidSoulCompanionService`
- `MaidSoulStateRegistry`

这些保留，但职责要缩窄。

### 9.2 建议逐步弱化

- `MaidSoulActionExecutorService`

它现在更像一个大杂烩执行器。

建议把它拆成：

- `MaidSoulExecutionController`
- 多个 `TaskDriver`

### 9.3 建议新增

- `MaidSoulExecutionController`
- `MaidSoulExecutionSession`
- `MaidSoulPlannerService`
- `MaidSoulExecutionPlan`
- `MaidSoulTaskDriver`
- `MaidSoulSingleAttackTaskDriver`
- `MaidSoulGroupAttackTaskDriver`
- `MaidSoulFeedbackOrchestrator`

---

## 10. 分阶段落地方案

### Phase 1：先把攻击层独立出来

目标：

- 只重构攻击链

具体做法：

- 引入 `ExecutionSession`
- 引入 `SingleAttackTaskDriver`
- 引入 `GroupAttackTaskDriver`
- planner 只负责提交攻击目标
- 攻击推进改为每 tick 持续执行

完成后能先解决：

- 锁定不打
- 转圈
- 多目标混乱
- 无差别攻击

### Phase 2：把跟随 / 坐下 / 日程切换纳入执行层

目标：

- 所有确定性行为都不再由聊天层直接驱动

### Phase 3：把任务反馈事件标准化

目标：

- 每个任务都有统一的开始、进度、失败、完成反馈

### Phase 4：planner 升级成真正的目标调度器

目标：

- 支持任务中断
- 支持任务队列
- 支持事件优先级抢占

---

## 11. 我建议现在立刻做什么

如果按收益排序，最优先是：

### 第一优先级

先做：

- `ExecutionSession`
- `SingleAttackTaskDriver`
- `GroupAttackTaskDriver`

这是当前最影响体验的部分。

### 第二优先级

再做：

- `FeedbackOrchestrator`

解决“开始不说、结束才说、互动感弱”。

### 第三优先级

最后做：

- planner 升级为持续目标调度器

---

## 12. 最终结论

`Player2NPC` 对我们最大的启发不是“怎么做 AI 对话”，而是：

- **动作执行必须脱离聊天链路**
- **任务必须有持续推进的 controller**
- **planner 只负责定目标，不负责每一步动作**
- **反馈层应该独立，不要夹在执行器里硬写**

这也是我们当前最需要补的底层。

一句话总结：

> 现在的 MaidSoulCore 更像“会聊天的任务触发器”，重构后要变成“会聊天的持续执行体”。

---

## 13. 我建议的下一步实施项

下一步我建议直接开工做：

1. `MaidSoulExecutionSession`
2. `MaidSoulExecutionController`
3. `MaidSoulSingleAttackTaskDriver`
4. `MaidSoulGroupAttackTaskDriver`
5. 让现有攻击命令统一改走这套执行层

这样改完后，攻击链会先稳定下来，后续再把跟随、坐下、日程切换迁进去。
