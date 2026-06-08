# MaidSoulCore AI 架构设计（独立模组，不改 TouhouLittleMaid 源码）

## 1. 基本原则
- `MaidSoulCore` 作为独立 Forge 模组运行。
- 不修改 `TouhouLittleMaid` 源码与资源。
- 仅通过其公开扩展点接入：`ILittleMaid` 的 `registerAITool`、`registerAIMaidContext` 等。

## 2. 对接方式（插件式）
- 在 `MaidSoulCore` 中实现一个 `ILittleMaid` 扩展实现类。
- 在该实现类里注册：
  - AI 工具（tool）
  - AI 上下文（context category + context item）
- 由 TLM 在启动时扫描扩展并注入（不需要改 TLM 代码）。

## 3. 系统分层（MaidSoulCore 内部）
1) `Perception` 感知层
- 采集：主人状态、女仆状态、周边实体、家坐标、时间天气。
- 可选：主人视角截图（客户端上报）与视觉标签。

2) `Blackboard` 世界黑板层
- 统一存储结构化状态：
  - owner_pov、self_state、home_state、tactical_state、emotion_state
- 提供 TTL 缓存，避免高频重复查询。

3) `Planner` 规划层
- 输出结构化 `ActionPlan`（JSON）：动作、参数、优先级、理由。
- 先规则门控，再 utility 评分，再 LLM 裁决。

4) `Executor` 执行层
- 将计划映射为工具调用。
- 执行保护：幂等、冷却、冲突互斥、预算限制。

5) `Reply` 表达层
- 负责人格化文本与语气，不直接做高风险状态决策。
- 与动作结果对齐，防止“说做不一致”。

6) `Memory+Mood` 记忆与情绪层
- 短期会话记忆 + 长期摘要记忆。
- 情绪三轴（valence/arousal/bond）驱动主动性与语气。

## 4. 多模型协作建议
- `Planner Model`：低温、强结构化、工具导向。
- `Reply Model`：拟人表达。
- `Vision Model`（可选）：异步处理截图，输出标签到黑板。
- `Summary Model`：低成本周期压缩记忆。

## 5. 你关心的工具集合（首批）
A. 主人视角
- `get_owner_status`
- `get_owner_focus_target`
- `get_owner_view_entities`
- `get_owner_view_snapshot`（可选）

B. 女仆自身
- `get_self_pose`
- `get_self_task`
- `get_self_inventory`
- `get_self_home`

C. 世界战术
- `scan_nearby_entities`
- `scan_threat_level`
- `scan_path_risk`

D. 动作控制
- `switch_follow_state_ext`
- `switch_sit_ext`
- `switch_work_task_ext`
- `guard_owner`
- `move_to_home`

## 6. 常驻在线机制
- 反应环：100~300ms（新消息/危险即时处理）
- 主动环：2~8s（决定是否主动互动/动作）
- 维护环：1~10min（摘要、情绪衰减、偏好更新）

主动触发示例：
- 主人低血或夜晚风险升高
- 长时间未互动但距离近
- 战斗结束后的安抚/提示

## 7. 与 TLM 的边界
- TLM 负责：实体基础行为、现有 AI 主链路、UI 与持久化框架。
- MaidSoulCore 负责：新增上下文、工具、规划策略、情绪与主动交互策略。
- 所有新增能力都通过扩展注册注入，不侵入 TLM 源。

## 8. Phase 1 落地（先做可运行）
1) 建立 `ILittleMaid` 扩展入口。
2) 先注册 3 个 context 分类：`owner_pov`、`self_state_plus`、`emotion_state`。
3) 先注册 5~8 个核心工具（读多写少）。
4) 加一个轻量 planner（规则+评分），先不引入视觉模型。
5) 打通“主动环”最小闭环（可配置开关和频率）。

## 9. 结论
- 你的方向完全正确：应该做独立扩展模组而不是改 TLM 本体。
- `MaidSoulCore` 作为“认知与陪伴层”，通过 API 注入能力，是长期可维护方案。
