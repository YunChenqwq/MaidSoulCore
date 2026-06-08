# MaidSoulCore 情绪 OU/HMM 与事件记忆融合施工文档

版本：v1  
日期：2026-06-08  
目标：把当前“数值条式情绪系统”重构为“结构化事件驱动的关系动力学 + 证据型记忆系统”。

---

## 1. 当前问题

当前系统已经接通了真实 planner 语义事件链路：

```text
玩家自然语言
-> PlannerRunner
-> affect_event / confidence / evidence
-> MaidSoulRuntime
-> AffectEngine
-> ReplyGenerator
```

这条主链路是对的，但底层状态机还不成熟。

### 1.1 情绪增长太像数值条

当前 `AffectEngine` 主要做：

```text
事件进来
-> intimacy/trust/valence 等字段直接加减
-> inferStage() 用阈值判断关系阶段
```

问题：

- 正向事件连续出现时，`intimacy / trust / valence / longing` 会一起快速打满。
- `relationshipStage` 是硬阈值推断，不是阶段概率。
- `trust` 这种长期变量不应该几轮对话就满值。
- 短期心情、长期关系、主动意愿没有分清速度。

### 1.2 没有真正的 OU 回落

当前 `decayByElapsedTime()` 是确定性 approach：

```text
value = value + (baseline - value) * ratio
```

它能回落，但不是原本想要的 OU 过程：

```text
X(t+dt) = X(t) + theta * (baseline - X(t)) * dt + sigma * sqrt(dt) * noise
```

OU 的意义：

- 情绪会自然向基线回弹。
- 亲密和冲突有不同回落速度。
- 同样事件在不同阶段会有不同余波。
- 可以让状态更像“关系动态”，不是纯数值条。

### 1.3 没有 HMM 阶段转移

当前阶段推断类似：

```text
if conflict > 0.58 -> cold
if intimacy > 0.82 && trust > 0.58 -> passionate
...
```

应改为：

```text
stageBelief = {
  courting: 0.10,
  sweet: 0.45,
  passionate: 0.25,
  stable: 0.15,
  cold: 0.02,
  repairing: 0.03
}

事件进入后：
-> 计算 transition probabilities
-> 更新 belief
-> 选择最高概率阶段
```

HMM 的意义：

- 关系阶段不是一个 if 结果，而是一组概率分布。
- 冲突、道歉、亲密事件会改变概率，而不是直接跳档。
- 可以表达“表面甜蜜但还有修复风险”这种状态。

### 1.4 记忆写入还没有和情绪事件统一

当前记忆写回已经有雏形，但还没有完全做到：

```text
同一个结构化事件
同时驱动：
- 情绪层
- 记忆层
- 图谱层
- 回复上下文
```

正确方向是让 planner 输出一个统一事件包，情绪和记忆都吃它，而不是各自从文本里猜。

---

## 2. 目标架构

最终链路：

```text
玩家输入 / 世界事件 / 工具观察
        |
        v
Planner 语义理解
        |
        v
StructuredEvent 统一事件包
        |
        +--> AffectEngine: OU + HMM 更新关系和情绪
        |
        +--> MemoryWritebackService: 分类写入证据型记忆
        |
        +--> MemoryGraph: subject-object-event 图谱边
        |
        +--> ReplyContext: 当前事件、关系状态、相关记忆
        |
        v
ReplyGenerator 生成女仆回复
```

核心原则：

- 自然语言语义只由 planner / tool loop 理解。
- `AffectEngine` 不做关键词判断。
- `MemoryWritebackService` 不靠原文硬编码分类。
- 关系状态和记忆写入共用同一个结构化事件。

---

## 3. 统一结构化事件包

### 3.1 新建类

建议新增：

```text
src/main/java/com/yunchen/maidsoulcore/core/event/StructuredEvent.java
src/main/java/com/yunchen/maidsoulcore/core/event/StructuredEventType.java
src/main/java/com/yunchen/maidsoulcore/core/event/StructuredEventScope.java
src/main/java/com/yunchen/maidsoulcore/core/event/EventImportancePolicy.java
```

