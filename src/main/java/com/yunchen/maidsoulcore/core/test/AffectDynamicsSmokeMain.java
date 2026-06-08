package com.yunchen.maidsoulcore.core.test;

import com.yunchen.maidsoulcore.core.affect.AffectEngine;
import com.yunchen.maidsoulcore.core.affect.AffectProfile;
import com.yunchen.maidsoulcore.core.event.EventImportancePolicy;
import com.yunchen.maidsoulcore.core.event.StructuredEvent;
import com.yunchen.maidsoulcore.core.event.StructuredEventScope;
import com.yunchen.maidsoulcore.core.event.StructuredEventType;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AffectDynamicsSmokeMain {
    private AffectDynamicsSmokeMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        StringBuilder report = new StringBuilder();
        AffectEngine engine = new AffectEngine();

        AffectProfile sweet = new AffectProfile();
        append(report, "# Affect Dynamics Smoke Report");
        append(report, "");
        append(report, "## Positive 20 Turns");
        append(report, "");
        List<StructuredEventType> positive = List.of(
                StructuredEventType.INITIATE,
                StructuredEventType.INITIATE,
                StructuredEventType.AFFECTION,
                StructuredEventType.MEMORY_ANCHOR,
                StructuredEventType.AFFECTION,
                StructuredEventType.APOLOGY,
                StructuredEventType.AFFECTION,
                StructuredEventType.CARE,
                StructuredEventType.CARE,
                StructuredEventType.APOLOGY,
                StructuredEventType.APOLOGY,
                StructuredEventType.REPAIR_CHECK,
                StructuredEventType.AFFECTION,
                StructuredEventType.AFFECTION,
                StructuredEventType.FATIGUE,
                StructuredEventType.CARE,
                StructuredEventType.PROMISE,
                StructuredEventType.MEMORY_ANCHOR,
                StructuredEventType.CARE,
                StructuredEventType.AFFECTION
        );
        for (int i = 0; i < positive.size(); i++) {
            engine.apply(sweet, event(positive.get(i), "positive turn " + (i + 1)));
            append(report, "turn=" + (i + 1)
                    + " event=" + positive.get(i).id()
                    + " stage=" + sweet.relationshipStage
                    + " intimacy=" + fmt(sweet.intimacy)
                    + " trust=" + fmt(sweet.trust)
                    + " conflict=" + fmt(sweet.conflict)
                    + " emotion=" + sweet.emotion
                    + " proactive=" + fmt(sweet.proactiveBias));
        }
        append(report, "");
        append(report, codeBlock(sweet.brief()));

        AffectProfile conflict = new AffectProfile();
        append(report, "");
        append(report, "## Conflict Repair");
        append(report, "");
        for (StructuredEventType type : List.of(
                StructuredEventType.AFFECTION,
                StructuredEventType.FIGHT,
                StructuredEventType.REJECT,
                StructuredEventType.APOLOGY,
                StructuredEventType.REPAIR_CHECK,
                StructuredEventType.CARE
        )) {
            engine.apply(conflict, event(type, "conflict repair " + type.id()));
            append(report, "event=" + type.id()
                    + " stage=" + conflict.relationshipStage
                    + " intimacy=" + fmt(conflict.intimacy)
                    + " trust=" + fmt(conflict.trust)
                    + " conflict=" + fmt(conflict.conflict)
                    + " hurtDebt=" + fmt(conflict.hurtDebt)
                    + " repairDebt=" + fmt(conflict.repairDebt)
                    + " emotion=" + conflict.emotion);
        }
        append(report, "");
        append(report, codeBlock(conflict.brief()));

        append(report, "");
        append(report, "## Time Decay 72h");
        sweet.updatedAtEpochMillis -= 72L * 3_600_000L;
        engine.apply(sweet, event(StructuredEventType.NEUTRAL_WORLD, "72h time pass"));
        append(report, codeBlock(sweet.brief()));

        Path output = Path.of("build", "reports", "maidsoulcore", "affect-dynamics-smoke.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("report=" + output.toAbsolutePath());
    }

    private static StructuredEvent event(StructuredEventType type, String evidence) {
        StructuredEvent event = new StructuredEvent();
        event.type = type.id();
        event.scope = StructuredEventScope.OWNER_TO_MAID.id();
        event.subject = "主人";
        event.object = "灵汐";
        event.summary = evidence;
        event.evidence = evidence;
        event.confidence = 0.92D;
        event.importance = EventImportancePolicy.defaultImportance(type, event.confidence);
        event.memoryCategory = EventImportancePolicy.defaultMemoryCategory(type);
        event.shouldUpdateAffect = type != StructuredEventType.NEUTRAL_WORLD;
        event.shouldWriteMemory = EventImportancePolicy.shouldWriteMemory(type, event.confidence, event.importance);
        event.normalize();
        return event;
    }

    private static void append(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private static String codeBlock(String text) {
        return "```text\n" + text + "\n```";
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
