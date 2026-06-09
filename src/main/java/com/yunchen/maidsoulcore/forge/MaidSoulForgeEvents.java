package com.yunchen.maidsoulcore.forge;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.perception.MaidViewPerceptionService;
import com.yunchen.maidsoulcore.forge.runtime.MaidBrainRuntimeRegistry;
import com.yunchen.maidsoulcore.forge.soul.SoulBindingService;
import com.yunchen.maidsoulcore.forge.speech.MaidSpeechDispatcher;
import com.yunchen.maidsoulcore.forge.tlm.MaidSoulTlmBootstrapper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MaidSoulForgeEvents {
    private MaidSoulForgeEvents() {
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        if (!SoulBindingService.isRegistered(maid)) {
            return;
        }
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
        MaidBrainRuntimeRegistry.getOrCreate(maid).tick();
        MaidSpeechDispatcher.flush(maid);
        MaidViewPerceptionService.onMaidTick(maid);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!SoulBindingService.isRegistered(maid)) {
            return;
        }
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        // 每秒扫一次玩家附近的自家女仆，提前保证 TLM 聊天界面打开前已经换成 MaidSoulCore runtime。
        if (event.player.tickCount % 20 != 0) {
            return;
        }
        event.player.level()
                .getEntitiesOfClass(EntityMaid.class, event.player.getBoundingBox().inflate(16.0D),
                        maid -> maid.isAlive() && maid.isOwnedBy(event.player) && SoulBindingService.isRegistered(maid))
                .forEach(MaidSoulTlmBootstrapper::ensureMaidSoulRuntime);
    }

    @SubscribeEvent
    public static void onInteract(InteractMaidEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        if (!SoulBindingService.isRegistered(event.getMaid())) {
            return;
        }
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(event.getMaid());
        String playerName = event.getPlayer().getName().getString();
        String itemName = event.getStack().getDisplayName().getString();
        MaidBrainRuntimeRegistry.receiveWorldEvent(event.getMaid(), "maid.interact", playerName + " interacted with item=" + itemName);
    }

    @SubscribeEvent
    public static void onAttack(MaidAttackEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        String attacker = direct == null ? event.getSource().getMsgId() : direct.getName().getString();
        String attackerUuid = direct == null ? "none" : direct.getUUID().toString();
        boolean ownerAttack = direct instanceof Player player
                && maid.getOwner() != null
                && player.getUUID().equals(maid.getOwner().getUUID());
        String type = ownerAttack ? "maid.attacked.by_owner" : "maid.attacked";
        String detail = "attacker=" + attacker
                + ", attacker_uuid=" + attackerUuid
                + ", amount=" + event.getAmount()
                + ", source=" + event.getSource().getMsgId()
                + ", health=" + maid.getHealth() + "/" + maid.getMaxHealth();
        MaidBrainRuntimeRegistry.receiveWorldEvent(maid, type, detail);
    }

    @SubscribeEvent
    public static void onEat(MaidAfterEatEvent event) {
        if (event.getMaid().level().isClientSide()) {
            return;
        }
        MaidBrainRuntimeRegistry.receiveWorldEvent(
                event.getMaid(),
                "maid.ate",
                "food=" + event.getFoodAfterEat().getDisplayName().getString()
        );
    }

    @SubscribeEvent
    public static void onDeath(MaidDeathEvent event) {
        if (event.getMaid().level().isClientSide()) {
            return;
        }
        MaidBrainRuntimeRegistry.receiveWorldEvent(event.getMaid(), "maid.death", "source=" + event.getSource().getMsgId());
    }
}
