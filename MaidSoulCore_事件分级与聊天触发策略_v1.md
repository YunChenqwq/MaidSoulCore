# MaidSoulCore 事件分级与聊天触发策略 v1

## 1. 文档目标

这份文档用于确定：

- 哪些事件应该广播给 `LLM`
- 哪些事件只进入黑板和记忆
- 哪些事件允许触发聊天反馈
- 哪些状态切换必须走强规则，不允许模型自由决定

这份文档的重点不是“事件越多越好”，而是：

- 事件要有分级
- 说话要有节制
- 跟随规则要稳定
- 陪伴感要强，但不能失控

---

## 2. 总体原则

### 2.1 不是所有事件都要发给 LLM

必须先做筛选。

否则会出现几个问题：

- 上下文噪音过大
- 模型频繁说废话
- 主动性变成刷屏
- 工具调用和聊天互相打架

所以事件必须分成：

1. 可直接触发聊天反馈
2. 只更新黑板和记忆
3. 只做底层状态同步

### 2.2 事件要按“边沿”而不是“持续状态”触发

例如：

- “睡着了”应该在 `未睡 -> 睡着` 时触发一次
- “起床了”应该在 `睡着 -> 未睡` 时触发一次

而不是每 tick 都触发。

### 2.3 跟随状态不能交给模型随意改

这是一个硬规则：

- 默认长期跟随主人
- 只有主人明确下令时，才允许切换为待在家里 / 原地等待

也就是说：

- `follow_owner` 是默认基线
- `stay_here` 是用户显式授权后的例外

---

## 3. 事件分级

建议分为 A / B / C 三类。

### 3.1 A 级事件

定义：

- 可以进入聊天反馈链路
- 可以触发短回复
- 但必须有冷却和风格约束

### 3.2 B 级事件

定义：

- 主要用于更新黑板、情绪、记忆、规划输入
- 通常不直接说话
- 只在特殊人格场景下才允许转成聊天

### 3.3 C 级事件

定义：

- 仅用于内部状态同步、统计和 trace
- 不进入聊天链路

---

## 4. A 级事件：可触发聊天反馈

这类事件是“陪伴感”的主来源。

---

### 4.1 女仆受击

原版相关事件：

- `MaidHurtEvent`
- `MaidDamageEvent`
- `MaidDeathEvent`

源码：

- [MaidHurtEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidHurtEvent.java)
- [MaidDamageEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidDamageEvent.java)
- [MaidDeathEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidDeathEvent.java)

建议策略：

- 轻伤：
  - 默认不说话
  - 只记入事件尾部
- 中伤：
  - 允许短反馈
  - 例如“有点痛……”
- 重伤 / 连续受击：
  - 可触发保护类提醒
  - 例如“主人，小心一点……”
- 死亡：
  - 必须高优先级处理

聊天冷却建议：

- 普通受击反馈：`8s`
- 重伤反馈：`3s`
- 死亡：无冷却，强制上报

---

### 4.2 主人主动喂食 / 女仆吃完

原版相关事件：

- `MaidAfterEatEvent`

源码：

- [MaidAfterEatEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidAfterEatEvent.java)

建议策略：

- 如果判定是主人主动照顾女仆：
  - 允许正向反馈
  - 增加好感和亲密度
- 如果只是工作中自我进食：
  - 默认不聊天
  - 只更新能量和记忆

可触发话术方向：

- 感谢
- 依赖
- 轻微撒娇

聊天冷却建议：

- `15s`

---

### 4.3 入睡边沿

原版没有特别干净的独立 `SleepEvent`，但可以基于这些状态做边沿检测：

- `maid.isSleeping()`
- `Pose.SLEEPING`
- `MaidTickEvent`
- `MaidBedTask`
- `MaidClearSleepTask`

源码：

- [MaidTickEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidTickEvent.java)
- [MaidBedTask.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\ai\brain\task\MaidBedTask.java)
- [MaidClearSleepTask.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\ai\brain\task\MaidClearSleepTask.java)

建议策略：

- `not sleeping -> sleeping`
  - 触发一次 `MAID_SLEEP_ENTER`
- 可以对主人说“晚安”

注意：

- 只能边沿触发一次
- 不允许持续状态重复触发

聊天冷却建议：

- 同一睡眠周期只允许一次

---

### 4.4 起床边沿

建议策略：

- `sleeping -> not sleeping`
  - 触发一次 `MAID_SLEEP_EXIT`
- 可说“早安”“我醒了”

聊天冷却建议：

- 同一睡眠周期只允许一次

---

### 4.5 主人主动交互

原版相关事件：

- `InteractMaidEvent`

源码：

- [InteractMaidEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\InteractMaidEvent.java)

建议策略：

- 这是非常重要的陪伴入口
- 主人点击女仆、打开交互、喂食、接触行为，都可以由这里衍生

用途：

- 增加亲密度
- 触发短反馈
- 更新最近互动时间

聊天冷却建议：

- `3s`

---

### 4.6 好感等级变化

原版相关事件：

- `MaidFavorabilityLevelChangeEvent`

源码：

- [MaidFavorabilityLevelChangeEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidFavorabilityLevelChangeEvent.java)

建议策略：

- 这是典型高价值人格事件
- 可触发更明显的语气变化和关系表达

用途：

- 更新关系层状态
- 更新长期记忆
- 可触发一次较高质量对话

聊天冷却建议：

- 无需普通冷却，但同一等级变化只触发一次

---

## 5. B 级事件：主要影响黑板和规划

这类事件很重要，但通常不适合直接说话。

---

### 5.1 女仆发起攻击

原版相关事件：

- `MaidAttackEvent`
- `MaidHurtTarget`

源码：

- [MaidAttackEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidAttackEvent.java)
- [MaidHurtTarget.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidHurtTarget.java)

建议策略：

- 更新战斗状态
- 更新情绪，例如紧张、专注、兴奋
- 记录最近战斗目标
- 一般不直接说话

只有在以下情况可转成聊天：

- 战斗结束总结
- 成功保护主人
- 击败高威胁目标

---

### 5.2 物品请求

原版相关事件：

- `MaidRequestItemEvent`

源码：

- [MaidRequestItemEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidRequestItemEvent.java)

建议策略：

- 进入黑板
- 更新“当前缺什么”
- 影响 Planner 对后续任务的选择

示例：

- 缺火把
- 缺桶
- 缺食物
- 缺箭

默认不直接说话。

如果将来要说，也应只在高层 `care_owner` 或 `resource_hint` 能力里统一触发。

---

### 5.3 钓鱼结果

原版相关事件：

- `MaidFishedEvent`

源码：

- [MaidFishedEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidFishedEvent.java)

建议策略：

- 普通鱼获：只记录
- 稀有掉落：可选触发短反馈

因此默认归为 B 级。

---

### 5.4 装备变化

原版相关事件：

- `MaidEquipEvent`
- `MaidBackpackChangeEvent`
- `MaidBaubleChangeEvent`

源码：

- [MaidEquipEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidEquipEvent.java)
- [MaidBackpackChangeEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidBackpackChangeEvent.java)
- [MaidBaubleChangeEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidBaubleChangeEvent.java)

建议策略：

- 刷新能力评估
- 刷新可执行任务集合
- 更新战斗能力 / 生存能力
- 默认不聊天

---

### 5.5 驯服 / 绑定主人

原版相关事件：

- `MaidTamedEvent`

源码：

- [MaidTamedEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidTamedEvent.java)

建议策略：

- 初始化关系系统
- 初始化主人意图记录
- 初始化默认跟随规则

这个事件理论上可以聊天，但它更偏初始化。
建议在系统里归为 B 级，但允许在首次绑定时特殊输出一次欢迎语。

---

## 6. C 级事件：只做同步和 trace

这类事件不要进入聊天链路。

---

### 6.1 普通 Tick

原版相关事件：

- `MaidTickEvent`

源码：

- [MaidTickEvent.java](E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\event\MaidTickEvent.java)

用途：

- 状态同步
- 边沿检测
- 冷却更新
- trace 采样

绝不能每 tick 丢给 LLM。

---

### 6.2 普通位置变化

建议策略：

- 仅更新黑板中的位置缓存
- 不产生聊天

---

### 6.3 普通任务运行中

建议策略：

- 任务持续运行心跳只做 trace
- 不说话

---

## 7. 跟随切换强规则

这是本系统的硬约束之一。

### 7.1 默认行为

默认状态：

- `follow_owner = true`

也就是说：

- 女仆的长期基础行为是跟随主人
- 陪伴模式默认也建立在跟随之上

### 7.2 允许切换为不跟随的唯一条件

只有当主人表达了明确指令，才允许切换：

- “你待在家里吧”
- “你就在这里等我”
- “别跟着我了”
- “你先留在这”

### 7.3 不允许的情况

以下情况不允许模型自己关闭跟随：

- 只是它“觉得现在不该跟”
- 只是为了省事
- 只是为了切工作任务
- 只是因为附近有家点

### 7.4 工程实现建议

不要依赖原版是否提供跟随切换事件。

而应该在 `MaidSoulCore` 自己的适配层中强制统一入口：

- 所有跟随切换都必须走 `follow_owner` / `stay_here`
- 由适配层记录：
  - 谁发起的
  - 为什么切换
  - 是否显式授权

### 7.5 建议新增状态字段

在黑板里新增：

- `follow_policy`
  - `DEFAULT_FOLLOW`
  - `EXPLICIT_STAY`
- `follow_policy_source`
  - `owner_command`
  - `system_default`
- `follow_policy_reason`
  - 最近一次理由摘要

---

## 8. 睡觉聊天规则

你特别提到“睡觉时给主人说晚安”，这个非常适合做。

但要定规则，不然会乱。

### 8.1 允许触发

- 仅在 `入睡边沿` 触发一次“晚安”
- 仅在 `起床边沿` 触发一次“早安 / 我醒了”

### 8.2 不允许触发

- 睡着期间每轮都说
- 因为状态轮询重复说
- 休息和睡觉混淆后反复说

### 8.3 风格建议

