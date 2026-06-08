package com.maidsoulcore.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ITool;
import com.github.tartaricacid.touhoulittlemaid.ai.agent.tool.ToolRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.FunctionTool;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.ObjectParameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.function.schema.parameter.Parameter;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.Role;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAIClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAISite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ChatCompletion;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.request.ResponseFormat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.maidsoulcore.sim.SimulationResolvedModel;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 命令型聊天专用的 TLM tool loop client。
 * <p>
 * 这个 client 仍然复用 TLM 原生的：
 * - Tool schema
 * - Tool result 回灌
 * - LLMCallback 的多轮 tool loop
 * 但模型来源改为 MaidSoulCore 当前加载的 MaiBot tool_use 槽位。
 */
public final class MaidSoulToolLoopClient extends LLMOpenAIClient implements LLMClient {
    private final SimulationResolvedModel resolvedModel;

    public MaidSoulToolLoopClient(LLMOpenAISite site, SimulationResolvedModel resolvedModel) {
        super(com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite.LLM_HTTP_CLIENT, site);
        this.resolvedModel = resolvedModel;
    }

    @Override
    public void chat(com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback callback) {
        EntityMaid maid = callback.getMaid();
        URI url = URI.create(this.site.url());
        String apiKey = this.site.secretKey();
        String model = resolvedModel.modelConfig().modelIdentifier();
        boolean isReasoningModel = this.site.isReasoningModel(model);

        ChatCompletion chatCompletion = ChatCompletion.create()
                .model(model)
                .setResponseFormat(ResponseFormat.text());
        chatCompletion = this.extraArgs(chatCompletion);

        for (LLMMessage message : callback.getMessages()) {
            if (message.role() == Role.USER) {
                chatCompletion.userChat(message.message());
            } else if (message.role() == Role.ASSISTANT) {
                if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                    chatCompletion.assistantChat(message.message());
                } else {
                    chatCompletion.assistantChat(message.message(), message.toolCalls());
                }
            } else if (message.role() == Role.SYSTEM) {
                if (isReasoningModel) {
                    chatCompletion.developerChat(message.message());
                } else {
                    chatCompletion.systemChat(message.message());
                }
            } else if (message.role() == Role.TOOL) {
                chatCompletion.toolChat(message.message(), message.toolCallId());
            }
        }

        if (callback.needAddTools) {
            for (var entry : ToolRegister.getAllTools().entrySet()) {
                String toolId = entry.getKey();
                ITool<?> tool = entry.getValue();
                if (tool == null || !tool.trigger(maid, chatCompletion)) {
                    continue;
                }
                ObjectParameter root = ObjectParameter.create();
                Parameter parameter = tool.parameters(root, maid);
                chatCompletion.addTool(FunctionTool.create()
                        .setName(toolId)
                        .setDescription(tool.summary(maid))
                        .setParameters(parameter)
                        .build());
            }
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(chatCompletion)))
                .timeout(MAX_TIMEOUT)
                .uri(url);
        this.site.headers().forEach(builder::header);

        HttpRequest request = builder.build();
        this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> handle(callback, response, throwable, request));
    }
}
