# MaidSoulCore：TouhouLittleMaid 原版 Agent 可复用能力映射

## 1. 文档目的

这份文档只回答一件事：

- `TouhouLittleMaid` 1.20 / 1.5.1 这一套原版 AI 框架里，已经有哪些能力可以直接拿来给 `MaidSoulCore` 用。

这里不讨论抽象理想架构，重点是：

- 原版已经给了哪些扩展点
- 原版已经有哪些工具
- 原版已经有哪些上下文
- 原版已经有哪些任务能力
- `MaidSoulCore` 应该复用哪些，补哪些，不应该重复造哪些轮子

---

## 2. 总体判断

原版 `TLM` 已经不是“只有普通实体 AI”这么简单。

它其实已经具备了一套基础版 Agent 框架：

1. `Tool`：让大模型调用的原子操作
2. `Context`：让大模型按需读取游戏状态
3. `Task`：让女仆切换成某种长期工作模式
4. `Extension API`：允许外部模组继续加 Tool / Context / Task

所以对 `MaidSoulCore` 来说，最优方案不是重写执行层，而是：

- 直接复用原版 `Tool`
- 直接复用原版 `Context`
- 直接复用原版 `Task`
- 在上层补 `情绪`、`记忆`、`主动互动`、`视觉摘要`、`调试观测`

一句话：

- 原版 `TLM` 适合做执行层
- `MaidSoulCore` 适合做认知层和人格层

---

## 3. 原版已经提供的扩展点

扩展入口在：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\api\ILittleMaid.java:51`

### 3.1 可直接用于 AI 扩展的接口

- `addMaidTask(TaskManager manager)`
  - 给女仆追加新的工作任务
- `addExtraMaidBrain(ExtraMaidBrainManager manager)`
  - 给女仆追加额外 Brain 数据，例如记忆、传感器、行为节点
- `registerAIChatSerializer(SerializerRegister register)`
  - 给 AI 聊天链路追加序列化支持
- `registerAITool(ToolRegister register)`
  - 注册新的 AI 工具
- `registerAIMaidContext(GameContextRegister register)`
  - 注册新的上下文分类和上下文项

### 3.2 对 MaidSoulCore 的实际意义

这意味着 `MaidSoulCore` 可以作为独立模组接入，不需要修改原版源码，就能：

- 给 LLM 增加新工具
- 给 LLM 增加新上下文
- 给女仆增加新任务
- 给女仆增加更复杂的 Brain / Memory 能力

这正好符合我们现在的路线：

- 不改 `TouhouLittleMaid`
- 单独做 `MaidSoulCore`

---

## 4. 原版 Tool 系统能复用什么

工具注册入口在：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\ToolRegister.java:18`

原版内置工具一共 6 个。

### 4.1 `use_skill`

源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\UseSkillTool.java:20`

作用：

- 加载某个 skill 的专用说明文本
- 本质上不是执行游戏动作
- 更像是“给模型临时补一段专业提示词”

对 `MaidSoulCore` 的意义：

- 可以复用这个思路做“陪伴模式 skill”“战斗模式 skill”“照护模式 skill”
- 也可以保留原版这个概念，在我们自己的 Planner 前后动态切换提示词包

### 4.2 `query_game_context`

源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\QueryGameContextTool.java:16`

作用：

- 按 `category_id` 读取一整类上下文
- 例如：附近实体、装备、主人状态、坐标等

这是原版最重要的读取型工具。

对 `MaidSoulCore` 的意义：

- 它天然适合做“按需拉取上下文”
- 这比一次性把所有信息都塞给模型更节省 token
- 我们后面新增的 `emotion`、`memory`、`home`、`trace`、`vision` 也应该继续沿用这种分类读取模式

### 4.3 `switch_follow_state`

源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\SwitchFollowStateTool.java:14`

作用：

- `follow=true`：开始跟随主人
- `follow=false`：停止跟随，并把当前位置设为 home 模式点

对 `MaidSoulCore` 的意义：

- 跟随 / 回家 / 待命这些高层行为，不要自己重复造底层控制
- 直接把这个 Tool 包成我们的高层语义即可

例如：

- `follow_owner` -> 调原版 `switch_follow_state(true)`
- `stay_here` -> 调原版 `switch_follow_state(false)`

### 4.4 `switch_work_task`

源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\SwitchWorkTaskTool.java:26`

作用：

- 切换女仆当前工作任务
- 参数核心是 `task_id`
- 攻击类任务还支持 `entity_id`

