package com.maidsoul.brain.forge.tlm.llm;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializableSite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SupportModelSelect;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MaidSoulRuntimeSite implements LLMSite, SupportModelSelect {
    public static final String API_TYPE = "maidsoul_runtime";

    private final String id;
    private final ResourceLocation icon;
    private final Map<String, String> headers;
    private final Map<String, String> models;
    private String url;
    private boolean enabled;

    public MaidSoulRuntimeSite(String id, ResourceLocation icon, String url, boolean enabled, Map<String, String> headers, Map<String, String> models) {
        this.id = id;
        this.icon = icon;
        this.url = url;
        this.enabled = enabled;
        this.headers = new LinkedHashMap<>(headers);
        this.models = new LinkedHashMap<>(models);
    }

    public MaidSoulRuntimeSite(String id, ResourceLocation icon, String url, boolean enabled, Map<String, String> headers, List<String> models) {
        Map<String, String> modelMap = models.stream()
                .collect(Collectors.toMap(model -> model, model -> model, (left, right) -> left, LinkedHashMap::new));
        this.id = id;
        this.icon = icon;
        this.url = url;
        this.enabled = enabled;
        this.headers = new LinkedHashMap<>(headers);
        this.models = new LinkedHashMap<>(modelMap);
    }

    @Override
    public String getApiType() {
        return API_TYPE;
    }

    @Override
    public LLMClient client() {
        return new MaidSoulRuntimeClient(this);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public ResourceLocation icon() {
        return icon;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public Map<String, String> headers() {
        return headers;
    }

    @Override
    public Map<String, String> models() {
        return models;
    }

    public static final class Serializer implements SerializableSite<MaidSoulRuntimeSite> {
        private static final Codec<Map<String, String>> MODELS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);
        private static final Codec<MaidSoulRuntimeSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf(ID).forGetter(MaidSoulRuntimeSite::id),
                ResourceLocation.CODEC.fieldOf(ICON).forGetter(MaidSoulRuntimeSite::icon),
                Codec.STRING.fieldOf(URL).forGetter(MaidSoulRuntimeSite::url),
                Codec.BOOL.fieldOf(ENABLED).forGetter(MaidSoulRuntimeSite::enabled),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf(HEADERS).forGetter(MaidSoulRuntimeSite::headers),
                MODELS_CODEC.fieldOf(MODELS).forGetter(MaidSoulRuntimeSite::models)
        ).apply(instance, MaidSoulRuntimeSite::new));

        @Override
        public Codec<MaidSoulRuntimeSite> codec() {
            return CODEC;
        }

        @Override
        public MaidSoulRuntimeSite defaultSite() {
            return new MaidSoulRuntimeSite(
                    API_TYPE,
                    SerializableSite.defaultIcon(API_TYPE),
                    "maidsoul://runtime",
                    true,
                    Map.of(),
                    List.of("maidsoul-runtime")
            );
        }
    }
}