### 3.2 StructuredEvent 字段

```java
public final class StructuredEvent {
    public String id;
    public String type;
    public String scope;

    public String subject;
    public String object;

    public String summary;
    public String evidence;
    public String sourceText;

    public double confidence;
    public double importance;

    public String memoryCategory;
    public boolean shouldWriteMemory;
    public boolean shouldUpdateAffect;

    public long happenedAtEpochMillis;
}
```

### 3.3 事件类型

第一版事件类型：

```text
initiate
affection
care
apology
repair_check
fight
reject
promise
memory_anchor
fatigue
boundary_request
danger
world_change
maid_interact
owner_attack
maid_death
neutral_world
```

说明：

- `repair_check`：主人确认女仆是否委屈、是否还在生气。
- `promise`：承诺、约定，例如“我去新世界也带上你”。
- `memory_anchor`：主人明确要求记住的重要事实。
- `fatigue`：主人累了，适合低打扰陪伴。
- `boundary_request`：主人要求安静、空间、暂停。

### 3.4 Planner 输出 JSON 扩展

当前 planner 输出：

```json
{
  "action": "reply",
  "affect_event": "affection",
  "affect_confidence": 0.92,
  "affect_evidence": "主人表达想念"
}
```

应升级为：

```json
{
  "action": "reply",
  "target_message_id": "",
  "wait_seconds": 0,
  "memory_query": "",
  "reference_info": "",
  "reason": "",
  "event": {
    "type": "affection",
    "scope": "owner_to_maid",
    "subject": "主人",
    "object": "灵汐",
    "summary": "主人表达下雨时想念灵汐",
    "evidence": "今天下雨了，我突然有点想你",
    "confidence": 0.92,
    "importance": 0.74,
    "memory_category": "relation_event",
    "should_write_memory": true,
    "should_update_affect": true
  }
}
```

为了兼容当前代码施工，可分两步：

1. 保留 `affect_event / affect_confidence / affect_evidence`。
2. 新增 `event` 字段。
3. runtime 优先读 `event`，没有时回退旧字段。

---

## 4. OU 关系与情绪慢变量

### 4.1 新建 OUProcess

建议新增：

```text
src/main/java/com/yunchen/maidsoulcore/core/affect/OuProcess.java
```

字段：

```java
public final class OuProcess {
    public double value;
    public double baseline;
    public double reversionSpeed;
    public double volatility;

    public void bump(double amount);
    public void step(double hours, Random random);
    public void reset(double value);
}
```

算法：

```java
double drift = reversionSpeed * (baseline - value) * hours;
double noise = volatility * Math.sqrt(hours) * random.nextGaussian();
value = clamp01(value + drift + noise);
```

注意：

- 测试环境可以把 volatility 设为 0，方便复现。
- 正式运行可开很小噪声，例如 0.005-0.02。
- `hours` 需要从 `updatedAtEpochMillis` 计算。

### 4.2 哪些字段走 OU

第一版建议：

```text
intimacy: OU, baseline 0.50, theta 0.05, sigma 0.01
conflict: OU, baseline 0.10, theta 0.08, sigma 0.01
valence:  OU, baseline 随 stage, theta 0.15, sigma 0.015
arousal:  OU, baseline 随 stage, theta 0.20, sigma 0.015
dominance: OU, baseline 随 stage, theta 0.12, sigma 0.01
```

不建议第一版把所有字段都做 OU。

### 4.3 trust / attachment 的速度

`trust` 和 `attachment` 是长期慢变量，不应该被短期对话快速打满。

建议：

```text
trust:
  affection +0.006
  care +0.008
  apology +0.012
  fight -0.035
  owner_attack -0.120

attachment:
  affection +0.006
  promise +0.012
  memory_anchor +0.010
  long_silence +0.008
  reject -0.020
```