这是原版最关键的执行型能力之一。

对 `MaidSoulCore` 的意义：

- 大多数“去做某件事”的需求，都不需要新写 Tool
- 只需要把高层意图映射成某个 `task_id`

例如：

- “去钓鱼” -> `fishing`
- “去种地” -> `farm`
- “去灭火” -> `extinguishing`
- “保护我，打那个怪” -> `attack + entity_id`

### 4.5 `switch_schedule`

源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\SwitchScheduleTool.java:15`

作用：

- 切换日程模式：`DAY` / `NIGHT` / `ALL`

对 `MaidSoulCore` 的意义：

- 这是长期行为策略的底层控制开关
- 我们做“白天干活、晚上陪伴、全天守护”时，可以直接走这个接口

### 4.6 `switch_sit`

源码：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\implement\SwitchSitTool.java:12`

作用：

- 让女仆坐下或起立

对 `MaidSoulCore` 的意义：

- “休息”“乖乖坐下”“陪我待着别乱跑”这些语义都能直接映射

---

## 5. 原版 Tool 抽象本身值不值得复用

接口定义在：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\tool\ITool.java:40`

原版一个 Tool 具备这些核心字段和行为：

- `id()`
- `summary(EntityMaid maid)`
- `codec()`
- `onCall(...)`
- `onCallAsync(...)`
- `invocationSummary(...)`
- `trigger(...)`

### 5.1 这说明什么

原版 Tool 并不是简单的“命令字符串”，而是正式的函数调用协议。

它已经具备：

- 工具名
- 工具用途说明
- 参数结构
- 同步 / 异步执行
- 调用结果摘要
- 自动触发能力

### 5.2 对 MaidSoulCore 的建议

建议 `MaidSoulCore` 完全遵守这套抽象，不另起炉灶。

最合适的方式是：

1. 对外给 LLM 暴露 `MaidSoulCore` 的高层工具
2. 工具内部再调用原版 Tool / Task / Entity API

这样做的好处：

- 兼容原版生态
- 后续维护成本低
- 调试界面也更容易统一显示

---

## 6. 原版 Context 系统能复用什么

上下文注册入口在：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\GameContextRegister.java:27`

原版已经区分了两种上下文：

1. `promptContext=true`
   - 常驻注入到对话前
   - 适合短小但经常用的信息
2. `promptContext=false`
   - 只能通过 `query_game_context` 按需拉取
   - 适合较长、较重、较细的信息

这个设计是合理的，`MaidSoulCore` 应继续沿用。

### 6.1 原版常驻 Prompt Context

#### `world`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\prompts\WorldContexts.java:22`

内容大致包括：

- 游戏时间
- 天气
- 维度
- 生物群系

#### `status`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\prompts\MaidContexts.java:20`

内容大致包括：

- 女仆血量
- 睡眠状态
- 跟随状态
- 坐下状态
- 骑乘状态
- 当前日程
- 当前活动
- 当前工作任务

### 6.2 原版按需查询 Tool Context

#### `equipment`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\EquipmentMaidContexts.java:23`

内容：

- 主手物品
- 副手物品
- 背包物品
- 护甲物品

#### `user`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\UserContexts.java:21`

内容：

- 主人名称
- 主人血量
- 主人主手
- 主人护甲

#### `position`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\PositionMaidContexts.java:19`

内容：

- 女仆自身坐标
- 主人坐标
- 与主人的距离
- 光照等级

#### `nearby_entities`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\NearbyEntityMaidContexts.java:29`

内容：

- 附近实体类型
- 实体 id
- 与女仆距离
- 与主人距离

这是非常重要的一类上下文，因为攻击型任务切换时经常要用到目标实体。

#### `effects`

