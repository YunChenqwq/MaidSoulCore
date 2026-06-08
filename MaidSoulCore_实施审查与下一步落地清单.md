# MaidSoulCore 实施审查与下一步落地清单

> 日期：2026-04-08  
> 结论基线：以 `MaidSoulCore_MC落地实施方案_终版审查稿.md` 为主，结合当前 Forge 实现代码复核。  
> 新决策：**TLM 原版 agent 不再作为核心决策器使用，最多只作为底层执行器；也可以完全不使用。**

---

## 1. 本文档的目的

这份文档只回答 4 个问题：

1. 我们之前文档里承诺了什么；
2. 现在代码实际做到了什么；
3. 哪些内容还没有真正落地；
4. 在“**原版 agent 只作为执行器，甚至不使用**”的新前提下，下一步应如何施工。

---

## 2. 当前总判断

当前 `MaidSoulCore` 的状态是：

- 已经完成了 **基础聊天桥接**
- 已经完成了 **部分主动事件回复**
- 已经完成了 **基础 trace 采集与回显**
- 已经完成了 **分句输出、等待态、空闲搭话**

但是它**还不是**文档里定义的完整正式版，原因是：

- 还没有形成真正的 **MaidSoulCore 自主决策闭环**
- 还没有形成真正的 **多模型协作 runtime**
- 还没有形成真正的 **视觉链路**
- 还没有形成真正的 **调试面板 / debug 命令 / trace 文件导出**
- 还没有形成真正的 **Favorability / mood / energy / blackboard 正式系统**

因此，当前版本应定义为：

> **“MaidSoulCore Forge 陪伴原型版”**  
> 不是“完整 MC 正式版”。

---

## 3. 新架构立场：TLM 原版 agent 的定位

这是本次最重要的收敛决策。

### 3.1 不再采用的路线

以下路线现在明确放弃：

- 不再把 `TLM 原版 AI agent` 当作我们的主脑；
- 不再把 `TLM 原版 planner / function-call agent` 当作上层裁决中心；
- 不再以“改 prompt 让 TLM 原版 agent 更聪明”作为主线；
- 不再把系统核心能力绑定在原版 agent 内部实现细节上。

### 3.2 保留的路线

现在确定的路线是：

- **MaidSoulCore 自己负责认知层、规划层、情绪层、黑板层、视觉层、主动陪伴层**
- `TouhouLittleMaid` 只负责：
  - 女仆实体
  - 原版数据结构
  - 可复用执行能力
  - 可复用 UI / 聊天入口
  - 可复用好感度系统

### 3.3 TLM 原版 agent 的最终定位

最终可接受的两种定位：

#### 方案 A：只作为执行器

即：

- MaidSoulCore 输出结构化动作意图；
- 再映射到 TLM 原版已有的能力执行；
- TLM 只负责“做动作”，不负责“决定要不要做”。

适合的场景：

- 跟随 / 坐下 / 回家 / 切日程 / 切任务
- 原版已有的安全动作

#### 方案 B：完全不使用原版 agent

即：

- MaidSoulCore 直接调用实体接口、任务接口或自己做扩展执行器；
- 不走原版 agent 的推理链。

适合的场景：

- 主动陪伴
- 情绪表达
- 视觉解释
- 主动观察
- 多模型协作
- 调试 trace

### 3.4 当前推荐

当前推荐：

> **短期采用方案 A：TLM 只作为执行器**  
> **中期逐步过渡到方案 B：重要能力不依赖原版 agent**

原因：

- 施工速度更快；
- 兼容现有 TLM 女仆能力；
- 不会被原版 agent 的内部实现反向绑架；
- 后续替换更平滑。

---

## 4. 已落实内容

以下内容已经在当前代码中落地。

### 4.1 聊天桥接

已完成：

- MaiBot 配置目录读取
- TLM LLM 站点同步
- `customSetting` 自动注入
- 女仆聊天预设自动注入
- 模组设置面板基础配置

说明：

- 当前是“桥接式复用 TLM 聊天入口”
- 不是 MaidSoulCore 完全接管聊天链

### 4.2 主动回复基础链

已完成：

- 受击事件回复
- 交互事件回复
- 吃饭事件回复
- 睡眠切换回复
- 敌对生物摘要提醒
- 空闲陪伴搭话