也就是说：

- `intimacy` 可以相对快。
- `trust` 必须慢。
- `attachment` 也慢，但持续互动会逐渐加深。

---

## 5. HMM 关系阶段转移

### 5.1 新建 RelationshipHmm

建议新增：

```text
src/main/java/com/yunchen/maidsoulcore/core/affect/RelationshipHmm.java
src/main/java/com/yunchen/maidsoulcore/core/affect/RelationshipDynamicsConfig.java
```

`RelationshipHmm` 负责：

```text
stageBelief[6]
transitionMatrix
observe(event, intimacy, conflict)
computeTransitionProbabilities()
selectStage()
```

### 5.2 阶段枚举

沿用当前：

```text
courting    初识试探
sweet       甜蜜亲近
passionate  热烈依恋
stable      稳定陪伴
cold        冷淡疏离
repairing   修复中
```

### 5.3 初始 belief

第一版：

```text
courting:    0.70
sweet:       0.10
passionate:  0.00
stable:      0.20
cold:        0.00
repairing:   0.00
```

如果默认人设希望“已经是绑定女仆”，可改为：

```text
courting:    0.25
sweet:       0.25
passionate:  0.00
stable:      0.50
cold:        0.00
repairing:   0.00
```

### 5.4 转移矩阵

第一版参考：

```text
COURTING:
  courting 0.60
  sweet 0.30
  cold 0.10

SWEET:
  courting 0.05
  sweet 0.50
  passionate 0.35
  cold 0.10

PASSIONATE:
  sweet 0.10
  passionate 0.40
  stable 0.40
  cold 0.10

STABLE:
  sweet 0.05
  passionate 0.05
  stable 0.70
  cold 0.15
  repairing 0.05

COLD:
  stable 0.10
  cold 0.60
  repairing 0.30

REPAIRING:
  sweet 0.15
  stable 0.35
  cold 0.20
  repairing 0.30
```

### 5.5 事件对 OU 的 bump

第一版建议：

```text
initiate:        intimacy +0.050, conflict -0.010
affection:       intimacy +0.070, conflict -0.030
care:            intimacy +0.045, conflict -0.025
apology:         intimacy +0.035, conflict -0.100
repair_check:    intimacy +0.025, conflict -0.060
promise:         intimacy +0.055, conflict -0.020
memory_anchor:   intimacy +0.050, conflict -0.010
fatigue:         intimacy +0.015, conflict -0.005
boundary_request:intimacy -0.010, conflict +0.015
fight:           intimacy -0.120, conflict +0.180
reject:          intimacy -0.100, conflict +0.100
danger:          intimacy +0.015, conflict +0.060
owner_attack:    intimacy -0.250, conflict +0.350
maid_death:      intimacy -0.080, conflict +0.180
```

相比当前系统，正向事件调低，避免 7 轮满值。

### 5.6 事件对阶段概率的调制

伪代码：

```java
Map<Stage, Double> probs = baseTransition[currentStage];

if (intimacy > 0.70) {
    probs[SWEET] *= 1.6;
    probs[PASSIONATE] *= 1.6;
    probs[COLD] *= 0.4;
}

if (intimacy < 0.30) {
    probs[COLD] *= 2.0;
    probs[SWEET] *= 0.4;
    probs[PASSIONATE] *= 0.2;
}

if (conflict > 0.50) {
    probs[COLD] *= 3.0;
    probs[REPAIRING] *= 2.0;
    probs[SWEET] *= 0.3;
    probs[PASSIONATE] *= 0.2;
}

switch (event) {
    case FIGHT:
        probs[COLD] *= 5.0;
        probs[currentStage] *= 0.5;
    case APOLOGY, REPAIR_CHECK:
        probs[REPAIRING] *= 2.5;
        probs[COLD] *= 0.4;
    case AFFECTION:
        if (intimacy > 0.62) {
            probs[PASSIONATE] *= 2.0;
            probs[SWEET] *= 1.5;
        }
    case PROMISE, MEMORY_ANCHOR:
        probs[STABLE] *= 1.4;
        probs[SWEET] *= 1.3;
    case BOUNDARY_REQUEST:
        probs[STABLE] *= 1.2;
        probs[PASSIONATE] *= 0.8;
}
normalize(probs);
```

