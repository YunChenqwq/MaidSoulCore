# MaidSoulCore

MaidSoulCore 是一个独立的 Minecraft 女仆灵魂核心项目。

它的目标不是做普通聊天转发器，而是让女仆拥有可持续成长的角色状态：人格配置、长期记忆、关系进展、情绪变化、世界事件记录、视角摘要，以及围绕这些状态运行的规划与回复链路。

当前仓库包含两部分：

- Java 聊天大脑：负责模型调用、规划器、回复器、记忆检索、情绪事件、回复后处理和运行时节奏。
- Forge 适配层：负责把聊天核心接入 Minecraft、女仆实体、灵魂核心道具、配置页、聊天界面和视觉摘要。

## 功能

- 独立角色包：人设、关系、情绪、长期记忆和世界事实分层保存。
- 记忆系统：支持用户画像、关系事件、角色自我记忆、世界事实、修复记录和维护循环。
- 工具式规划：由 planner 决定回复、等待、查询记忆或观察当前视角。
- 视觉摘要：客户端截图后直接请求视觉模型，只把文字摘要写回女仆核心。
- Forge 配置页：基础、模型、视觉和调试分页配置。
- 灵魂绑定：通过灵魂核心把一个可迁移的灵魂绑定到女仆实体。

## 运行与配置

桌面原型配置位于：

```text
config/
```

Minecraft Forge 实例配置位于：

```text
<minecraft-instance>/config/maidsoulcore/
```

主要模型配置：

```text
config/maidsoulcore/model/llm.properties
config/maidsoulcore/model/vision.properties
```

不要把 API Key 提交到仓库。测试时可以写入本地配置文件，或使用环境变量。

## 构建

```powershell
.\gradlew.bat build --console=plain
```

构建产物在：

```text
build/libs/
```

## 状态

项目仍在快速迭代中。当前重点是稳定 Forge 聊天链路、完善记忆图谱、强化视觉工具调用，以及把角色包配置做成更直观的外部编辑体验。
