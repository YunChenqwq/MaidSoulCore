package com.maidsoul.brain.reply.effect;

import java.util.List;

/**
 * 回复造成的摩擦信号。
 *
 * <p>显式负反馈和修复循环来自用户真实后续消息；uncannyRisk 暂时保持中性，
 * 后续接入 LLM judge 后再替换成更完整的感知质量评分。</p>
 */
public record FrictionSignals(
        double explicitNegative,
        double repairLoop,
        double uncannyRisk,
        List<String> evidenceMessages
) {
}
