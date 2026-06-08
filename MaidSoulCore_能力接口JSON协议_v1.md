# MaidSoulCore 能力接口 JSON 协议 v1

## 1. 文档目的

这份文档用于固定 `MaidSoulCore Capability Layer` 的协议格式。

目标是让下面几层统一说同一种语言：

- `Planner`
- `Reply`
- `ActiveLoop`
- `DebugPanel`
- `TLM Adapter`
- 未来可能接入的文字模拟器 / Forge 模组 / 远程服务

这份协议不直接暴露原版 `TLM Tool` 细节，而是暴露 `MaidSoulCore` 自己的统一能力接口。

---

## 2. 协议原则

### 2.1 所有能力都走统一包结构

无论是：

- 读上下文
- 切姿态
- 切工作
- 组合能力

都使用统一的请求和响应格式。

### 2.2 先保证结构稳定，再逐步加字段

v1 的目标不是一次设计到完美，而是先保证：

- 能调用
- 能调试
- 能追踪
- 能稳定扩展

### 2.3 读写能力统一返回 trace

这样调试面板才能完整显示：

- 上层请求了什么
- 适配层实际调用了什么
- 为什么成功 / 失败

---

## 3. 统一请求结构

所有能力调用统一为：

```json
{
  "capability": "scan_self_state",
  "request_id": "cap-20260408-0001",
  "source": "planner",
  "timestamp": "2026-04-08T21:30:00+08:00",
  "maid_id": "maid-01",
  "owner_id": "player-01",
  "dry_run": false,
  "args": {}
}
```

---

## 4. 统一字段说明

### 4.1 顶层字段

- `capability`
  - 能力名
  - 例如：`follow_owner`、`scan_owner_state`
- `request_id`
  - 全链路唯一请求 id
  - 用于调试和 trace 关联
- `source`
  - 调用来源
  - 建议枚举：
    - `planner`
    - `reply`
    - `active_loop`
    - `event_reactor`
    - `manual_debug`
- `timestamp`
  - 发起时间
- `maid_id`
  - 女仆唯一标识
- `owner_id`
  - 主人唯一标识
- `dry_run`
  - 是否只模拟执行，不真正调用底层
- `args`
  - 能力自己的参数对象

---

## 5. 统一响应结构

所有能力调用统一返回：

```json
{
  "request_id": "cap-20260408-0001",
  "capability": "scan_self_state",
  "success": true,
  "status": "ok",
  "message": "self state loaded",
  "data": {
    "health": 18,
    "following": true,
    "sitting": false,
    "work_task": "idle"
  },
  "error": null,
  "trace": {
    "adapter": "TlmCapabilityAdapter",
    "mapped_calls": [
      {
        "type": "tlm_tool",
        "name": "query_game_context",
        "args": {
          "category_id": "status"
        },
        "result": "ok"
      }
    ],
    "cooldown_hit": false,
    "idempotent_skip": false,
    "duration_ms": 12
  }
}
```

---

## 6. 顶层响应字段说明

- `success`
  - 布尔值
- `status`
  - 建议枚举：
    - `ok`
    - `partial_ok`
    - `noop`
    - `blocked`
    - `invalid_args`
    - `cooldown`
    - `adapter_error`
    - `runtime_error`
- `message`
  - 给上层和调试面板看的短摘要
- `data`
  - 业务结果
- `error`
  - 失败详情
- `trace`
  - 映射执行过程

---

## 7. 统一错误结构

失败时 `error` 建议固定为：

```json
{
  "code": "MISSING_REQUIRED_CONTEXT",
  "message": "nearby entity context is empty",
  "retryable": true,
  "details": {
    "required_context": "nearby_entities"
  }
}
```

### 7.1 建议错误码

