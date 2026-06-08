# MaidSoulCore TLM 能力映射表 v1

## 1. 文档目标

这份文档用于确定一件事：

- `MaidSoulCore` 对上层 `Planner / Reply / DebugPanel` 暴露什么统一能力
- 这些统一能力在底层如何映射到 `TouhouLittleMaid` 原版已有的 `Tool / Context / Task`

这份映射表的核心原则是：

- 上层只认 `MaidSoulCore` 语义
- 底层尽量复用 `TLM` 原版能力
- 不把上层逻辑直接绑死在原版 `Tool` 名称上

---

## 2. 设计原则

### 2.1 上层接口稳定

`Planner`、`Reply`、`主动裁决器`、`调试面板` 不应该直接依赖原版：

- `switch_work_task`
- `switch_follow_state`
- `query_game_context`

而应该依赖我们自己的统一接口，例如：

- `follow_owner`
- `guard_owner`
- `scan_owner_state`

### 2.2 底层尽量复用

只要原版已经能做，就不要自己重写：

- 跟随
- 坐下
- 切任务
- 读取主人状态
- 读取周边实体

### 2.3 映射层负责兜底

映射层除了转发，还要负责：

- 参数清洗
- 前置条件检查
- 冷却控制
- 幂等处理
- 结果摘要
- Trace 调试输出

---

## 3. 总体分层

推荐固定为四层：

1. `Planner Layer`
   - 输出高层意图
2. `MaidSoul Capability Layer`
   - 统一能力接口
3. `TLM Adapter Layer`
   - 把统一能力映射到原版 Tool / Task / Context
4. `TouhouLittleMaid Runtime`
   - 真正执行行为

一句话：

- 上层说“我要保护主人”
- 适配层决定“该调用 `query_game_context` 还是 `switch_work_task`”
- 原版负责真正执行

---

## 4. 能力分组总览

第一版建议把能力分成 5 组：

1. 状态读取能力
2. 基础姿态控制能力
3. 工作任务控制能力
4. 陪伴与守护能力
5. MaidSoulCore 自定义能力

---

## 5. 状态读取能力映射

这一组能力原则上都是读操作。

---

### 5.1 `scan_world_state`

**对外语义**

- 读取当前世界环境摘要

**主要用途**

- 判断白天 / 夜晚
- 判断天气
- 判断维度 / 生物群系

**底层映射**

- `query_game_context(category_id = "world")`

**原版来源**

- `WorldContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\prompts\WorldContexts.java:22`

**返回建议结构**

```json
{
  "time": "day",
  "weather": "clear",
  "dimension": "minecraft:overworld",
  "biome": "plains"
}
```

---

### 5.2 `scan_self_state`

**对外语义**

- 读取女仆当前自身状态

**主要用途**

- 判断是否坐下
- 判断是否跟随
- 判断当前任务
- 判断睡眠与活动状态

**底层映射**

- `query_game_context(category_id = "status")`

**原版来源**

- `MaidContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\prompts\MaidContexts.java:20`

**返回建议结构**

```json
{
  "health": 18,
  "sleeping": false,
  "following": true,
  "sitting": false,
  "riding": false,
  "schedule": "ALL",
  "activity": "follow",
  "work_task": "idle"
}
```

---

### 5.3 `scan_owner_state`

**对外语义**

- 读取主人状态摘要

**主要用途**

- 判断主人是否受伤
- 判断主人是否装备武器
- 判断当前是否需要照护或防卫

**底层映射**

- `query_game_context(category_id = "user")`

**原版来源**

- `UserContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\UserContexts.java:21`

**返回建议结构**

```json
{
  "name": "player",
  "health": 14,
  "mainhand": "minecraft:diamond_sword",
  "armor": ["minecraft:iron_helmet"]
}
```

---

### 5.4 `scan_position_state`

**对外语义**

- 读取女仆与主人之间的位置关系

**主要用途**

- 判断距离
- 判断是否该靠近主人
- 判断当前位置明暗

**底层映射**

- `query_game_context(category_id = "position")`

**原版来源**

- `PositionMaidContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\PositionMaidContexts.java:19`

