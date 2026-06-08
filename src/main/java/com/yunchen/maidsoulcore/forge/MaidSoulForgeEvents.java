package com.yunchen.maidsoulcore.forge;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.forge.runtime.MaidRuntimeRegistry;
import com.yunchen.maidsoulcore.forge.speech.MaidSpeechDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
        MaidRuntimeRegistry.syncFavorability(maid);
        MaidSpeechDispatcher.flush(maid);
    }

    @SubscribeEvent
    public static void onInteract(InteractMaidEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        String playerName = event.getPlayer().getName().getString();
        String itemName = event.getStack().getDisplayName().getString();
        MaidRuntimeRegistry.receiveWorldEvent(event.getMaid(), "maid.interact", playerName + " interacted with item=" + itemName);
    }

    @SubscribeEvent
    public static void onAttack(MaidAttackEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        boolean ownerAttack = direct instanceof Player player
                && maid.getOwner() != null
                && player.getUUID().equals(maid.getOwner().getUUID());
        String attacker = direct == null ? event.getSource().getMsgId() : direct.getName().getString();
        String detail = "attacker=" + attacker
                + ", amount=" + event.getAmount()
                + ", source=" + event.getSource().getMsgId()
                + ", health=" + maid.getHealth() + "/" + maid.getMaxHealth();
        MaidRuntimeRegistry.receiveWorldEvent(maid, ownerAttack ? "maid.attacked.by_owner" : "maid.attacked", detail);
    }

    @SubscribeEvent
    public static void onEat(MaidAfterEatEvent event) {
        if (!event.getMaid().level().isClientSide()) {
            MaidRuntimeRegistry.receiveWorldEvent(event.getMaid(), "maid.ate", "food=" + event.getFoodAfterEat().getDisplayName().getString());
        }
    }

    @SubscribeEvent
    public static void onDeath(MaidDeathEvent event) {
        if (!event.getMaid().level().isClientSide()) {
            MaidRuntimeRegistry.receiveWorldEvent(event.getMaid(), "maid.death", "source=" + event.getSource().getMsgId());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MaidRuntimeRegistry.shutdownAll();
    }
}