来源：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\ai\agent\context\tools\EffectsMaidContexts.java:22`

内容：

- 当前状态效果

### 6.3 这些原版 Context 已经能支撑什么

只靠原版这些 Context，模型已经可以做出下面这类判断：

- 我现在是不是在跟随主人
- 我现在适合坐下还是起身
- 我身边有没有敌对实体
- 主人是不是受伤了
- 我和主人的距离是否过远
- 我的装备、背包、护甲是否足够完成某任务
- 当前天气 / 时间 / 维度是否适合某行为

也就是说，原版已经具备最基础的“看环境再行动”的能力。

---

## 7. 原版 Task 系统能复用什么

任务注册在：

- `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskManager.java:35`

原版 `Task` 本质上就是“长期工作模式”。

对 `MaidSoulCore` 来说，它们完全可以被视作：

- 一组高层动作能力

### 7.1 战斗类能力

- `attack`
  - 近战攻击附近敌对生物
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskAttack.java:133`
- `ranged_attack`
  - 使用弓箭远程攻击
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskBowAttack.java:203`
- `crossbow_attack`
  - 使用弩攻击
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskCrossBowAttack.java:145`
- `danmaku_attack`
  - 使用御币弹幕攻击
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskDanmakuAttack.java:205`
- `trident_attack`
  - 使用三叉戟
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskTridentAttack.java:162`

### 7.2 农业与采集类能力