### 5.7 阶段选择策略

第一版用 deterministic argmax：

```java
stage = maxProbabilityStage(stageBelief);
```

后续可加随机采样，但调试阶段不要引入太多不可复现性。

---

## 6. AffectProfile 新字段设计

可以不考虑旧兼容，直接重置新字段。

### 6.1 推荐字段

```java
public final class AffectProfile {
    public String relationshipStage = "stable";
    public double[] stageBelief = new double[]{0.25, 0.25, 0.0, 0.50, 0.0, 0.0};

    public double intimacy = 0.50;
    public double conflict = 0.10;
    public double trust = 0.50;
    public double attachment = 0.58;

    public double valence = 0.30;
    public double arousal = 0.40;
    public double dominance = 0.45;
    public String emotion = "neutral";

    public double hurtDebt = 0.0;
    public double repairDebt = 0.0;
    public double memoryTriggerScore = 0.0;
    public double longing = 0.55;
    public double proactiveBias = 0.65;

    public String lastSemanticEvent = "neutral_world";
    public String lastEventEvidence = "";
    public long lastEventAtEpochMillis = 0L;
    public long updatedAtEpochMillis = 0L;

    public int positiveEventStreak = 0;
    public int repairEventStreak = 0;
    public int conflictEventStreak = 0;
}
```

### 6.2 为什么保留 streak

HMM 能表达阶段概率，但业务上还需要防止“单次表白直接热烈依恋”。

例如：

```text
passionate 需要：
- stageBelief[passionate] 最高
- intimacy > 0.72
- positiveEventStreak >= 3
```

这样能避免测试里第 4 轮就进入热烈依恋。

---

## 7. VAD 情绪层

### 7.1 stage baseline

```text
COURTING:
  valence 0.20, arousal 0.55, dominance 0.38
SWEET:
  valence 0.62, arousal 0.50, dominance 0.45
PASSIONATE:
  valence 0.78, arousal 0.65, dominance 0.50
STABLE:
  valence 0.50, arousal 0.35, dominance 0.52
COLD:
  valence -0.35, arousal 0.55, dominance 0.34
REPAIRING:
  valence 0.05, arousal 0.48, dominance 0.40
```

### 7.2 event VAD bump

```text
affection:     valence +0.080, arousal +0.025, dominance +0.010
care:          valence +0.060, arousal -0.020, dominance +0.015
apology:       valence +0.050, arousal -0.060, dominance +0.020
repair_check:  valence +0.035, arousal -0.035, dominance +0.015
promise:       valence +0.060, arousal +0.015, dominance +0.015
memory_anchor: valence +0.050, arousal +0.020, dominance +0.010
fatigue:       valence +0.010, arousal -0.070, dominance +0.005
fight:         valence -0.160, arousal +0.150, dominance -0.050
reject:        valence -0.120, arousal +0.080, dominance -0.070
danger:        valence -0.100, arousal +0.180, dominance -0.080
```

### 7.3 VAD 到 emotion

沿用当前 `EmotionLabel.fromVad()`，但建议检查边界：

```text
joy
trust
excitement
neutral
sadness
fear
anger
anxiety
relief
```

第一版无需复杂化。

---

## 8. Longing 与主动好奇

### 8.1 Longing 不应该等于亲密满值

当前 `longing` 很容易被 intimacy 和 memory trigger 推满。

建议：

```text
longing =
  0.35
  + attachment * 0.24
  + intimacy * 0.14
  + memoryTriggerScore * 0.22
  + repairDebt * 0.08
  - boundaryCooldown * 0.12
```

