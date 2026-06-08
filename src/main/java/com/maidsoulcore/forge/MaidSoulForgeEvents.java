package com.maidsoulcore.forge;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.memory.MaidSoulLifeMemoryService;
import com.maidsoulcore.forge.service.MaidSoulActionExecutorService;
import com.maidsoulcore.forge.service.MaidSoulCompanionService;
import com.maidsoulcore.forge.service.MaidSoulEmotionService;
import com.maidsoulcore.forge.service.MaidSoulPlanService;
import com.maidsoulcore.forge.service.MaidSoulSiteService;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * Forge 侧的 TLM 事件桥。
 * <p>
 * 当前版本先接一批稳定且高价值的事件，
 * 用于维护运行时状态和调试轨迹：
 * tick、交互、受击、进食、死亡。
 */
public final class MaidSoulForgeEvents {
    private MaidSoulForgeEvents() {
    }

    /**
     * 每 tick 对女仆状态做一次差分观察。
     */
    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        if (event.getMaid().level().isClientSide()) {
            return;
        }
        MaidSoulStateRegistry.observeTick(event.getMaid());
        MaidSoulEmotionService.onTick(event.getMaid());
        MaidSoulActionExecutorService.onMaidTick(event.getMaid());
        MaidSoulPlanService.onMaidTick(event.getMaid());
        MaidSoulCompanionService.onMaidTick(event.getMaid());
        MaidSoulLifeMemoryService.onMaidTick(event.getMaid());
    }

    /**
     * 记录玩家对女仆的显式交互。
     */
    @SubscribeEvent
    public static void onInteract(InteractMaidEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        String detail = event.getPlayer().getName().getString() + " -> " + event.getStack().getDisplayName().getString();
        MaidSoulStateRegistry.record(event.getMaid(), "maid.interact", detail, EventPriority.P1);
        MaidSoulCompanionService.onExplicitEvent(event.getMaid(), "maid.interact", detail, EventPriority.P1);
    }

    /**
     * 记录女仆被攻击事件。
     */
    @SubscribeEvent
    public static void onAttack(MaidAttackEvent event) {
        if (event.getMaid().level().isClientSide()) {
            return;
        }
        Entity directEntity = event.getSource().getDirectEntity();
        String attacker = directEntity == null ? event.getSource().getMsgId() : directEntity.getName().getString();
        String attackerUuid = directEntity == null ? "none" : directEntity.getUUID().toString();
        String detail = attacker + " attackerUuid=" + attackerUuid + " amount=" + event.getAmount() + " source=" + event.getSource().getMsgId();
        String eventType = directEntity instanceof Player player && event.getMaid().getOwner() != null && player.getUUID().equals(event.getMaid().getOwner().getUUID())
                ? "maid.attacked.by_owner"
                : "maid.attacked";
        MaidSoulStateRegistry.record(event.getMaid(), eventType, detail, EventPriority.P0);
        MaidSoulCompanionService.onExplicitEvent(event.getMaid(), eventType, detail, EventPriority.P0);
    }

    /**
     * 记录进食事件。
     */
    @SubscribeEvent
    public static void onEat(MaidAfterEatEvent event) {
        if (event.getMaid().level().isClientSide()) {
            return;
        }
        String detail = "maid_self_ate:" + event.getFoodAfterEat().getDisplayName().getString();
        MaidSoulStateRegistry.record(event.getMaid(), "maid.ate", detail, EventPriority.P1);
        MaidSoulCompanionService.onExplicitEvent(event.getMaid(), "maid.ate", detail, EventPriority.P1);
    }

    /**
     * 记录死亡事件。
     */
    @SubscribeEvent
    public static void onDeath(MaidDeathEvent event) {
        if (event.getMaid().level().isClientSide()) {
            return;
        }
        String detail = event.getSource().getMsgId();
        MaidSoulStateRegistry.record(event.getMaid(), "maid.death", detail, EventPriority.P0);
        MaidSoulEmotionService.observeEvent(event.getMaid(), "maid.death", detail, EventPriority.P0);
    }

    /**
     * 服务端启动后主动同步一次 TLM LLM 站点配置。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MaidSoulSiteService.synchronizeTlmSiteFromMaiBotIfNeeded();
    }
}
