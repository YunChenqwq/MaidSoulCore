# MaidSoulCore 拟人化改造落地说明 v2

## 本轮已落实

### 1. 主动事件优先级补强
- 已把天气变化、时间段变化、任务切换、日程切换、坐下/起立、跟随/留守、睡觉/醒来等状态边缘接入主动裁决链。
- 主人攻击女仆会被单独识别为 `maid.attacked.by_owner`，和普通受击拆开处理。
- 动作执行结果会通过 `MaidSoulActionFeedbackService` 反向广播给主人，例如：
  - 跟随成功
  - 留守成功
  - 坐下/起立成功
  - 日程切换成功
  - 任务切换成功
  - 缺少条件
  - 目标丢失
  - 目标不允许攻击

### 2. 视觉主动性改造
- 不再固定单一采样间隔。
- 现在分为两档：
  - 聊天活跃窗口：主人最近和女仆聊过天时，高频采样
  - 空闲模式：平时自动降频
- 新增“环境类主动回复冷却”，避免看到路人玩家、动物、普通场景时疯狂开口。
- 视觉摘要已加入分类：
  - 陌生玩家
  - 其他女仆
  - 可爱动物
  - 高风险怪物
  - 普通敌对生物
  - 其他实体
- 会跳过“自己这只女仆”，避免把自己当成旁边的女仆反复提及。

### 3. 提示词拟人化强化
- 人设已从偏冷硬的“执行器女仆”改成更柔和的陪伴女仆。
- 要求模型：
  - 更像真人短句聊天
  - 允许少量颜文字
  - 危险时认真
  - 平静时柔和
  - 看到陌生玩家时轻微警惕 + 好奇
  - 看到可爱动物时可以表达喜欢
  - 命令成功时给出“我接手了”的执行反馈感
  - 命令失败时解释原因，不要只冷冰冰说失败

### 4. 调试与面板
- Forge 设置页已扩展以下选项：
  - 聊天活跃窗口秒数
  - 活跃视角采样间隔
  - 空闲视角采样间隔
  - 环境回复冷却
  - 普通怪提醒阈值
  - 高风险生物列表
- 现有聊天栏 trace 回显链继续保留，方便联调。

## 关键代码落点
- `src/main/java/com/maidsoulcore/forge/service/MaidSoulCompanionService.java`
- `src/main/java/com/maidsoulcore/forge/service/MaidSoulVisionService.java`
- `src/main/java/com/maidsoulcore/forge/service/MaidSoulActionFeedbackService.java`
- `src/main/java/com/maidsoulcore/forge/service/MaidSoulEntityAwarenessService.java`
- `src/main/java/com/maidsoulcore/forge/service/MaidSoulPromptService.java`
- `src/main/java/com/maidsoulcore/forge/state/MaidSoulAgentState.java`
- `src/main/java/com/maidsoulcore/forge/config/MaidSoulCommonConfig.java`
- `src/main/java/com/maidsoulcore/forge/config/MaidSoulConfigScreen.java`
- `src/main/java/com/maidsoulcore/forge/MaidSoulForgeEvents.java`

## 建议测试顺序
1. 先让主人和女仆正常聊天，确认 30 秒内视觉更活跃。
2. 看向其他玩家、其他女仆、动物、苦力怕，观察是否区分语气。
3. 命令“坐下 / 起立 / 跟着我 / 你留在这里 / 帮我打它”，观察是否会先执行再反馈。
4. 主人打一下女仆、喂食女仆、等到黄昏/下雨，观察是否有更自然的主动反馈。
5. 在 Mod 设置页调整视觉间隔和环境冷却，确认节流效果。

## 还可继续补的下一层
- 把事件类型继续细分到“任务开始 / 任务完成 / 任务失败原因”三级。
- 让视觉摘要进一步结合主人最近聊天主题，决定该不该主动续话。
- 再加一层情绪/精力变化曲线，让同一事件在不同状态下说法不同。