限制：

```text
如果主人刚表达 fatigue / boundary_request：
  proactiveBias 降低
  但 attachment 不降低
```

这能让女仆“很喜欢主人，但知道现在不要吵”。

### 8.2 主动好奇

建议拆成：

```text
proactiveBias: 长期主动倾向
proactiveChance: 本轮是否主动的概率
```

计算：

```text
proactiveBias =
  0.30
  + longing * 0.35
  + attachment * 0.18
  + memoryTriggerScore * 0.15
  + repairDebt * 0.10
  - recentBoundaryNeed * 0.25
```

然后 timing gate / planner 再结合：

```text
最近是否刚回复
玩家是否累了
是否有风险事件
是否有视角摘要话题
```

---

## 9. 记忆框架融合设计

### 9.1 记忆分类

沿用并扩展：

```text
short_context       短期上下文
owner_profile       主人画像
relation_event      关系事件
maid_self           女仆自我记忆
world_fact          世界事实
repair_record       修复/冲突记录
promise             承诺/约定
memory_anchor       主人要求记住的重要锚点
error_mark          错误/可疑记忆标记
```

### 9.2 事件到记忆分类映射

```text
affection:
  relation_event

care:
  relation_event / owner_profile

apology:
  repair_record

repair_check:
  repair_record

fight/reject/owner_attack:
  repair_record

promise:
  promise + relation_event

memory_anchor:
  memory_anchor + maid_self

fatigue:
  owner_profile

boundary_request:
  owner_profile + short_context

danger/world_change:
  world_fact
```

### 9.3 记忆条目结构

建议新增：

```text
src/main/java/com/yunchen/maidsoulcore/core/memory/EventMemoryRecord.java
```

字段：

```java
public final class EventMemoryRecord {
    public String id;
    public String category;
    public String subject;
    public String object;
    public String eventType;
    public String summary;
    public String evidence;

    public double confidence;
    public double importance;
    public double salience;
    public double decayRate;

    public boolean pinned;
    public boolean contradicted;
    public boolean errorMarked;

    public String sourceMessageId;
    public long createdAtEpochMillis;
    public long updatedAtEpochMillis;
    public int mergeCount;
}
```

### 9.4 写入策略

不是每句话都写。

写入条件：

```text
confidence >= 0.70
importance >= 0.50
event.shouldWriteMemory == true
```

强制写入：

```text
promise
memory_anchor
owner_attack
maid_death
world_change
高置信 apology/fight
```

低优先写入：

```text
普通 initiate
普通 care
重复 affection
```

### 9.5 证据绑定

每条记忆必须带：

```text
evidence
sourceMessageId
subject
object
confidence
```

这样后续才能做：

- 谁说的。
- 什么时候说的。
- 为什么相信。
- 是否和新证据冲突。

### 9.6 记忆维护循环

建议周期：

```text
每 20 次写入后轻维护
每次启动后做一次轻维护
每 24 小时做一次重维护
```

维护任务：

```text
去重:
  summary 相似 + subject/object/category 相同

合并:
  同类 promise / relation_event 合并 mergeCount

冲突检测:
  同一 subject/object 下出现互斥事实

旧记忆降权:
  salience = importance * recencyWeight * confidence

重要记忆固化:
  promise / memory_anchor / 高重要 relation_event -> pinned

错误记忆标记:
  contradicted 高且新证据置信更高 -> errorMarked
```

### 9.7 记忆检索

回复上下文应检索：

```text
1. 与最新消息语义相关的记忆
2. 与当前事件类型相关的记忆
3. 与当前关系阶段相关的修复/承诺记忆
4. pinned 重要记忆
```

输出给 replyer 时要分组：

```text
相关关系记忆:
- ...

主人画像:
- ...

未完成承诺:
- ...

修复记录:
- ...
```

不要把所有记忆混成一坨文本。

---

