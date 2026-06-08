# MaidSoulCore 执行层收口方案 v2

## 1. 背景结论

当前 `MaidSoulCore` 的总体方向是对的：

- 继续复用 `TouhouLittleMaid` 的女仆底层能力
- 在其上叠加 `聊天 / 视觉 / 主动事件 / 计划 / 人设`
- 不走 `Player2NPC` 那种“重造一套类玩家实体底座”的路线

现阶段真正的问题，不是架构方向错了，而是**计划层、执行层、反馈层的边界还不够干净**。

最典型的现象：

- `PlanService` 同时负责计划推进、执行触发、完成判断
- 战斗已经开始抽成独立执行层，但其它动作仍然停留在“直接调用 + 猜状态”
- 聊天、视觉、主动事件、任务执行之间仍然容易互相抢状态
- 连续任务、战斗锁定、执行反馈有时不同步

因此，本次目标不是推翻现有架构，而是对现有架构做一次**执行层收口**。

---

## 2. 当前结构的真实状态

### 2.1 现有职责分布

当前大致是下面这条链：

- `MaidSoulChatLoopRuntimeService`
  - 接收聊天、主动事件、工具调用结果
  - 提交计划或直接触发动作
- `MaidSoulPlanService`
  - 持有 `plan`
  - 在 tick 中推进 `step`
  - 直接调用 `MaidSoulActionExecutorService.execute(...)`
  - 直接根据返回值或战斗状态猜测步骤是否完成
- `MaidSoulActionExecutorService`
  - 作为动作门面
  - 即时动作直接落地
  - 战斗动作委托给 `MaidSoulCombatExecutionController`
- `MaidSoulCombatExecutionController`
  - 维护战斗 session
  - 每 tick 推进战斗执行
  - 处理锁定、切换目标、超时、结束

### 2.2 当前最大的边界问题

`MaidSoulPlanService` 现在不是纯计划器，而是：

- 计划器
- 执行触发器
- 一部分执行状态判断器
- 一部分完成判定器

这会导致：

- `PlanService` 和执行器同时知道太多运行时细节
- 非战斗动作没有统一的执行生命周期
- 计划完成判定依赖“轮询 + 猜测”
- 交互反馈时机不稳定

---

## 3. 目标原则

### 3.1 不推翻现有上层能力

以下能力全部保留：

- 本地命令快路
- TLM tool loop
- 视觉摘要
- 主动陪伴事件
- 话题去重
- 聊天焦点模式
- 现有 prompt / 人设 / 追问 / 分句输出

### 3.2 不重写 TLM 底层

以下能力继续复用：

- TLM 女仆实体
- TLM 任务切换
- TLM 攻击任务和 AI 脑
- 现有女仆基础行为

### 3.3 只做边界收口

目标不是“新造一套系统”，而是把现有系统收成四层：

- `Intent Layer`：聊天、事件、视觉产生意图
- `Plan Layer`：把意图组织成 plan / steps
- `Execution Layer`：真正推进动作执行
- `Feedback Layer`：根据执行事件生成给主人看的反馈

---

## 4. v2 目标结构

### 4.1 建议分层

#### A. Intent Layer

负责“为什么要做这件事”：

- `MaidSoulChatLoopRuntimeService`
- `MaidSoulCompanionService`
- `MaidSoulVisionService`
- `MaidSoulLocalCommandParserService`

输出统一结构：

- `MaidSoulIntent`
  - `source`
  - `intentType`
  - `objective`
  - `priority`
  - `rawContext`

#### B. Plan Layer

负责“要按什么顺序做”：

- `MaidSoulPlanService`

只保留以下职责：

- 接收 `intent`
- 生成 `MaidSoulPlan`
- 维护 `activePlan / queue`
- 根据 `ExecutionEvent` 推进当前步骤
- 决定是否抢占、挂起、恢复 plan

明确移除以下职责：

- 不直接控制攻击锁定
- 不直接判断战斗细节
- 不直接猜测寻路是否成功
- 不直接依赖 `maid.getTarget()` 这类执行态细节

#### C. Execution Layer

负责“当前这一步到底怎么跑”：

- 新增统一入口：`MaidSoulExecutionManager`
- 现有 `MaidSoulCombatExecutionController` 并入其下，作为战斗 driver

