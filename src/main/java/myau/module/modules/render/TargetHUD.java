package myau.module.modules.render;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.impl.Render2DEvent;
import myau.event.impl.Render3DEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.module.modules.combat.KillAura;
import myau.property.properties.*;
import myau.util.player.TeamUtil;
import myau.util.render.ColorUtil;
import myau.util.render.RenderUtil;
import myau.util.render.ShapeUtil;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedUtils;
import myau.util.time.TimerUtil;
import myau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TargetHUD extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final DecimalFormat healthFormat =
      new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
  private static final DecimalFormat diffFormat =
      new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
  private final TimerUtil lastAttackTimer = new TimerUtil();
  private final TimerUtil animTimer = new TimerUtil();
  private EntityLivingBase lastTarget = null;
  private EntityLivingBase target = null;
  private ResourceLocation headTexture = null;
  private float oldHealth = 0.0F;
  private float newHealth = 0.0F;
  private float maxHealth = 0.0F;
  private long lastAliveMS;
  private double lastHealth;
  private float lastHealthBar;
  private TimerUtil fadeTimer;
  private TimerUtil healthBarTimer;
  public EntityLivingBase renderEntity;
  private boolean positionInitialized = false;

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Raven", "Myau"});
  public final ModeProperty color =
      new ModeProperty(
          "Color", 0, new String[] {"DEFAULT", "HUD"}, () -> this.mode.getValue() == 1);
  public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
  public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
  public final DragProperty drag = new DragProperty("Position", new Vector2d(70, 30));

  {
    this.drag.render = true;
  }

  public final BooleanProperty showStatus = new BooleanProperty("Show win or loss", true);
  public final BooleanProperty healthColor = new BooleanProperty("Traditional health color", false);
  public final BooleanProperty renderEsp = new BooleanProperty("Render ESP", true);
  public final PercentProperty background =
      new PercentProperty("Background", 25, () -> this.mode.getValue() == 1);
  public final BooleanProperty head =
      new BooleanProperty("Head", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty indicator =
      new BooleanProperty("Indicator", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty outline =
      new BooleanProperty("Outline", false, () -> this.mode.getValue() == 1);
  public final BooleanProperty animations =
      new BooleanProperty("Animations", true, () -> this.mode.getValue() == 1);
  public final BooleanProperty kaOnly = new BooleanProperty("KA only", true);
  public final BooleanProperty chatPreview = new BooleanProperty("Chat preview", false);

  public TargetHUD() {
    super("TargetHUD", false, true);
  }

  @Override
  public void onDisabled() {
    this.target = null;
    this.lastTarget = null;
    reset();
  }

  @Override
  public void onEnabled() {
    positionInitialized = false;
  }

  private EntityLivingBase resolveTarget() {
    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    if (killAura.isEnabled()
        && killAura.isAttackAllowed()
        && TeamUtil.isEntityLoaded(killAura.getTarget())) {
      return killAura.getTarget();
    } else if (!this.kaOnly.getValue()
        && !this.lastAttackTimer.hasTimeElapsed(1500L)
        && TeamUtil.isEntityLoaded(this.lastTarget)) {
      return this.lastTarget;
    } else {
      return (this.chatPreview.getValue()
              || mc.currentScreen instanceof net.minecraft.client.gui.GuiChat
              || mc.currentScreen instanceof myau.ui.clickgui.ClickGui)
          ? mc.thePlayer
          : null;
    }
  }

  private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
    if (entityLivingBase instanceof EntityPlayer) {
      NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
      if (playerInfo != null) {
        return playerInfo.getLocationSkin();
      }
    }
    return null;
  }

  private Color getTargetColor(EntityLivingBase entityLivingBase) {
    if (entityLivingBase instanceof EntityPlayer) {
      if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
        return Myau.friendManager.getColor();
      }
      if (TeamUtil.isTarget((EntityPlayer) entityLivingBase)) {
        return Myau.targetManager.getColor();
      }
    }
    switch (this.color.getValue()) {
      case 0:
        if (!(entityLivingBase instanceof EntityPlayer)) {
          return new Color(-1);
        }
        return TeamUtil.getTeamColor((EntityPlayer) entityLivingBase, 1.0F);
      case 1:
        int rgb =
            ((HUD) Myau.moduleManager.modules.get(HUD.class))
                .getColor(System.currentTimeMillis())
                .getRGB();
        return new Color(rgb);
      default:
        return new Color(-1);
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      reset();
      return;
    }

    if (this.mode.getValue() == 1) {
      EntityLivingBase previousTarget = this.target;
      this.target = this.resolveTarget();
      if (this.target != null) {
        renderMyauStyle(previousTarget);
      }
    } else {
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
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!renderEsp.getValue() || !this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    if (killAura == null) return;

    if (killAura.showTarget.getValue() != 0) return;

    EntityLivingBase espTarget = killAura.getTarget();
    if (espTarget != null && killAura.isEnabled()) {
      RenderUtil.renderEntity(espTarget, 2, 0.0, 0.0, -1, false);
    } else if (renderEntity != null) {
      RenderUtil.renderEntity(renderEntity, 2, 0.0, 0.0, -1, false);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
      C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
      if (packet.getAction() != Action.ATTACK) {
        return;
      }
      Entity entity = packet.getEntityFromWorld(mc.theWorld);
      if (entity instanceof EntityLivingBase) {
        if (entity instanceof EntityArmorStand) {
          return;
        }
        this.lastAttackTimer.reset();
        this.lastTarget = (EntityLivingBase) entity;
      }
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

    float boxWidth = targetStrWithPadding + padding * 2;
    float boxHeight = mc.fontRendererObj.FONT_HEIGHT + 12 + padding * 2;

    if (!positionInitialized) {
      float centerX = scaledResolution.getScaledWidth() / 2f;
      float centerY = scaledResolution.getScaledHeight() / 2f;

      if (this.drag.position.x < scaledResolution.getScaledWidth() / 3f) {
        double absX = centerX - boxWidth / 2f + this.drag.position.x;
        double absY = centerY + 15f + this.drag.position.y - padding;
        this.drag.position.x = absX;
        this.drag.position.y = absY;
        this.drag.targetPosition.x = absX;
        this.drag.targetPosition.y = absY;
      }
      positionInitialized = true;
    }

    float absX = (float) this.drag.position.x;
    float absY = (float) this.drag.position.y;

    int alpha =
        (fadeTimer == null)
            ? 255
            : Math.max(0, 255 - (int) (fadeTimer.getElapsedTime() * 255 / 400));

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

      float n6 = absX * invSc;
      float n7 = absY * invSc;
      float n8 = (absX + boxWidth) * invSc;
      float n9 = (absY + boxHeight - 13f) * invSc;

      float x = n6 + padding * invSc;
      float y = n7 + padding * invSc;

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

      float n13 = n6 + 6f * invSc;
      float n14 = n8 - 6f * invSc;
      float n15 = n9;

      drawRoundedRectangle(
          n13, n15, n14, n15 + 5f * invSc, 4.0f, mergeAlpha(Color.black.getRGB(), maxAlphaOutline));

      int mergedGradientLeft = mergeAlpha(new Color(0x70CEFF).getRGB(), maxAlphaBackground);
      int mergedGradientRight = mergeAlpha(new Color(0x4D8DFF).getRGB(), maxAlphaBackground);

      float healthBar = n14 + (n13 - n14) * (float) (1 - health);

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

  private void renderMyauStyle(EntityLivingBase previousTarget) {
    float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
    float abs = this.target.getAbsorptionAmount() / 2.0F;
    float heal = this.target.getHealth() / 2.0F + abs;

    if (this.target != previousTarget) {
      this.headTexture = null;
      this.animTimer.setTime();
      this.oldHealth = heal;
      this.newHealth = heal;
    }

    if (!this.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
      this.oldHealth = this.newHealth;
      this.newHealth = heal;
      this.maxHealth = this.target.getMaxHealth() / 2.0F;
      if (this.oldHealth != this.newHealth) {
        this.animTimer.reset();
      }
    }

    ResourceLocation resourceLocation = this.getSkin(this.target);
    if (resourceLocation != null) {
      this.headTexture = resourceLocation;
    }

    float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L);
    float healthRatio =
        Math.min(
            Math.max(
                RenderUtil.lerpFloat(this.newHealth, this.oldHealth, elapsedTime / 150.0F)
                    / this.maxHealth,
                0.0F),
            1.0F);
    Color targetColor = this.getTargetColor(this.target);
    Color healthBarColor =
        this.color.getValue() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
    float healthDeltaRatio = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
    Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);
    ScaledResolution scaledResolution = new ScaledResolution(mc);
    String targetNameText =
        ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(this.target)));
    int targetNameWidth = mc.fontRendererObj.getStringWidth(targetNameText);
    String healthText =
        ChatColors.formatColor(
            String.format("&r&f%s%s\u2764&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c"));
    int healthTextWidth = mc.fontRendererObj.getStringWidth(healthText);
    String statusText =
        ChatColors.formatColor(
            String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
    int statusTextWidth = mc.fontRendererObj.getStringWidth(statusText);
    String healthDiffText =
        ChatColors.formatColor(
            String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal)));
    int healthDiffWidth = mc.fontRendererObj.getStringWidth(healthDiffText);
    float barContentWidth =
        Math.max(
            (float) targetNameWidth
                + (this.indicator.getValue() ? 2.0F + (float) statusTextWidth + 2.0F : 0.0F),
            (float) healthTextWidth
                + (this.indicator.getValue() ? 2.0F + (float) healthDiffWidth + 2.0F : 0.0F));
    float headIconOffset = this.head.getValue() && this.headTexture != null ? 25.0F : 0.0F;
    float barTotalWidth =
        Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);

    float posX = (float) this.drag.position.x / this.scale.getValue();
    float posY = (float) this.drag.position.y / this.scale.getValue();

    this.drag.scale.x = barTotalWidth * this.scale.getValue();
    this.drag.scale.y = 27.0F * this.scale.getValue();

    GlStateManager.pushMatrix();
    GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 1.0F);
    GlStateManager.translate(posX, posY, -450.0F);
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(770, 771);
    GlStateManager.disableAlpha();
    GlStateManager.disableDepth();
    GlStateManager.disableTexture2D();
    int backgroundColor =
        new Color(0.0F, 0.0F, 0.0F, (float) this.background.getValue() / 100.0F).getRGB();
    int outlineColor =
        this.outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
    ShapeUtil.drawOutlineRect(
        0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, backgroundColor, outlineColor);
    ShapeUtil.drawRect(
        headIconOffset + 2.0F,
        22.0F,
        barTotalWidth - 2.0F,
        25.0F,
        ColorUtil.darker(healthBarColor, 0.2F).getRGB());
    ShapeUtil.drawRect(
        headIconOffset + 2.0F,
        22.0F,
        headIconOffset + 2.0F + healthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F),
        25.0F,
        healthBarColor.getRGB());
    GlStateManager.enableTexture2D();
    GlStateManager.enableAlpha();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    mc.fontRendererObj.drawString(
        targetNameText, headIconOffset + 2.0F, 2.0F, -1, this.shadow.getValue());
    mc.fontRendererObj.drawString(
        healthText, headIconOffset + 2.0F, 12.0F, -1, this.shadow.getValue());
    if (this.indicator.getValue()) {
      mc.fontRendererObj.drawString(
          statusText,
          barTotalWidth - 2.0F - (float) statusTextWidth,
          2.0F,
          healthDeltaColor.getRGB(),
          this.shadow.getValue());
      mc.fontRendererObj.drawString(
          healthDiffText,
          barTotalWidth - 2.0F - (float) healthDiffWidth,
          12.0F,
          ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(),
          this.shadow.getValue());
    }
    if (this.head.getValue() && this.headTexture != null) {
      GlStateManager.color(1.0F, 1.0F, 1.0F);
      mc.getTextureManager().bindTexture(this.headTexture);
      net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
          2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
      net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
          2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
      GlStateManager.color(1.0F, 1.0F, 1.0F);
    }
    GlStateManager.disableBlend();
    GlStateManager.enableDepth();
    GlStateManager.popMatrix();
  }

  private void reset() {
    fadeTimer = null;
    target = null;
    healthBarTimer = null;
    renderEntity = null;
  }

  private float getTimedProgress(float total, long duration) {
    if (healthBarTimer == null) return 0;
    long elapsed = healthBarTimer.getElapsedTime();
    float progress = Math.min(1.0f, (float) elapsed / (float) duration);
    return total * progress;
  }

  private int mergeAlpha(int rgb, int alpha) {
    return (MathHelper.clamp_int(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
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
