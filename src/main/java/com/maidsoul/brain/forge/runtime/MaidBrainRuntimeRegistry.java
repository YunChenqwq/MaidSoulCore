package com.maidsoul.brain.forge.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.maidsoul.brain.config.BrainConfig;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.config.ForgeDebugOptions;
import com.maidsoul.brain.forge.debug.OwnerChatDebugEcho;
import com.maidsoul.brain.forge.speech.MaidSpeechDispatcher;
import com.maidsoul.brain.forge.soul.SoulBindingData;
import com.maidsoul.brain.llm.OpenAiCompatibleClient;
import com.maidsoul.brain.prompt.PromptCatalog;
import com.maidsoul.brain.runtime.ConversationRuntime;
import com.maidsoul.brain.runtime.RuntimeTraceSink;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MaidBrainRuntimeRegistry {
    private static final ConcurrentMap<UUID, ConversationRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, CopyOnWriteArrayList<LLMCallback>> TLM_CALLBACKS = new ConcurrentHashMap<>();
    private static final Set<UUID> ERROR_NOTIFIED = ConcurrentHashMap.newKeySet();

    private MaidBrainRuntimeRegistry() {
    }

    public static ConversationRuntime getOrCreate(EntityMaid maid) {
        return RUNTIMES.computeIfAbsent(runtimeKey(maid), ignored -> createRuntime(maid));
    }

    public static void receiveOwnerChat(EntityMaid maid, String content) {
        ForgeDebugOptions debug = debugOptions();
        OwnerChatDebugEcho.echoImportant(maid, debug, "owner.input", content);
        getOrCreate(maid).receiveUserMessage(ownerName(maid), content);
    }

    public static void receiveOwnerChat(EntityMaid maid, String content, LLMCallback callback) {
        if (callback != null) {
            TLM_CALLBACKS.computeIfAbsent(maid.getUUID(), ignored -> new CopyOnWriteArrayList<>()).add(callback);
        }
        receiveOwnerChat(maid, content);
    }

    public static void receiveWorldEvent(EntityMaid maid, String eventType, String detail) {
        ForgeDebugOptions debug = debugOptions();
        OwnerChatDebugEcho.echo(maid, debug, "event", eventType + " | " + detail);
        getOrCreate(maid).receiveWorldEvent(eventType, detail);
    }

    private static ConversationRuntime createRuntime(EntityMaid maid) {
        Path root = ForgeBrainConfigInstaller.configRoot();
        BrainConfig baseConfig = BrainConfig.load(root);
        SoulBindingData binding = SoulBindingData.fromTag(maid.getPersistentData());
        BrainConfig runtimeConfig = baseConfig.withRuntimeIdentity(
                binding.isBound() ? binding.soulId() : "unbound-" + maid.getUUID(),
                ownerId(maid),
                binding.isBound() ? "*" : worldId(maid)
        );
        PromptCatalog prompts = new PromptCatalog(ForgeBrainConfigInstaller.promptRoot());
        ForgeDebugOptions debug = ForgeDebugOptions.load(root);
        final ConversationRuntime[] holder = new ConversationRuntime[1];
        RuntimeTraceSink trace = (stage, detail) -> {
            if ("runtime.error".equals(stage)) {
                MaidSoulCoreForgeMod.LOGGER.error("[{}] {} {}", maid.getUUID(), stage, detail);
                OwnerChatDebugEcho.echoImportant(maid, debug, stage, detail);
                notifyRuntimeErrorOnce(maid, detail);
            } else {
                MaidSoulCoreForgeMod.LOGGER.debug("[{}] {} {}", maid.getUUID(), stage, detail);
            }
            OwnerChatDebugEcho.echo(maid, debug, stage, detail);
            if (debug.echoAffectToOwnerChat() && "runtime.cycle".equals(stage) && holder[0] != null) {
                OwnerChatDebugEcho.echo(maid, debug, "affect", holder[0].affectSummary());
            }
        };
        ConversationRuntime runtime = new ConversationRuntime(
                runtimeConfig,
                prompts,
                new OpenAiCompatibleClient(runtimeConfig.model()),
                text -> {
                    deliverToTlmChatBoxOrFallback(maid, debug, text);
                },
                trace
        );
        holder[0] = runtime;
        runtime.start();
        return runtime;
    }

    private static void deliverToTlmChatBoxOrFallback(EntityMaid maid, ForgeDebugOptions debug, String text) {
        CopyOnWriteArrayList<LLMCallback> callbacks = TLM_CALLBACKS.get(maid.getUUID());
        if (callbacks != null && !callbacks.isEmpty()) {
            LLMCallback callback = callbacks.remove(0);
            if (callbacks.isEmpty()) {
                TLM_CALLBACKS.remove(maid.getUUID(), callbacks);
            }
            callback.runOnServerThread(() -> maid.getChatBubbleManager().addLLMChatText(text, callback.getWaitingChatBubbleId()));
            return;
        }
        if (debug.echoReplyToOwnerChat()) {
            OwnerChatDebugEcho.echoImportant(maid, debug, "reply", text);
        }
        MaidSpeechDispatcher.queueSpeechOnServer(maid, text);
    }

    private static String ownerName(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null ? "主人" : owner.getName().getString();
    }

    public static void invalidate(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        ERROR_NOTIFIED.remove(maid.getUUID());
        TLM_CALLBACKS.remove(maid.getUUID());
        ConversationRuntime runtime = RUNTIMES.remove(runtimeKey(maid));
        if (runtime != null) {
            runtime.close();
        }
        RUNTIMES.remove(maid.getUUID());
    }

    private static UUID runtimeKey(EntityMaid maid) {
        return maid.getUUID();
    }

    private static String ownerId(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null ? "owner-unknown" : owner.getUUID().toString();
    }

    public static String worldIdFor(EntityMaid maid) {
        return worldId(maid);
    }

    private static String worldId(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString().replace(':', '_').replace('/', '_');
        }
        return "minecraft_overworld";
    }

    private static ForgeDebugOptions debugOptions() {
        return ForgeDebugOptions.load(ForgeBrainConfigInstaller.configRoot());
    }

    private static void notifyRuntimeErrorOnce(EntityMaid maid, String detail) {
        if (maid == null || !ERROR_NOTIFIED.add(maid.getUUID())) {
            return;
        }
        String message = "唔...我的思考核心刚刚卡住了。请看 latest.log 或检查 maidsoulcore/model/llm.properties。";
        if (detail != null && !detail.isBlank()) {
            message += " (" + clip(detail, 80) + ")";
        }
        MaidSpeechDispatcher.queueSpeechOnServer(maid, message);
    }

    private static String clip(String text, int max) {
        String clean = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}
