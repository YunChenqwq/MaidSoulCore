# Java 源码级重写映射表

这不是“参考思路”，而是后续 Java 实现的对照清单。每一块都要以参考源码的职责、输入输出和运行时行为为准。

参考源码规模：

- `src` 下约 419 个源文件。
- `prompts/zh-CN` 下 17 个 prompt 文件，已经原样同步到本工程。
- 许可为 GPL-3.0，继续源码级重写时需要保持许可证兼容。

## 第一优先级：聊天核心

| 参考文件 | Java 目标模块 | 状态 |
| --- | --- | --- |
| `src/maisaka/runtime.py` | `runtime.ConversationRuntime` | 部分原型，需重写为完整会话状态机 |
| `src/maisaka/reasoning_engine.py` | `reasoning.ReasoningEngine` | 部分原型，需改为工具式循环 |
| `src/maisaka/chat_loop_service.py` | `loop.ChatLoopService` | 待移植 |
| `src/maisaka/context_messages.py` | `context.*` | 待移植 |
| `src/maisaka/history_utils.py` | `history.HistoryUtils` | 待移植 |
| `src/maisaka/history_post_processor.py` | `history.HistoryPostProcessor` | 待移植 |
| `src/maisaka/planner_message_utils.py` | `planner.PlannerMessageUtils` | 待移植 |
| `src/chat/replyer/maisaka_generator_base.py` | `reply.ReplyGeneratorBase` | 待移植 |
| `src/chat/replyer/maisaka_generator.py` | `reply.ReplyGenerator` | 待移植 |
| `src/chat/replyer/replyer_manager.py` | `reply.ReplyerManager` | 待移植 |

## 第二优先级：模型服务层

| 参考文件 | Java 目标模块 | 状态 |
| --- | --- | --- |
| `src/llm_models/payload_content/message.py` | `llm.message.*` | 待移植 |
| `src/llm_models/payload_content/tool_option.py` | `tool.ToolCall/ToolOption` | 待移植 |
| `src/llm_models/payload_content/resp_format.py` | `llm.ResponseFormat` | 待移植 |
| `src/llm_models/model_client/base_client.py` | `llm.client.BaseModelClient` | 待移植 |
| `src/llm_models/model_client/openai_client.py` | `llm.client.OpenAiModelClient` | 待移植 |
| `src/llm_models/request_snapshot.py` | `llm.RequestSnapshot` | 待移植 |
| `src/services/llm_service.py` | `services.LlmServiceClient` | 待移植 |

## 第三优先级：配置树

| 参考文件 | Java 目标模块 | 状态 |
| --- | --- | --- |
| `src/config/config.py` | `config.ConfigManager` | 待移植 |
| `src/config/official_configs.py` | `config.schema.*` | 待移植 |
| `src/config/model_configs.py` | `config.model.*` | 待移植 |
| `src/config/file_watcher.py` | `config.FileWatcher` | 待移植 |
| `src/config/config_upgrade_hooks.py` | `config.upgrade.*` | 待移植 |

## 第四优先级：工具系统

| 参考文件 | Java 目标模块 | 状态 |
| --- | --- | --- |
| `src/core/tooling.py` | `tool.ToolRegistry` | 待移植 |
| `src/maisaka/builtin_tool/*.py` | `tool.builtin.*` | 待移植 |
| `src/maisaka/tool_provider.py` | `tool.BuiltinToolProvider` | 待移植 |
| `src/plugin_runtime/*` | `plugin.*` | 后续 |
| `src/mcp_module/*` | `mcp.*` | 后续 |

## 第五优先级：表达、学习、记忆

| 参考模块 | Java 目标模块 | 状态 |
| --- | --- | --- |
| `src/chat/heart_flow` | `heartflow.*` | 待移植 |
| `src/learners` | `learning.*` | 待移植 |
| `src/emoji_system` | `emoji.*` | 待移植 |
| `src/chat/knowledge` | `knowledge.*` | 待移植 |
| `src/A_memorix` | `memory.*` | 后续完整移植 |

## 明确不再允许的简化

- 不用 JSON planner 替代工具式 planner。
- 不用简单字符串历史替代结构化上下文消息。
- 不用裸 HTTP 客户端替代模型服务层。
- 不用自写简化 prompt 替代原始 prompt。
- 不把动作、思考、台词混在同一个输出文本里。