内部拆为：

- `MaidSoulExecutionManager`
- `MaidSoulExecutionSession`
- `MaidSoulExecutionDriver`
- `MaidSoulCombatExecutionDriver`
- `MaidSoulFollowExecutionDriver`
- `MaidSoulSitExecutionDriver`
- `MaidSoulScheduleExecutionDriver`
- `MaidSoulTaskSwitchExecutionDriver`

说明：

- `MaidSoulActionExecutorService` 不再承担“执行逻辑”
- 逐步退化为兼容门面，最终只做参数转换

#### D. Feedback Layer

负责“什么时候告诉主人什么话”：

- 新增：`MaidSoulExecutionFeedbackService`

只消费执行事件，不关心具体攻击逻辑。

例如：

- `STEP_STARTED`
- `STEP_BLOCKED`
- `STEP_RETRYING`
- `STEP_COMPLETED`
- `STEP_FAILED`
- `PLAN_COMPLETED`

再由它决定：

- 是否立即播报给主人
- 是否写 trace
- 是否走可爱口吻反馈
- 是否进入主动补充说明

---

## 5. 关键改造点

### 5.1 `PlanService` 退回“真计划器”

当前做法：

- `executeCurrentStep(...)` 中直接调用执行器
- `checkRunningStep(...)` 中直接轮询战斗状态

目标做法：

- `PlanService` 只做：
  - `submitPlan(...)`
  - `activatePlan(...)`
  - `dispatchCurrentStepToExecution(...)`
  - `onExecutionEvent(...)`

即：

- 第一次启动 step 时，把 step 交给 `ExecutionManager`
- 后续是否完成、失败、阻塞，全部由 `ExecutionManager` 发事件回来

这样连续任务不会丢，反而更稳：

- “先杀兔子，再杀猪”
- “先跟随，再坐下，再切夜班”
- “切任务失败后回退并提示主人”

都能统一通过事件推进

### 5.2 引入统一执行事件

新增：

- `MaidSoulExecutionEvent`

建议字段：

- `maidUuid`
- `planId`
- `stepId`
- `eventType`
- `status`
- `detail`
- `timestamp`

建议事件类型：

- `STEP_DISPATCHED`
- `STEP_STARTED`
- `STEP_RUNNING`
- `STEP_BLOCKED`
- `STEP_RETRYING`
- `STEP_COMPLETED`
- `STEP_FAILED`
- `STEP_CANCELLED`
- `PLAN_PREEMPTED`
- `PLAN_RESUMED`

### 5.3 把“动作推进”全部纳入 session

当前只有战斗有 session。

v2 建议：

- 即时动作也有最轻量 session
- 即使它只活 1 tick，也有统一生命周期

收益：

- 所有步骤都能统一输出事件
- 所有步骤都能走统一反馈
- 所有步骤都能统一 debug trace

### 5.4 聊天与执行抢占规则显式化

当前已有“聊天焦点模式”，但执行层没有完全配合。

v2 需要明确：

- 当主人进入文字指令聊天时：
  - 低优先级视觉事件暂停
  - 非关键主动播报暂停
  - 视觉模型轮询降频
- 当前执行中的战斗/跟随等动作不应直接丢失
  - 而是标记为 `background_running`
  - 或按策略 `paused`

这部分由：

- `PlanService` 决定是否抢占
- `ExecutionManager` 决定是否暂停或继续

### 5.5 调试输出从“零散 trace”升级为“计划链 trace”

当前 trace 偏碎。

v2 建议统一格式：

- `plan.accepted`
- `plan.activated`
- `plan.preempted`
- `step.dispatched`
- `step.started`
- `step.blocked`
- `step.retrying`
- `step.completed`
- `step.failed`
- `plan.completed`

每条 trace 至少包含：

- `planId`
- `stepIndex`
- `actionType`
- `result`

这样你在聊天回显里能完整看出整条链。

---

## 6. 为什么这版比“完全照抄 Player2NPC”更适合

### 6.1 你的产品目标不同

`Player2NPC` 的重点是：

- 自建一个类玩家实体
- 用完整 player-like 能力执行任务

