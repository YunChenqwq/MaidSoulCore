# MaidSoulCore 实践落地方案 v1

## 1. 目标

基于前面已经整理出的文档，当前最合理的落地方向已经很清晰：

- 不修改 `TouhouLittleMaid` 本体
- `MaidSoulCore` 作为独立 Forge 1.20.1 模组存在
- 底层尽量复用 `TLM` 已有的 `Tool / Context / Task`
- 上层新增 `情绪 / 记忆 / 主动性 / 事件广播 / 多模型协作 / 调试面板`
- 整个系统先做“稳定闭环”，再做“拟人化增强”

一句话：

- `TLM` 负责执行
- `MaidSoulCore` 负责人格、认知、陪伴和调度

---

## 2. 已确认的基础事实

前面文档已经确认，原版 `TLM` 已经有可复用的 Agent 基础设施：

- 可扩展接口：
  - `registerAITool`
  - `registerAIMaidContext`
  - `addMaidTask`
  - `addExtraMaidBrain`
- 原版内置 Tool：
  - `query_game_context`
  - `switch_follow_state`
  - `switch_work_task`
  - `switch_schedule`
  - `switch_sit`
  - `use_skill`
- 原版内置 Context：
  - `world`
  - `status`
  - `equipment`
  - `user`
  - `position`
  - `nearby_entities`
  - `effects`
- 原版内置 Task：
  - 战斗
  - 农业
  - 照护
  - 维护
  - 娱乐

所以我们不应该重写这一层。

---

## 3. 现阶段最正确的工程边界

### 3.1 TLM 负责什么

- 实体状态
- 跟随 / 坐下 / 日程 / 任务切换
- 背包装备和附近实体等基础上下文
- 工作任务的实际执行

### 3.2 MaidSoulCore 负责什么

- 统一能力映射层
- 事件广播与事件分级
- 黑板状态
- 情绪 / 好感 / 能量
- 主动聊天和主动行为裁决
- MaiBot 风格模型协作
- 调试输出和可视化面板

### 3.3 明确不做什么

当前阶段不做：

- 直接侵入修改 `TLM` 源码
- 让模型直接操纵原版底层状态，不经过规则门控
- 不受限制的自动切换跟随 / 工作 / 发言

---

## 4. 已经形成的设计共识

### 4.1 必须有映射层

上层不要直接认原版工具名，而是认 `MaidSoulCore Capability`。

例如：

- `follow_owner`
- `stay_here`
- `guard_owner`
- `keep_company`
- `scan_owner_state`

底层再映射到原版：

- `switch_follow_state`
- `switch_work_task`
- `query_game_context`

这样更稳，后面扩展不会乱。

### 4.2 跟随规则必须是硬约束

当前规则已经明确：

- 默认长期跟随主人
- 只有主人明确下令，才允许切到待机 / 留家 / 原地等待
- 模型不能因为“自我判断”随意关闭跟随

这条必须写进规则门控，不是提示词建议。

### 4.3 聊天触发必须做事件分级

不是所有事件都对 LLM 开放。

只允许高价值事件触发聊天：

- 受击
- 主人喂食
- 入睡
- 起床
- 主人主动交互
- 好感等级变化

普通 Tick、普通位置变化、普通工作心跳不允许直接触发说话。

### 4.4 好感和能量必须做硬限制

已经定下：

- 每日好感最多 `30`
- 分桶上限：
  - 聊天 `10`
  - 行为 `10`
  - 照护 `10`
- `energy < 20` 时禁止工作任务
- 非聊天状态发言冷却绑定截图间隔
- 聊天会话内改为短冷却

这些约束非常关键，它们决定系统会不会失真。

---

## 5. 推荐的最终系统结构

建议把系统固定为 8 个模块。

### 5.1 `TlmAdapter`

职责：

- 对接原版 `TLM`
- 调原版 Tool
- 读原版 Context
- 切原版 Task

### 5.2 `CapabilityService`

职责：

- 对上层暴露统一能力接口
- 把高层意图映射到底层执行

这是整个系统的能力中台。