**返回建议结构**

```json
{
  "self_pos": [100, 64, 100],
  "owner_pos": [104, 64, 102],
  "distance_to_owner": 4.5,
  "light_level": 7
}
```

---

### 5.5 `scan_inventory_state`

**对外语义**

- 读取女仆装备和背包摘要

**主要用途**

- 判断能不能执行某任务
- 判断是否缺少火把、剪刀、水桶、食物等

**底层映射**

- `query_game_context(category_id = "equipment")`

**原版来源**

- `EquipmentMaidContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\EquipmentMaidContexts.java:23`

---

### 5.6 `scan_nearby_entities`

**对外语义**

- 读取女仆附近实体列表

**主要用途**

- 战斗决策
- 守护决策
- 互动对象识别

**底层映射**

- `query_game_context(category_id = "nearby_entities")`

**原版来源**

- `NearbyEntityMaidContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\NearbyEntityMaidContexts.java:29`

**特别说明**

这项能力应该成为：

- `guard_owner`
- `attack_target`
- `estimate_threat`

这些高层能力的基础输入。

---

### 5.7 `scan_effect_state`

**对外语义**

- 读取当前状态效果

**底层映射**

- `query_game_context(category_id = "effects")`

**原版来源**

- `EffectsMaidContexts`
- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\EffectsMaidContexts.java:22`

---

## 6. 基础姿态控制能力映射

这一组是最底层的“姿态/模式切换”。

---

### 6.1 `follow_owner`

**对外语义**

- 开始跟随主人

**底层映射**

- `switch_follow_state(follow = true)`

**原版来源**

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\SwitchFollowStateTool.java:14`

**幂等要求**

- 如果已经处于跟随状态，则直接返回成功，不重复切换

---

### 6.2 `stay_here`

**对外语义**

- 停止跟随，并在当前位置待命

**底层映射**

- `switch_follow_state(follow = false)`

**特别说明**

原版这里不是单纯“停下”，而是会把当前位置设为 home 模式点。

---

### 6.3 `sit_down`

**对外语义**

- 坐下

**底层映射**

- `switch_sit(sit = true)`

