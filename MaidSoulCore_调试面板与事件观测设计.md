# MaidSoulCore 调试面板与事件观测设计

> 目标：做一个可视化面板 + 全事件输出链路，方便你在开发阶段快速定位“为什么女仆这么做”。

## 1. 设计目标

- **看得见状态**：当前情绪、任务、计划、工具执行都能实时看到。
- **看得见事件**：所有输入事件都能被观测、过滤、导出。
- **看得见决策**：事件如何进入裁决链路，为什么触发某动作，要有解释字段。
- **可控开销**：开发时全量，发布时可降级为采样/关闭。

---

## 2. 面板分层（建议）

### A. HUD 小面板（常驻）
- 位置：游戏左上角（可拖拽）。
- 显示：
  - 女仆当前模式（跟随/坐下/战斗/休息）
  - Mood 三轴（valence/arousal/bond）
  - Planner 状态（idle/running/fallback）
  - 最近一次动作与耗时
- 作用：边玩边观察，不打断体验。

### B. Debug 全量面板（F9 或命令打开）
- Tab1：`State`（黑板快照）
- Tab2：`Events`（事件流）
- Tab3：`Decisions`（裁决链路）
- Tab4：`Tools`（工具调用日志）
- Tab5：`LLM`（请求/响应摘要、token、延迟、错误）

---

## 3. “全事件输出接口”建议（核心）

定义统一事件观测接口：

```java
public interface EventTraceSink {
    void onEvent(TraceEvent event);
}
```

再做多路输出：
- `InMemoryRingBufferSink`：面板实时读取（最近 N 条，如 2000）。
- `JsonlFileSink`：写 `logs/maidsc-events-*.jsonl` 便于离线复盘。
- `ConsoleSink`：开发期直接打控制台。
- `RemoteSink`（可选）：HTTP/WebSocket 推给外部调试器。

建议通过 `CompositeTraceSink` 聚合，避免业务代码关心输出目的地。

---

## 4. 事件统一格式（调试友好）

```json
{
  "seq": 102334,
  "ts": 1710000000000,
  "side": "SERVER",
  "maid_id": "uuid",
  "owner_id": "uuid",
  "stage": "INGEST|NORMALIZE|QUEUE|GATE|PLAN|EXEC|REPLY",
  "type": "maid.attacked",
  "priority": "P0",
  "payload": {},
  "decision": {
    "route": "LOCAL_RULE",
    "reason": "low_hp + hostile_nearby",
    "latency_ms": 4
  },
  "link_id": "同一条链路追踪ID"
}
```

关键字段：
- `stage`：看到事件卡在哪一步。
- `route/reason`：看到“为什么这么做”。
- `link_id`：把“事件→计划→工具→回复”串起来。

---

## 5. 面板里最该有的调试功能

- **过滤器**：按 `type/priority/stage/maid_id` 筛选。
- **暂停滚动**：战斗高频事件时可冻结查看。
- **链路展开**：点一条事件看到完整决策树。
- **复制 JSON**：一键复制单条或批量导出。
- **统计视图**：每分钟事件量、LLM 请求量、失败率、平均延迟。

---

## 6. Forge 侧接入建议

- 服务端采集：Forge 事件订阅 + TLM API 事件 + Tick 差分事件。
- 客户端显示：通过网络包同步“已脱敏、可显示”的调试数据。
- 命令控制：
  - `/maidsc debug on|off`
  - `/maidsc debug level all|info|warn|error`
  - `/maidsc debug panel`
  - `/maidsc debug export`

建议默认：
- 单机开发：`debug=on + all`
- 服务器发布：`debug=off`（或仅 warn/error）

---

## 7. 性能与安全保护

- RingBuffer 固定大小，避免内存爆炸。
- 文件异步刷盘（批量写），避免主线程阻塞。
- 高频事件（如 tick）默认采样或聚合显示。
- LLM 原始内容可配“脱敏模式”（隐藏玩家隐私与密钥）。

---

## 8. 最小可行实现（MVP）

第一步先做 4 件事：
- `TraceEvent` 数据结构 + `link_id` 贯通。
- `InMemoryRingBufferSink` + `JsonlFileSink`。
- 一个简单 Debug Screen（先做 `Events` + `State` 两个 Tab）。
- `/maidsc debug` 命令组开关。

完成后你就能做到：
- 实时看所有事件；
- 回放某次“异常决策”全链路；
- 快速判断问题在采集层、裁决层还是模型层。

---

## 9. 和你当前架构的对应关系

- 对应 `MaidSoulCore_事件矩阵与裁决链路.md`：本文件提供“可观测性实现层”。
- 对应 `MaidSoulCore_AI架构设计.md`：本文件是运行期调试与运维能力补全。
- 建议把调试系统当成一等模块：`debug-observability`，不要后补。

