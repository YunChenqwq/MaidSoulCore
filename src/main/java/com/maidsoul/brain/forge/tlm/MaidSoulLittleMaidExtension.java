package com.maidsoul.brain.forge.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.tlm.llm.MaidSoulRuntimeSite;

/**
 * 车万女仆原生扩展入口。
 *
 * <p>这里不放任何人格、记忆或情绪逻辑，只把 MaidSoulCore 注册成 TLM 的一个
 * LLM 站点。真正的对话和记忆运行仍然在 com.maidsoul.brain 核心包里。</p>
 */
@LittleMaidExtension
public final class MaidSoulLittleMaidExtension implements ILittleMaid {
    @Override
    public void registerAIChatSerializer(SerializerRegister register) {
        register.register(ServiceType.LLM, MaidSoulRuntimeSite.API_TYPE, new MaidSoulRuntimeSite.Serializer());
        MaidSoulCoreForgeMod.LOGGER.info("Registered MaidSoulCore TLM LLM site: {}", MaidSoulRuntimeSite.API_TYPE);
    }
}