- `INVALID_CAPABILITY`
- `INVALID_ARGS`
- `MISSING_REQUIRED_CONTEXT`
- `MISSING_REQUIRED_ITEM`
- `TARGET_NOT_FOUND`
- `COOLDOWN_ACTIVE`
- `ALREADY_IN_STATE`
- `MAPPING_FAILED`
- `TLM_TOOL_FAILED`
- `TLM_TASK_FAILED`
- `RUNTIME_EXCEPTION`
- `UNSUPPORTED_IN_CURRENT_MODE`

---

## 8. Trace 结构

所有能力都应该返回 `trace`。

建议结构如下：

```json
{
  "adapter": "TlmCapabilityAdapter",
  "mapped_calls": [
    {
      "type": "tlm_tool",
      "name": "query_game_context",
      "args": {
        "category_id": "nearby_entities"
      },
      "result": "ok"
    },
    {
      "type": "tlm_tool",
      "name": "switch_work_task",
      "args": {
        "task_id": "attack",
        "entity_id": 123
      },
      "result": "ok"
    }
  ],
  "decision_reason": "hostile entity near owner",
  "cooldown_hit": false,
  "idempotent_skip": false,
  "duration_ms": 34
}
```

### 8.1 `mapped_calls` 的 type 建议枚举

- `tlm_tool`
- `tlm_context`
- `tlm_task`
- `maidsoul_context`
- `maidsoul_runtime`
- `rule_gate`

---

## 9. 能力分类与参数协议

下面开始定义每个能力的 `args` 和 `data`。

---

## 10. 状态读取能力协议

这类能力原则上：

- 无副作用
- 默认允许高频调用
- 适合被 Planner 和 DebugPanel 使用

---

### 10.1 `scan_self_state`

**请求**

```json
{
  "capability": "scan_self_state",
  "args": {}
}
```

**响应 data**

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

**底层映射**

- `query_game_context("status")`

---

### 10.2 `scan_owner_state`

**请求**

```json
{
  "capability": "scan_owner_state",
  "args": {}
}
```

**响应 data**

```json
{
  "name": "player",
  "health": 14,
  "mainhand": "minecraft:diamond_sword",
  "armor": [
    "minecraft:iron_helmet"
  ]
}
```

**底层映射**

- `query_game_context("user")`

---

### 10.3 `scan_position_state`

**请求**

```json
{
  "capability": "scan_position_state",
  "args": {}
}
```

**响应 data**

```json
{
  "self_pos": [100, 64, 100],
  "owner_pos": [104, 64, 102],
  "distance_to_owner": 4.5,
  "light_level": 7
}
```

**底层映射**

- `query_game_context("position")`

---

### 10.4 `scan_inventory_state`

**请求**

```json
{
  "capability": "scan_inventory_state",
  "args": {
    "include_backpack": true
  }
}
```

**参数说明**

- `include_backpack`
  - 是否返回背包详细摘要

**响应 data**

```json
{
  "mainhand": "minecraft:iron_sword",
  "offhand": "minecraft:torch",
  "inventory_items": [
    "minecraft:torch*16",
    "minecraft:bread*8"
  ],
  "armor_items": [
    "minecraft:iron_helmet"
  ]
}
```

**底层映射**

- `query_game_context("equipment")`

---

### 10.5 `scan_nearby_entities`

**请求**

```json
{
  "capability": "scan_nearby_entities",
  "args": {
    "max_count": 16,
    "only_hostile": false
  }
}
```

**参数说明**

- `max_count`
  - 最多返回数量
- `only_hostile`
  - 是否仅返回敌对实体

**响应 data**

```json
{
  "entities": [
    {
      "entity_id": 123,
      "type": "minecraft:zombie",
      "distance_to_maid": 6.0,
      "distance_to_owner": 4.0,
      "hostile": true
    }
  ]
}
```

**底层映射**

- `query_game_context("nearby_entities")`

**v1 说明**

- `only_hostile` 和 `max_count` 在 v1 可以由适配层二次过滤

---

### 10.6 `scan_world_state`

**请求**

