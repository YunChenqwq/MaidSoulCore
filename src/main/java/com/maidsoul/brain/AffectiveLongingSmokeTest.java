package com.maidsoul.brain;

import com.maidsoul.brain.affect.AffectEngine;
import com.maidsoul.brain.affect.AffectEvent;
import com.maidsoul.brain.affect.AffectEventKind;
import com.maidsoul.brain.affect.AffectProfile;
import com.maidsoul.brain.affect.RelationshipStage;

/**
 * 新版情绪动力学验收。
 *
 * <p>这个测试不喂自然语言，也不做关键词判断；所有输入都是 planner/工具层已经识别好的
 * 结构化事件。它验证 VAD、亲密/冲突、阶段与 OU 回落是否能独立工作。</p>
 */
public final class AffectiveLongingSmokeTest {
    private AffectiveLongingSmokeTest() {
    }

    public static void main(String[] args) {
        AffectEngine engine = new AffectEngine();
        AffectProfile profile = new AffectProfile();
        profile.normalize();

        double baseIntimacy = profile.intimacy;
        engine.apply(profile, AffectEvent.of(AffectEventKind.OWNER_AFFECTION, 85, "test", "semantic-affection"));
        engine.apply(profile, AffectEvent.of(AffectEventKind.OWNER_AFFECTION, 85, "test", "semantic-affection"));
        if (profile.intimacy <= baseIntimacy || profile.valence <= 0.18D) {
            throw new IllegalStateException("亲近事件没有推动亲密/VAD: " + profile.brief());
        }

        double intimacyBeforeFight = profile.intimacy;
        engine.apply(profile, AffectEvent.of(AffectEventKind.OWNER_ATTACK, 90, "test", "semantic-fight"));
        if (profile.conflict < 0.18D || profile.intimacy >= intimacyBeforeFight || profile.stage() != RelationshipStage.COLD) {
            throw new IllegalStateException("冲突事件没有进入防备状态: " + profile.brief());
        }

        double conflictBeforeApology = profile.conflict;
        engine.apply(profile, AffectEvent.of(AffectEventKind.OWNER_APOLOGY, 90, "test", "semantic-apology"));
        if (profile.conflict >= conflictBeforeApology || profile.repairDebt >= 0.20D) {
            throw new IllegalStateException("道歉事件没有降低冲突/修复债务: " + profile.brief());
        }

        double arousalBeforeQuiet = profile.arousal;
        engine.recoverAfterQuietTime(profile);
        if (profile.arousal > arousalBeforeQuiet && profile.stage() != RelationshipStage.REPAIRING) {
            throw new IllegalStateException("安静回落没有按阶段基线收敛: " + profile.brief());
        }

        if (profile.replyStyleAdvice().isBlank() || !profile.brief().contains("VAD=")) {
            throw new IllegalStateException("新版状态没有生成可读表达建议: " + profile.brief());
        }
        System.out.println("AFFECTIVE_LONGING_SMOKE_OK");
    }
}
