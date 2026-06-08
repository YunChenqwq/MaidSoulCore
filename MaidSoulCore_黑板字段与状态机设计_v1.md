# MaidSoulCore 黑板字段与状态机设计 v1

## 1. 文档目标

这份文档用于把 `MaidSoulCore` 内部最核心的状态定下来。

重点解决 4 件事：

1. 好感度怎么涨，怎么限流
2. 能量怎么消耗，何时禁止继续执行任务
3. 发言冷却怎么和截图 / 感知节奏绑定
4. 这些状态如何进入黑板并影响 Planner

这份文档是前面几份文档的内部落地补充。

---

## 2. 总体原则

### 2.1 好感度不能无限刷

如果不限制：

- 玩家连续对话就能刷满
- 一个事件反复触发就会失真
- 关系系统会很快失去意义

所以必须做：

- 每日总上限
- 按行为类型分桶上限
- 同类行为冷却

### 2.2 能量必须成为行为闸门

如果没有能量约束：

- 女仆会无限执行任务
- 看起来不像长期在线陪伴，而像无消耗脚本

所以：

- 不同任务要扣不同能量
- 能量低于阈值后，要限制工作类行为
- 但保留基础跟随和聊天能力

### 2.3 发言冷却不应该固定写死

你这个想法是对的：

- 可以和截图 / 感知刷新节奏绑定

这样做的好处是：

- 系统时钟统一
- 非聊天状态下不会太吵
- 聊天状态下又可以足够自然

---

## 3. 黑板主字段总览

建议在 `MaidSoulCoreBlackboard` 中固定这些字段。

### 3.1 关系与情绪字段

- `favorability_total`
  - 当前总好感
- `favorability_daily_gain`
  - 今日已增加好感
- `favorability_daily_gain_reset_day`
  - 今日计数归属日期
- `favorability_bucket_chat_gain`
  - 今日聊天类好感累计
- `favorability_bucket_action_gain`
  - 今日行为类好感累计
- `favorability_bucket_care_gain`
  - 今日照护类好感累计
- `mood`
  - 当前心情标签
- `stress`
  - 当前压力值
- `initiative`
  - 当前主动性

### 3.2 能量字段

- `energy`
  - 当前能量值，建议范围 `0 ~ 100`
- `last_energy_update_time`
  - 上次能量自然恢复时间
- `energy_state`
  - `HIGH` / `NORMAL` / `LOW` / `EXHAUSTED`
- `energy_block_work`
  - 是否禁止继续执行工作任务

### 3.3 互动节奏字段

- `last_reply_time`
  - 上次发言时间
- `last_active_speak_time`
  - 上次主动发言时间
- `last_event_speak_time_by_type`
  - 各事件类型最近一次发言时间
- `chat_session_active`
  - 当前是否处于聊天会话期
- `chat_session_expire_time`
  - 聊天会话超时点
- `last_capture_time`
  - 上次截图时间
- `capture_interval_ms`
  - 截图间隔

### 3.4 跟随与意图字段

- `follow_policy`
  - `DEFAULT_FOLLOW`
  - `EXPLICIT_STAY`
- `follow_policy_source`
  - `system_default`
  - `owner_command`
- `follow_policy_reason`
  - 最近一次切换理由
- `last_owner_explicit_command`
  - 最近一次明确命令
- `last_owner_explicit_command_time`
  - 最近一次明确命令时间

### 3.5 事件与边沿字段

- `recent_events`
  - 最近事件队列
- `was_sleeping_last_tick`
  - 上一轮是否睡眠
- `sleep_cycle_id`
  - 当前睡眠周期编号
- `sleep_enter_announced`
  - 当前睡眠周期是否已说晚安
- `sleep_exit_announced`
  - 当前睡眠周期是否已说早安

---

## 4. 好感度系统设计

你提出的约束应该直接定成规则。

---

## 5. 好感度主规则

### 5.1 每日总上限

建议：

- `每日最多增加 30 点好感`

也就是：

- `favorability_daily_gain >= 30` 后
- 当天所有正向事件都不再继续增加好感

这条规则非常重要，因为它强制关系成长变慢，不会被刷爆。

### 5.2 分桶上限

建议把正向好感按来源分成 3 桶。

#### 聊天类

- `favorability_bucket_chat_gain`
- 每日上限：`10`

来源示例：

- 连续正常对话
- 主动聊天时的积极互动

#### 行为类

- `favorability_bucket_action_gain`
- 每日上限：`10`

来源示例：

- 主人互动
- 一起活动
- 陪伴成功

#### 照护类

