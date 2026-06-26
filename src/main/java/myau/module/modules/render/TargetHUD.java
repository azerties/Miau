package myau.module.modules.render;

import java.awt.*;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.Render2DEvent;
import myau.event.impl.Render3DEvent;
import myau.module.Module;
import myau.module.modules.combat.KillAura;
import myau.property.properties.*;
import myau.util.render.ColorUtil;
import myau.util.render.RenderUtil;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedUtils;
import myau.util.time.TimerUtil;
import myau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class TargetHUD extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private EntityLivingBase target;
  private long lastAliveMS;
  private double lastHealth;
  private float lastHealthBar;
  private TimerUtil fadeTimer;
  private TimerUtil healthBarTimer;
  public EntityLivingBase renderEntity;

  /** Tracks whether drag.position has been converted from offset → absolute coordinates */
  private boolean positionInitialized = false;

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Modern", "Legacy"});
  public final BooleanProperty showStatus = new BooleanProperty("Show win or loss", true);
  public final BooleanProperty healthColor = new BooleanProperty("Traditional health color", false);
  public final BooleanProperty renderEsp = new BooleanProperty("Render ESP", true);
  public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
  public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
  public final DragProperty drag = new DragProperty("Position", new Vector2d(70, 30));

  public TargetHUD() {
    super("TargetHUD", false, true);
  }

  @Override
  public void onDisabled() {
    reset();
  }

  @Override
  public void onEnabled() {
    // Reset position flag so we re-initialize on next render
    positionInitialized = false;
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      reset();
      return;
    }

    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    if (killAura == null) return;

    EntityLivingBase killTarget = killAura.getTarget();
    if (killTarget != null && killAura.isEnabled()) {
      target = killTarget;
      lastAliveMS = System.currentTimeMillis();
      fadeTimer = null;
    } else if (target != null) {
      if (System.currentTimeMillis() - lastAliveMS >= 400 && fadeTimer == null) {
        fadeTimer = new TimerUtil();
        fadeTimer.reset();
      }
    } else {
      return;
    }

    String playerInfo = target.getDisplayName().getFormattedText();
    double health = target.getHealth() / target.getMaxHealth();
    if (target.isDead) {
      health = 0;
    }

    if (health != lastHealth) {
      healthBarTimer = new TimerUtil();
      healthBarTimer.reset();
    }
    lastHealth = health;

    String healthStr =
        " "
            + (target.getHealth() == (int) target.getHealth()
                ? String.valueOf((int) target.getHealth())
                : String.format("%.1f", target.getHealth()));
    playerInfo += healthStr;

    drawTargetHUD(playerInfo, health);
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!renderEsp.getValue() || !this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    if (killAura == null) return;

    // Skip ESP rendering when KillAura's showTarget is not NONE,
    // because KillAura already renders its own target visualization
    // (SIGMA_RING=1, ABOVE_BOX=2, FULL_BOX=3)
    if (killAura.showTarget.getValue() != 0) return;

    EntityLivingBase espTarget = killAura.getTarget();
    if (espTarget != null && killAura.isEnabled()) {
      RenderUtil.renderEntity(espTarget, 2, 0.0, 0.0, -1, false);
    } else if (renderEntity != null) {
      RenderUtil.renderEntity(renderEntity, 2, 0.0, 0.0, -1, false);
    }
  }

  private void drawTargetHUD(String string, double health) {
    if (showStatus.getValue()) {
      float playerTotalHealth = mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount();
      float playerMaxHealth = mc.thePlayer.getMaxHealth();
      boolean shouldWin = health <= (double) (playerTotalHealth / playerMaxHealth);
      string += shouldWin ? " §aW" : " §cL";
    }

    ScaledResolution scaledResolution = new ScaledResolution(mc);
    int padding = 8;
    int targetStrWithPadding = mc.fontRendererObj.getStringWidth(string) + padding;

    // Box dimensions at unscaled size
    float boxWidth = targetStrWithPadding + padding * 2;
    float boxHeight = mc.fontRendererObj.FONT_HEIGHT + 12 + padding * 2;

    // --- Initialization: Convert offset → absolute screen coordinates ---
    // The default drag.position (70, 30) stores an offset from screen center.
    // We convert it to absolute (top-left corner) once.
    // After conversion, the absolute coordinates (e.g. ~824, ~525 at 1080p)
    // are saved to config. On next launch, position.x will be > scaledWidth/3
    // so this check will correctly skip conversion.
    if (!positionInitialized) {
      float centerX = scaledResolution.getScaledWidth() / 2f;
      float centerY = scaledResolution.getScaledHeight() / 2f;

      // Detect if position is still the relative offset (small value)
      // vs already-converted absolute coordinates (large value).
      // At any reasonable resolution, absolute X is always > scaledWidth/3.
      if (this.drag.position.x < scaledResolution.getScaledWidth() / 3f) {
        // position.x is still the offset — convert to absolute
        double absX = centerX - boxWidth / 2f + this.drag.position.x;
        double absY = centerY + 15f + this.drag.position.y - padding;
        this.drag.position.x = absX;
        this.drag.position.y = absY;
        this.drag.targetPosition.x = absX;
        this.drag.targetPosition.y = absY;
      }
      // If position is already absolute (from config restore), keep it as-is.
      positionInitialized = true;
    }

    // Absolute screen top-left corner (unscaled)
    float absX = (float) this.drag.position.x;
    float absY = (float) this.drag.position.y;

    int alpha =
        (fadeTimer == null)
            ? 255
            : Math.max(0, 255 - (int) (fadeTimer.getElapsedTime() * 255 / 400));

    // Update drag scale for DragManager hit-testing
    this.drag.scale.x = boxWidth;
    this.drag.scale.y = boxHeight;

    if (alpha > 0) {
      int maxAlphaOutline = Math.min(alpha, 110);
      int maxAlphaBackground = Math.min(alpha, 210);

      float sc = this.scale.getValue();
      float invSc = 1.0f / sc;

      GlStateManager.pushMatrix();
      if (sc != 1.0F) {
        GL11.glScalef(sc, sc, 1.0F);
      }

      // Convert absolute coords to GL-scaled space (GL will multiply back by sc)
      float n6 = absX * invSc;
      float n7 = absY * invSc;
      float n8 = (absX + boxWidth) * invSc;
      float n9 = (absY + boxHeight - 13f) * invSc;

      // Text position inside the box (in GL-scaled space)
      float x = n6 + padding * invSc;
      float y = n7 + padding * invSc;

      switch (mode.getValue()) {
        case 0: // Modern - bloom + blur
          float bloomRadius = (fadeTimer == null) ? 2f : (2f * alpha / 255f);
          float blurRadius = (fadeTimer == null) ? 3f : (3f * alpha / 255f);
          BlurUtils.prepareBloom();
          RoundedUtils.drawRound(
              n6,
              n7,
              Math.abs(n6 - n8),
              Math.abs(n7 - (n9 + 13f * invSc)),
              8.0f,
              true,
              new Color(0, 0, 0, maxAlphaBackground));
          BlurUtils.bloomEnd(3, bloomRadius);
          BlurUtils.prepareBlur();
          RoundedUtils.drawRound(
              n6,
              n7,
              Math.abs(n6 - n8),
              Math.abs(n7 - (n9 + 13f * invSc)),
              8.0f,
              true,
              new Color(mergeAlpha(Color.black.getRGB(), maxAlphaOutline)));
          BlurUtils.blurEnd(2, blurRadius);
          break;
        case 1: // Legacy - gradient outline
          drawRoundedGradientOutlinedRectangle(
              n6,
              n7,
              n8,
              n9 + 13f * invSc,
              10.0f,
              mergeAlpha(Color.black.getRGB(), maxAlphaOutline),
              mergeAlpha(new Color(0x70CEFF).getRGB(), alpha),
              mergeAlpha(new Color(0x4D8DFF).getRGB(), alpha));
          break;
      }

      float n13 = n6 + 6f * invSc;
      float n14 = n8 - 6f * invSc;
      float n15 = n9;

      // Bar background
      drawRoundedRectangle(
          n13, n15, n14, n15 + 5f * invSc, 4.0f, mergeAlpha(Color.black.getRGB(), maxAlphaOutline));

      int mergedGradientLeft = mergeAlpha(new Color(0x70CEFF).getRGB(), maxAlphaBackground);
      int mergedGradientRight = mergeAlpha(new Color(0x4D8DFF).getRGB(), maxAlphaBackground);

      float healthBar = n14 + (n13 - n14) * (float) (1 - health);

      // Health bar smooth animation
      boolean smoothBack = false;
      if (healthBar != lastHealthBar
          && Math.abs(lastHealthBar - n13) >= 3f * invSc
          && healthBarTimer != null) {
        float diff = lastHealthBar - healthBar;
        if (diff > 0) {
          lastHealthBar = lastHealthBar - getTimedProgress(diff, 400);
        } else {
          smoothBack = true;
          lastHealthBar = lastHealthBar + getTimedProgress(-diff, 400);
        }
      } else {
        lastHealthBar = healthBar;
      }

      if (healthColor.getValue()) {
        Color healthBlend = ColorUtil.getHealthBlend((float) health);
        mergedGradientLeft =
            mergedGradientRight = mergeAlpha(healthBlend.getRGB(), maxAlphaBackground);
      }

      if (lastHealthBar > n14) {
        lastHealthBar = n14;
      }

      switch (mode.getValue()) {
        case 0: // Modern
          drawRoundedRectangle(
              n13,
              n15,
              lastHealthBar,
              n15 + 5f * invSc,
              4.0f,
              mergeAlpha(
                  ColorUtil.darker(new Color(mergedGradientRight), 0.25f).getRGB(),
                  maxAlphaBackground));
          drawRoundedGradientRect(
              n13,
              n15,
              smoothBack ? lastHealthBar : healthBar,
              n15 + 5f * invSc,
              4.0f,
              mergedGradientLeft,
              mergedGradientLeft,
              mergedGradientRight,
              mergedGradientRight);
          break;
        case 1: // Legacy
          drawRoundedGradientRect(
              n13,
              n15,
              lastHealthBar,
              n15 + 5f * invSc,
              4.0f,
              mergedGradientLeft,
              mergedGradientLeft,
              mergedGradientRight,
              mergedGradientRight);
          break;
      }

      GL11.glPushMatrix();
      GL11.glEnable(GL11.GL_BLEND);
      int textColor =
          (new Color(220, 220, 220, 255).getRGB() & 0xFFFFFF)
              | (MathHelper.clamp_int(alpha + 15, 0, 255) << 24);
      mc.fontRendererObj.drawString(string, x, y, textColor, shadow.getValue());
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glPopMatrix();

      GlStateManager.popMatrix();
    } else {
      target = null;
      healthBarTimer = null;
    }
  }

  private float getTimedProgress(float total, long duration) {
    if (healthBarTimer == null) return 0;
    long elapsed = healthBarTimer.getElapsedTime();
    float progress = Math.min(1.0f, (float) elapsed / (float) duration);
    return total * progress;
  }

  private void reset() {
    fadeTimer = null;
    target = null;
    healthBarTimer = null;
    renderEntity = null;
  }

  private int mergeAlpha(int rgb, int alpha) {
    return (MathHelper.clamp_int(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
  }

  private void drawRoundedGradientOutlinedRectangle(
      float left,
      float top,
      float right,
      float bottom,
      float radius,
      int backgroundColor,
      int firstColor,
      int secondColor) {
    drawRoundedGradientRect(
        left, top, right, bottom, radius, firstColor, firstColor, secondColor, secondColor);
    drawRoundedRectangle(
        left + 1.0F,
        top + 1.0F,
        right - 1.0F,
        bottom - 1.0F,
        Math.max(0.0F, radius - 1.0F),
        backgroundColor);
  }

  private void drawRoundedRectangle(
      float left, float top, float right, float bottom, float radius, int color) {
    drawRoundedGradientRect(left, top, right, bottom, radius, color, color, color, color);
  }

  private void drawRoundedGradientRect(
      float left,
      float top,
      float right,
      float bottom,
      float radius,
      int topLeft,
      int bottomLeft,
      int bottomRight,
      int topRight) {
    if (right <= left || bottom <= top) return;
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_QUADS);
    glColor(topLeft);
    GL11.glVertex2f(left, top);
    glColor(bottomLeft);
    GL11.glVertex2f(left, bottom);
    glColor(bottomRight);
    GL11.glVertex2f(right, bottom);
    glColor(topRight);
    GL11.glVertex2f(right, top);
    GL11.glEnd();
    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
  }

  private void glColor(int color) {
    GL11.glColor4f(
        (color >> 16 & 255) / 255.0F,
        (color >> 8 & 255) / 255.0F,
        (color & 255) / 255.0F,
        (color >> 24 & 255) / 255.0F);
  }
}