```json
{
  "capability": "scan_world_state",
  "args": {}
}
```

**响应 data**

```json
{
  "game_time": "day",
  "weather": "clear",
  "dimension": "minecraft:overworld",
  "biome": "plains"
}
```

**底层映射**

- `query_game_context("world")`

---

### 10.7 `scan_effect_state`

**请求**

```json
{
  "capability": "scan_effect_state",
  "args": {}
}
```

**响应 data**

```json
{
  "effects": [
    "minecraft:regeneration",
    "minecraft:speed"
  ]
}
```

**底层映射**

- `query_game_context("effects")`

---

## 11. 基础姿态控制能力协议

---

### 11.1 `follow_owner`

**请求**

```json
{
  "capability": "follow_owner",
  "args": {
    "reason": "owner_requested"
  }
}
```

**响应 data**

```json
{
  "follow": true
}
```

**底层映射**

- `switch_follow_state(true)`

**特殊状态**

- 已在跟随中时：
  - `status = "noop"`
  - `error.code = "ALREADY_IN_STATE"` 可选

---

### 11.2 `stay_here`

**请求**

```json
{
  "capability": "stay_here",
  "args": {
    "reason": "manual_order"
  }
}
```

**响应 data**

```json
{
  "follow": false,
  "home_mode_anchor_set": true
}
```

**底层映射**

- `switch_follow_state(false)`

---

### 11.3 `sit_down`

**请求**

```json
{
  "capability": "sit_down",
  "args": {
    "reason": "keep_company"
  }
}
```

**响应 data**

```json
{
  "sitting": true
}
```

**底层映射**

- `switch_sit(true)`

---

### 11.4 `stand_up`

**请求**

```json
{
  "capability": "stand_up",
  "args": {
    "reason": "prepare_action"
  }
}
```

**响应 data**

```json
{
  "sitting": false
}
```

**底层映射**

- `switch_sit(false)`

---

### 11.5 `set_schedule`

**请求**

```json
{
  "capability": "set_schedule",
  "args": {
    "schedule": "ALL",
    "reason": "companion_mode"
  }
}
```

**参数说明**

- `schedule`
  - 枚举：
    - `DAY`
    - `NIGHT`
    - `ALL`

**响应 data**

```json
{
  "schedule": "ALL"
}
```

**底层映射**

- `switch_schedule(schedule)`

---

## 12. 工作任务能力协议

为了避免能力爆炸，v1 建议提供两层：

1. 通用入口 `set_work_task`
2. 少量常用语义别名能力

---

### 12.1 `set_work_task`

**请求**

```json
{
  "capability": "set_work_task",
  "args": {
    "task_id": "attack",
    "entity_id": 123,
    "reason": "hostile_near_owner"
  }
}
```

**参数说明**

- `task_id`
  - 原版 TLM task id
- `entity_id`
  - 可选
  - 攻击目标实体 id
- `reason`
  - 调试和日志用途

**响应 data**

```json
{
  "task_id": "attack",
  "entity_id": 123
}
```

**底层映射**

- `switch_work_task(task_id, entity_id?)`

**v1 建议允许的任务**

- `idle`
- `attack`
- `ranged_attack`
- `crossbow_attack`
- `danmaku_attack`
- `trident_attack`
- `farm`
- `sugar_cane`
- `melon`
- `cocoa`
- `honey`
- `grass`
- `snow`
- `feed`
- `shears`
- `milk`
- `torch`
- `feed_animal`
- `fishing`
- `extinguishing`
- `board_games`

---

### 12.2 `enter_idle_mode`

**请求**

```json
{
  "capability": "enter_idle_mode",
  "args": {
    "reason": "waiting_for_next_decision"
  }
}
```

**底层映射**

- `set_work_task(task_id = "idle")`

---

### 12.3 `start_fishing`

**请求**

```json
{
  "capability": "start_fishing",
  "args": {
    "reason": "owner_requested_relax"
  }
}
```