### 4.3 拟真输出基础链

已完成：

- 等待态气泡
- 模型返回后的分句拆分
- 逐句输出到聊天气泡和聊天栏
- 不再总是整段瞬间弹出

### 4.4 trace 基础能力

已完成：

- 内存 ring buffer
- `maidsoul_trace_tail` 工具
- 基础事件 trace
- trace 聊天栏回显

### 4.5 基础环境规则

已完成：

- 普通敌对 `< 8` 不主动提醒
- 高风险白名单怪绕过阈值
- 主动聊天冷却

---

## 5. 尚未落实内容

以下是当前真正缺失的核心模块。

## 5.1 MaidSoulCore 自主决策层

当前状态：

- 没有正式进入 Forge 运行时
- 还没有把 `planner / tool_use / reply / vision` 组织成完整协作链

未落实点：

- Planner 正式运行
- ToolUse 正式运行
- 结构化 ActionPlan 正式落地
- 决策门控 `DecisionGate` Forge 化
- 多阶段 trace：`GATE -> PLAN -> EXEC -> REPLY`

结论：

> 现在只有“主动回复”，还没有“自主裁决”。

---

## 5.2 正式黑板系统

当前状态：

- Forge 侧只有轻量状态缓存
- 不是文档中的完整 blackboard

文档承诺但未落实：

- `mood`
- `initiative`
- `energy`
- `follow_policy`
- `vision`
- `threat_summary`
- `trace_tail`
- `last_reply`
- `recent_events`
- `relation_context`

结论：

> 现在的状态结构只能算“调试缓存”，还不能算“LLM 黑板”。

---

## 5.3 好感度正式接入

当前状态：

- 只读了 TLM 的好感度
- 还没把行为奖励 / 惩罚真正调用到 `FavorabilityManager`

未落实点：

- 事件到 `FavorabilityManager.apply(...)` 的映射
- `canAdd(...)` 限流复用
- 聊天奖励、行为奖励、照护奖励的正式归口
- 负面事件扣分归口

结论：

> 现在还没有真正做到“复用 TLM 好感度作为唯一真值”。

---

## 5.4 情绪 / 心情 / 主动性系统

当前状态：

- 文档设计了 mood / initiative
- Forge 正式版还没做

未落实点：

- 心情三轴
- 压力值
- 主动性
- 情绪如何影响 planner / reply / 行为偏好

结论：

> 目前“像真人”的程度，主要来自 prompt 和分句，不来自状态机。

---

## 5.5 能量系统

当前状态：

- 文本游戏里有
- Forge 正式版里没有

未落实点：

- `energy 0~100`
- 能量状态分层
- 任务耗能
- 低能量禁用任务
- 睡觉/休息恢复
- 对 planner 的硬门控

结论：

> 当前女仆不会因为疲劳而改变真实行为策略。

---

## 5.6 视觉链路

当前状态：

- Forge 模组里没有真实截图链
- 没有真实 VLM 调用链

未落实点：

- 定时截图
- 主人视角截图
- 女仆视角或环境视角抽象
- 视觉模型解释
- 视觉摘要写入黑板
- `vision.capture` 正式事件

结论：

> 这是当前最影响“像真人在线陪伴”的缺口之一。

---

## 5.7 原版任务能力映射

当前状态：

- 文档已定
- 提示词里提到
- Forge 正式版没有完整落地

未落实点：

- `switch_follow_state`
- `switch_sit`
- `switch_schedule`
- `switch_work_task`
- `query_game_context`

以及配套：

- 主人位置
- 女仆位置
- 女仆朝向
- 家坐标
- 周围实体
- 周围敌对摘要
- 背包摘要

结论：

> 现在还没有形成“认知 -> 工具 -> 执行”的真实闭环。

---

## 5.8 事件矩阵未补齐

文档承诺的第一批事件里，以下还没完整接入：

- `owner.attacked`
- `owner.feed_maid`
- `maid.follow.changed`
- `vision.capture`
- `world.night.enter`
- `world.day.enter`

说明：

- 现在接入的还是最基础一批事件；
- 还不是完整事件广播总线。

---

## 5.9 调试系统正式版

当前状态：

- 有 trace
- 有聊天栏回显
- 没有真正 panel

未落实点：

