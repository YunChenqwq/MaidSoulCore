# MaidSoulCore Forge 灵魂与记忆架构

## 核心原则

MaidSoulCore 是车万女仆的附属模组，但人格与记忆不应该被女仆实体 UUID 绑死。

- 女仆实体是身体：负责位置、生命、互动、TLM 行为和当前世界事件。
- `soulId` 是灵魂：负责长期人格、关系、画像、记忆图谱和跨存档身份。
- 世界是事件来源：移动到新世界、换身体、解绑、重新绑定都要成为长期记忆事实。
- MC 内部只加载必要配置：基础人设、模型调用、节奏、调试、绑定关系。
- 外部角色包/GUI 负责复杂编辑：人格包、推荐参数、记忆图谱浏览、批量维护、可视化配置。

## 目录约定

```text
config/maidsoulcore/
  model/llm.properties
  bot/identity.properties
  conversation/flow.properties
  conversation/splitter.properties
  memory/memory.properties
  debug/trace.properties
  characters/<characterId>/
  souls/<soulId>/soul.json
  memory/maids/<soulId>/
    life_memory.jsonl
    affect.json
    user_profile.json
    daily/
    a_memorix/
      paragraphs.jsonl
      entities.jsonl
      relations.jsonl
      episodes.jsonl
      external_refs.tsv
      maintenance_log.jsonl
```

绑定后的运行时会把 `maidId` 替换为 `soulId`，并把 `worldId` 设为 `*`。
这表示记忆是灵魂级共享目录，不再按单个存档分叉。

## 女仆 NBT

女仆实体只保存轻量绑定信息：

```text
MaidSoulCoreSoul:
  soulId
  bindingId
  ownerUuid
  maidUuid
  worldId
  boundAt
  schemaVersion
```

这份 NBT 的目标是让身体知道自己当前承载哪个灵魂，不承载长期人格本体。

## 灵魂事件

以下事件会被写入长期记忆，并进入图谱：

- `soul.first_bound`：第一次把灵魂绑定到女仆身体。
- `soul.transferred_world`：同一灵魂出现在新世界。
- `soul.rebound_same_world`：同一世界换了女仆身体。
- `soul.replaced_binding`：当前身体从旧灵魂切换到新灵魂。
- `soul.binding_refreshed`：刷新同一绑定。
- `soul.unbound`：解除灵魂绑定。
- `soul.migrated_legacy_maid`：从旧 maidUuid 记忆目录恢复到 soulId。

这些事件不是 prompt 口头设定，而是结构化世界事实。

## 调试命令

需要 OP 权限：

```text
/maidsoulcore give_core
/maidsoulcore souls [limit]
/maidsoulcore where
/maidsoulcore binding <maid>
/maidsoulcore migrate_legacy <maid> <soulId>
```

`give_core` 用于快速拿到灵魂核心道具。
正式流程仍然是手持灵魂核心右键车万女仆，打开绑定界面。

## 迁移策略

旧版可能存在这种目录：

```text
config/maidsoulcore/memory/maids/<maidUuid>/<worldId>/
```

迁移到：

```text
config/maidsoulcore/memory/maids/<soulId>/
```

迁移器只复制缺失文件，不覆盖已有 soul 记忆。迁移后会绑定当前女仆，并写入
`soul.migrated_legacy_maid` 事件。

## 后续 GUI 边界

MC 内 GUI 只建议保留：

- 当前女仆绑定的灵魂。
- 可用 soul 列表。
- 新建、绑定、解绑、迁移入口。
- 少量安全调试开关。

外部 GUI 更适合做：

- 角色包编辑。
- 推荐参数模板。
- 记忆图谱可视化。
- 记忆合并、去重、冲突处理。
- 多存档 soul 管理。
- 模型与 API 配置迁移。
