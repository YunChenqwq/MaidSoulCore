package com.maidsoul.brain.forge.soul;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 女仆灵魂绑定的唯一写入口。
 *
 * <p>灵魂绑定同时影响三处状态：女仆实体 NBT、跨世界 soul.json 档案、运行中的对话 Runtime。
 * 如果每个入口各写各的，很容易出现“道具显示已绑定，但聊天运行时还在用旧身份”的问题。
 * 所以道具、GUI 网络包、以后可能出现的命令迁移，都应该尽量复用这里的 bind/unbind 方法。</p>
 */
public final class SoulBindingService {
    public static final String DEFAULT_SOUL_ID = "prototype-jiuhu";
    public static final String DEFAULT_DISPLAY_NAME = "\u9152\u72d0";

    private SoulBindingService() {
    }

    public static boolean canOperate(ServerPlayer player, EntityMaid maid) {
        return maid.getOwner() == null || maid.getOwner().getUUID().equals(player.getUUID());
    }

    public static void sendNotOwnerMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("\u8fd9\u53ea\u5973\u4ec6\u5df2\u7ecf\u6709\u4e3b\u4eba\u4e86\uff0c\u4e0d\u80fd\u7ed1\u5b9a\u5979\u7684\u7075\u9b42\u6838\u5fc3\u3002"));
    }

    public static SoulBindResult bindDefault(ServerPlayer player, EntityMaid maid) {
        return bind(player, maid, DEFAULT_SOUL_ID, DEFAULT_DISPLAY_NAME);
    }

    public static SoulBindResult bind(ServerPlayer player, EntityMaid maid, String requestedSoulId, String displayName) {
        String soulId = SoulId.sanitize(requestedSoulId);
        String safeDisplayName = displayName == null || displayName.isBlank() ? soulId : displayName.trim();
        SoulBindingData previous = SoulBindingData.fromTag(maid.getPersistentData());
        SoulBindingData next = SoulBindingData.create(
                soulId,
                player.getUUID(),
                maid.getUUID(),
                MaidBrainRuntimeRegistry.worldIdFor(maid)
        );
        next.writeTo(maid.getPersistentData());

        SoulBindResult result = SoulStore.global().bind(soulId, safeDisplayName, previous, next);
        MaidBrainRuntimeRegistry.invalidate(maid);
        MaidBrainRuntimeRegistry.receiveWorldEvent(maid, result.eventType(), result.eventDetail());
        return result;
    }

    public static void unbind(ServerPlayer player, EntityMaid maid) {
        SoulBindingData.clear(maid.getPersistentData());
        MaidBrainRuntimeRegistry.invalidate(maid);
        MaidBrainRuntimeRegistry.receiveWorldEvent(
                maid,
                "soul.unbound",
                "maidUuid=" + maid.getUUID() + ", ownerUuid=" + player.getUUID()
        );
    }
}
