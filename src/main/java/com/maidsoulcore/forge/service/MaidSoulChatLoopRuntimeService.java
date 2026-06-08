package com.maidsoulcore.forge.service;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ErrorCode;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.forge.config.MaidSoulCommonConfig;
import com.maidsoulcore.forge.conversation.ConversationJournalService;
import com.maidsoulcore.forge.conversation.ConversationLlmTimingGateService;
import com.maidsoulcore.forge.conversation.ConversationMemoryService;
import com.maidsoulcore.forge.conversation.ConversationReflectionService;
import com.maidsoulcore.forge.plan.MaidSoulPlan;
import com.maidsoulcore.forge.runtime.MaidSoulRuntimeJob;
import com.maidsoulcore.forge.runtime.MaidSoulRuntimeJobType;
import com.maidsoulcore.forge.runtime.MaidSoulRuntimeMode;
import com.maidsoulcore.forge.runtime.MaidSoulTimingAction;
import com.maidsoulcore.forge.runtime.MaidSoulTimingDecision;
import com.maidsoulcore.forge.state.MaidSoulStateRegistry;
import com.maidsoulcore.forge.tlm.llm.MaidSoulRuntimeSite;
import com.maidsoulcore.forge.v2.MaidSoulHeartflowChatService;
import com.maidsoulcore.sim.SimulationMaiBotConfigLoader;
import com.maidsoulcore.sim.SimulationMaiBotRuntimeConfig;
import com.maidsoulcore.sim.SimulationOpenAiChatClient;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 每只女仆唯一的会话运行时。
 *
 * <p>主循环只认三类触发：玩家消息、主动事件、等待超时。玩家连续发话时，
 * 运行时先收集 pending messages，等一个短安静窗口后再统一生成回复；主动事件
 * 先作为 reference anchor 进入同一条时间线，再决定是否可见发言。</p>
 */
public final class MaidSoulChatLoopRuntimeService {
    /**
     * 同话题续话不是紧急事件，应该等上一句完整播完再接。
     * 这里给它一个短重试窗口，避免“续话到点时分句队列还没清空”就被永久丢掉。
     */
    private static final int FOLLOWUP_BUSY_RETRY_LIMIT = 30;
    private static final long FOLLOWUP_BUSY_RETRY_MILLIS = 1000L;
    private static final int PROACTIVE_MODEL_TIMEOUT_SECONDS = 8;
    private static final long PROACTIVE_TIMEOUT_BACKOFF_MILLIS = 60_000L;
    private static final ConcurrentMap<UUID, Long> PROACTIVE_COLD_LLM_PAUSE_UNTIL = new ConcurrentHashMap<>();

    private static final ConcurrentMap<UUID, RuntimeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService CHAT_EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "maidsoulcore-chat-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "maidsoulcore-chat-runtime-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private MaidSoulChatLoopRuntimeService() {
    }

    /**
     * TLM 玩家聊天入口。这里不再分叉到旧 runtime；所有普通聊天统一排队。
     */
    public static void handleChat(MaidSoulRuntimeSite site, LLMCallback callback) {
        if (callback == null || callback.getMaid() == null) {
            return;
        }
        EntityMaid maid = callback.getMaid();
        MaidSoulCompanionService.markOwnerChat(maid);

        if (MaidSoulCommonConfig.LOCAL_COMMAND_FAST_PATH_ENABLED.get()) {
            MaidSoulLocalCommandParserService.ParsedCommandPlan localPlan = MaidSoulLocalCommandParserService
                    .parse(maid, callback.getMessages())
                    .orElse(null);
            if (localPlan != null) {
                handleLocalCommand(callback, maid, localPlan);
                return;
            }
        }

        enqueueOwnerChat(site, callback, maid);
    }

