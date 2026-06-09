package com.maidsoul.brain.forge.soul;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.Optional;

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

    public static boolean isRegistered(EntityMaid maid) {
        return maid != null && SoulBindingData.fromTag(maid.getPersistentData()).isBound();
    }

    public static boolean isRegisteredFor(ServerPlayer player, EntityMaid maid) {
        if (player == null || maid == null || !maid.isAlive() || !maid.isOwnedBy(player) || !isRegistered(maid)) {
            return false;
        }
        SoulBindingData binding = SoulBindingData.fromTag(maid.getPersistentData());
        return binding.ownerUuid().isBlank() || binding.ownerUuid().equals(player.getUUID().toString());
    }

    public static Optional<EntityMaid> firstRegisteredMaid(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        return firstRegisteredMaidByIteration(player, player.serverLevel());
    }

    private static Optional<EntityMaid> firstRegisteredMaidByIteration(ServerPlayer player, ServerLevel level) {
        java.util.ArrayList<EntityMaid> maids = new java.util.ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityMaid maid && isRegisteredFor(player, maid)) {
                maids.add(maid);
            }
        }
        return maids.stream()
                .min(Comparator
                        .comparingLong((EntityMaid maid) -> SoulBindingData.fromTag(maid.getPersistentData()).boundAt())
                        .thenComparing(maid -> maid.getUUID().toString()));
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
        if ("soul.first_bound".equals(result.eventType())) {
            MaidBrainRuntimeRegistry.receiveWorldEvent(maid, "soul.first_meeting",
                    "主人第一次使用灵魂核心正式注册并唤醒了这只女仆。"
                            + "maidUuid=" + maid.getUUID()
                            + ", soulId=" + soulId
                            + ", displayName=" + safeDisplayName
                            + ", owner=" + player.getName().getString()
                            + "。这是你和主人第一次正式相遇，可以自然地回应主人。");
        }
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