## 10. 记忆图谱设计

### 10.1 节点类型

```text
person: 主人、女仆、其他玩家
maid: 绑定的具体女仆
event: 结构化事件
memory: 记忆条目
world: 世界/维度/地点
item: 灵魂核心等关键物品
promise: 承诺
```

### 10.2 边类型

```text
said
cares_for
apologized_to
promised
remembered_as
bound_to
moved_to_world
occurred_in
evidences
contradicts
```

### 10.3 事件图谱写入

例如：

```text
主人: 如果我去新的世界，也会把你一起带上。
```

生成：

```text
person:主人 --promised--> maid:灵汐
event:promise --evidences--> memory:去新世界也带上灵汐
memory --occurred_in--> world:当前世界
```

这样未来 GUI 可以直接可视化：

- 主人和女仆的关系边。
- 承诺链。
- 修复记录。
- 世界迁移事件。

---

## 11. 运行时改造顺序

### Phase 1：数据结构

新增：

```text
StructuredEvent
StructuredEventType
StructuredEventScope
OuProcess
RelationshipHmm
RelationshipDynamicsConfig
EventMemoryRecord
```

修改：

```text
PlanDecision
AffectProfile
```

验收：

- `compileJava` 通过。
- 默认新建 `affect.json` 不闪退。
- `AffectProfile.brief()` 能输出 stage belief。

### Phase 2：Planner 事件包

修改：

```text
planner.md
PlannerRunner
MaidSoulRuntime
```

目标：

- planner 输出 `event` 对象。
- runtime 优先消费 `event`。
- 旧 `affect_event` 暂时保留作 fallback。

验收：

- trace 中能看到：

```text
structured.event type=affection confidence=0.92 category=relation_event
affect.event affection ...
memory.write relation_event ...
```

### Phase 3：AffectEngine 替换为 OU + HMM

修改：

```text
AffectEngine.apply()
AffectEngine.decayByElapsedTime()
AffectEngine.finish()
```

删除或废弃：

```text
inferStage() 阈值主导逻辑
简单 approach 回落主逻辑
```

验收：

- 20 轮甜蜜对话不会过早 trust=1。
- 关系阶段不会第 4 轮直接满热恋。
- 长时间 step_time 后 intimacy/conflict 会回 baseline。

### Phase 4：事件驱动记忆写入

修改：

```text
MemoryWritebackService
LifeMemoryStore
MemoryMaintenanceService
```

目标：

- 用 StructuredEvent 写记忆。
- 记忆有 evidence 和 subject/object。
- promise 和 memory_anchor 固化。

验收：

- 重要承诺进入 `promise`。
- 道歉进入 `repair_record`。
- 主人疲惫进入 `owner_profile` 或短期上下文。

### Phase 5：记忆维护循环

实现：

```text
去重
合并
降权
固化
冲突标记
错误标记
```

验收：

- 重复“我最喜欢你了”不会写 20 条。
- 新证据能合并旧关系事件。
- 冲突事实能被标记，而不是直接覆盖。

### Phase 6：真实 trace 与调参

测试集：

```text
1. 甜蜜推进 20 轮
2. 冲突 -> 道歉 -> 修复 15 轮
3. 主人疲惫 -> 安静陪伴 8 轮
4. 世界迁移/灵魂核心绑定事件
5. 长时间沉默后主动追问
```

验收指标：

```text
甜蜜推进:
  intimacy 可升高
  trust 慢升
  stage 可到 sweet，后期才 passionate

冲突修复:
  fight 后 conflict 上升
  apology 后 repairDebt 下降
  repairing 阶段能出现

疲惫陪伴:
  proactiveBias 不应强行刷屏
  attachment 不下降

记忆:
  promise / memory_anchor 被写入
  重复 affection 被合并
```

---

## 12. 数值调参目标

### 12.1 正向 20 轮预期

理想结果：

