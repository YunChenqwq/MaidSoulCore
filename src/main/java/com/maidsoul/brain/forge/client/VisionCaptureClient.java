package com.maidsoul.brain.forge.client;

import com.maidsoul.brain.forge.MaidSoulCoreForgeMod;
import com.maidsoul.brain.forge.config.ForgeBrainConfigInstaller;
import com.maidsoul.brain.forge.network.ModNetwork;
import com.maidsoul.brain.forge.network.VisionCaptureRequestPacket;
import com.maidsoul.brain.forge.network.VisionCaptureResultPacket;
import com.maidsoul.brain.forge.network.VisionProxyImagePacket;
import com.maidsoul.brain.vision.VisionConfig;
import com.maidsoul.brain.vision.VisionSummaryClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端截图与视觉摘要入口。
 *
 * <p>默认 client_direct 模式会在客户端本地请求视觉模型，只把摘要文本发给服务端；
 * 这样 MC 服务器不会承担截图图片的上行流量。server_proxy 模式仅作为备用。</p>
 */
public final class VisionCaptureClient {
    private VisionCaptureClient() {
    }

    public static void captureAndSend(VisionCaptureRequestPacket request) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getMainRenderTarget() == null) {
            return;
        }
        try (NativeImage nativeImage = new NativeImage(
                minecraft.getMainRenderTarget().width,
                minecraft.getMainRenderTarget().height,
                false
        )) {
            minecraft.getMainRenderTarget().bindRead();
            nativeImage.downloadTexture(0, true);
            nativeImage.flipY();
            BufferedImage image = toBufferedImage(nativeImage);
            BufferedImage scaled = scale(image, Math.max(64, request.maxWidth()), Math.max(64, request.maxHeight()));
            byte[] jpeg = encodeJpeg(scaled, clampQuality(request.jpegQuality()));
            String base64 = Base64.getEncoder().encodeToString(jpeg);
            VisionConfig config = VisionConfig.load(ForgeBrainConfigInstaller.configRoot());
            if (config.clientDirectMode()) {
                CompletableFuture.runAsync(() -> sendClientDirectSummary(request, config, base64));
            } else {
                ModNetwork.CHANNEL.sendToServer(new VisionProxyImagePacket(
                        request.maidUuid(),
                        request.reason(),
                        request.sceneHint(),
                        "jpeg",
                        base64
                ));
            }
        } catch (RuntimeException e) {
            MaidSoulCoreForgeMod.LOGGER.warn("MaidSoulCore client vision capture failed", e);
        } catch (Exception e) {
            MaidSoulCoreForgeMod.LOGGER.warn("MaidSoulCore client vision capture failed", e);
        }
    }

    private static void sendClientDirectSummary(VisionCaptureRequestPacket request, VisionConfig config, String imageBase64) {
        try {
            String summary = new VisionSummaryClient(config).summarize("jpeg", imageBase64, request.sceneHint());
            ModNetwork.CHANNEL.sendToServer(new VisionCaptureResultPacket(
                    request.maidUuid(),
                    request.reason(),
                    request.sceneHint(),
                    summary
            ));
        } catch (RuntimeException e) {
            MaidSoulCoreForgeMod.LOGGER.warn("MaidSoulCore client vision summary failed", e);
            ModNetwork.CHANNEL.sendToServer(new VisionCaptureResultPacket(
                    request.maidUuid(),
                    request.reason(),
                    request.sceneHint(),
                    "[视觉摘要失败] " + clip(e.getMessage(), 160)
            ));
        }
    }

    private static BufferedImage toBufferedImage(NativeImage nativeImage) {
        BufferedImage image = new BufferedImage(nativeImage.getWidth(), nativeImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < nativeImage.getHeight(); y++) {
            for (int x = 0; x < nativeImage.getWidth(); x++) {
                int abgr = nativeImage.getPixelRGBA(x, y);
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static BufferedImage scale(BufferedImage source, int maxWidth, int maxHeight) {
        double ratio = Math.min(maxWidth / (double) source.getWidth(), maxHeight / (double) source.getHeight());
        if (ratio >= 1.0D) {
            return source;
        }
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前运行环境没有 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static float clampQuality(float quality) {
        if (Float.isNaN(quality)) {
            return 0.72F;
        }
        return Math.max(0.05F, Math.min(1.0F, quality));
    }

    private static String clip(String text, int max) {
        String clean = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }
}
