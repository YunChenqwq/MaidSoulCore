package com.yunchen.maidsoulcore.forge.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yunchen.maidsoulcore.MaidSoulCoreMod;
import com.yunchen.maidsoulcore.core.affect.AffectProfileStore;
import com.yunchen.maidsoulcore.core.config.DialogueConfigLoader;
import com.yunchen.maidsoulcore.core.config.DialogueCoreConfig;
import com.yunchen.maidsoulcore.core.llm.OpenAiCompatibleClient;
import com.yunchen.maidsoulcore.core.memory.LifeMemoryStore;
import com.yunchen.maidsoulcore.core.prompt.PromptCatalog;
import com.yunchen.maidsoulcore.core.reasoning.PlannerRunner;
import com.yunchen.maidsoulcore.core.reasoning.TimingGateRunner;
import com.yunchen.maidsoulcore.core.reply.ReplyGenerator;
import com.yunchen.maidsoulcore.core.runtime.MaidSoulRuntime;
import com.yunchen.maidsoulcore.forge.debug.OwnerChatDebugEcho;
import com.yunchen.maidsoulcore.forge.speech.MaidSpeechDispatcher;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidRuntimeRegistry {
    private static final ConcurrentMap<UUID, MaidSoulRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private MaidRuntimeRegistry() {
    }

    public static MaidSoulRuntime getOrCreate(EntityMaid maid) {
        return RUNTIMES.computeIfAbsent(maid.getUUID(), id -> create(maid, id));
    }

    public static void receiveOwnerChat(EntityMaid maid, String content) {
        DialogueCoreConfig config = config();
        String ownerName = maid.getOwner() == null ? config.ownerName : maid.getOwner().getName().getString();
        OwnerChatDebugEcho.important(maid, config, "owner.input", content);
        getOrCreate(maid).receiveOwnerMessage(ownerName, content);
    }

    public static void receiveWorldEvent(EntityMaid maid, String eventType, String detail) {
        DialogueCoreConfig config = config();
        OwnerChatDebugEcho.echo(maid, config, "event", eventType + " | " + detail);
        MaidSoulRuntime runtime = getOrCreate(maid);
        runtime.syncFavorability(maid.getFavorability());
        runtime.receiveWorldEvent(eventType, detail);
    }

    public static void syncFavorability(EntityMaid maid) {
        getOrCreate(maid).syncFavorability(maid.getFavorability());
    }

    public static void shutdownAll() {
        RUNTIMES.values().forEach(MaidSoulRuntime::close);
        RUNTIMES.clear();
    }

    private static MaidSoulRuntime create(EntityMaid maid, UUID maidId) {
        Path root = FMLPaths.CONFIGDIR.get().resolve("maidsoulcore");
        DialogueCoreConfig config = DialogueConfigLoader.loadOrCreate(root.resolve("dialogue-config.json"));
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts"));
        prompts.installDefaultsIfMissing();
        Path maidDir = root.resolve("memory").resolve(maidId.toString());
        OpenAiCompatibleClient llm = new OpenAiCompatibleClient(config.model);
        TimingGateRunner timingGate = new TimingGateRunner(config, prompts, llm);
        PlannerRunner planner = new PlannerRunner(config, prompts, llm);
        ReplyGenerator replyer = new ReplyGenerator(config, prompts, llm);
        MaidSoulRuntime runtime = new MaidSoulRuntime(
                config,
                prompts,
                timingGate,
                planner,
                replyer,
                new LifeMemoryStore(maidDir.resolve("life.json")),
                new AffectProfileStore(maidDir.resolve("affect.json")),
                text -> {
                    if (config.debug != null && config.debug.echoReplyToOwnerChat) {
                        OwnerChatDebugEcho.important(maid, config, "reply", text);
                    }
                    MaidSpeechDispatcher.queueOnServer(maid, text);
                },
                (stage, detail) -> {
                    MaidSoulCoreMod.LOGGER.debug("[{}] {} {}", maid.getUUID(), stage, detail);
                    OwnerChatDebugEcho.echo(maid, config, stage, detail);
                }
        );
        runtime.syncFavorability(maid.getFavorability());
        return runtime;
    }

    private static DialogueCoreConfig config() {
        return DialogueConfigLoader.loadOrCreate(FMLPaths.CONFIGDIR.get().resolve("maidsoulcore").resolve("dialogue-config.json"));
    }
}
