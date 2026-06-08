# MaidSoulCore MC 落地实施方案（终版审查稿）

## 1. 文档目的

这份文档只回答 5 个最终落地问题：

1. `MaidSoulCore` 在 Minecraft 里到底复用 `TouhouLittleMaid` 的哪些现成能力；
2. 哪些部分由 `MaidSoulCore` 自己补；
3. 玩家在 MC 里如何直接和女仆聊天；
4. trace、调试面板、事件广播怎么落地；
5. 敌对生物提醒阈值和过滤规则如何定。

这份文档是**最终审查稿**，目标是：

- 不再讨论大方向；
- 只保留可以直接开工的实现决策。

---

## 2. 最终总原则

最终边界确定为：

- `TouhouLittleMaid` 负责：**实体、任务、聊天入口、好感度、原版 AI 能力**
- `MaidSoulCore` 负责：**事件广播、黑板、规划器、多模型协作、主动互动、trace、调试面板**

一句话：

- **执行层和原版交互层尽量复用 TLM**
- **认知层和拟人化层由 MaidSoulCore 叠加**

---

## 3. 最终复用结论

## 3.1 聊天入口：直接复用 TLM

MC 内与女仆聊天，不自己重写一套 UI / 网络协议，直接复用 TLM 现有链路。

现有链路已经完整：

- 玩家看向自己的女仆
- 按聊天快捷键
- 客户端发 `OpenMaidAIChatMessage`
- 服务端鉴权后回传 `SyncMaidAIDataMessage`
- 客户端打开 `AIChatScreen`
- 玩家输入文本
- 客户端发 `SendUserChatMessage`
- 服务端调用 `maid.getAiChatManager().chat(...)`

关键源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\client\event\PressAIChatKeyEvent.java`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\network\message\ai\OpenMaidAIChatMessage.java`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\client\gui\entity\maid\ai\AIChatScreen.java`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\network\message\SendUserChatMessage.java`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\manager\entity\MaidAIChatManager.java`

最终决策：

- **MaidSoulCore 不单独做新的聊天屏**
- **MaidSoulCore 直接挂接 TLM 的聊天链路**
- **MaidSoulCore 只负责在 chat 前后补上下文、事件、trace、工具能力**

---

## 3.2 好感度：直接复用 TLM

好感度不自己再维护一套平行数值，直接复用：

- `EntityMaid.getFavorability()`
- `EntityMaid.getFavorabilityManager()`
- `FavorabilityManager.apply(Type.xxx)`
- `FavorabilityManager.canAdd(typeName)`

关键源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\favorability\FavorabilityManager.java`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\favorability\Type.java`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\passive\EntityMaid.java`

这样做的好处：

- 避免和原版 GUI / 属性成长 / 饰品联动冲突；
- 原版已有冷却与事件体系；
- 原版已有升级阈值、攻击和生命成长逻辑；
- 后续调试时只看一套真实好感度。

最终决策：

- `MaidSoulCore` 自己不保存“主好感度”
- `MaidSoulCore` 只维护“行为评估上下文”
- 真正加减分统一落到 `FavorabilityManager`

### 建议映射

- 喂食、睡觉、游戏、照护：优先映射到原版已有 `Type`
- 主动聊天奖励：新增 `MaidSoulCore` 自定义 `Type`
- 负面事件：必要时用 `reduce()` / `reduceWithoutLevel()`，但只用于明确负反馈

---

## 3.3 任务能力：直接复用 TLM Task / Tool

文字游戏里已经完成了“原版任务能力的统一语义层模拟”。

进 MC 后不再直接用模拟执行器，而是把这些统一能力映射到 TLM 原版：

- `switch_follow_state`
- `switch_sit`
- `switch_schedule`
- `switch_work_task`
- `query_game_context`

核心原则：

- 上层只认 `MaidSoulCore` 语义名
- 底层全部落到 TLM 原版任务 / 工具

例如：

- `follow_owner` -> `switch_follow_state(true)`
- `stay_here` -> `switch_follow_state(false)`
- `start_fishing` -> `switch_work_task("fishing")`
- `start_feeding_owner` -> `switch_work_task("feed")`
- `start_melee_guard` -> `switch_work_task("attack")`

---

## 4. Minecraft 内的最终交互结构

## 4.1 玩家如何和女仆聊天

直接复用 TLM 的交互方式：

1. 玩家视线对准自己的女仆；
2. 按原版聊天快捷键；
3. 打开 `AIChatScreen`；
4. 输入文本后通过 `SendUserChatMessage` 发给服务端；
5. 服务端进入 `maid.getAiChatManager().chat(...)`；
6. `MaidSoulCore` 在这里补事件、上下文、trace 和工具适配。

最终结论：

- **不做“主人输入 / 找附近女仆 / 再选目标”的新链路**
- **优先保持 TLM 原版交互手感**

## 4.2 与谁聊天

