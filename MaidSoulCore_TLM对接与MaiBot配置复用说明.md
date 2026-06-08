# MaidSoulCore TLM 对接与 MaiBot 配置复用说明

## 当前采用的方案

- `MaidSoulCore` 现在是一个独立 Forge 1.20.1 模组工程。
- 编译期不再使用 Modrinth 上旧版 TLM 依赖。
- 编译期直接使用本机已编好的新版本 TLM：
  - `E:/wallpaper/TouhouLittleMaid/build/libs/touhoulittlemaid-1.5.1-forge+mc1.20.1.jar`
- AI 人设与模型分工配置直接复用：
  - `E:/bot/MaiBotOneKey/modules/MaiBot/config/bot_config.toml`
  - `E:/bot/MaiBotOneKey/modules/MaiBot/config/model_config.toml`

## 为什么这样做

- Modrinth 上当前拉到的 TLM 版本没有你本地源码里的新 AI 扩展接口。
- 你本地编好的 `1.5.1` jar 已包含：
  - `registerAITool`
  - `registerAIMaidContext`
  - `ai.agent.context.*`
  - `ai.agent.tool.*`
- 所以现在这套方案既能编译，也能真正接你要的 AI 扩展点。

## 已完成的接入

### 1. Forge 模组工程
- 已把 `MaidSoulCore` 切成 ForgeGradle 工程。
- 已补 `mods.toml`、Gradle wrapper、`reobfJar` 打包链路。

### 2. TLM AI 扩展
- 已实现 `ILittleMaid` 扩展：
  - `com.maidsoulcore.forge.tlm.MaidSoulLittleMaidExtension`
- 已注册一批上下文分类：
  - `maidsoul_profile`
  - `maidsoul_runtime`
  - `maidsoul_models`
- 已注册一个调试工具：
  - `maidsoul_trace_tail`

### 3. MaiBot 配置复用
- 已实现 `MaiBotConfigService`
- 会从 MaiBot 配置里读取：
  - personality
  - reply_style
  - plan_style
  - planner/replyer/tool_use/vlm 的模型列表

### 4. 事件桥
- 已监听：
  - `MaidTickEvent`
  - `InteractMaidEvent`
  - `MaidAttackEvent`
  - `MaidAfterEatEvent`
  - `MaidDeathEvent`
- 已做轻量状态注册表和 trace ring buffer。

## 当前能提供给 TLM AI 的信息

### Prompt 注入
- MaiBot 风格的人设
- MaiBot 回复风格
- MaiBot 规划规则

### 动态查询
- 最近一次事件
- schedule/task/homeMode/sitting/sleeping
- 女仆名、主人名、血量、已观测事件数
- planner/reply/tool/vlm 当前配置模型组

### 调试工具
- `maidsoul_trace_tail(count)`
- 返回当前女仆最近若干条 MaidSoulCore 观测事件

## 下一步最值得继续做的

1. 接更多 runtime context
- 主人视角目标
- 女仆当前位置/朝向
- home 点
- 背包摘要
- 周边威胁摘要

2. 接更多控制 tool
- 护主
- 回家
- 切战斗风格
- 切主动陪伴模式

3. 接真正的多模型运行时
- 现在复用的是 MaiBot 配置，不是直接嵌 MaiBot Python 运行时
- 后面可以继续补 Java 侧 OpenAI-compatible client
- 让 Planner / Reply / VLM 真正按 MaiBot 风格拆开跑

