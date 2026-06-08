package com.yunchen.maidsoulcore.core.memory;

import com.yunchen.maidsoulcore.core.affect.AffectiveResult;
import com.yunchen.maidsoulcore.core.event.EventImportancePolicy;
import com.yunchen.maidsoulcore.core.event.StructuredEvent;
import com.yunchen.maidsoulcore.core.event.StructuredEventType;

import java.util.ArrayList;
import java.util.List;

public final class MemoryWritebackService {
    public List<MemoryWritebackCandidate> propose(
            String ownerName,
            String maidName,
            String ownerText,
            StructuredEvent event,
            AffectiveResult affective
    ) {
        List<MemoryWritebackCandidate> result = new ArrayList<>();
        if (ownerText == null || ownerText.isBlank() || event == null) {
            return result;
        }
        event.normalize();
        StructuredEventType type = event.typeEnum();
        if (!event.shouldWriteMemory || event.confidence < 0.70D) {
            return result;
        }
        String owner = blankToDefault(ownerName, "owner");
        String maid = blankToDefault(maidName, "maid");
        String evidence = event.evidence == null || event.evidence.isBlank()
                ? owner + ": " + ownerText.trim()
                : event.evidence;
        String subject = blankToDefault(event.subject, maid);
        String object = blankToDefault(event.object, maid);
        String summary = event.summary == null || event.summary.isBlank()
                ? defaultSummary(type, affective)
                : event.summary;
        int importance = Math.max(1, Math.min(100, (int) Math.round(event.importance * 100.0D)));
        MemoryCategory category = categoryOf(event.memoryCategory, type);

        switch (type) {
            case AFFECTION, INITIATE, LONG_MESSAGE, PROMISE, MEMORY_ANCHOR -> {
                result.add(new MemoryWritebackCandidate(
                        category,
                        subject,
                        object,
                        summary + "，关系阶段=" + affective.snapshot().relationshipStage().id(),
                        evidence,
                        event.confidence,
                        importance,
                        type.id()
                ));
                if (affective.memoryTriggerScore() > 0.25D) {
                    result.add(new MemoryWritebackCandidate(
                            MemoryCategory.MAID_SELF,
                            maid,
                            owner,
                            "当前互动触发了重要回忆，女仆更想靠近主人",
                            evidence,
                            0.76D,
                            68,
                            type.id()
                    ));
                }
            }
            case APOLOGY, REPAIR_CHECK -> result.add(new MemoryWritebackCandidate(
                    category,
                    subject,
                    object,
                    summary,
                    evidence,
                    event.confidence,
                    importance,
                    type.id()
            ));
            case FIGHT, REJECT, OWNER_ATTACK -> result.add(new MemoryWritebackCandidate(
                    category,
                    subject,
                    object,
                    summary,
                    evidence,
                    event.confidence,
                    importance,
                    type.id()
            ));
            case DANGER, WORLD_CHANGE, MAID_DEATH -> result.add(new MemoryWritebackCandidate(
                    category,
                    subject,
                    object,
                    summary,
                    evidence,
                    event.confidence,
                    importance,
                    type.id()
            ));
            case CARE, FATIGUE, BOUNDARY_REQUEST -> result.add(new MemoryWritebackCandidate(
                    category,
                    subject,
                    object,
                    summary,
                    evidence,
                    event.confidence,
                    importance,
                    type.id()
            ));
            default -> {
            }
        }
        return result;
    }

    public void write(LifeMemoryStore store, List<MemoryWritebackCandidate> candidates) {
        if (store == null || candidates == null) {
            return;
        }
        for (MemoryWritebackCandidate candidate : candidates) {
            if (candidate.confidence() < 0.60D || candidate.summary() == null || candidate.summary().isBlank()) {
                continue;
            }
            store.appendRecord(toRecord(candidate));
        }
    }

    private static EventMemoryRecord toRecord(MemoryWritebackCandidate candidate) {
        EventMemoryRecord record = new EventMemoryRecord();
        record.category = candidate.category().id();
        record.subject = candidate.subject();
        record.object = candidate.object();
        record.eventType = candidate.eventType();
        record.summary = candidate.summary();
        record.evidence = candidate.evidence();
        record.confidence = candidate.confidence();
        record.importance = Math.max(0.01D, Math.min(1.0D, candidate.importance() / 100.0D));
        record.salience = record.importance * record.confidence;
        record.pinned = candidate.category() == MemoryCategory.PROMISE
                || candidate.category() == MemoryCategory.MEMORY_ANCHOR
                || candidate.importance() >= 88;
        record.decayRate = record.pinned ? 0.002D : 0.02D;
        record.normalize();
        return record;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static MemoryCategory categoryOf(String category, StructuredEventType type) {
        String id = category == null || category.isBlank()
                ? EventImportancePolicy.defaultMemoryCategory(type)
                : category;
        for (MemoryCategory value : MemoryCategory.values()) {
            if (value.id().equals(id)) {
                return value;
            }
        }
        return MemoryCategory.RELATION_EVENT;
    }

    private static String defaultSummary(StructuredEventType type, AffectiveResult affective) {
        return switch (type) {
            case AFFECTION -> "主人表达亲近或喜欢";
            case CARE -> "主人表现出照顾和关心";
            case APOLOGY -> "主人尝试道歉和修复关系";
            case REPAIR_CHECK -> "主人确认女仆是否还委屈，尝试继续修复";
            case FIGHT, REJECT, OWNER_ATTACK -> "关系中出现冲突或受伤，需要后续修复";
            case PROMISE -> "主人做出重要承诺";
            case MEMORY_ANCHOR -> "主人要求女仆记住重要事件";
            case FATIGUE -> "主人表达疲惫，需要安静陪伴";
            case BOUNDARY_REQUEST -> "主人表达边界或低打扰需求";
            case DANGER, WORLD_CHANGE, MAID_DEATH -> "发生安全或世界相关重要事件";
            default -> "发生一条关系相关事件";
        };
    }
}