    /**
     * Forge 事件、视角摘要、idle 续话都从这里进入同一个队列。
     */
    public static void handleProactiveEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        if (maid == null) {
            return;
        }
        RuntimeSession session = sessionOf(maid);
        MaidSoulRuntimeJob job;
        synchronized (session) {
            job = MaidSoulRuntimeJob.proactiveEvent(
                    ++session.nextJobId,
                    session.version,
                    eventType,
                    detail,
                    priority,
                    System.currentTimeMillis()
            );
            session.queue.addLast(job);
            session.mode = MaidSoulRuntimeMode.WAIT;
        }
        traceQueue(maid, "enqueue_proactive", job, session);
        scheduleProcess(maid, 0L);
    }

    public static String describeRuntimeState(EntityMaid maid) {
        RuntimeSession session = maid == null ? null : SESSIONS.get(maid.getUUID());
        if (session == null) {
            return "mode=STOP, queue=0, current=none";
        }
        synchronized (session) {
            String current = session.currentJob == null ? "none" : session.currentJob.type().name();
            long waitRemain = Math.max(0L, session.waitUntilMillis - System.currentTimeMillis());
            return "mode=" + session.mode
                    + ", queue=" + session.queue.size()
                    + ", current=" + current
                    + ", version=" + session.version
                    + ", wait_ms=" + waitRemain
                    + ", completed=" + session.completedTurns;
        }
    }

    public static boolean hasPendingOwnerInput(EntityMaid maid) {
        RuntimeSession session = maid == null ? null : SESSIONS.get(maid.getUUID());
        if (session == null) {
            return false;
        }
        synchronized (session) {
            return session.currentJob != null && session.currentJob.type() == MaidSoulRuntimeJobType.OWNER_CHAT
                    || session.queue.stream().anyMatch(job -> job.type() == MaidSoulRuntimeJobType.OWNER_CHAT);
        }
    }

    /**
     * 强事件打断后台主动事件。真实玩家输入仍通过版本号自然淘汰旧请求。
     */
    public static void interruptForEvent(EntityMaid maid, String eventType, String detail, EventPriority priority) {
        if (maid == null || !MaidSoulCommonConfig.CONVERSATION_INTERRUPT_ENABLED.get()) {
            return;
        }
        RuntimeSession session = sessionOf(maid);
        synchronized (session) {
            removeQueuedProactiveJobs(session.queue);
            session.waitUntilMillis = 0L;
            session.version++;
        }
        ConversationJournalService.appendRuntimeReference(maid, "runtime.interrupt", eventType + " | " + clean(detail));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.runtime.interrupt", eventType);
        scheduleProcess(maid, 0L);
    }

    public static void requestWait(EntityMaid maid, long millis, String reason) {
        requestControl(maid, MaidSoulTimingDecision.waitFor(reason, millis));
    }

    public static void requestNoReply(EntityMaid maid, String reason) {
        requestControl(maid, MaidSoulTimingDecision.noReply(reason));
    }

    public static void requestFinish(EntityMaid maid, String reason) {
        requestControl(maid, MaidSoulTimingDecision.finishNow(reason));
    }

    public static void requestContinue(EntityMaid maid, String reason) {
        requestControl(maid, MaidSoulTimingDecision.continueNow(reason));
    }

    private static void requestControl(EntityMaid maid, MaidSoulTimingDecision decision) {
        if (maid == null || decision == null) {
            return;
        }
        RuntimeSession session = sessionOf(maid);
        synchronized (session) {
            session.controlDecision = decision;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.runtime.control", decision.action() + " | " + decision.reason());
        scheduleProcess(maid, 0L);
    }

    private static void enqueueOwnerChat(MaidSoulRuntimeSite site, LLMCallback callback, EntityMaid maid) {
        RuntimeSession session = sessionOf(maid);
        String latestUserMessage = extractLatestUserMessage(callback.getMessages());
        MaidSoulTimingDecision timingDecision = evaluateOwnerTiming(session, latestUserMessage);
        if (timingDecision.action() == MaidSoulTimingAction.NO_REPLY) {
            silentlyCompleteOwnerCallback(maid, callback, "owner_no_reply");
            return;
        }
        if (timingDecision.action() == MaidSoulTimingAction.FINISH) {
            synchronized (session) {
                session.queue.clear();
                session.mode = MaidSoulRuntimeMode.STOP;
            }
            silentlyCompleteOwnerCallback(maid, callback, "owner_finish");
            return;
        }

        List<MaidSoulRuntimeJob> replaced;
        MaidSoulRuntimeJob job;
        long delayMillis = Math.max(0L, MaidSoulCommonConfig.CHAT_RUNTIME_DEBOUNCE_MILLIS.get());
        synchronized (session) {
            session.runtimeSite = site;
            session.version++;
            replaced = removeQueuedOwnerJobs(session.queue);
            ArrayList<String> collected = new ArrayList<>();
            for (MaidSoulRuntimeJob old : replaced) {
                collected.addAll(old.collectedOwnerMessages());
            }
            collected.add(latestUserMessage);
            job = MaidSoulRuntimeJob.ownerChat(
                    ++session.nextJobId,
                    session.version,
                    callback,
                    List.copyOf(callback.getMessages()),
                    latestUserMessage,
                    System.currentTimeMillis()
            ).withCollectedOwnerMessages(collected);
            session.queue.addFirst(job);
            session.mode = MaidSoulRuntimeMode.WAIT;
            session.waitUntilMillis = Math.max(session.waitUntilMillis, System.currentTimeMillis() + delayMillis);
        }
        for (MaidSoulRuntimeJob old : replaced) {
            silentlyCompleteOwnerCallback(maid, old.callback(), "owner_collected_by_newer_input");
        }
        traceQueue(maid, "enqueue_owner", job, session);
        scheduleProcess(maid, delayMillis);
    }

    private static void processQueue(EntityMaid maid) {
        RuntimeSession session = SESSIONS.get(maid.getUUID());
        if (session == null) {
            return;
        }
        MaidSoulRuntimeJob job;
        MaidSoulRuntimeSite site;
        synchronized (session) {
            if (session.currentJob != null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (session.waitUntilMillis > now) {
                scheduleProcess(maid, session.waitUntilMillis - now);
                return;
            }
            session.waitUntilMillis = 0L;
            job = session.queue.pollFirst();
            if (job == null) {
                session.mode = MaidSoulRuntimeMode.STOP;
                return;
            }
            session.currentJob = job;
            session.mode = MaidSoulRuntimeMode.RUNNING;
            site = session.runtimeSite;
        }
        if (job.type() == MaidSoulRuntimeJobType.OWNER_CHAT) {
            startOwnerChatJob(site, maid, session, job);
        } else if (job.type() == MaidSoulRuntimeJobType.PROACTIVE_EVENT) {
            startProactiveJob(maid, session, job);
        } else {
            finishCurrentJob(maid, session, "timeout_noop");
        }
    }

    private static void startOwnerChatJob(MaidSoulRuntimeSite site,
                                          EntityMaid maid,
                                          RuntimeSession session,
                                          MaidSoulRuntimeJob job) {
        CompletableFuture
                .supplyAsync(() -> runOwnerChatJob(maid, job), CHAT_EXECUTOR)
                .whenComplete((result, throwable) -> job.callback().runOnServerThread(() ->
                        completeOwnerChatJob(site, maid, session, job, result, throwable)));
    }

    /**
     * 玩家轮处理：先更新脑内状态，再用收集到的整轮玩家输入生成回复。
     * 注意：此处不立即写 journal，避免 prompt 里同时出现“历史里的最新玩家话”
     * 和“当前轮玩家话”两份重复文本。
     */
    private static OwnerChatResult runOwnerChatJob(EntityMaid maid, MaidSoulRuntimeJob job) {
        List<String> collected = cleanCollected(job.collectedOwnerMessages());
        for (String ownerMessage : collected) {
            MaidSoulCognitionService.observeOwnerMessage(maid, ownerMessage);
            MaidSoulEmotionService.observeOwnerMessage(maid, ownerMessage);
            ConversationMemoryService.observeOwnerMessage(maid, ownerMessage);
            MaidSoulUnderstandingService.observeOwnerMessage(maid, ownerMessage);
        }
        MaidSoulHeartflowChatService.ChatTurnResult heartflowResult = MaidSoulHeartflowChatService.chat(
                maid,
                job.messages(),
                collected
        );
        if (heartflowResult.waiting()) {
            return OwnerChatResult.waitFor(heartflowResult.reason(), heartflowResult.waitMillis());
        }
        if (heartflowResult.silent()) {
            return OwnerChatResult.silent(heartflowResult.reason());
        }
        return OwnerChatResult.reply(heartflowResult.reply(), collected);
    }

    private static void completeOwnerChatJob(MaidSoulRuntimeSite site,
                                             EntityMaid maid,
                                             RuntimeSession session,
                                             MaidSoulRuntimeJob job,
                                             OwnerChatResult result,
                                             Throwable throwable) {
        if (isStale(session, job)) {
            silentlyCompleteOwnerCallback(maid, job.callback(), "stale_owner_turn");
            finishCurrentJob(maid, session, "stale_owner_turn");
            return;
        }
        if (throwable != null) {
            job.callback().onFailure(dummyRequest(site), throwable, ErrorCode.REQUEST_RECEIVED_ERROR);
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.runtime.turn.error", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            finishCurrentJob(maid, session, "owner_turn_error");
            return;
        }
        if (result == null || result.silent()) {
            silentlyCompleteOwnerCallback(maid, job.callback(), result == null ? "silent" : result.reason());
            finishCurrentJob(maid, session, "owner_turn_silent");
            return;
        }
        if (result.waiting()) {
            synchronized (session) {
                session.waitUntilMillis = System.currentTimeMillis() + Math.max(100L, result.waitMillis());
                session.queue.addFirst(job);
            }
            finishCurrentJob(maid, session, "owner_turn_wait");
            return;
        }

        for (String ownerMessage : result.collectedOwnerMessages()) {
            ConversationJournalService.appendOwnerMessage(maid, ownerMessage);
        }
        String reply = result.reply().isBlank()
                ? MaidSoulCommonConfig.CONVERSATION_EMPTY_REPLY_FALLBACK.get()
                : result.reply();
        job.callback().getChatManager().addAssistantHistory(reply);
        MaidSoulSpeechService.queueSpeech(maid, reply, job.callback().getWaitingChatBubbleId());
        ConversationJournalService.appendMaidMessage(maid, reply);
        ConversationMemoryService.observeAssistantReply(maid, reply);
        ConversationReflectionService.maybeReflect(maid);
        MaidSoulStateRegistry.record(maid, "maidsoul.runtime.turn.reply", reply, EventPriority.P1);
        scheduleConversationFollowup(maid, session, job, reply);
        finishCurrentJob(maid, session, "owner_turn_done");
    }

    /**
     * 主回复后的同话题续话。
     *
     * <p>这就是“像人”的一部分：她不是每次都等主人再发一句才动，
     * 如果刚才的话题还有余味、她的上一句像是在等回应，主人又沉默了一会儿，
     * 就把一个 conversation.followup 重新投进同一个主动事件入口。</p>
     */
    private static void scheduleConversationFollowup(EntityMaid maid,
                                                     RuntimeSession session,
                                                     MaidSoulRuntimeJob ownerJob,
                                                     String assistantReply) {
        if (!MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_ENABLED.get()
                || MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN.get() <= 0
                || !shouldConsiderFollowup(maid, ownerJob.collectedOwnerMessages(), assistantReply)) {
            return;
        }
        long delayMillis = Math.max(0L, MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_DELAY_MILLIS.get());
        long ownerVersion = ownerJob.version();
        long ownerJobId = ownerJob.jobId();
        synchronized (session) {
            if (session.followupVersion != ownerVersion) {
                session.followupVersion = ownerVersion;
                session.followupsForVersion = 0;
            }
            if (session.followupsForVersion >= MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_MAX_PER_OWNER_TURN.get()) {
                return;
            }
            session.followupsForVersion++;
        }
        SCHEDULER.schedule(() -> {
            if (maid.getServer() == null) {
                return;
            }
            maid.getServer().execute(() -> fireConversationFollowupIfStillRelevant(
                    maid,
                    session,
                    ownerVersion,
                    ownerJobId,
                    ownerJob.collectedOwnerMessages(),
                    assistantReply,
                    FOLLOWUP_BUSY_RETRY_LIMIT
            ));
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void fireConversationFollowupIfStillRelevant(EntityMaid maid,
                                                                RuntimeSession session,
                                                                long ownerVersion,
                                                                long ownerJobId,
                                                                List<String> ownerMessages,
                                                                String assistantReply,
                                                                int retryLeft) {
        synchronized (session) {
            if (session.version != ownerVersion || hasQueuedOwnerInput(session)) {
                return;
            }
            if (session.currentJob != null) {
                rescheduleConversationFollowup(maid, session, ownerVersion, ownerJobId, ownerMessages, assistantReply, retryLeft);
                return;
            }
        }
        if (MaidSoulSpeechService.hasPendingSpeech(maid)) {
            rescheduleConversationFollowup(maid, session, ownerVersion, ownerJobId, ownerMessages, assistantReply, retryLeft);
            return;
        }
        if (MaidSoulEmotionService.hasActiveOwnerOffenseUnresolved(maid)) {
            return;
        }
        String ownerText = String.join(" / ", cleanCollected(ownerMessages));
        String detail = "same topic follow-up candidate after a quiet pause; owner=\"" + abbreviate(ownerText)
                + "\"; previous_reply=\"" + abbreviate(assistantReply)
                + "\"; source_job=" + ownerJobId
                + "; do not assume the owner is dazing, ignoring, or refusing to answer";
        MaidSoulCompanionService.triggerProactiveEvent(
                maid,
                "conversation.followup",
                detail,
                MaidSoulEmotionService.hasActiveUnresolvedTopic(maid) ? EventPriority.P1 : EventPriority.P2
        );
    }

    private static void rescheduleConversationFollowup(EntityMaid maid,
                                                       RuntimeSession session,
                                                       long ownerVersion,
                                                       long ownerJobId,
                                                       List<String> ownerMessages,
                                                       String assistantReply,
                                                       int retryLeft) {
        if (retryLeft <= 0) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.runtime.followup.skip", "busy_timeout");
            return;
        }
        SCHEDULER.schedule(() -> {
            if (maid.getServer() == null) {
                return;
            }
            maid.getServer().execute(() -> fireConversationFollowupIfStillRelevant(
                    maid,
                    session,
                    ownerVersion,
                    ownerJobId,
                    ownerMessages,
                    assistantReply,
                    retryLeft - 1
            ));
        }, FOLLOWUP_BUSY_RETRY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static boolean shouldConsiderFollowup(EntityMaid maid, List<String> ownerMessages, String assistantReply) {
        if (MaidSoulCommonConfig.FULL_SILENT_MODE_ENABLED.get()
                || MaidSoulEmotionService.hasActiveOwnerOffenseUnresolved(maid)) {
            return false;
        }
        String owner = String.join(" ", cleanCollected(ownerMessages));
        String reply = clean(assistantReply);
        if (owner.isBlank() || reply.isBlank()) {
            return false;
        }
        if (looksLikeMeaningfulChat(owner) && reply.length() >= 8) {
            return true;
        }
        return MaidSoulEmotionService.hasActiveUnresolvedTopic(maid)
                || endsWithQuestion(reply)
                || containsAny(owner, MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_TRIGGERS.get())
                || containsAny(reply, MaidSoulCommonConfig.CONVERSATION_FOLLOWUP_TRIGGERS.get());
    }

    private static boolean looksLikeMeaningfulChat(String ownerText) {
        String text = clean(ownerText);
        if (text.length() < 4) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "").toLowerCase();
        return !List.of("嗯嗯", "哦哦", "好吧", "好的", "行吧", "ok", "okay")
                .contains(compact);
    }

    private static void startProactiveJob(EntityMaid maid, RuntimeSession session, MaidSoulRuntimeJob job) {
        ConversationJournalService.appendEvent(maid, job.eventType(), job.eventDetail());
        CompletableFuture
                .supplyAsync(() -> runProactiveJob(maid, job), CHAT_EXECUTOR)
                .whenComplete((result, throwable) -> {
                    if (maid.getServer() != null) {
                        maid.getServer().execute(() -> completeProactiveJob(maid, session, job, result, throwable));
                    }
                });
    }

    /**
     * 主动事件不允许自己绕开主循环说话。这里仍在同一队列里执行：
     * 热事件优先用回复池；需要模型的主动搭话也只作为当前 runtime job 运行。
     */
    private static ProactiveResult runProactiveJob(EntityMaid maid, MaidSoulRuntimeJob job) {
        MaidSoulRuntimeRouterService.ProactivePath path = MaidSoulRuntimeRouterService.classifyProactivePath(job.eventType());
        if (path == MaidSoulRuntimeRouterService.ProactivePath.HOT_POOLED) {
            String pooled = MaidSoulEventLinePoolService.pickLine(maid, job.eventType(), job.eventDetail());
            if (!pooled.isBlank()) {
                return ProactiveResult.reply(pooled);
            }
        }
        if (MaidSoulEmotionService.hasActiveOwnerOffenseUnresolved(maid) && isCasualProactive(job.eventType())) {
            return ProactiveResult.silent("owner_offense_unresolved");
        }
        if (isColdProactivePaused(maid, job.eventType())) {
            return ProactiveResult.silent("proactive_cold_llm_backoff");
        }
        SimulationMaiBotRuntimeConfig runtimeConfig = SimulationMaiBotConfigLoader.loadFromDirectory(
                Path.of(MaidSoulCommonConfig.MAIBOT_CONFIG_DIR.get())
        );
        if (!runtimeConfig.available()) {
            return ProactiveResult.silent("config_unavailable");
        }
        MaidSoulTimingDecision gateDecision = ConversationLlmTimingGateService.decideProactive(
                maid,
                runtimeConfig,
                job.eventType(),
                job.eventDetail()
        );
        if (gateDecision.action() == MaidSoulTimingAction.WAIT) {
            return ProactiveResult.waitFor(gateDecision.reason(), gateDecision.waitMillis());
        }
        if (gateDecision.action() == MaidSoulTimingAction.NO_REPLY || gateDecision.action() == MaidSoulTimingAction.FINISH) {
            return ProactiveResult.silent("timing_gate_" + gateDecision.reason());
        }
        SimulationOpenAiChatClient client = new SimulationOpenAiChatClient(runtimeConfig);
        String systemPrompt = MaidSoulPromptService.buildProactiveSystemPrompt(maid, runtimeConfig);
        String userPrompt = MaidSoulPromptService.buildProactiveUserPrompt(
                maid,
                job.eventType(),
                job.eventDetail(),
                runtimeConfig,
                MaidSoulTopicCooldownService.tailRecentTopics(maid, 3)
        );
        MaidSoulStateRegistry.echoFullDebugToOwnerChat(
                maid,
                "proactive_reply_request",
                "event=" + job.eventType()
                        + "\ndetail=" + job.eventDetail()
                        + "\nsystem:\n" + systemPrompt
                        + "\n\nuser:\n" + userPrompt
        );
        String raw = client.completeText(
                runtimeConfig.replyTask(),
                List.of(
                        new SimulationOpenAiChatClient.ChatMessage("system", systemPrompt),
                        new SimulationOpenAiChatClient.ChatMessage("user", userPrompt)
                ),
                Math.min(PROACTIVE_MODEL_TIMEOUT_SECONDS, MaidSoulCommonConfig.CONVERSATION_MODEL_TIMEOUT_SECONDS.get()),
                1
        );
        MaidSoulStateRegistry.echoFullDebugToOwnerChat(maid, "proactive_reply_raw", raw);
        return ProactiveResult.reply(raw);
    }

    private static void completeProactiveJob(EntityMaid maid,
                                             RuntimeSession session,
                                             MaidSoulRuntimeJob job,
                                             ProactiveResult result,
                                             Throwable throwable) {
        if (isStale(session, job)) {
            finishCurrentJob(maid, session, "stale_proactive");
            return;
        }
        if (throwable != null) {
            if (isCasualProactive(job.eventType())) {
                PROACTIVE_COLD_LLM_PAUSE_UNTIL.put(maid.getUUID(), System.currentTimeMillis() + PROACTIVE_TIMEOUT_BACKOFF_MILLIS);
            }
            MaidSoulStateRegistry.record(maid, "maidsoul.runtime.proactive.error", throwable.getClass().getSimpleName() + ": " + throwable.getMessage(), EventPriority.P1);
            finishCurrentJob(maid, session, "proactive_error");
            return;
        }
        if (result != null && result.waiting()) {
            synchronized (session) {
                int waitCount = session.gateWaitsByJob.merge(job.jobId(), 1, Integer::sum);
                if (waitCount <= 2 && !hasQueuedOwnerInput(session)) {
                    session.waitUntilMillis = System.currentTimeMillis() + Math.max(1000L, result.waitMillis());
                    session.queue.addFirst(job);
                    finishCurrentJob(maid, session, "proactive_timing_wait:" + result.reason());
                    return;
                }
            }
            finishCurrentJob(maid, session, "proactive_timing_wait_limit");
            return;
        }
        String reply = result == null ? "" : MaidSoulChatSanitizerService.sanitizeModelOutput(result.reply());
        MaidSoulTimingDecision decision = evaluateProactiveTiming(maid, session, job.eventType(), reply);
        if (result == null || result.silent() || reply.isBlank() || decision.action() == MaidSoulTimingAction.NO_REPLY) {
            finishCurrentJob(maid, session, result == null ? "proactive_silent" : result.reason());
            return;
        }
        MaidSoulSpeechService.queueSpeech(maid, reply, -1L);
        maid.getAiChatManager().addAssistantHistory(reply);
        ConversationJournalService.appendMaidMessage(maid, reply);
        ConversationMemoryService.observeAssistantReply(maid, reply);
        MaidSoulTopicCooldownService.markSpoken(maid, job.eventType(), job.eventDetail(), reply);
        MaidSoulStateRegistry.record(maid, "maidsoul.runtime.proactive.reply", reply, job.priority());
        finishCurrentJob(maid, session, "proactive_done");
    }

    private static void handleLocalCommand(LLMCallback callback,
                                           EntityMaid maid,
                                           MaidSoulLocalCommandParserService.ParsedCommandPlan parsedPlan) {
        String latestOwnerMessage = extractLatestUserMessage(callback.getMessages());
        MaidSoulCognitionService.observeOwnerMessage(maid, latestOwnerMessage);
        MaidSoulEmotionService.observeOwnerMessage(maid, latestOwnerMessage);
        ConversationMemoryService.observeOwnerMessage(maid, latestOwnerMessage);
        MaidSoulUnderstandingService.observeOwnerMessage(maid, latestOwnerMessage);

        MaidSoulPlan plan = MaidSoulPlanBuilderService.buildOwnerCommandPlan(parsedPlan.objective(), parsedPlan.steps());
        String submitResult = MaidSoulPlanService.submitPlan(maid, plan);
        callback.getChatManager().addAssistantHistory(parsedPlan.acknowledgement());
        MaidSoulSpeechService.queueSpeech(maid, parsedPlan.acknowledgement(), callback.getWaitingChatBubbleId());
        ConversationJournalService.appendOwnerMessage(maid, latestOwnerMessage);
        ConversationJournalService.appendMaidMessage(maid, parsedPlan.acknowledgement());
        ConversationMemoryService.observeAssistantReply(maid, parsedPlan.acknowledgement());
        MaidSoulStateRegistry.record(maid, "maidsoul.runtime.local_plan", submitResult, EventPriority.P1);
    }

    private static MaidSoulTimingDecision evaluateOwnerTiming(RuntimeSession session, String latestUserMessage) {
        MaidSoulTimingDecision requested = pollRequestedDecision(session);
        if (requested != null) {
            return requested;
        }
        if (latestUserMessage == null || latestUserMessage.isBlank()) {
            return MaidSoulTimingDecision.noReply("empty_owner_message");
        }
        return MaidSoulTimingDecision.continueNow("owner_message");
    }

    private static MaidSoulTimingDecision evaluateProactiveTiming(EntityMaid maid,
                                                                  RuntimeSession session,
                                                                  String eventType,
                                                                  String reply) {
        MaidSoulTimingDecision requested = pollRequestedDecision(session);
        if (requested != null) {
            return requested;
        }
        if (reply == null || reply.isBlank()) {
            return MaidSoulTimingDecision.noReply("empty_proactive_reply");
        }
        if (hasQueuedOwnerInput(session)) {
            return MaidSoulTimingDecision.noReply("owner_input_pending");
        }
        if (MaidSoulSpeechService.hasPendingSpeech(maid) && !isCriticalProactiveEvent(eventType)) {
            return MaidSoulTimingDecision.noReply("speech_queue_busy");
        }
        return MaidSoulTimingDecision.continueNow("proactive_allowed");
    }

    private static boolean isCriticalProactiveEvent(String eventType) {
        String type = eventType == null ? "" : eventType;
        return type.startsWith("maid.attacked")
                || type.contains("task.failed")
                || type.contains("hostile")
                || type.contains("death");
    }

    private static boolean isCasualProactive(String eventType) {
        String type = eventType == null ? "" : eventType;
        return type.startsWith("maid.idle.") || type.startsWith("owner.view.") || type.startsWith("world.time_phase");
    }

    private static boolean isColdProactivePaused(EntityMaid maid, String eventType) {
        if (!isCasualProactive(eventType)) {
            return false;
        }
        long until = PROACTIVE_COLD_LLM_PAUSE_UNTIL.getOrDefault(maid.getUUID(), 0L);
        return until > System.currentTimeMillis();
    }

    private static boolean hasQueuedOwnerInput(RuntimeSession session) {
        return session.queue.stream().anyMatch(job -> job.type() == MaidSoulRuntimeJobType.OWNER_CHAT);
    }

    private static boolean isStale(RuntimeSession session, MaidSoulRuntimeJob job) {
        synchronized (session) {
            return job.version() != session.version || session.currentJob == null || session.currentJob.jobId() != job.jobId();
        }
    }

    private static void finishCurrentJob(EntityMaid maid, RuntimeSession session, String reason) {
        synchronized (session) {
            session.currentJob = null;
            if (session.gateWaitsByJob.size() > 128) {
                session.gateWaitsByJob.clear();
            }
            session.completedTurns++;
            session.mode = session.queue.isEmpty() ? MaidSoulRuntimeMode.STOP : MaidSoulRuntimeMode.WAIT;
        }
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.runtime.job.finish", reason);
        scheduleProcess(maid, 0L);
    }

    private static MaidSoulTimingDecision pollRequestedDecision(RuntimeSession session) {
        synchronized (session) {
            MaidSoulTimingDecision decision = session.controlDecision;
            session.controlDecision = null;
            return decision;
        }
    }

    private static List<MaidSoulRuntimeJob> removeQueuedOwnerJobs(Deque<MaidSoulRuntimeJob> queue) {
        ArrayList<MaidSoulRuntimeJob> removed = new ArrayList<>();
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            MaidSoulRuntimeJob job = iterator.next();
            if (job.type() == MaidSoulRuntimeJobType.OWNER_CHAT) {
                iterator.remove();
                removed.add(job);
            }
        }
        return removed;
    }

    private static void removeQueuedProactiveJobs(Deque<MaidSoulRuntimeJob> queue) {
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().type() == MaidSoulRuntimeJobType.PROACTIVE_EVENT) {
                iterator.remove();
            }
        }
    }

    private static List<String> cleanCollected(List<String> messages) {
        ArrayList<String> result = new ArrayList<>();
        if (messages != null) {
            for (String message : messages) {
                String cleaned = clean(message);
                if (!cleaned.isBlank()) {
                    result.add(cleaned);
                }
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static String extractLatestUserMessage(List<LLMMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            LLMMessage message = messages.get(index);
            if (message != null
                    && message.role() == Role.USER
                    && message.message() != null
                    && MaidSoulChatSanitizerService.isRealOwnerMessage(message.message())) {
                return MaidSoulChatSanitizerService.sanitizeLatestUserMessage(message.message());
            }
        }
        return "";
    }

    private static boolean containsAny(String text, List<? extends String> triggers) {
        if (text == null || text.isBlank() || triggers == null || triggers.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String trigger : triggers) {
            if (trigger != null && !trigger.isBlank() && normalized.contains(trigger.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithQuestion(String text) {
        String trimmed = clean(text);
        return trimmed.endsWith("?") || trimmed.endsWith("？");
    }

    private static String abbreviate(String text) {
        String cleaned = clean(text);
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64) + "...";
    }

    private static void silentlyCompleteOwnerCallback(EntityMaid maid, LLMCallback callback, String reason) {
        if (callback == null) {
            return;
        }
        callback.runOnServerThread(() -> maid.getChatBubbleManager().removeChatBubble(callback.getWaitingChatBubbleId()));
        MaidSoulStateRegistry.echoTraceToOwnerChat(maid, "maidsoul.runtime.callback.done", reason);
    }

    private static void scheduleProcess(EntityMaid maid, long delayMillis) {
        long safeDelay = Math.max(0L, delayMillis);
        SCHEDULER.schedule(() -> {
            if (maid.getServer() != null) {
                maid.getServer().execute(() -> processQueue(maid));
            }
        }, safeDelay, TimeUnit.MILLISECONDS);
    }

    private static void traceQueue(EntityMaid maid, String phase, MaidSoulRuntimeJob job, RuntimeSession session) {
        synchronized (session) {
            MaidSoulStateRegistry.echoTraceToOwnerChat(
                    maid,
                    "maidsoul.runtime.queue." + phase,
                    "job=" + job.jobId() + ", type=" + job.type() + ", queue=" + session.queue.size() + ", version=" + session.version
            );
        }
    }

    private static RuntimeSession sessionOf(EntityMaid maid) {
        return SESSIONS.computeIfAbsent(maid.getUUID(), id -> new RuntimeSession());
    }

    private static HttpRequest dummyRequest(MaidSoulRuntimeSite site) {
        String url = site == null ? "maidsoul://runtime" : site.url();
        return HttpRequest.newBuilder(URI.create(url)).GET().build();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final class RuntimeSession {
        private final Deque<MaidSoulRuntimeJob> queue = new ArrayDeque<>();
        private MaidSoulRuntimeMode mode = MaidSoulRuntimeMode.STOP;
        private MaidSoulRuntimeJob currentJob;
        private MaidSoulRuntimeSite runtimeSite;
        private MaidSoulTimingDecision controlDecision;
        private int version;
        private long nextJobId;
        private long completedTurns;
        private long waitUntilMillis;
        private long followupVersion = -1L;
        private int followupsForVersion;
        private final Map<Long, Integer> gateWaitsByJob = new ConcurrentHashMap<>();
    }

    private record OwnerChatResult(
            boolean silent,
            boolean waiting,
            String reason,
            long waitMillis,
            String reply,
            List<String> collectedOwnerMessages
    ) {
        private static OwnerChatResult silent(String reason) {
            return new OwnerChatResult(true, false, reason == null ? "" : reason, 0L, "", List.of());
        }

        private static OwnerChatResult waitFor(String reason, long waitMillis) {
            return new OwnerChatResult(false, true, reason == null ? "" : reason, Math.max(0L, waitMillis), "", List.of());
        }

        private static OwnerChatResult reply(String reply, List<String> collectedOwnerMessages) {
            return new OwnerChatResult(false, false, "reply", 0L, reply == null ? "" : reply, List.copyOf(collectedOwnerMessages));
        }
    }

    private record ProactiveResult(boolean silent, boolean waiting, String reason, long waitMillis, String reply) {
        private static ProactiveResult silent(String reason) {
            return new ProactiveResult(true, false, reason == null ? "" : reason, 0L, "");
        }

        private static ProactiveResult waitFor(String reason, long waitMillis) {
            return new ProactiveResult(false, true, reason == null ? "" : reason, Math.max(0L, waitMillis), "");
        }

        private static ProactiveResult reply(String reply) {
            return new ProactiveResult(false, false, "reply", 0L, reply == null ? "" : reply);
        }
    }
}
