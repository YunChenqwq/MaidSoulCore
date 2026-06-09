package com.maidsoul.brain.forge.network;

import com.maidsoul.brain.action.ysm.ActionBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

/**
 * 客户端动作调度器。
 *
 * <p>收 PlayActionPacket → 游戏线程调 ActionBridge → 回发 ActionResultPacket。
 */
public final class ClientActionHandler {

    private ClientActionHandler() {}

    /** PlayActionPacket 落地入口 */
    public static void handle(PlayActionPacket packet, ServerPlayer sender) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            String err;
            if ("animation".equals(packet.actionType())) {
                err = ActionBridge.playAnimation(packet.actionName());
            } else {
                err = ActionBridge.playPose(packet.actionName(), packet.duration());
            }
            String reason = err != null ? err : "ok";
            boolean success = err == null;

            // 回传结果给服务端
            if (mc.player != null) {
                ModNetwork.CHANNEL.sendToServer(new ActionResultPacket(packet.actionName(), success, reason));
            }
        });
    }
}