- `favorability_bucket_care_gain`
- 每日上限：`10`

来源示例：

- 主人喂食
- 主人照顾女仆
- 特殊关怀事件

这样就得到：

- 总上限 `30`
- 每桶上限 `10`

### 5.3 同类行为冷却

必须加。

建议：

- 聊天类加分冷却：`10min`
- 互动类加分冷却：`5min`
- 喂食类加分冷却：`15min`
- 睡觉晚安 / 早安类：默认 `0` 分或极低分，不应成为刷分手段

### 5.4 单次加分建议

#### 普通对话

- `+1`
- 但一天最多记 `10`

#### 主人主动交互

- `+1 ~ +2`

#### 主人喂食

- `+2`

#### 重大照护 / 关键陪伴

- `+2 ~ +3`

#### 睡觉问候

- 建议不直接加好感
- 或者最多 `+0 / +1`，并且一天最多一次

### 5.5 不加分的情况

以下情况建议只更新记忆，不加好感：

- 机械式连续聊天刷屏
- 短时间重复点击
- 已达桶上限
- 已达每日总上限

---

## 6. 好感度事件处理流程

建议固定为：

1. 事件进入
2. 识别事件归属桶
3. 检查事件冷却
4. 检查桶上限
5. 检查每日总上限
6. 若都通过，再增加好感
7. 更新记忆和 trace

建议返回结构：

```json
{
  "applied": true,
  "gain": 2,
  "bucket": "care",
  "bucket_total": 6,
  "daily_total": 18,
  "reason": "owner_fed_maid"
}
```

如果失败：

```json
{
  "applied": false,
  "gain": 0,
  "reason": "daily_cap_reached"
}
```

---

## 7. 能量系统设计

能量建议单独作为硬门控，不要只当装饰条。

---

## 8. 能量基础规则

### 8.1 数值范围

建议：

- `energy` 范围固定 `0 ~ 100`

### 8.2 能量状态分段

建议：

- `80 ~ 100`：`HIGH`
- `50 ~ 79`：`NORMAL`
- `20 ~ 49`：`LOW`
- `0 ~ 19`：`EXHAUSTED`

### 8.3 低能量行为限制

#### `LOW`

- 不再主动开启高消耗任务
- 保留：
  - 跟随
  - 基础聊天
  - 简单陪伴

#### `EXHAUSTED`

- 禁止工作任务
- 禁止主动战斗任务
- 尽量转入：
  - 待机
  - 坐下
  - 休息
  - 睡觉相关行为

### 8.4 一条硬规则

你提的这个必须写死：

- `能量低于阈值后不再执行任务`

建议阈值：

- `energy < 20` 时，禁止所有工作任务切换

例外：

- 跟随主人
- 紧急自保
- 紧急保护主人

---

## 9. 任务耗能建议

不同任务应有不同耗能。

### 9.1 低耗能

- `follow_owner`
- `stay_here`
- `sit_down`
- `enter_idle_mode`
- 普通聊天

建议消耗：

- `0 ~ 1`

### 9.2 中耗能

- `fishing`
- `feed`
- `feed_animal`
- `torch`
- `grass`

建议消耗：

- 每次任务触发或每个工作周期 `2 ~ 4`

### 9.3 高耗能

- `attack`
- `ranged_attack`
- `crossbow_attack`
- `danmaku_attack`
- `trident_attack`
- `farm`
- `melon`
- `cocoa`
- `snow`
- `extinguishing`

建议消耗：

- 每个工作周期 `4 ~ 8`

### 9.4 特殊耗能：受击与高压

建议：

- 受击时额外扣 `1 ~ 3`
- 持续战斗时每轮额外扣压力量

### 9.5 恢复机制

建议：

- 每分钟自然恢复 `2 ~ 4`
- 睡觉状态恢复加速
- 被主人喂食时恢复额外能量

---

## 10. 能量对 Planner 的影响

Planner 不应只看“能不能做”，还要看“值不值得做”。

建议：

### 10.1 任务前检查

在所有 `set_work_task` 和高层工作能力前，先查：

- `energy`
- `energy_state`

### 10.2 自动回退策略

如果能量不足：

- 工作类能力返回 `blocked`
- 给出原因 `LOW_ENERGY`

例如：

```json
{
  "success": false,
  "status": "blocked",
  "message": "energy too low for work task",
  "error": {
    "code": "LOW_ENERGY",
    "message": "current energy is 14"
  }
}
```

### 10.3 高层替代行为

低能量时，Planner 应优先选：

- `keep_company`
- `sit_down`
- `enter_idle_mode`
- `sleep`

