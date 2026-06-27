package myau.util.shader;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;
import myau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

public class KawaseBlur {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public static ShaderUtils kawaseDown = new ShaderUtils("kawaseDown");
  public static ShaderUtils kawaseUp = new ShaderUtils("kawaseUp");
  public static Framebuffer framebuffer = new Framebuffer(1, 1, false);
  private static int currentIterations;

  private static final List<Framebuffer> framebufferList = new ArrayList<>();

  private static void initFrameBuffers(float iterations) {
    for (Framebuffer fb : framebufferList) {
      fb.deleteFramebuffer();
    }
    framebufferList.clear();

    framebufferList.add(framebuffer = RenderUtil.createFrameBuffer(null));

    for (int i = 1; i <= iterations; i++) {
      Framebuffer currentBuffer =
          new Framebuffer(
              (int) (mc.displayWidth / Math.pow(3, i)),
              (int) (mc.displayHeight / Math.pow(3, i)),
              false);
      currentBuffer.setFramebufferFilter(GL_LINEAR);
      org.lwjgl.opengl.GL11.glBindTexture(
          org.lwjgl.opengl.GL11.GL_TEXTURE_2D, currentBuffer.framebufferTexture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
      org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);

      framebufferList.add(currentBuffer);
    }
  }

  public static void renderBlur(int stencilFrameBufferTexture, int iterations, float offset) {
    if (currentIterations != iterations
        || framebuffer.framebufferWidth != mc.displayWidth
        || framebuffer.framebufferHeight != mc.displayHeight) {
      initFrameBuffers(iterations);
      currentIterations = iterations;
    }

    renderFBO(framebufferList.get(1), mc.getFramebuffer().framebufferTexture, kawaseDown, offset);

    for (int i = 1; i < iterations; i++) {
      renderFBO(
          framebufferList.get(i + 1),
          framebufferList.get(i).framebufferTexture,
          kawaseDown,
          offset);
    }

    for (int i = iterations; i > 1; i--) {
      renderFBO(
          framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, offset);
    }

    Framebuffer lastBuffer = framebufferList.get(0);
    lastBuffer.framebufferClear();
    lastBuffer.bindFramebuffer(false);

    kawaseUp.init();
    kawaseUp.setUniformf("offset", offset, offset);
    kawaseUp.setUniformi("inTexture", 0);
    kawaseUp.setUniformi("check", 1);
    kawaseUp.setUniformi("textureToCheck", 16);
    kawaseUp.setUniformf(
        "halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
    kawaseUp.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
    GL13.glActiveTexture(GL13.GL_TEXTURE16);
    org.lwjgl.opengl.GL11.glBindTexture(
        org.lwjgl.opengl.GL11.GL_TEXTURE_2D, stencilFrameBufferTexture);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    org.lwjgl.opengl.GL11.glBindTexture(
        org.lwjgl.opengl.GL11.GL_TEXTURE_2D, framebufferList.get(1).framebufferTexture);
    ShaderUtils.drawQuads();
    kawaseUp.unload();

    mc.getFramebuffer().bindFramebuffer(false);
    org.lwjgl.opengl.GL11.glBindTexture(
        org.lwjgl.opengl.GL11.GL_TEXTURE_2D, framebufferList.get(0).framebufferTexture);
    RenderUtil.setAlphaLimit(0);
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    ShaderUtils.drawQuads();
    org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0);
    GlStateManager.disableBlend();

    GlStateManager.alphaFunc(GL_GREATER, 0.1F);
  }

  private static void renderFBO(
      Framebuffer fb, int framebufferTexture, ShaderUtils shader, float offset) {
    fb.framebufferClear();
    fb.bindFramebuffer(false);
    shader.init();
    org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, framebufferTexture);
    shader.setUniformf("offset", offset, offset);
    shader.setUniformi("inTexture", 0);
    shader.setUniformi("check", 0);
    shader.setUniformf("halfpixel", 1.0f / fb.framebufferWidth, 1.0f / fb.framebufferHeight);
    shader.setUniformf("iResolution", fb.framebufferWidth, fb.framebufferHeight);
    ShaderUtils.drawQuads();
    shader.unload();
  }
}
