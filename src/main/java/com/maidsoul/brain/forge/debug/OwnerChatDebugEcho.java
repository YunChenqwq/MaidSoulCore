package com.maidsoul.brain.forge.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.config.ForgeDebugOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class OwnerChatDebugEcho {
    private OwnerChatDebugEcho() {
    }

    public static void echo(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail) {
        if (debug == null || !debug.echoTraceToOwnerChat()) {
            return;
        }
        send(maid, debug, stage, detail, ChatFormatting.GRAY);
    }

    public static void echoImportant(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail) {
        send(maid, debug, stage, detail, ChatFormatting.AQUA);
    }

    private static void send(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail, ChatFormatting color) {
        if (maid == null || debug == null) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        int max = Math.max(40, debug.maxChatEchoChars());
        TraceLine line = TraceLine.of(stage, detail);
        String text = "[" + MaidSoulCoreForgeMod.MOD_ID + "] " + line.title() + " | " + abbreviate(line.detail(), max);
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static String abbreviate(String text, int max) {
        String cleaned = text == null ? "" : text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
    }

    private record TraceLine(String title, String detail) {
        private static TraceLine of(String stage, String detail) {
            String safeStage = stage == null ? "" : stage.trim();
            String safeDetail = detail == null ? "" : detail.trim();
            return switch (safeStage) {
                case "owner.input", "input.user" -> new TraceLine("输入/主人", compactUserInput(safeDetail));
                case "event", "input.world" -> worldEventLine(safeDetail);
                case "affect" -> new TraceLine("心理状态", safeDetail);
                case "affect.event" -> new TraceLine("情绪事件", compactAffectEvent(safeDetail));
                case "memory.event" -> new TraceLine("记忆写入", safeDetail);
                case "planner" -> new TraceLine("Planner", safeDetail);
                case "tool.scan_environment" -> new TraceLine("工具/环境扫描", compactToolResult(safeDetail));
                case "tool.observe_view" -> new TraceLine("工具/视角摘要", compactToolResult(safeDetail));
                case "tool.query_memory" -> new TraceLine("工具/记忆检索", compactToolResult(safeDetail));
                case "reply" -> new TraceLine("回复回显", safeDetail);
                default -> fallbackLine(safeStage, safeDetail);
            };
        }

        private static TraceLine fallbackLine(String stage, String detail) {
            if (stage.startsWith("llm.")) {
                return new TraceLine("模型/" + stage.substring(4), detail);
            }
            if (stage.startsWith("proactive.")) {
                return new TraceLine("主动节奏/" + stage.substring("proactive.".length()), detail);
            }
            if (stage.startsWith("runtime.")) {
                return new TraceLine("运行时/" + stage.substring("runtime.".length()), detail);
            }
            if (stage.startsWith("tool.")) {
                return new TraceLine("工具/" + stage.substring("tool.".length()), compactToolResult(detail));
            }
            return new TraceLine(stage.isBlank() ? "trace" : stage, detail);
        }

        private static TraceLine worldEventLine(String detail) {
            String type = extractAfter(detail, "type=", " ");
            String payload = extractAfter(detail, "detail=", "");
            if (type.isBlank()) {
                int split = detail.indexOf(" | ");
                if (split >= 0) {
                    type = detail.substring(0, split).trim();
                    payload = detail.substring(split + 3).trim();
                }
            }
            if (payload.isBlank()) {
                payload = detail;
            }
            String title = classifyWorldEvent(type);
            return new TraceLine(title, "type=" + (type.isBlank() ? "unknown" : type) + "；" + compactWorldPayload(payload));
        }

        private static String classifyWorldEvent(String type) {
            String lower = type == null ? "" : type.toLowerCase();
            if (lower.contains("attacked") || lower.contains("hurt") || lower.contains("death")) {
                return "交互/受伤";
            }
            if (lower.contains("interact") || lower.contains("ate") || lower.contains("soul.")) {
                return "交互/关系";
            }
            if (lower.contains("vision_summary")) {
                return "视角摘要";
            }
            if (lower.startsWith("owner.view.risk")) {
                return "世界/高风险";
            }
            if (lower.startsWith("owner.view")) {
                return "世界/视角扫描";
            }
            return "世界事件";
        }

        private static String compactUserInput(String detail) {
            String text = extractAfter(detail, "text=", "");
            return text.isBlank() ? detail : text;
        }

        private static String compactAffectEvent(String detail) {
            int slash = detail.indexOf(" / ");
            if (slash < 0) {
                return detail;
            }
            return "事件=" + detail.substring(0, slash).trim() + "；" + detail.substring(slash + 3).trim();
        }

        private static String compactToolResult(String detail) {
            String cleaned = detail == null ? "" : detail.trim();
            if (cleaned.startsWith("[工具结果]")) {
                cleaned = cleaned.substring("[工具结果]".length()).trim();
            }
            return compactWorldPayload(cleaned);
        }

        private static String compactWorldPayload(String payload) {
            String result = payload == null ? "" : payload.trim();
            result = result.replace("owner_looking_at_request_maid=", "看着当前女仆=");
            result = result.replace("owner_risks=", "主人风险=");
            result = result.replace("nearby_monsters=", "附近怪物=");
            result = result.replace("nearby_entities_count=", "附近实体数=");
            result = result.replace("nearby_entities=", "附近实体=");
            result = result.replace("companion_cues=", "陪伴线索=");
            result = result.replace("topic_candidates=", "可提话题=");
            result = result.replace("maid_distance=", "女仆距离=");
            result = result.replace("weather=", "天气=");
            result = result.replace("time=", "时间=");
            result = result.replace("owner_state=", "主人状态=");
            result = result.replace("focus=", "焦点=");
            result = result.replace("attacker=", "攻击者=");
            result = result.replace("amount=", "伤害=");
            result = result.replace("health=", "女仆生命=");
            result = result.replace("source=", "来源=");
            return result;
        }

        private static String extractAfter(String text, String key, String until) {
            if (text == null || key == null || key.isBlank()) {
                return "";
            }
            int start = text.indexOf(key);
            if (start < 0) {
                return "";
            }
            start += key.length();
            if (until == null || until.isEmpty()) {
                return text.substring(start).trim();
            }
            int end = text.indexOf(until, start);
            return (end < 0 ? text.substring(start) : text.substring(start, end)).trim();
        }
    }
}
