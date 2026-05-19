# Maid Soul Core Brain Prototype

这是一个独立的 Java 聊天大脑工程，不依赖 Forge、不依赖游戏环境。

当前目标已经调整为：按参考项目的核心源码职责，用 Java 做源码级重写，而不是写一个“思路相似”的女仆模组原型。

- 先完整重写聊天大脑、配置、模型服务、提示词、工具式规划器、回复器和后处理。
- prompt 使用 `prompts/zh-CN` 中同步过来的原始模板，不再另写一套。
- Forge/TLM 只是后续适配层，当前不作为核心目标。

## 许可注意

参考项目使用 GPL-3.0。若本工程继续按其源码职责和 prompt 做 Java 重写，最终发布时也需要按 GPL-3.0 处理派生代码的许可边界。

## 运行

1. 编辑 `config/model/llm.properties`，填入 `baseUrl`、`apiKey`、`model`。
2. 构建：

```powershell
.\scripts\build.ps1
```

3. 交互测试：

```powershell
.\scripts\run.ps1
```

也可以不写入配置文件，临时使用环境变量：

```powershell
$env:MAIDSOUL_API_KEY="sk-..."
.\scripts\run.ps1
```