而 `MaidSoulCore` 的重点是：

- 保留 TLM 女仆的原生身份
- 强化其聊天、陪伴、计划和拟人感

因此：

- 不该换底座
- 只该整理边界

### 6.2 你已有更强的上层交互

你现在已经有：

- 事件陪伴
- 视觉摘要
- topic 去重
- 可爱口吻输出
- tool loop
- prompt 人设

这些都是 `Player2NPC` 当前并不擅长的部分。

所以最合理的做法就是：

- 保留你现有上层
- 学它的“执行层边界清晰”

---

## 7. 分阶段落地方案

### 阶段 A：执行层收口

目标：

- 不改 prompt
- 不改视觉策略
- 不改主动事件策略
- 只把动作执行统一收进 `ExecutionManager`

落地项：

- 新增 `MaidSoulExecutionManager`
- 让 `MaidSoulCombatExecutionController` 变成战斗 driver
- 为 `FOLLOW / SIT / SCHEDULE / SET_TASK` 增加轻量 driver
- `PlanService` 改为只分发 step，不直接碰执行细节

### 阶段 B：反馈收口

目标：

- 所有计划步骤开始/阻塞/完成/失败都能统一反馈给主人

落地项：

- 新增 `MaidSoulExecutionFeedbackService`
- 所有执行事件统一接入反馈
- 所有 trace 统一格式

### 阶段 C：策略强化

目标：

- 聊天焦点模式、沉默模式、视觉降频、主动搭话节制都与执行层协同

落地项：

- 抢占策略
- pause / resume 机制
- background session 策略

---

## 8. 建议新增类

建议首批新增：

- `com.maidsoulcore.forge.execution.MaidSoulExecutionManager`
- `com.maidsoulcore.forge.execution.MaidSoulExecutionEvent`
- `com.maidsoulcore.forge.execution.MaidSoulExecutionEventType`
- `com.maidsoulcore.forge.execution.MaidSoulExecutionDriver`
- `com.maidsoulcore.forge.execution.MaidSoulFollowExecutionDriver`
- `com.maidsoulcore.forge.execution.MaidSoulSitExecutionDriver`
- `com.maidsoulcore.forge.execution.MaidSoulScheduleExecutionDriver`
- `com.maidsoulcore.forge.execution.MaidSoulTaskSwitchExecutionDriver`
- `com.maidsoulcore.forge.service.MaidSoulExecutionFeedbackService`

兼容保留：

- `MaidSoulActionExecutorService`
  - 先做 facade
  - 后续内部全部转发给 `MaidSoulExecutionManager`

---

## 9. 对当前代码的直接判断

### 9.1 哪部分已经走在正确方向上

- `MaidSoulCombatExecutionController`
- `MaidSoulExecutionSession`
- `MaidSoulSingleAttackTaskDriver`
- `MaidSoulGroupAttackTaskDriver`

这几块已经是正确雏形。

### 9.2 哪部分是当前最大的技术债

- `MaidSoulPlanService`
  - 承担职责过多
- 非战斗动作没有统一 session
- 完成判定还依赖猜测
- 执行反馈还没有统一事件源

---

## 10. 最终结论

本项目当前不需要：

- 重写为 `Player2NPC`
- 改用类玩家实体底座
- 推翻现有聊天与人设体系

本项目当前真正需要的是：

- 把 `PlanService` 从“半执行器”收回成“真计划器”
- 把执行推进统一收进 `ExecutionManager`
- 用统一 `ExecutionEvent` 串起计划推进和主人反馈

这样做之后：

- 连续任务会更稳
- 攻击/跟随/坐下/切任务会更统一
- 聊天和执行不容易互相抢状态
- 调试 trace 会更可读
- 也更容易继续做“像人”的高层体验

---

## 11. 建议的下一步实现顺序

建议按下面顺序落代码：

1. 新增 `MaidSoulExecutionManager`
2. 给 `FOLLOW / SIT / SCHEDULE / SET_TASK` 补 driver
3. 让 `PlanService` 改为只分发 step 和消费 execution event
4. 新增 `MaidSoulExecutionFeedbackService`
5. 最后再收聊天焦点模式与执行抢占策略

这条顺序风险最低，也最容易逐步验证。
