package com.yunchen.maidsoulcore.forge.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.yunchen.maidsoulcore.forge.tlm.llm.MaidSoulRuntimeSite;

@LittleMaidExtension
public final class MaidSoulLittleMaidExtension implements ILittleMaid {
    @Override
    public void registerAIChatSerializer(SerializerRegister register) {
        register.register(ServiceType.LLM, MaidSoulRuntimeSite.API_TYPE, new MaidSoulRuntimeSite.Serializer());
    }
}