**底层映射**

- `set_work_task(task_id = "fishing")`

---

### 12.4 `start_melee_guard`

**请求**

```json
{
  "capability": "start_melee_guard",
  "args": {
    "entity_id": 123,
    "reason": "nearest_hostile_selected"
  }
}
```

**底层映射**

- `set_work_task(task_id = "attack", entity_id)`

---

## 13. 高层组合能力协议

这类能力是 `MaidSoulCore` 的重点。

特点是：

- 对上层来说更像“意图”
- 对底层来说会拆成多个步骤

---

### 13.1 `guard_owner`

**请求**

```json
{
  "capability": "guard_owner",
  "args": {
    "preferred_style": "auto",
    "allow_ranged": true,
    "reason": "owner_in_risk"
  }
}
```

**参数说明**

- `preferred_style`
  - `auto`
  - `melee`
  - `ranged`
- `allow_ranged`
  - 是否允许自动切到远程战斗

**响应 data**

```json
{
  "mode": "guard_owner",
  "selected_task": "attack",
  "selected_target": 123,
  "threat_count": 2
}
```

**建议内部流程**

1. `scan_self_state`
2. `scan_owner_state`
3. `scan_position_state`
4. `scan_nearby_entities`
5. 选择目标
6. 选择战斗任务
7. 调 `set_work_task`

**失败示例**

- 没有发现目标：
  - `status = "partial_ok"`
  - `message = "no hostile target found, fallback to follow"`
  - 然后改走 `follow_owner`

---

### 13.2 `care_owner`

**请求**

```json
{
  "capability": "care_owner",
  "args": {
    "reason": "owner_health_low"
  }
}
```

**响应 data**

```json
{
  "mode": "care_owner",
  "selected_task": "feed",
  "owner_health": 8,
  "care_needed": true
}
```

**建议内部流程**

1. `scan_owner_state`
2. `scan_inventory_state`
3. 判断是否满足照护条件
4. 满足则 `set_work_task("feed")`
5. 不满足则返回 `noop`

---

### 13.3 `keep_company`

**请求**

```json
{
  "capability": "keep_company",
  "args": {
    "preferred_distance": 4.0,
    "allow_sit": true,
    "reason": "companion_loop"
  }
}
```

**响应 data**

```json
{
  "mode": "keep_company",
  "selected_action": "follow_owner",
  "distance_to_owner": 8.5
}
```

**建议内部流程**

1. `scan_self_state`
2. `scan_position_state`
3. 如果太远：
   - `follow_owner`
4. 如果较近且允许坐下：
   - `sit_down`
5. 如果当前正在工作但不该工作：
   - `enter_idle_mode`

**说明**

这是最典型的长期在线陪伴基础能力。

---

### 13.4 `light_up_nearby_area`

**请求**

```json
{
  "capability": "light_up_nearby_area",
  "args": {
    "light_threshold": 7,
    "reason": "night_safety"
  }
}
```

**响应 data**

```json
{
  "selected_task": "torch",
  "light_level": 4,
  "has_torch": true
}
```

**建议内部流程**

1. `scan_position_state`
2. `scan_inventory_state`
3. 判断是否低于阈值
4. 判断是否有火把
5. 满足则 `set_work_task("torch")`

---

## 14. MaidSoulCore 自定义能力协议

这部分底层不走原版 TLM，而是走我们自己的 Runtime / Blackboard。

---

### 14.1 `scan_emotion_state`

**请求**

```json
{
  "capability": "scan_emotion_state",
  "args": {}
}
```

**响应 data**

```json
{
  "mood": "calm",
  "favorability": 68,
  "energy": 72,
  "stress": 15,
  "initiative": 54
}
```

---

### 14.2 `scan_memory_state`

**请求**

```json
{
  "capability": "scan_memory_state",
  "args": {
    "max_items": 8
  }
}
```

**响应 data**