- 晚安偏温柔、困倦、贴近陪伴
- 早安偏轻松、依赖、恢复活力

### 8.4 数据建议

建议新增边沿事件：

- `MAID_SLEEP_ENTER`
- `MAID_SLEEP_EXIT`

它们不是原版事件，而是 `MaidSoulCore` 在 Tick 边沿检测后合成出来的高层事件。

---

## 9. 推荐新增的聚合接口

除了原版已经有的 Tool / Context / Task，建议补这几个高价值接口。

---

### 9.1 `scan_recent_events`

作用：

- 返回最近若干条高价值事件尾部

用途：

- 让回复模型快速知道最近发生了什么
- 减少直接把整条日志塞给 LLM

建议结构：

```json
{
  "events": [
    {
      "type": "MAID_HURT",
      "time": "2026-04-08T21:40:00+08:00",
      "summary": "maid took medium damage from zombie"
    },
    {
      "type": "MAID_SLEEP_ENTER",
      "time": "2026-04-08T21:42:00+08:00",
      "summary": "maid went to sleep"
    }
  ]
}
```

---

### 9.2 `scan_available_tasks`

作用：

- 返回当前装备、背包、环境允许执行的任务集合

用途：

- 避免 Planner 选出做不了的任务

可结合：

- 原版任务条件
- `MaidTaskEnableEvent`
- 当前背包和主手

---

### 9.3 `estimate_threat`

作用：

- 把 `nearby_entities + owner_state + self_state` 聚合成一个威胁等级

用途：

- 不必每次都把全部实体原始数据交给回复模型
- 让 Planner 更快做保护决策

建议结构：

```json
{
  "threat_level": "medium",
  "hostile_count": 2,
  "nearest_hostile_id": 123,
  "owner_at_risk": true
}
```

---

### 9.4 `estimate_need_care`

作用：

- 判断主人当前是否需要照护

用途：

- 触发 `care_owner`
- 触发安抚或提醒

建议结构：

```json
{
  "need_care": true,
  "reason": "owner_low_health",
  "priority": "high"
}
```

---

### 9.5 `scan_companion_state`

作用：

- 聚合陪伴所需的关键状态

建议包含：

- 与主人距离
- 最近互动时间
- 当前情绪
- 当前是否空闲
- 当前是否适合主动开口
- 当前是否处于冷却中

这个接口很适合主动交互循环直接使用。

---

### 9.6 `scan_owner_intent`

作用：

- 记录最近一次主人明确命令

用途：

- 区分默认跟随和被授权待机
- 约束跟随策略

建议结构：

```json
{
  "last_explicit_command": "stay_here",
  "time": "2026-04-08T21:35:00+08:00",
  "still_active": true
}
```

---

## 10. 聊天触发的硬限制

为了避免刷屏，建议所有 A 级聊天反馈都必须经过这几个门。

### 10.1 总开关门

- 当前是否允许主动发言
- 当前是否在沉默期

### 10.2 战斗门

- 高强度战斗中，非必要不说

### 10.3 睡眠门

- 睡眠状态只允许睡眠风格输出

### 10.4 重复门

- 最近是否说过几乎一样的话

### 10.5 事件冷却门

- 每类事件独立冷却

---

## 11. 推荐的第一版实施清单

如果要开始做代码，我建议第一版先把这些落地。

### 11.1 先接的原版事件

- `InteractMaidEvent`
- `MaidAfterEatEvent`
- `MaidHurtEvent`
- `MaidDamageEvent`
- `MaidDeathEvent`
- `MaidAttackEvent`
- `MaidFavorabilityLevelChangeEvent`
- `MaidRequestItemEvent`
- `MaidEquipEvent`
- `MaidBackpackChangeEvent`
- `MaidBaubleChangeEvent`
- `MaidFishedEvent`
- `MaidTickEvent`

### 11.2 先合成的高层事件

- `MAID_SLEEP_ENTER`
- `MAID_SLEEP_EXIT`
- `FOLLOW_POLICY_CHANGED`
- `OWNER_EXPLICIT_STAY_COMMAND`
- `OWNER_EXPLICIT_FOLLOW_COMMAND`

### 11.3 先允许聊天反馈的事件

- `MAID_MEDIUM_HURT`
- `MAID_HEAVY_HURT`
- `MAID_AFTER_EAT_BY_OWNER`
- `MAID_SLEEP_ENTER`
- `MAID_SLEEP_EXIT`
- `MAID_FAVORABILITY_LEVEL_UP`
- `OWNER_INTERACT`

---

## 12. 最终结论

这套事件策略的核心不是“让 LLM 知道一切”，而是：

- 让 LLM 只知道值得知道的事
- 让女仆只在该说话的时候说话
- 让默认跟随保持稳定
- 让睡觉、喂食、受击这些关键时刻更像一个人在回应主人

对 `MaidSoulCore` 来说，最重要的规则有两条：

1. 默认跟随，不允许模型随意切换成不跟随
2. 聊天反馈只对高价值边沿事件开放，不对持续状态开放

只要这两条守住，系统的人味会出来，但不会乱。