### 5.3 `EventBridge`

职责：

- 监听原版事件
- 合成高层事件
- 把事件写入黑板和 trace

### 5.4 `BlackboardService`

职责：

- 保存当前世界状态、关系状态、能量状态、节奏状态
- 提供统一读写入口

### 5.5 `RuleGateService`

职责：

- 在 LLM 裁决前做硬规则门控

例如：

- 默认跟随规则
- 低能量禁工
- 发言冷却
- 好感上限

### 5.6 `PlannerService`

职责：

- 输入黑板状态和上下文
- 输出结构化意图和能力调用计划

### 5.7 `ReplyService`

职责：

- 负责人格化表达
- 只负责怎么说，不负责底层执行是否合法

### 5.8 `DebugPanel / TraceService`

职责：

- 显示事件流
- 显示能力调用
- 显示黑板字段
- 显示模型输入输出摘要

---

## 6. 先做什么，不先做什么

### 6.1 当前最优路线

先做：

1. 能力层
2. 黑板层
3. 事件桥
4. 规则门控
5. 最小 Planner
6. 调试面板

后做：

- 更复杂的视觉模型
- 更复杂的长期记忆
- 更复杂的多角色人格切换

### 6.2 原因

如果没有能力层、黑板层、规则门控，后面再加任何高级人格效果都会很乱。

相反，如果这几层先定住：

- 模型可以后换
- 事件可以后扩
- 界面可以后美化
- 但系统骨架不会变

---

## 7. 第一阶段的最小可运行闭环

第一阶段不要追求完整，只要追求：

- 能跑
- 能看
- 能调
- 能约束

### 7.1 第一阶段必须打通的能力

建议只实现这 10 个：

- `scan_self_state`
- `scan_owner_state`
- `scan_position_state`
- `scan_nearby_entities`
- `scan_inventory_state`
- `follow_owner`
- `stay_here`
- `sit_down`
- `enter_idle_mode`
- `guard_owner`

### 7.2 第一阶段必须打通的事件

- `InteractMaidEvent`
- `MaidAfterEatEvent`
- `MaidHurtEvent`
- `MaidDamageEvent`
- `MaidDeathEvent`
- `MaidAttackEvent`
- `MaidFavorabilityLevelChangeEvent`
- `MaidRequestItemEvent`
- `MaidTickEvent`

再在 `MaidSoulCore` 内部合成：

- `MAID_SLEEP_ENTER`
- `MAID_SLEEP_EXIT`
- `FOLLOW_POLICY_CHANGED`

### 7.3 第一阶段必须落地的黑板字段

- `favorability_total`
- `favorability_daily_gain`
- `favorability_bucket_chat_gain`
- `favorability_bucket_action_gain`
- `favorability_bucket_care_gain`
- `energy`
- `energy_state`
- `follow_policy`
- `last_reply_time`
- `chat_session_active`
- `last_capture_time`
- `recent_events`
- `was_sleeping_last_tick`

### 7.4 第一阶段必须实现的硬规则

- 默认跟随
- 显式命令才能待机留家
- 每日好感最多 30
- 能量低于 20 禁止工作任务
- 非聊天状态发言冷却跟截图间隔绑定
- 聊天会话中放宽发言冷却

---

## 8. 第二阶段的拟人化增强

当第一阶段稳定后，再加第二阶段内容。

### 8.1 增加更多高层能力

- `keep_company`
- `care_owner`
- `light_up_nearby_area`
- `scan_recent_events`
- `scan_available_tasks`
- `estimate_threat`
- `estimate_need_care`
- `scan_companion_state`
- `scan_owner_intent`

### 8.2 增强情绪系统

- 心情
- 压力
- 主动性
- 好感驱动的回复风格变化

### 8.3 增强多模型协作

- Planner 模型
- Reply 模型
- Tool Use 模型
- VLM 模型
- Summary 模型

### 8.4 增强调试面板

显示：

- 当前模式
- 情绪条
- 好感条
- 能量条
- 最近事件
- 最近能力调用
- 最近模型输出摘要