```json
{
  "recent_events": [
    "owner_fed_maid",
    "maid_attacked_zombie",
    "owner_chatted_hello"
  ],
  "recent_summary": "recently stayed near owner and helped in minor combat"
}
```

---

### 14.3 `scan_runtime_state`

**请求**

```json
{
  "capability": "scan_runtime_state",
  "args": {}
}
```

**响应 data**

```json
{
  "planner_state": "idle_waiting",
  "reply_state": "ready",
  "active_loop_state": "cooldown",
  "last_tool_call": "follow_owner",
  "last_reply_time": "2026-04-08T21:28:00+08:00"
}
```

---

### 14.4 `scan_home_state`

**请求**

```json
{
  "capability": "scan_home_state",
  "args": {}
}
```

**响应 data**

```json
{
  "home_pos": [120, 64, 88],
  "distance_to_home": 35.0,
  "is_home_safe": true,
  "home_summary": "small house with bed, chest and torch light"
}
```

---

### 14.5 `scan_vision_state`

**请求**

```json
{
  "capability": "scan_vision_state",
  "args": {}
}
```

**响应 data**

```json
{
  "last_capture_time": "2026-04-08T21:29:58+08:00",
  "labels": [
    "owner_front_view",
    "zombie_nearby",
    "night"
  ],
  "summary": "the owner is facing a zombie in a dim outdoor area"
}
```

---

## 15. 建议的状态码语义

### 15.1 `ok`

- 成功完成

### 15.2 `partial_ok`

- 主流程成功，但有降级或回退

例如：

- `guard_owner` 没找到目标，回退成 `follow_owner`

### 15.3 `noop`

- 当前已经处于目标状态
- 或当前判断不需要执行

例如：

- 已在跟随状态，再次调用 `follow_owner`

### 15.4 `blocked`

- 因前置条件不满足而阻断

例如：

- 没火把却要补光
- 没桶却要挤奶

### 15.5 `cooldown`

- 命中冷却，不执行

---

## 16. 冷却和幂等建议

### 16.1 读能力

- 默认无冷却或超短冷却
- 适合 0.1 到 1 秒级缓存

### 16.2 原子控制能力

- 需要幂等
- 相同状态重复命令应返回 `noop`

### 16.3 高层组合能力

- 建议加能力级冷却

例如：

- `guard_owner`：500ms 到 1s
- `keep_company`：2s 到 5s
- `care_owner`：3s 到 10s

---

## 17. 调试面板协议展示建议

既然协议已经统一，调试面板就应直接显示这些字段：

- `request_id`
- `capability`
- `source`
- `args`
- `status`
- `message`
- `error.code`
- `trace.mapped_calls`
- `trace.duration_ms`

这样你调试时可以立刻看见：

- Planner 想做什么
- Adapter 实际做了什么
- TLM 最终是否执行成功

---

## 18. v1 最小闭环建议

如果现在要先落地一版能跑的协议实现，我建议先支持这 10 个能力：

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

这 10 个能力已经足够完成：

- 基础陪伴
- 主人跟随
- 遇敌保护
- 状态调试

然后第二批再补：

- `keep_company`
- `care_owner`
- `light_up_nearby_area`
- `scan_emotion_state`
- `scan_memory_state`

---

## 19. 最终结论

`MaidSoulCore` 现在最需要的不是继续发散更多概念，而是把协议定死。

一旦这份协议稳定下来，后面几件事都会变简单：

- 文字模拟器可以直接复用这套 JSON
- Forge 模组实现可以按能力逐个接线
- 调试面板不再和底层实现耦合
- Planner / Reply / ActiveLoop 的输入输出能稳定记录

因此 v1 最重要的工程成果不是“做了多少能力”，而是：

- 能力名稳定
- 输入输出稳定
- trace 稳定
- 错误码稳定

只要这四件事定住，后面系统就能持续长大，而不会越做越乱。
