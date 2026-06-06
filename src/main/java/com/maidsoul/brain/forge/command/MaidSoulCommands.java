package com.maidsoul.brain.forge.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.site.AvailableSites;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.registry.ModItems;
import com.maidsoul.brain.forge.runtime.MaidBrainRuntimeRegistry;
import com.maidsoul.brain.forge.soul.LegacyMaidMemoryMigrator;
import com.maidsoul.brain.forge.soul.SoulBindingData;
import com.maidsoul.brain.forge.soul.SoulStore;
import com.maidsoul.brain.forge.soul.SoulSummary;
import com.maidsoul.brain.forge.tlm.MaidSoulTlmBootstrapper;
import com.maidsoul.brain.forge.tlm.llm.MaidSoulRuntimeSite;
import com.maidsoul.brain.forge.vision.MaidVisionService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Path;
import java.util.List;

/**
 * Forge 内测命令。
 *
 * <p>正式游玩仍然走“灵魂核心”道具和 GUI；命令只服务开发阶段的快速验证：
 * 给道具、查看 soul 列表、查看某只女仆当前绑定、迁移旧 maidUuid 记忆目录。</p>
 */
public final class MaidSoulCommands {
    private MaidSoulCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("maidsoulcore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give_core").executes(context -> giveCore(context.getSource())))
                .then(Commands.literal("souls").executes(context -> listSouls(context.getSource(), 8))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 40))
                                .executes(context -> listSouls(context.getSource(), IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("where").executes(context -> showPaths(context.getSource())))
                .then(Commands.literal("binding")
                        .then(Commands.argument("maid", EntityArgument.entity())
                                .executes(context -> showBinding(context.getSource(), EntityArgument.getEntity(context, "maid")))))
                .then(Commands.literal("diagnose_tlm")
                        .then(Commands.argument("maid", EntityArgument.entity())
                                .executes(context -> diagnoseTlm(context.getSource(), EntityArgument.getEntity(context, "maid")))))
                .then(Commands.literal("vision")
                        .then(Commands.argument("maid", EntityArgument.entity())
                                .executes(context -> requestVision(context.getSource(), EntityArgument.getEntity(context, "maid")))))
                .then(Commands.literal("migrate_legacy")
                        .then(Commands.argument("maid", EntityArgument.entity())
                                .then(Commands.argument("soulId", StringArgumentType.word())
                                        .executes(context -> migrateLegacy(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "maid"),
                                                StringArgumentType.getString(context, "soulId")))))));
    }

    private static int giveCore(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("这个命令只能由玩家执行。"));
            return 0;
        }
        player.getInventory().add(new ItemStack(ModItems.SOUL_CORE.get()));
        source.sendSuccess(() -> Component.literal("已给予灵魂核心。右键车万女仆打开绑定界面；潜行右键可快速绑定默认角色包。"), false);
        return 1;
    }

    private static int listSouls(CommandSourceStack source, int limit) {
        List<SoulSummary> souls = SoulStore.global().list().stream().limit(limit).toList();
        if (souls.isEmpty()) {
            source.sendSuccess(() -> Component.literal("还没有灵魂档案。"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("灵魂档案：" + souls.size() + " 条"), false);
        for (SoulSummary soul : souls) {
            source.sendSuccess(() -> Component.literal("- " + soul.soulId()
                    + " / " + soul.displayName()
                    + " / world=" + empty(soul.lastWorldId())
                    + " / maid=" + empty(soul.lastMaidUuid())), false);
        }
        return souls.size();
    }

    private static int showPaths(CommandSourceStack source) {
        Path root = ForgeBrainConfigInstaller.configRoot();
        source.sendSuccess(() -> Component.literal("配置目录: " + root.toAbsolutePath().normalize()), false);
        source.sendSuccess(() -> Component.literal("灵魂目录: " + root.resolve("souls").toAbsolutePath().normalize()), false);
        source.sendSuccess(() -> Component.literal("共享记忆目录: " + root.resolve("memory").resolve("maids").toAbsolutePath().normalize()), false);
        return 1;
    }

    private static int showBinding(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            source.sendFailure(Component.literal("目标不是车万女仆。"));
            return 0;
        }
        SoulBindingData binding = SoulBindingData.fromTag(maid.getPersistentData());
        if (!binding.isBound()) {
            source.sendSuccess(() -> Component.literal("这只女仆还没有绑定灵魂。maidUuid=" + maid.getUUID()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("绑定 soulId=" + binding.soulId()
                + ", bindingId=" + binding.bindingId()
                + ", worldId=" + binding.worldId()
                + ", maidUuid=" + binding.maidUuid()), false);
        return 1;
    }

    private static int migrateLegacy(CommandSourceStack source, Entity entity, String soulId) {
        if (!(entity instanceof EntityMaid maid)) {
            source.sendFailure(Component.literal("目标不是车万女仆。"));
            return 0;
        }
        LegacyMaidMemoryMigrator.Result result = LegacyMaidMemoryMigrator.migrateCurrentMaid(maid, soulId);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        MaidBrainRuntimeRegistry.receiveWorldEvent(maid, "soul.migrated_legacy_maid", result.eventDetail());
        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int diagnoseTlm(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            source.sendFailure(Component.literal("目标不是车万女仆。"));
            return 0;
        }
        MaidSoulTlmBootstrapper.ensureMaidSoulRuntime(maid);
        var manager = maid.getAiChatManager();
        source.sendSuccess(() -> Component.literal("MaidSoulCore TLM 诊断:"), false);
        source.sendSuccess(() -> Component.literal("- maidUuid=" + maid.getUUID()), false);
        source.sendSuccess(() -> Component.literal("- llmSite=" + manager.llmSite), false);
        source.sendSuccess(() -> Component.literal("- llmModel=" + manager.llmModel), false);
        source.sendSuccess(() -> Component.literal("- customSettingSentinel=" + MaidSoulTlmBootstrapper.SENTINEL_SETTING.equals(manager.customSetting)), false);
        source.sendSuccess(() -> Component.literal("- TLM_LLM_ENABLED=" + (AIConfig.LLM_ENABLED == null ? "unknown" : AIConfig.LLM_ENABLED.get())), false);
        source.sendSuccess(() -> Component.literal("- TLM_AUTO_GEN_SETTING=" + (AIConfig.AUTO_GEN_SETTING_ENABLED == null ? "unknown" : AIConfig.AUTO_GEN_SETTING_ENABLED.get())), false);
        source.sendSuccess(() -> Component.literal("- runtimeSiteAvailable=" + AvailableSites.LLM_SITES.containsKey(MaidSoulRuntimeSite.API_TYPE)), false);
        source.sendSuccess(() -> Component.literal("- runtimeSiteEnabled=" + (AvailableSites.LLM_SITES.get(MaidSoulRuntimeSite.API_TYPE) != null && AvailableSites.LLM_SITES.get(MaidSoulRuntimeSite.API_TYPE).enabled())), false);
        return 1;
    }

    private static int requestVision(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            source.sendFailure(Component.literal("目标不是车万女仆。"));
            return 0;
        }
        boolean sent = MaidVisionService.requestManualSummary(maid, "manual command from " + source.getTextName());
        if (!sent) {
            source.sendFailure(Component.literal("没有发起视觉摘要：请确认主人在线、视觉模型已启用，或等待冷却结束。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已向主人客户端请求截图，视觉摘要稍后会写入女仆事件。"), false);
        return 1;
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
