package com.maidsoul.brain;

import com.maidsoul.brain.affect.AffectEngine;
import com.maidsoul.brain.affect.AffectEvent;
import com.maidsoul.brain.affect.AffectEventKind;
import com.maidsoul.brain.affect.AffectProfile;

/**
 * 好奇心数值回归测试。
 */
public final class AffectCuriositySmokeTest {
    private AffectCuriositySmokeTest() {
    }

    public static void main(String[] args) {
        AffectProfile profile = new AffectProfile();
        AffectEngine engine = new AffectEngine();
        profile.curiosity = 97;
        engine.observeOwnerMessage(profile, "任意文本不再由情绪层解析");
        engine.apply(profile, AffectEvent.of(AffectEventKind.OWNER_DISTRESS, 60, "test", "semantic-event"));
        if (profile.curiosity > 100 || profile.tension <= 10) {
            throw new IllegalStateException("结构化情绪事件没有正确更新主动欲望/紧张: " + profile.brief());
        }
        engine.observeAssistantReply(profile, "我会注意。");
        if (profile.curiosity >= 100) {
            throw new IllegalStateException("回复后好奇心没有释放: " + profile.brief());
        }
        System.out.println("AFFECT_CURIOSITY_SMOKE_OK");
    }
}
