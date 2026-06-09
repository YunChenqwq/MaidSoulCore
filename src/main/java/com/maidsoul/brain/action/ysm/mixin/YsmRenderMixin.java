package com.maidsoul.brain.action.ysm.mixin;

import com.maidsoul.brain.action.ysm.YsmBoneUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM 渲染 Mixin — 挂在 YSM 模型渲染方法上，每帧覆写骨骼旋转。
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.oOOooOooO000oo0oooo0oo0o", remap = false)
public abstract class YsmRenderMixin {

    @Inject(
            method = "O0OOOoOooOO0OO0o00OoO0O0"
                    + "(Lcom/elfmcys/yesstevemodel/OoOOoOooOo000OO0O00oOo00;"
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/elfmcys/yesstevemodel/oOOooOooO000oo0oooo0oo0o;"
                           + "O0OOOoOooOO0OO0o00OoO0O0"
                           + "(Lcom/elfmcys/yesstevemodel/O0oo0Oo0o00OOO0oOOo0OoOo;"
                           + "Lcom/elfmcys/yesstevemodel/OOO0oOOo0O0000oO00ooooO0;"
                           + "FLcom/mojang/blaze3d/vertex/PoseStack;"
                           + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                           + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                           + "IIFFFF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void maidsoul$onYsmRender(@Coerce Object wrapper,
                                       ResourceLocation texture,
                                       float limbSwing,
                                       float partialTicks,
                                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                                       MultiBufferSource bufferSource,
                                       int packedLight,
                                       CallbackInfo ci) {
        YsmBoneUtil.applyIfNeeded(wrapper);
    }
}