继续复用 TLM 的“视线命中自己的女仆”逻辑。

现有辅助：

- `PressAIChatKeyEvent` 已校验“鼠标指向 + 必须是自己拥有的女仆”
- `MaidRayTraceHelper` 可复用作更通用的目标选择辅助

这意味着：

- 聊天目标天然唯一；
- 不会误把别人的女仆当成自己的聊天对象；
- 不需要额外做目标列表 UI。

---

## 5. MaidSoulCore 在 MC 里真正新增什么

## 5.1 事件广播层

MaidSoulCore 负责把 TLM / Forge 事件整理成统一广播流。

第一批必须接入的事件：

- `maid.attacked`
- `owner.attacked`
- `owner.feed_maid`
- `owner.interact_maid`
- `maid.sleep.enter`
- `maid.sleep.exit`
- `maid.follow.changed`
- `maid.schedule.changed`
- `maid.task.changed`
- `maid.home_mode.changed`
- `vision.capture`
- `world.hostile_summary.changed`
- `world.night.enter`
- `world.day.enter`

事件进入链路后：

- 先写黑板；
- 再过本地门控；
- 再决定是否需要 planner / reply / tool_use；
- 全程写 trace。

---

## 5.2 黑板与状态层

MaidSoulCore 自己维护的不是“替代 TLM 状态”，而是“面向 LLM 的运行时黑板”。

黑板最少保留：

- `owner`
- `maid`
- `work_task`
- `follow_policy`
- `schedule`
- `home`
- `vision`
- `threat_summary`
- `last_events`
- `last_reply`
- `mood`
- `initiative`
- `trace_tail`

注意：

- `task / sit / follow / favorability` 这些真实状态优先从 TLM 实体读；
- `mood / initiative / conversation memory / planner output` 这些才是 MaidSoulCore 自己维护。

---

## 5.3 多模型协作层

最终保留四个模型职责：

1. `planner`
   - 决定本轮是否说话、是否调用工具、是否切任务
2. `reply`
   - 生成角色台词
3. `tool_use`
   - 在候选工具中选最合适的工具调用
4. `vision`
   - 解释截图或场景摘要

这里仍然可以直接复用你现有 MaiBot 配置习惯，但运行接入层在 MaidSoulCore。

---

## 6. trace 与调试输出：必须落地

这是这次方案里的强制项，不是可选项。

## 6.1 trace 必须输出哪些内容

每轮至少输出：

- 事件类型
- 优先级
- 进入时间
- 黑板版本
- gate 决策结果
- planner 原始结果
- tool_use 原始结果
- 实际执行的工具
- 工具执行结果
- reply 是否被压制
- 最终台词

## 6.2 trace 输出到哪里

至少三处：

1. 内存环形缓冲
   - 便于调试面板实时读尾部
2. Tool 查询接口
   - 现在已有 `maidsoul_trace_tail`
3. 调试面板 / 命令输出
   - 便于你现场排查

当前已有基础：

- `E:\wallpaper\MaidSoulCore\src\main\java\com\maidsoulcore\trace\TraceEvent.java`
- `E:\wallpaper\MaidSoulCore\src\main\java\com\maidsoulcore\trace\RingBufferTraceSink.java`
- `E:\wallpaper\MaidSoulCore\src\main\java\com\maidsoulcore\forge\tlm\tool\MaidSoulTraceTool.java`
- `E:\wallpaper\MaidSoulCore\src\main\java\com\maidsoulcore\forge\state\MaidSoulStateRegistry.java`
- `E:\wallpaper\MaidSoulCore\src\main\java\com\maidsoulcore\forge\state\MaidSoulAgentState.java`

## 6.3 调试面板最终最少显示项

一只女仆最少显示：

- 女仆名称 / UUID
- 当前 follow / sit / sleep / schedule / task
- 当前好感度等级 / 点数 / 距离下一级差值
- 当前 mood / initiative / energy
- 最近截图时间 / 最近视觉摘要
- 最近 hostile summary
- 最近 10 条事件
- 最近 10 条 trace
- 最近 5 次工具调用
- 最近 3 次最终回复

---

## 7. 好感度的最终接法

## 7.1 一律以 TLM 为准

最终方案不是：

- MaidSoulCore 自己算一个好感度
- 再同步回 TLM

而是：

- **好感度真值只存在于 TLM**
- `MaidSoulCore` 只读、只触发、只解释

## 7.2 MaidSoulCore 该做什么

MaidSoulCore 只负责两件事：

1. 决定“这次事件要不要加 / 减好感”
2. 把这个决策映射成 TLM 的 `FavorabilityManager` 调用

## 7.3 好感度推荐规则

建议保守，不要把聊天奖励做太大。

推荐：

- 普通聊天：极低权重，且强冷却
- 喂食、照顾、睡觉、游戏：优先复用原版逻辑
- 长时间被主人忽略：默认不减
- 明确伤害、长时间高压、主人主动惩罚：才考虑减

