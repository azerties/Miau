package myau.module.modules.render;

import java.io.IOException;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.Render2DEvent;
import myau.mixin.IAccessorShaderGroup;
import myau.module.Module;
import myau.motionblur.MotionBlurShaderHook;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class MotionBlur extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final ResourceLocation MOTION_BLUR_SHADER =
      new ResourceLocation("shaders/post/myau_motion_blur.json");

  public final ModeProperty mode =
      new ModeProperty("mode", 0, new String[] {"PHOSPHOR", "MOULBERRY"});
  public final FloatProperty strength = new FloatProperty("strength", 5.0F, 1.0F, 9.0F);

  private Framebuffer blurBufferMain;
  private Framebuffer blurBufferInto;

  public MotionBlur() {
    super("MotionBlur", false);
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled()) {
      return;
    }
    if (mc.theWorld == null || mc.thePlayer == null || !OpenGlHelper.isFramebufferEnabled()) {
      this.unloadPhosphorShader();
      return;
    }
    if (this.mode.getValue() == 0) {
      this.doPhosphorBlur();
    } else {
      this.unloadPhosphorShader();
      this.doMoulberryBlur();
    }
  }

  @Override
  public void onEnabled() {
    if (this.mode.getValue() == 0) {
      this.loadPhosphorShader();
    }
  }

  @Override
  public void onDisabled() {
    this.unloadPhosphorShader();
  }

  @Override
  public void verifyValue(String string) {
    this.unloadPhosphorShader();
    if (this.isEnabled() && this.mode.getValue() == 0) {
      this.loadPhosphorShader();
    }
  }

  private void doPhosphorBlur() {
    if (!OpenGlHelper.shadersSupported) {
      return;
    }
    if (!this.isPhosphorShaderActive()) {
      this.loadPhosphorShader();
    } else {
      this.reloadPhosphorIntensity();
    }
  }

  private void loadPhosphorShader() {
    if (mc.theWorld == null
        || mc.thePlayer == null
        || !OpenGlHelper.shadersSupported
        || this.isPhosphorShaderActive()) {
      return;
    }
    try {
      ShaderGroup shaderGroup =
          new ShaderGroup(
              mc.getTextureManager(),
              mc.getResourceManager(),
              mc.getFramebuffer(),
              MOTION_BLUR_SHADER);
      shaderGroup.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
      ((MotionBlurShaderHook) mc.entityRenderer).myau$setMotionBlurShader(shaderGroup);
      this.reloadPhosphorIntensity();
    } catch (IOException | RuntimeException e) {
      e.printStackTrace();
      this.setEnabled(false);
    }
  }

  private void unloadPhosphorShader() {
    MotionBlurShaderHook hook = (MotionBlurShaderHook) mc.entityRenderer;
    ShaderGroup shaderGroup = hook.myau$getMotionBlurShader();
    if (shaderGroup != null) {
      shaderGroup.deleteShaderGroup();
      hook.myau$setMotionBlurShader(null);
    }
  }

  private void reloadPhosphorIntensity() {
    ShaderGroup shaderGroup = ((MotionBlurShaderHook) mc.entityRenderer).myau$getMotionBlurShader();
    if (shaderGroup == null) {
      return;
    }
    try {
      List<Shader> shaders = ((IAccessorShaderGroup) shaderGroup).myau$getListShaders();
      for (Shader shader : shaders) {
        ShaderUniform weight = shader.getShaderManager().getShaderUniform("Weight");
        if (weight != null) {
          weight.set(
              Math.max(Math.min(1.0F - (this.strength.getValue() / 10.0F) + 0.1F, 1.0F), 0.1F));
        }
      }
    } catch (RuntimeException ignored) {
    }
  }

  private boolean isPhosphorShaderActive() {
    return OpenGlHelper.shadersSupported
        && ((MotionBlurShaderHook) mc.entityRenderer).myau$getMotionBlurShader() != null;
  }

  private void doMoulberryBlur() {
    int width = mc.getFramebuffer().framebufferWidth;
    int height = mc.getFramebuffer().framebufferHeight;
    GlStateManager.matrixMode(GL11.GL_PROJECTION);
    GlStateManager.pushMatrix();
    GlStateManager.loadIdentity();
    GlStateManager.ortho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
    GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    GlStateManager.pushMatrix();
    GlStateManager.loadIdentity();
    GlStateManager.translate(0.0F, 0.0F, -2000.0F);

    this.blurBufferMain = checkFramebufferSizes(this.blurBufferMain, width, height);
    this.blurBufferInto = checkFramebufferSizes(this.blurBufferInto, width, height);
    this.blurBufferInto.framebufferClear();
    this.blurBufferInto.bindFramebuffer(true);

    OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 1);
    GlStateManager.disableLighting();
    GlStateManager.disableFog();
    GlStateManager.disableBlend();
    mc.getFramebuffer().bindFramebufferTexture();
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    drawTexturedRectNoBlend(0.0F, 0.0F, width, height, 0.0F, 1.0F, 0.0F, 1.0F, GL11.GL_NEAREST);

    GlStateManager.enableBlend();
    this.blurBufferMain.bindFramebufferTexture();
    GlStateManager.color(1.0F, 1.0F, 1.0F, getMoulberryAlpha());
    drawTexturedRectNoBlend(0.0F, 0.0F, width, height, 0.0F, 1.0F, 1.0F, 0.0F, GL11.GL_NEAREST);

    mc.getFramebuffer().bindFramebuffer(true);
    this.blurBufferInto.bindFramebufferTexture();
    GlStateManager.color(1.0F, 1.0F, 1.0F, getMoulberryAlpha() + 1.0F);
    GlStateManager.enableBlend();
    OpenGlHelper.glBlendFunc(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, GL11.GL_ONE_MINUS_SRC_ALPHA);
    drawTexturedRectNoBlend(0.0F, 0.0F, width, height, 0.0F, 1.0F, 0.0F, 1.0F, GL11.GL_NEAREST);

    Framebuffer swap = this.blurBufferMain;
    this.blurBufferMain = this.blurBufferInto;
    this.blurBufferInto = swap;

    GlStateManager.matrixMode(GL11.GL_PROJECTION);
    GlStateManager.popMatrix();
    GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    GlStateManager.popMatrix();
  }

  private float getMoulberryAlpha() {
    return Math.min(0.1F + (this.strength.getValue() * 0.1F), 0.9F);
  }

  private static Framebuffer checkFramebufferSizes(Framebuffer framebuffer, int width, int height) {
    if (framebuffer == null
        || framebuffer.framebufferWidth != width
        || framebuffer.framebufferHeight != height) {
      if (framebuffer == null) {
        framebuffer = new Framebuffer(width, height, true);
      } else {
        framebuffer.createBindFramebuffer(width, height);
      }
      framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
    }
    return framebuffer;
  }

  private static void drawTexturedRectNoBlend(
      float x,
      float y,
      float width,
      float height,
      float uMin,
      float uMax,
      float vMin,
      float vMax,
      int filter) {
    GlStateManager.enableTexture2D();
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
    Tessellator tessellator = Tessellator.getInstance();
    WorldRenderer worldRenderer = tessellator.getWorldRenderer();
    worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
    worldRenderer.pos(x, y + height, 0.0D).tex(uMin, vMax).endVertex();
    worldRenderer.pos(x + width, y + height, 0.0D).tex(uMax, vMax).endVertex();
    worldRenderer.pos(x + width, y, 0.0D).tex(uMax, vMin).endVertex();
    worldRenderer.pos(x, y, 0.0D).tex(uMin, vMin).endVertex();
    tessellator.draw();
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
  }
}
