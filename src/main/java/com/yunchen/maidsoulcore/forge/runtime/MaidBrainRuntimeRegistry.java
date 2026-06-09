package com.yunchen.maidsoulcore.forge.runtime;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
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
import com.yunchen.maidsoulcore.forge.config.ForgeBrainConfigInstaller;
import com.yunchen.maidsoulcore.forge.config.ForgeDebugOptions;
import com.yunchen.maidsoulcore.forge.debug.OwnerChatDebugEcho;
import com.yunchen.maidsoulcore.forge.soul.SoulBindingData;
import com.yunchen.maidsoulcore.forge.speech.MaidSpeechDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MaidBrainRuntimeRegistry {
    private static final ConcurrentMap<UUID, MaidSoulRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, CopyOnWriteArrayList<LLMCallback>> TLM_CALLBACKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> THINKING_BUBBLES = new ConcurrentHashMap<>();
    private static final Set<UUID> ERROR_NOTIFIED = ConcurrentHashMap.newKeySet();

    private MaidBrainRuntimeRegistry() {
    }

    public static MaidSoulRuntime getOrCreate(EntityMaid maid) {
        return RUNTIMES.computeIfAbsent(runtimeKey(maid), ignored -> createRuntime(maid));
    }

    public static void receiveOwnerChat(EntityMaid maid, String content) {
        if (maid == null || content == null || content.isBlank()) {
            return;
        }
        ForgeDebugOptions debug = debugOptions();
        OwnerChatDebugEcho.echoImportant(maid, debug, "owner.input", content);
        getOrCreate(maid).receiveOwnerMessage(ownerName(maid), content);
    }

    public static void receiveOwnerChat(EntityMaid maid, String content, LLMCallback callback) {
        if (callback != null && maid != null) {
            TLM_CALLBACKS.computeIfAbsent(maid.getUUID(), ignored -> new CopyOnWriteArrayList<>()).add(callback);
        }
        receiveOwnerChat(maid, content);
    }

    public static void beginThinking(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        if (maid.getServer() == null || maid.getServer().isSameThread()) {
            addThinkingBubble(maid);
        } else {
            maid.getServer().execute(() -> addThinkingBubble(maid));
        }
    }

    public static void receiveWorldEvent(EntityMaid maid, String eventType, String detail) {
        if (maid == null) {
            return;
        }
        ForgeDebugOptions debug = debugOptions();
        OwnerChatDebugEcho.echo(maid, debug, "event", eventType + " | " + detail);
        getOrCreate(maid).receiveWorldEvent(eventType, detail);
    }

    private static MaidSoulRuntime createRuntime(EntityMaid maid) {
        Path root = ForgeBrainConfigInstaller.configRoot();
        DialogueCoreConfig config = DialogueConfigLoader.loadOrCreate(root.resolve("dialogue-config.json"));
        SoulBindingData binding = SoulBindingData.fromTag(maid.getPersistentData());
        if (binding.isBound()) {
            config.ownerName = ownerName(maid);
        }
        PromptCatalog prompts = new PromptCatalog(root.resolve("prompts"));
        prompts.installDefaultsIfMissing();
        Path maidDir = root.resolve("memory").resolve(runtimeMemoryId(maid, binding));
        OpenAiCompatibleClient timingLlm = new OpenAiCompatibleClient(config.model.copyForModel(config.model.timingModel));
        OpenAiCompatibleClient plannerLlm = new OpenAiCompatibleClient(config.model.copyForModel(config.model.plannerModel));
        OpenAiCompatibleClient replyerLlm = new OpenAiCompatibleClient(config.model.copyForModel(config.model.replyerModel));
        ForgeDebugOptions debug = debugOptions();
        MaidSoulRuntime runtime = new MaidSoulRuntime(
                config,
                prompts,
                new TimingGateRunner(config, prompts, timingLlm),
                new PlannerRunner(config, prompts, plannerLlm),
                new ReplyGenerator(config, prompts, replyerLlm),
                new ForgePlannerToolExecutor(maid),
                new LifeMemoryStore(maidDir.resolve("life.json")),
                new AffectProfileStore(maidDir.resolve("affect.json")),
                text -> deliverToTlmChatBoxOrFallback(maid, debug, text),
                (stage, detail) -> trace(maid, debug, stage, detail)
        );
        runtime.syncFavorability(maid.getFavorability());
        return runtime;
    }

    private static void trace(EntityMaid maid, ForgeDebugOptions debug, String stage, String detail) {
        if ("runtime.error".equals(stage)) {
            MaidSoulCoreMod.LOGGER.error("[{}] {} {}", maid.getUUID(), stage, detail);
            OwnerChatDebugEcho.echoImportant(maid, debug, stage, detail);
            notifyRuntimeErrorOnce(maid, detail);
        } else {
            MaidSoulCoreMod.LOGGER.debug("[{}] {} {}", maid.getUUID(), stage, detail);
        }
        OwnerChatDebugEcho.echo(maid, debug, stage, detail);
        if (debug.echoAffectToOwnerChat() && "runtime.cycle".equals(stage)) {
            OwnerChatDebugEcho.echo(maid, debug, "affect", getOrCreate(maid).affectProfile().brief());
        }
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
        Long waitingBubble = THINKING_BUBBLES.remove(maid.getUUID());
        long waitingBubbleId = waitingBubble == null ? -1L : waitingBubble;
        MaidSpeechDispatcher.queueSpeechOnServer(maid, text, waitingBubbleId);
    }

    private static void addThinkingBubble(EntityMaid maid) {
        Long previous = THINKING_BUBBLES.remove(maid.getUUID());
        if (previous != null && previous >= 0) {
            maid.getChatBubbleManager().removeChatBubble(previous);
        }
        long waitingId = maid.getChatBubbleManager().addThinkingText("ai.touhou_little_maid.chat.chat_bubble_waiting");
        if (waitingId >= 0) {
            THINKING_BUBBLES.put(maid.getUUID(), waitingId);
        }
    }

    public static void invalidate(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        ERROR_NOTIFIED.remove(maid.getUUID());
        TLM_CALLBACKS.remove(maid.getUUID());
        THINKING_BUBBLES.remove(maid.getUUID());
        MaidSoulRuntime runtime = RUNTIMES.remove(runtimeKey(maid));
        if (runtime != null) {
            runtime.close();
        }
        RUNTIMES.remove(maid.getUUID());
    }

    private static UUID runtimeKey(EntityMaid maid) {
        return maid.getUUID();
    }

    private static String runtimeMemoryId(EntityMaid maid, SoulBindingData binding) {
        if (binding != null && binding.isBound()) {
            return binding.soulId();
        }
        return maid.getUUID().toString();
    }

    public static String worldIdFor(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString().replace(':', '_').replace('/', '_');
        }
        return "minecraft_overworld";
    }

    private static String ownerName(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null ? "主人" : owner.getName().getString();
    }

    private static ForgeDebugOptions debugOptions() {
        return ForgeDebugOptions.load(ForgeBrainConfigInstaller.configRoot());
    }

    private static void notifyRuntimeErrorOnce(EntityMaid maid, String detail) {
        if (maid == null || !ERROR_NOTIFIED.add(maid.getUUID())) {
            return;
        }
        String message = "唔...我的思考核心刚刚卡住了。请看 latest.log 或检查 maidsoulcore/dialogue-config.json。";
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