工程原则：

- 不要再在 `MaidSoulCore` 里重做“每日 +30 上限”那一套到 MC 正式版
- 正式版优先让 `FavorabilityManager` 成为唯一真实数值系统
- 若后续需要“陪伴维度”的额外统计，单独做 `relation_context`，不要污染原版好感度

---

## 8. 敌对生物提醒规则（最终版）

这一条按你的要求收紧。

## 8.1 总原则

MC 本来就会自然刷很多怪，女仆不能因为周围有零散敌对生物就频繁提醒主人。

最终规则：

- **普通敌对生物数量 < 8 时，不主动提醒**
- **只有“特殊敌对目标”或“高风险目标”可以绕过数量阈值**

## 8.2 普通敌对目标

默认归为普通提醒池，例如：

- 僵尸
- 骷髅
- 苦力怕以外的普通夜间怪
- 蜘蛛

当这些目标数量 `< 8`：

- 不主动聊天提醒
- 只写入黑板
- 只在需要时影响 planner 的内部动作偏好

## 8.3 高风险 / 特殊目标

这类可以绕过 `< 8` 阈值，立即升高优先级。

建议第一版直接做成配置白名单：

- `minecraft:creeper`
- `minecraft:witch`
- `minecraft:enderman`
- `minecraft:ravager`
- `minecraft:evoker`
- `minecraft:vindicator`
- `minecraft:warden`
- `minecraft:wither`
- `minecraft:ender_dragon`

可选按距离再加一层条件：

- 与主人距离很近
- 或正在锁定主人 / 女仆
- 或造成持续远程威胁

## 8.4 最终决策逻辑

建议统一成：

- `special_or_high_risk_count >= 1` -> 可提醒
- `normal_hostile_count >= 8` -> 可提醒
- 其他情况 -> 不主动提醒，只写黑板

## 8.5 提醒冷却

即便满足提醒条件，也必须加冷却：

- 同类 threat 提醒冷却建议 `10s ~ 20s`
- 若正在持续聊天，可只做简短插话
- 若未在聊天中，则只在首次触发时发言

---

## 9. 最终推荐的系统结构

## 9.1 交互层

- 复用 `AIChatScreen`
- 复用 `SendUserChatMessage`
- 复用 `OpenMaidAIChatMessage`

## 9.2 实体状态层

- 复用 `EntityMaid`
- 复用 `FavorabilityManager`
- 复用原版 `Task / Tool / Context`

## 9.3 MaidSoulCore 中间层

- 事件采集器
- 黑板
- planner / reply / tool_use / vision 调度器
- trace sink
- TLM adapter

## 9.4 展示与调试层

- 调试面板
- trace tail tool
- 最近事件日志
- 黑板快照输出

---

## 10. 最终实施顺序

## Phase 1：正式接入 TLM 聊天链

目标：

- 不再用文字游戏验证“聊天入口”
- 直接挂进 TLM 的 `AIChatScreen -> SendUserChatMessage -> MaidAIChatManager`

交付：

- 聊天可直达 MaidSoulCore runtime
- reply 仍通过原版 chat bubble 输出

## Phase 2：正式接入 TLM 好感度

目标：

- 全部好感度行为改成调用 `FavorabilityManager`

交付：

- 调试面板能读等级、点数、下一级差值
- 事件可触发好感度变化

## Phase 3：正式接入 TLM Task / Tool

目标：

- 把文字游戏里已经验证过的能力映射到原版工具和任务

交付：

- 跟随 / 留守 / 坐下 / 回家 / 守护 / 工作任务切换可走真实女仆实体

## Phase 4：trace 与调试面板

目标：

- 所有事件、路由、工具执行、回复都可见

交付：

- panel
- trace tail
- 黑板导出

## Phase 5：敌对生物过滤与主动交互

目标：

- 把 `<8 不提醒` 和高风险白名单做进广播门控

交付：

- 女仆不再因为 MC 常规刷怪而话痨
- 真正危险时才像人一样提醒

---

## 11. 最终结论

最终方案已经收敛：

- **聊天：直接复用 TLM**
- **好感度：直接复用 TLM**
- **任务与控制：直接复用 TLM**
- **MaidSoulCore 只补事件黑板、多模型协作、trace、调试面板、主动性**

关于你最后强调的敌对提醒，最终也定死：

- **普通敌对 < 8，不提醒**
- **只有特殊 / 高风险目标才绕过阈值**

因此，当前真正剩下的实现重点只剩三块：

1. 把 `MaidSoulCore runtime` 正式插到 TLM 聊天链上；
2. 把文字游戏里的能力映射替换成真实 `TLM Adapter`；
3. 把 trace + panel 做到可现场排查。

这三块完成后，方案就从“验证原型”进入“MC 内正式可用版本”。