---

## 9. 第三阶段再考虑的内容

这些内容有价值，但不应该抢在前两阶段之前做。

### 9.1 真正的视觉链路

- 周期截图
- 主人视角同步
- VLM 解释
- 视觉摘要进黑板

### 9.2 更复杂的长期记忆

- 按日期归档
- 偏好抽取
- 关系事件摘要
- 记忆压缩

### 9.3 更复杂的自主行为

- 主动提醒
- 主动邀约互动
- 主动安抚
- 主动家务规划

这些都应该建立在前面规则稳定之后。

---

## 10. 推荐实现顺序

最推荐的顺序是下面这样。

### Step 1：实现 Java 基础骨架

先建这些模块：

- `capability`
- `adapter`
- `event`
- `blackboard`
- `rule`
- `planner`
- `reply`
- `debug`

### Step 2：把 JSON 协议落成 Java DTO

包括：

- `CapabilityRequest`
- `CapabilityResponse`
- `CapabilityError`
- `CapabilityTrace`
- `CapabilityStatus`

### Step 3：把 v1 能力接口接上

优先实现：

- 5 个读取能力
- 5 个控制 / 组合能力

### Step 4：把事件桥接上

先监听 A 级和核心 B 级事件。

### Step 5：实现黑板和规则门控

必须优先落地：

- 好感限流
- 能量禁工
- 发言冷却
- 默认跟随规则

### Step 6：实现最小 Planner

先不要搞特别复杂。

优先做：

- 规则门控
- 简单评分
- 再决定是否调用 LLM

### Step 7：实现调试面板

优先让你能看到：

- 事件
- 状态
- 调用
- 冷却
- 拦截原因

---

## 11. 现有文档如何分工

现在已经有的文档可以这样用。

### 基础分析

- [MaidSoulCore_TLM原版Agent可复用能力映射.md](E:\wallpaper\MaidSoulCore\MaidSoulCore_TLM原版Agent可复用能力映射.md)
  - 用来确认原版能复用什么

### 能力设计

- [MaidSoulCore_TLM能力映射表_v1.md](E:\wallpaper\MaidSoulCore\MaidSoulCore_TLM能力映射表_v1.md)
  - 用来定义上层能力名和底层映射关系

### 协议设计

- [MaidSoulCore_能力接口JSON协议_v1.md](E:\wallpaper\MaidSoulCore\MaidSoulCore_能力接口JSON协议_v1.md)
  - 用来定义 Java DTO 和调试输出结构

### 事件设计

- [MaidSoulCore_事件分级与聊天触发策略_v1.md](E:\wallpaper\MaidSoulCore\MaidSoulCore_事件分级与聊天触发策略_v1.md)
  - 用来实现事件桥和聊天触发

### 状态设计

- [MaidSoulCore_黑板字段与状态机设计_v1.md](E:\wallpaper\MaidSoulCore\MaidSoulCore_黑板字段与状态机设计_v1.md)
  - 用来实现黑板、好感、能量、冷却、状态机

---

## 12. 实际开工建议

如果你现在要我给一句最实际的话，就是：

不要再继续加新概念了，直接开始做 `v1 骨架`。

当前最应该做的是：

1. 按协议定义 Java 类
2. 按映射表实现能力层
3. 按事件文档接事件桥
4. 按黑板文档把好感 / 能量 / 冷却落地
5. 用一个简单面板把全部状态可视化

只要这 5 步跑起来，这个项目就已经从“想法”进入“系统”了。

---

## 13. 最终结论

现在这套设计已经足够支撑 `MaidSoulCore` 开工。

真正的实践方案不是继续讨论“还能不能更像人”，而是先把下面四件事落地：

1. 统一能力层
2. 统一事件层
3. 统一黑板层
4. 统一规则门控

然后把：

- 好感上限
- 能量消耗
- 发言冷却
- 默认跟随

这四条硬规则写进系统。

这样做出来的女仆，哪怕第一版功能不多，也会比“什么都能做但没有约束”的版本更稳定、更像一个长期在线陪伴体。