- HUD 小面板
- 全量 Debug Screen
- 命令：
  - `/maidsc debug on`
  - `/maidsc debug off`
  - `/maidsc debug panel`
  - `/maidsc debug export`
- JSONL 文件 trace
- 链路追踪 `link_id`
- LLM 请求/延迟/错误统计面板

结论：

> 当前只能算“调试基础设施雏形”，不是完整调试系统。

---

## 5.10 聊天链正式接管

当前状态：

- 仍以 TLM 原版聊天入口为主
- MaidSoulCore 只是注入配置和做主动旁路

未落实点：

- 用户消息进入 MaidSoulCore runtime
- MaidSoulCore 决定是否查询工具 / planner / reply
- 再把最终结果回灌到 TLM UI

结论：

> 目前是“桥接”，不是“接管”。

---

## 6. 哪些旧文档结论现在要修正

以下内容需要按新决策修正。

### 6.1 “原版 agent 可复用”要改写

旧说法：

- 原版 agent / tool / context 尽量复用

新说法：

- **原版 context / task / entity / UI 可复用**
- **原版 agent 不作为主脑复用**
- **原版 agent 最多作为执行器，不作为认知核心**

### 6.2 “接入 TLM 聊天链”要重新定义

旧理解：

- 复用原版聊天链即可

新理解：

- 短期：复用原版 UI
- 中期：消息进入 MaidSoulCore runtime 再输出
- 长期：TLM 聊天界面只作为展示壳

### 6.3 “工具映射”要强调独立接口层

现在必须明确：

- 上层只能看见 `MaidSoulCore Tool API`
- 底层是否映射到 TLM，由 adapter 决定
- 不允许让上层 prompt 直接绑定原版 agent 内部结构

---

## 7. 下一步施工顺序

这里按“最该做”和“最影响最终效果”排序。

## Phase A：先把主脑独立出来

目标：

- 不再依赖原版 agent 作为决策器

要做：

- 建立 Forge 正式版 `DecisionGate`
- 建立 Forge 正式版 `PlannerRuntime`
- 建立 Forge 正式版 `ToolUseRuntime`
- 聊天消息先进入 MaidSoulCore runtime

交付标准：

- 主动回复和被动聊天都走同一套 runtime

---

## Phase B：把工具层做实

目标：

- 真正让女仆“能做事”

要做：

- 定义 `MaidSoulCore Tool API`
- 做 TLM Adapter
- 先接 5 个核心工具：
  - 跟随切换
  - 坐下切换
  - 日程切换
  - 工作任务切换
  - 游戏上下文查询

交付标准：

- planner/tool_use 可以真正驱动实体行为

---

## Phase C：把视觉链路做实

目标：

- 真正具备“在线观察主人和环境”的能力

要做：

- 客户端截图采样
- 发到服务端或本地视觉调度器
- VLM 总结
- 写入 blackboard
- 形成 `vision.capture`

交付标准：

- 女仆能基于视野而不是只基于事件说话

---

## Phase D：把状态机做实

目标：

- 真正形成“像一个持续在线的人”

要做：

- 好感度事件映射到 `FavorabilityManager`
- mood 三轴
- initiative
- energy
- relation context

交付标准：

- 同一个事件在不同状态下会得到不同决策与不同语气

---

## Phase E：把调试系统做实

目标：

- 让后续调试效率足够高

要做：

- HUD
- Debug Screen
- `/maidsc debug` 命令
- JSONL trace
- link_id 全链路

交付标准：

- 任何一次异常行为都能现场追踪原因

---

## 8. 最终定版建议

从现在开始，建议把项目定义改成：

> **MaidSoulCore = 女仆认知与陪伴核心**  
> **TLM = 女仆实体与底层执行宿主**

进一步明确：

- **不用 TLM 原版 agent 做主脑**
- **只复用 TLM 实体、任务、UI、好感度、部分执行能力**
- **MaidSoulCore 自己做 planner / tool_use / reply / vision / mood / blackboard / trace**

---

## 9. 一句话结论

当前缺的不是“再调一下 prompt”，而是：

1. **Forge 正式版主脑 runtime**
2. **正式工具层**
3. **正式视觉链**
4. **正式状态机**
5. **正式调试系统**

只要这五块补上，项目就会从“会说话的原型”进入“真正在线陪伴 AI”阶段。

