package com.maidsoul.brain;

import com.maidsoul.brain.vision.VisionSceneFacts;

/**
 * 视觉事实提取 smoke。
 *
 * <p>这个测试只喂结构化 MC 状态，不靠自然语言关键词判断视觉摘要。</p>
 */
public final class VisionSceneFactsSmokeTest {
    private VisionSceneFactsSmokeTest() {
    }

    public static void main(String[] args) {
        String sceneHint = "vision_request_context={request_maid_uuid=maid-1}; structured_scene={"
                + "owner_looking_at_request_maid=true,"
                + "focus=current_maid_self:minecraft:maid,uuid=maid-1,distance=2.5,"
                + "time=night,"
                + "weather=clear,"
                + "owner_risks=[low_health],"
                + "nearby_monsters=2,"
                + "maid_distance=3.2"
                + "}";
        VisionSceneFacts facts = VisionSceneFacts.from("摘要=主人正看着女仆。", sceneHint);
        String text = facts.toPlannerText();
        if (!text.contains("owner_focus=current_maid_self")) {
            throw new IllegalStateException("没有识别当前女仆焦点: " + text);
        }
        if (!text.contains("danger_level=high")) {
            throw new IllegalStateException("没有识别主人风险为高危险: " + text);
        }
        if (!text.contains("affection_ping") || !text.contains("companionship") || !text.contains("care_check")) {
            throw new IllegalStateException("没有生成陪伴/关心话题候选: " + text);
        }
        System.out.println("VISION_SCENE_FACTS_SMOKE_OK");
    }
}