而不是继续派去干活。

---

## 11. 发言冷却设计

你这个想法应该定成系统主策略。

---

## 12. 发言冷却与截图间隔绑定

建议把非聊天状态的发言冷却，与截图 / 感知节奏绑定。

### 12.1 非聊天状态

当系统处于：

- 普通跟随
- 无主动聊天
- 无主人直接对话

则发言冷却建议：

- `speak_cooldown = capture_interval_ms`
- 或 `speak_cooldown = capture_interval_ms * 1 ~ 2`

例如：

- 截图间隔 5 秒
- 则普通主动说话冷却至少 5 秒

这样做的逻辑很顺：

- 每轮感知一次
- 每轮最多考虑一次主动表达

### 12.2 聊天会话状态

当进入聊天状态时：

- 发言冷却不再受截图间隔限制

也就是你说的：

- `在聊天时就无限制`

但这里建议做得更严谨一点：

- 不是完全无任何限制
- 而是进入 `chat_session_active = true`
- 在这个状态下使用“极短冷却”

建议：

- 聊天会话中：
  - 最短发言间隔 `300ms ~ 1000ms`
- 不再受截图周期限制

这样既自然，也不会因为异步回调抖动而重复刷句子。

### 12.3 聊天会话的开始条件

建议任一满足则开始：

- 主人发送文本
- 主人主动点女仆并触发交流
- 高优先级事件要求即时回应

### 12.4 聊天会话结束条件

建议：

- 最近 `15s ~ 30s` 没有对话交互
- 则 `chat_session_active = false`

---

## 13. 发言冷却分层建议

建议不要只有一个冷却，而是三层。

### 13.1 全局发言冷却

- 防止整体过吵

### 13.2 事件类型冷却

例如：

- 受击反馈冷却
- 喂食反馈冷却
- 睡觉反馈冷却

### 13.3 重复内容冷却

- 最近一句和当前候选内容高度相似时，压掉

---

## 14. 典型规则示例

### 14.1 普通跟随时

状态：

- `chat_session_active = false`
- `capture_interval_ms = 5000`

规则：

- 主动发言最短间隔 `>= 5000ms`
- 没有高价值事件时，不主动发言

### 14.2 聊天进行中

状态：

- `chat_session_active = true`

规则：

- 发言冷却降到 `500ms`
- 不参考截图间隔

### 14.3 睡前问候

状态：

- 检测到 `MAID_SLEEP_ENTER`

规则：

- 可立即发一次“晚安”
- 同一睡眠周期不再重复

### 14.4 低能量跟随

状态：

- `energy = 16`
- `follow_policy = DEFAULT_FOLLOW`

规则：

- 仍允许跟随
- 不允许开启种地、战斗、灭火等工作任务
- 回复语气更疲惫

---

## 15. 推荐状态机

建议把系统粗略分成 5 个上层状态。

### 15.1 `FOLLOW_IDLE`

特征：

- 默认跟随
- 不在明确工作中
- 不在聊天中

### 15.2 `CHAT_ACTIVE`

特征：

- 与主人对话进行中
- 发言短冷却

### 15.3 `WORKING`

特征：

- 正在执行任务
- 能量持续消耗

### 15.4 `LOW_ENERGY_RECOVERY`

特征：

- 能量过低
- 工作任务被抑制
- 倾向休息 / 陪伴 / 少说话

### 15.5 `SLEEPING`

特征：

- 睡眠边沿和睡眠风格输出生效

---

## 16. 建议新增的规则字段

为了把上面规则落地，建议再补这些字段。

- `daily_favorability_cap = 30`
- `daily_favorability_chat_cap = 10`
- `daily_favorability_action_cap = 10`
- `daily_favorability_care_cap = 10`
- `energy_work_block_threshold = 20`
- `energy_low_threshold = 50`
- `chat_session_min_reply_interval_ms = 500`
- `normal_speak_interval_factor = 1.0`

这样后面都可以做成配置，不用写死在代码里。

---

## 17. 最终结论

你这次补的三条约束都应该成为系统硬规则：

1. `每日好感最多加 30`
2. `任务会消耗能量，低于阈值后不再执行工作任务`
3. `非聊天状态的发言冷却跟截图间隔绑定，聊天状态则放宽`

这三条加进去之后，系统会立刻更像“长期在线的陪伴体”，而不是：

- 无限刷好感
- 无限工作
- 无限说话

对 `MaidSoulCore` 来说，这三条约束比继续加更多 fancy 能力更重要，因为它们决定了整个系统会不会失真。
