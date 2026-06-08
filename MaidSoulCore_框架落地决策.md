# MaidSoulCore 框架落地决策

## 结论

现阶段更合适的路线不是“先做完整 Forge 模组”，而是：

1. 先做 `MaidSoulCore` 纯 Java 核心框架；
2. 再做 `TLM + Forge 1.20.1` 适配层；
3. 最后再补客户端调试面板、网络同步、截图与视觉。

## 为什么不建议一开始就全写进模组

- Minecraft/Forge 生命周期复杂，调试成本高。
- LLM、Planner、Mood、Memory 这些本质上是“运行时框架”，不应该被游戏 API 反向主导。
- 你后面一定会反复改“事件合并、裁决链、工具权限、主动轮询”，这些在纯 Java 层改最快。

## 为什么要保留 TLM 适配层

- 你的目标实体仍然是 TLM 女仆，不是自定义实体。
- TLM 已经提供了关键扩展点：
  - `registerAITool`
  - `registerAIMaidContext`
  - `addExtraMaidBrain`
- TLM 也已经公开了关键事件：
  - `MaidTickEvent`
  - `InteractMaidEvent`
  - `MaidAttackEvent`
  - `MaidAfterEatEvent`
  - `MaidDeathEvent`
  - `MaidHurtTarget`

所以正确边界是：
- `MaidSoulCore core` 管运行时框架；
- `MaidSoulCore tlm-adapter` 管事件采集、状态读取、工具桥接。

## 这次参考 MaiBot 借了什么

从 `MaiBot` 源码里，真正值得借的是“职责切分”，不是照抄实现：

- `planner.py` / `brain_planner.py`
  - 说明 Planner 应独立于回复器存在；
  - 所以这里拆成了 `decision` + `planner` 两层。
- `events_manager.py`
  - 说明事件总线不能和聊天逻辑绑死；
  - 所以这里单独建了 `event` 包和统一 `MaidEvent`。
- `tool_use.py`
  - 说明工具调用应是独立运行时，而不是混在回复逻辑里；
  - 所以这里单独建了 `tool` 包。
- `webui/logs_ws.py` 和日志链路
  - 说明可观测性必须是一等能力；
  - 所以这里单独建了 `trace` 包，后面直接接你的调试面板。

但 Minecraft 版本不能照抄 MaiBot 的点也很明确：

- MaiBot 以聊天流为中心；
- MaidSoulCore 必须以“实体状态 + 世界事件 + 动作执行” 为中心；
- 所以这里把 `blackboard` 和 `adapter.tlm` 放到了更核心的位置。

## 这次已经落下来的骨架

代码目录：`src/main/java/com/maidsoulcore`

已经有的层：
- `event`：统一事件总线与优先级
- `blackboard`：状态快照与版本管理
- `decision`：L0/L1/L2 的路由入口
- `planner`：Planner 请求与动作计划结构
- `tool`：工具定义与注册表
- `trace`：全链路调试事件输出
- `runtime`：总调度运行时
- `adapter.tlm`：TLM 适配 SPI 占位

## 下一阶段应该怎么接到 Forge

第一步：
- 新建真正的 `forge adapter` 模块；
- 用 Forge 订阅器监听 TLM 事件；
- 将 TLM 事件转成 `MaidEvent` 发布到 core。

第二步：
- 给 `TlmSnapshotAdapter` 实现真实状态采集；
- 接女仆坐标、姿态、背包、任务、主人视角、home 点；
- 做 Tick 差分，补齐睡觉/作息/姿态切换事件。

第三步：
- 把调试面板接到 `trace`；
- 客户端看状态，服务端写决策；
- 用网络包同步最近 N 条调试事件。

## 当前判断

如果你现在的目标是“尽快把 AI 主链跑起来”，先做框架比先做完整模组更对。

因为真正难的不是 Forge 注册本身，而是：
- 事件如何合并；
- 什么时候交给 Planner；
- 工具怎么限权；
- 怎么让女仆“像人”但不乱动。
