package com.maidsoul.brain.memory.v2;

import com.maidsoul.brain.util.JsonText;
import com.maidsoul.brain.util.SimpleJson;

import java.util.Map;

/**
 * 人物画像快照。
 *
 * <p>对应 A_Memorix 的 person_profile_snapshots。第一版基于实体、关系和段落证据
 * 确定性生成，不调用 LLM，避免画像生成影响聊天主链路延迟。</p>
 */
public final class PersonProfileSnapshot {
    public String personId = "";
    public String profileText = "";
    public String evidenceIds = "";
    public double updatedAt = MemoryParagraph.now();

    String toJsonLine() {
        return "{"
                + "\"personId\":\"" + JsonText.escape(personId) + "\","
                + "\"profileText\":\"" + JsonText.escape(profileText) + "\","
                + "\"evidenceIds\":\"" + JsonText.escape(evidenceIds) + "\","
                + "\"updatedAt\":" + updatedAt
                + "}";
    }

    static PersonProfileSnapshot fromJsonLine(String line) {
        Map<String, String> data = SimpleJson.object(line);
        PersonProfileSnapshot snapshot = new PersonProfileSnapshot();
        snapshot.personId = data.getOrDefault("personId", "");
        snapshot.profileText = data.getOrDefault("profileText", "");
        snapshot.evidenceIds = data.getOrDefault("evidenceIds", "");
        try {
            snapshot.updatedAt = Double.parseDouble(data.getOrDefault("updatedAt", "0"));
        } catch (NumberFormatException ignored) {
            snapshot.updatedAt = MemoryParagraph.now();
        }
        return snapshot;
    }
}