```text
Turn 1:
  stage stable/courting
  intimacy 0.52-0.58
  trust 0.50-0.55

Turn 5:
  stage sweet
  intimacy 0.65-0.75
  trust 0.55-0.62

Turn 10:
  stage sweet/passsionate 边缘
  intimacy 0.75-0.88
  trust 0.62-0.72

Turn 20:
  stage passionate 或 stable
  intimacy 0.86-0.96
  trust 0.72-0.84
```

不理想：

```text
Turn 7:
  intimacy=1.00
  trust=1.00
```

### 12.2 冲突修复预期

```text
fight:
  conflict +0.15 左右
  valence 明显下降
  stage belief 向 cold/repairing 偏移

apology:
  conflict 明显下降
  repairDebt 下降
  trust 小幅恢复，不瞬间恢复

repair_check:
  repairing -> stable/sweet 的概率增加
```

---

## 13. Prompt 改造要点

### 13.1 planner

planner 必须输出结构事件，但不能为了写事件而强行分类。

关键提示：

```text
如果最新消息只是普通接话，event.type=neutral_world，confidence=0。
如果事件有长期意义，填写 memory_category 和 importance。
不要把事件理解写成最终台词。
```

### 13.2 replyer

replyer 需要看到：

```text
当前事件:
  type/summary/evidence

关系状态:
  stage + belief
  intimacy/conflict/trust/attachment

情绪状态:
  VAD + emotion

记忆:
  relation_event
  promise
  repair_record
  owner_profile
```

同时要更严：

```text
不要编造未发生的饭菜、房间、门锁、动作、现实承诺。
可以表达情绪，但不能创造未给出的事实。
```

---

## 14. 风险点

### 14.1 模型事件判断漂移

planner 可能把 `care` 判成 `affection`，或把普通感谢判成强关系事件。

缓解：

- event confidence 门槛。
- event importance 门槛。
- 对高影响事件要求更高置信度。

建议门槛：

```text
affect update: confidence >= 0.65
memory write: confidence >= 0.70 && importance >= 0.50
high risk memory: confidence >= 0.80
```

### 14.2 关系升太快

缓解：

- 降低正向 bump。
- trust 变成慢变量。
- passionate 需要 streak 或 belief 稳定。

### 14.3 记忆污染

缓解：

- 每条记忆必须有 evidence。
- 没证据不固化。
- 可疑记忆标记 errorMarked。

### 14.4 主动性刷屏

缓解：

- fatigue/boundary_request 降低 proactiveChance。
- 主动好奇不只看 longing。
- timing gate 需要读取最近回复时间。

---

## 15. 最终验收标准

### 15.1 技术验收

```text
.\gradlew.bat compileJava
.\gradlew.bat build
```

必须通过。

### 15.2 trace 验收

真实 trace 必须能看到：

```text
structured.event ...
affect.event ...
hmm.stage_belief ...
ou.step ...
memory.write ...
```

### 15.3 行为验收

女仆表现应满足：

- 能识别主人道歉，并进入修复语气。
- 能识别主人疲惫，并降低打扰。
- 能记住承诺和重要关系锚点。
- 亲密关系会成长，但不会几轮就全满。
- 冲突后不是只扣一个 mood，而是影响 conflict / repairDebt / stage belief。

---

## 16. 推荐施工顺序摘要

```text
1. StructuredEvent 统一事件包
2. AffectProfile 新字段
3. OUProcess
4. RelationshipHmm
5. AffectEngine 替换为 OU + HMM
6. Planner prompt 输出 event 对象
7. Runtime 消费 event
8. MemoryWritebackService 改为事件驱动
9. MemoryMaintenanceService 完善维护循环
10. 跑真实 trace 调参
```

这条路线完成后，MaidSoulCore 的核心会从：

```text
prompt + 简单状态数值
```

升级成：

```text
结构化事件 + 关系动力学 + 证据型记忆 + 可解释 trace
```

这才是“灵魂核心”应该有的底层。
