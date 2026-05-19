package com.maidsoul.brain;

import com.maidsoul.brain.reasoning.ReplyQualityGuard;

/**
 * 回复质量守门器测试。
 *
 * <p>这里测试的是本地确定性规则，不依赖模型是否“听话”。
 * 目标是防止口癖复读、内部文本和动作描写直接发给玩家。</p>
 */
public final class ReplyQualityGuardSmokeTest {
    private ReplyQualityGuardSmokeTest() {
    }

    public static void main(String[] args) {
        ReplyQualityGuard guard = new ReplyQualityGuard();
        assertBad(guard, "啧，知道了。啧，别说了。", "同一回复内重复口癖应拦截。");
        assertBad(guard, "哼，行吧。", "- [id=1] 01:00:00 酒狐: 哼，知道啦。", "最近已经出现过口癖应拦截。");
        assertBad(guard, "（低头整理裙摆）知道了。", "动作描写开头应拦截。");
        assertBad(guard, "{\"action\":\"reply\"}", "内部 JSON 应拦截。");
        assertBad(guard, "我听见了。（小声嘀咕）才不是担心你。", "中间夹动作描写应拦截。");
        assertBad(guard, "那你想让我怎么办嘛，我又不知道。", "模式=关系修复", "关系修复时把问题推回用户应拦截。");
        assertBad(guard, "下次说我笨之前，先想想你自己之前有多烦。", "模式=冲突冷却", "冲突冷却时翻旧账应拦截。");
        assertOk(guard, "好啦，我刚才确实没接好。你别急，我这次认真听。", "正常修复回复应通过。");
        System.out.println("REPLY_QUALITY_GUARD_SMOKE_OK");
    }

    private static void assertBad(ReplyQualityGuard guard, String reply, String reason) {
        assertBad(guard, reply, "", reason);
    }

    private static void assertBad(ReplyQualityGuard guard, String reply, String context, String reason) {
        ReplyQualityGuard.QualityResult result = guard.inspect(reply, context);
        if (result.ok()) {
            throw new IllegalStateException(reason + " reply=" + reply);
        }
    }

    private static void assertOk(ReplyQualityGuard guard, String reply, String reason) {
        ReplyQualityGuard.QualityResult result = guard.inspect(reply, "");
        if (!result.ok()) {
            throw new IllegalStateException(reason + " failReason=" + result.reason());
        }
    }
}