- `farm`
  - 普通农田作物种植和收获
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskNormalFarm.java:139`
- `sugar_cane`
  - 甘蔗种植和收获
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskSugarCane.java:87`
- `melon`
  - 南瓜 / 西瓜处理
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskMelon.java:101`
- `cocoa`
  - 可可豆种植和收获
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskCocoa.java:105`
- `honey`
  - 采蜂蜜 / 蜂脾
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskHoney.java:80`

### 7.3 清理与维护类能力

- `grass`
  - 清理杂草、花、草丛
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskGrass.java:56`
- `snow`
  - 清理积雪或收集雪
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskSnow.java:69`
- `shears`
  - 给附近动物剪毛
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskShears.java:59`
- `milk`
  - 给附近牛挤奶
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskMilk.java:66`
- `torch`
  - 在附近暗处放火把
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskTorch.java:69`
- `extinguishing`
  - 灭火
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskExtinguishing.java:74`

### 7.4 照料与陪伴类能力

- `feed`
  - 给主人喂食或解异常
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskFeedOwner.java:126`
- `feed_animal`
  - 喂养并繁殖附近动物
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskFeedAnimal.java:145`
- `board_games`
  - 找附近棋盘方块并游玩
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskBoardGames.java:52`
- `fishing`
  - 钓鱼
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskFishing.java:76`

### 7.5 基础状态类能力

- `idle`
  - 停止工作任务，进入待机
  - `E:\wallpaper\TouhouLittleMaid\src\main\java\com\github\tartaricacid\touhoulittlemaid\entity\task\TaskIdle.java:87`

### 7.6 这部分该怎么被 MaidSoulCore 利用

建议不要把这些任务再拆成大量重复工具。

更合理的做法是：

- `Planner` 输出高层目标
- `Executor` 统一映射成 `switch_work_task(task_id)`

这样 Tool 数量会更干净：

- 模型不用记几十个动作 Tool
- 只要学会“先看上下文，再切任务”

---

## 8. 原版已经能解决哪些问题

如果只看“能不能做一个会聊天、会执行、会感知环境的女仆 AI”，原版已经帮我们完成了下面几件大事：

### 8.1 已有执行层

已经能做：

- 跟随
- 停留
- 坐下
- 切日程
- 切工作任务

### 8.2 已有读取层

已经能读：

- 自身状态
- 主人状态
- 世界状态
- 附近实体
- 背包装备
- 状态效果

### 8.3 已有长期工作层

已经能持续执行：

- 战斗
- 农业
- 照料
- 维护
- 娱乐

### 8.4 已有插件入口

已经能扩：

- Tool
- Context
- Task
- Brain

也就是说，`MaidSoulCore` 完全不需要从“零 Agent”开始做。

---

## 9. 原版还缺什么

原版这套东西够做“会干活的 LLM 女仆”，但还不够做“像人在陪伴”的女仆。

它主要缺下面几层。

### 9.1 情绪层

原版没有真正的：

- 心情
- 紧张度
- 活力
- 依恋感
- 当下情绪驱动的表达和行为偏好

### 9.2 记忆层

原版没有适合陪伴式交互的：

- 最近事件短期记忆
- 长期摘要记忆
- 用户偏好记忆
- 关系历史

### 9.3 主动性层

原版更偏“被调用后执行”，不够“长期在线主动陪伴”。

缺：

- 主动打招呼
- 长时间无互动后的主动关心
- 战斗后安抚
- 夜晚提醒
- 风险预警

### 9.4 视觉层

原版没有你想要的：

- 固定周期截图
- 主人视角摘要
- VLM 输出的环境理解

### 9.5 调试与观测层

原版没有专门面向“LLM 行为调试”的：

- 事件广播可视化
- 当前黑板状态面板
- 最近工具调用链
- Planner 输入输出追踪

---

## 10. MaidSoulCore 应该怎么接原版

建议采用四层映射。

### 10.1 第一层：直接复用原版 Primitive

这一层不改语义，直接用：

- `query_game_context`
- `switch_follow_state`
- `switch_schedule`
- `switch_sit`
- `switch_work_task`

### 10.2 第二层：MaidSoulCore 高层封装

这一层给 Planner 暴露更贴近陪伴语义的能力，例如：

- `follow_owner`
- `stay_with_me`
- `guard_owner`
- `go_home_mode`
- `start_fishing`
- `care_owner`
- `scan_owner_state`

内部仍然转调原版 Primitive。

### 10.3 第三层：MaidSoulCore 新增 Context

建议新增的分类：

- `maidsoul_emotion`
  - 心情、能量、亲密度、压力、主动性
- `maidsoul_memory`
  - 最近事件摘要、最近对话摘要、最近异常
- `maidsoul_home`
  - 家坐标、家区域摘要、回家距离、家是否安全
- `maidsoul_vision`
  - 最近截图时间、视觉标签、视觉摘要
- `maidsoul_runtime`
  - 最近 Tool 调用、最近 Planner 决策、当前模式
- `maidsoul_relation`
  - 主人偏好、亲密互动历史、禁忌规则

### 10.4 第四层：MaidSoulCore 主动裁决器

这个是原版没有的，也是我们真正的核心差异：

- 事件流进入黑板
- Mood / Memory 持续更新
- Planner 周期性做轻裁决
- 在满足条件时主动说话或主动动作

---

## 11. 推荐的能力映射策略

### 11.1 什么应该直接复用

直接复用原版：

- 跟随
- 坐下
- 切日程
- 切任务
- 读主人状态
- 读自身状态
- 读附近实体
- 读装备背包

原因：

- 这些已经稳定
- 底层行为已经和 TLM 自身逻辑兼容
- 重写容易和原版内部状态冲突

### 11.2 什么应该包装复用

包装复用原版：

- 保护主人
- 回家待命
- 长驻陪伴
- 工作模式切换
- 风险响应

原因：

- 这些是高层语义
- 但底层仍然可以落到原版 Tool / Task

### 11.3 什么必须自己新增

必须自己新增：

- 情绪系统
- 记忆系统
- 主动互动系统
- 视觉摘要系统
- 调试面板和事件追踪
- 多模型协作调度

因为这些不是原版 TLM 的目标范围。

---

## 12. 对当前 MaidSoulCore 的施工建议

如果按你现在的目标继续落地，建议顺序如下。

### Phase 1：吃透原版执行层

先把这几个原版能力在我们的调试环境里统一封装好：

- `query_game_context`
- `switch_follow_state`
- `switch_sit`
- `switch_schedule`
- `switch_work_task`

输出统一调试日志：

- 输入参数
- 执行结果
- 当前女仆状态变化

### Phase 2：补上下文层

在原版已有 context 基础上，加我们自己的：

- `emotion`
- `memory`
- `home`
- `runtime`
- `vision`

### Phase 3：补主动裁决

让系统不只是“主人说一句，女仆回一句”，而是：

- 周期 Tick
- 事件广播
- 轻量规则门控
- 再交给 Planner 决定是否说话 / 是否行动

### Phase 4：补人格一致性

这一层接 MaiBot 风格配置：

- Planner 提示词
- Reply 提示词
- Tool Use 提示词
- VLM 提示词

最后把：

- 情绪
- 关系
- 记忆
- 当前上下文

一起喂给回复模型。

---

## 13. 最终结论

`TouhouLittleMaid` 原版已经有一套可复用的 Agent 基础设施。

它已经解决了：

- 怎么给 LLM 暴露工具
- 怎么给 LLM 暴露上下文
- 怎么把高层行为落到女仆工作任务
- 怎么允许外部模组扩展这些能力

因此 `MaidSoulCore` 最合理的定位不是替代原版 Agent，而是站在它上面补齐：

- 情绪
- 记忆
- 主动性
- 视觉
- 调试观测
- 多模型协作

最重要的工程判断是：

- 原版 `TLM` 负责“能执行”
- `MaidSoulCore` 负责“像人在陪伴”

这条边界清晰之后，后面的框架设计、事件广播、Planner、Reply、VLM、调试面板，都会顺很多。