**原版来源**

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\SwitchSitTool.java:12`

---

### 6.4 `stand_up`

**对外语义**

- 起身

**底层映射**

- `switch_sit(sit = false)`

---

### 6.5 `set_schedule_day`

**对外语义**

- 切换到白天工作日程

**底层映射**

- `switch_schedule(schedule = "DAY")`

---

### 6.6 `set_schedule_night`

**对外语义**

- 切换到夜间工作日程

**底层映射**

- `switch_schedule(schedule = "NIGHT")`

---

### 6.7 `set_schedule_all`

**对外语义**

- 切换到全天工作日程

**底层映射**

- `switch_schedule(schedule = "ALL")`

---

## 7. 工作任务控制能力映射

这一组能力的底层基本都统一映射到：

- `switch_work_task(task_id = ...)`

这组能力不要直接暴露原版 `task_id` 给上层，而要给上层更稳定的能力名。

---

### 7.1 `enter_idle_mode`

**底层映射**

- `switch_work_task(task_id = "idle")`

**用途**

- 停止当前工作
- 进入待机陪伴状态

---

### 7.2 `start_melee_guard`

**底层映射**

- `switch_work_task(task_id = "attack", entity_id = 可选)`

**用途**

- 让女仆用近战保护主人

---

### 7.3 `start_bow_guard`

**底层映射**

- `switch_work_task(task_id = "ranged_attack", entity_id = 可选)`

---

### 7.4 `start_crossbow_guard`

**底层映射**

- `switch_work_task(task_id = "crossbow_attack", entity_id = 可选)`

---

### 7.5 `start_danmaku_guard`

**底层映射**

- `switch_work_task(task_id = "danmaku_attack", entity_id = 可选)`

---

### 7.6 `start_trident_guard`

**底层映射**

- `switch_work_task(task_id = "trident_attack", entity_id = 可选)`

---

### 7.7 `start_farming`

**底层映射**

- `switch_work_task(task_id = "farm")`

---

### 7.8 `start_sugar_cane_work`

**底层映射**

- `switch_work_task(task_id = "sugar_cane")`

---

### 7.9 `start_melon_work`

**底层映射**

- `switch_work_task(task_id = "melon")`

---

### 7.10 `start_cocoa_work`

**底层映射**

- `switch_work_task(task_id = "cocoa")`

---

### 7.11 `start_honey_work`

**底层映射**

- `switch_work_task(task_id = "honey")`

---

### 7.12 `start_cleanup_grass`

**底层映射**

- `switch_work_task(task_id = "grass")`

---

### 7.13 `start_cleanup_snow`

**底层映射**

- `switch_work_task(task_id = "snow")`

---

### 7.14 `start_shearing`

**底层映射**

- `switch_work_task(task_id = "shears")`

---

### 7.15 `start_milking`

**底层映射**

- `switch_work_task(task_id = "milk")`

---

### 7.16 `start_torch_placing`

**底层映射**

- `switch_work_task(task_id = "torch")`

---

### 7.17 `start_feeding_owner`

**底层映射**

- `switch_work_task(task_id = "feed")`

---

### 7.18 `start_feeding_animals`

**底层映射**

- `switch_work_task(task_id = "feed_animal")`

---

### 7.19 `start_fishing`

**底层映射**

- `switch_work_task(task_id = "fishing")`

---

### 7.20 `start_extinguishing`

**底层映射**

- `switch_work_task(task_id = "extinguishing")`

---

### 7.21 `start_board_game`

**底层映射**

- `switch_work_task(task_id = "board_games")`

---

## 8. 高层陪伴能力映射

这一组不是直接等于原版 Tool，而是由适配层做组合。

---

### 8.1 `guard_owner`

**对外语义**

- 进入保护主人模式

**建议内部流程**

1. `scan_owner_state`
2. `scan_position_state`
3. `scan_nearby_entities`
4. 判断最近威胁目标
5. 选择最合适的战斗任务

**底层组合**

- 无威胁时：
  - `follow_owner`
- 有威胁且近战合适：
  - `switch_work_task("attack", entity_id)`
- 有威胁且远程合适：
  - `switch_work_task("ranged_attack" / "crossbow_attack" / "danmaku_attack", entity_id)`

**说明**

这是一个典型“高层能力 -> 多个原版 Primitive”的例子。

---

### 8.2 `care_owner`

**对外语义**

- 进入照护主人模式

**建议内部流程**

1. `scan_owner_state`
2. `scan_inventory_state`
3. 判断主人是否饥饿 / 受伤 / 需要净化
4. 若条件满足则切 `feed`
5. 若不满足则只返回观察结果

**底层组合**

- `query_game_context("user")`
- `query_game_context("equipment")`
- `switch_work_task("feed")`

---

### 8.3 `light_up_nearby_area`

**对外语义**

- 让女仆去补光

**建议内部流程**

1. `scan_position_state`
2. `scan_inventory_state`
3. 判断光照低且背包有火把
4. 执行火把任务

**底层组合**

- `query_game_context("position")`
- `query_game_context("equipment")`
- `switch_work_task("torch")`

---

### 8.4 `keep_company`

**对外语义**

- 进入陪伴模式

**建议内部流程**

1. 检查与主人距离
2. 检查当前是否战斗 / 工作中
3. 选择：
   - 跟随
   - 坐下陪伴
   - 待机
   - 触发聊天回复

**底层组合**

- `scan_position_state`
- `scan_self_state`
- `follow_owner` 或 `sit_down` 或 `enter_idle_mode`

**说明**

这个能力是典型的 `MaidSoulCore` 语义层能力，不应直接暴露给原版。

---

### 8.5 `return_home_and_standby`

**对外语义**

- 回到驻守模式 / 家模式

**v1 映射策略**

- 先用 `stay_here` 作为最小实现

**v2 扩展策略**

- 等 `MaidSoulCore` 自己补上 `home` 上下文和寻路指令后，再做真正的“回家”

---

## 9. MaidSoulCore 自定义 Context 映射建议

这部分原版没有，需要我们自己补。

---

### 9.1 `scan_emotion_state`

**对外语义**

- 读取心情、精力、亲密度、压力、主动性

**底层实现**

- `MaidSoulCore` 自己的 `maidsoul_emotion` context

**为什么必须自己做**

- 原版没有情绪层

---

### 9.2 `scan_memory_state`

**对外语义**

- 读取最近事件、最近对话摘要、最近异常记录

**底层实现**

- `MaidSoulCore` 自己的 `maidsoul_memory` context

---

### 9.3 `scan_runtime_state`

**对外语义**

- 读取最近工具调用、最近 Planner 输出、当前模式、当前等待状态

**底层实现**

- `MaidSoulCore` 自己的 `maidsoul_runtime` context

---

### 9.4 `scan_home_state`

**对外语义**

- 读取家坐标、家区域、回家距离、家是否安全

**底层实现**

- `MaidSoulCore` 自己的 `maidsoul_home` context

---

### 9.5 `scan_vision_state`

**对外语义**

- 读取最近截图时间、视觉标签、VLM 摘要

**底层实现**

- `MaidSoulCore` 自己的 `maidsoul_vision` context

---

## 10. 能力命名规范建议

为了后面接口统一，建议能力命名遵守这几个约定。

### 10.1 读取能力

统一用：

- `scan_*`

例如：

- `scan_self_state`
- `scan_owner_state`
- `scan_nearby_entities`

### 10.2 原子控制能力

统一用动词开头：

- `follow_owner`
- `stay_here`
- `sit_down`
- `stand_up`

### 10.3 工作能力

统一用：

- `start_*`
- `enter_*`

例如：

- `start_fishing`
- `start_farming`
- `enter_idle_mode`

### 10.4 高层组合能力

统一用贴近人格语义的词：

- `guard_owner`
- `care_owner`
- `keep_company`
- `return_home_and_standby`

---

## 11. v1 推荐最小能力集

如果先做第一版，我建议只落这 12 个统一能力。

### 11.1 读取

- `scan_self_state`
- `scan_owner_state`
- `scan_position_state`
- `scan_nearby_entities`
- `scan_inventory_state`

### 11.2 控制

- `follow_owner`
- `stay_here`
- `sit_down`
- `stand_up`

### 11.3 工作

- `enter_idle_mode`
- `start_melee_guard`
- `start_fishing`

### 11.4 高层组合

- `guard_owner`
- `keep_company`

如果想严格压缩到最小闭环，甚至可以先只做：

- `scan_self_state`
- `scan_owner_state`
- `scan_position_state`
- `scan_nearby_entities`
- `follow_owner`
- `stay_here`
- `sit_down`
- `enter_idle_mode`
- `guard_owner`
- `keep_company`

---

## 12. 调试面板应该显示什么

既然做了映射层，调试面板就不应该只显示原版 Tool，而应该分两栏。

### 12.1 上层能力调用

- 能力名
- 输入参数
- 调用时间
- 调用来源
- 决策理由

例如：

- `guard_owner`
- reason: 主人 8 格内出现敌对实体

### 12.2 底层原版调用

- 映射出的原版 Tool
- 参数
- 执行结果

例如：

- `query_game_context("nearby_entities")`
- `switch_work_task("attack", entity_id=123)`

这样调试时才能分清：

- 是 Planner 决策错了
- 还是映射错了
- 还是原版执行失败了

---

## 13. 最终结论

对 `MaidSoulCore` 来说，做映射层是更合理的。

原因不是“看起来更优雅”，而是工程上更稳：

- 上层语义稳定
- 底层依赖隔离
- 调试更清晰
- 后续扩展 `情绪 / 记忆 / 视觉 / 主动性` 时不会把系统搞乱

第一版最值得做的，不是新增一堆复杂 Tool，而是先把：

- 原版 `Tool`
- 原版 `Context`
- 原版 `Task`

统一收束到一层 `MaidSoulCore Capability`。

这层一旦定住，后面的 `Planner`、`Reply`、`Debug Panel`、`Event Broadcast` 才能真正稳定下来。
