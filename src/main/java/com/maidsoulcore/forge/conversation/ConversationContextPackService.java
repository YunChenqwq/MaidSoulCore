package com.maidsoulcore.forge.conversation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.memory.MaidSoulLifeMemoryService;
import com.maidsoulcore.forge.service.MaidSoulCognitionService;
import com.maidsoulcore.forge.service.MaidSoulEmotionService;
import com.maidsoulcore.forge.service.MaidSoulUnderstandingService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.forge.state.MaidSoulStateSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文打包器。
 * <p>
 * 这里做上下文分层：真正的对话历史走 chronological messages；
 * 视角、情绪、认知、理解都作为 reference 输入。reference 可以帮助理解，
 * 但不能强迫女仆每轮都换成视觉播报或复读旧回复。
 */
public final class ConversationContextPackService {
    private ConversationContextPackService() {
    }

    public static ContextPack build(EntityMaid maid,
                                    List<String> fallbackHistory,
                                    ConversationFlowService.TurnFrame turnFrame,
                                    String latestOwnerMessage) {
        return new ContextPack(
                dialogueHistory(maid, fallbackHistory),
                referenceBlock(maid, turnFrame, latestOwnerMessage)
        );
    }

    private static String dialogueHistory(EntityMaid maid, List<String> fallbackHistory) {
        List<String> local = maid == null
                ? List.of()
                : ConversationMemoryService.recentLines(maid, MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.get());
        List<String> source = local.isEmpty() ? fallbackHistory : local;
        if (source == null || source.isEmpty()) {
            return "(无)";
        }
        ArrayList<String> selected = new ArrayList<>();
        for (String line : source) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String cleaned = line.trim();
            selected.add(shortText(cleaned, 120));
        }
        if (selected.isEmpty()) {
            return "(无)";
        }
        int max = Math.max(1, MaidSoulCommonConfig.CONVERSATION_MEMORY_PROMPT_LINES.get());
        int from = Math.max(0, selected.size() - max);
        return String.join("\n", selected.subList(from, selected.size()));
    }

    private static String referenceBlock(EntityMaid maid, ConversationFlowService.TurnFrame turnFrame, String latestOwnerMessage) {
        if (maid == null) {
            return "none";
        }
        MaidSoulStateSnapshot snapshot = MaidSoulStateRegistry.snapshot(maid);
        String ownerView = ownerView(snapshot);
        // 这个 reference block 是“脑内背景”，不是用户刚刚说的话。
        // 主回复必须优先回答最后一条 chronological user message；
        // 视角、情绪、生命记忆只能解释语气和上下文，不能抢走话题。
        return """
                conversation_pacing_reference:
                %s

                owner_view_reference:
                %s

                owner_profile_reference:
                %s

                cognition_reference:
                %s

                emotion_relationship_reference:
                %s

                life_memory_reference:
                %s

                durable_understanding_reference:
                %s

                rule=Reference messages are background only. The visible chronological dialogue and the final owner message decide what to answer. Do not let any reference override the owner's latest words.
                """.formatted(
                ConversationFlowService.promptBlock(turnFrame),
                ownerView,
                ConversationPersonProfileService.ownerProfileReference(maid),
                MaidSoulCognitionService.promptBlock(maid),
                MaidSoulEmotionService.promptBlock(maid),
                MaidSoulLifeMemoryService.promptBlock(maid, latestOwnerMessage),
                MaidSoulUnderstandingService.promptBlock(maid)
        );
    }

    private static String ownerView(MaidSoulStateSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        String interpreted = snapshot.ownerViewInterpretedSummary();
        String raw = snapshot.ownerViewRawSummary();
        if ((interpreted == null || interpreted.isBlank()) && (raw == null || raw.isBlank())) {
            return "none";
        }
        return "interpreted=" + blankToDefault(interpreted, "none")
                + " | raw=" + blankToDefault(raw, "none");
    }

    private static String shortText(String text, int max) {
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private static String blankToDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    public record ContextPack(String dialogueHistory, String references) {
    }
}
