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
import myau.util.render.Themes;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedUtils;
import myau.util.time.TimerUtil;
import myau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.EnumChatFormatting;
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

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            "Raven",
            "Myau",
            "Astolfo1",
            "OldNovoline",
            "Simple",
            "Astolfo",
            "Turtle",
            "Exhibition",
            "Bingus"
          });
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

    int modeVal = this.mode.getValue();
    boolean showChatPreview =
        chatPreview.getValue() && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat;

    if (modeVal == 1) {
      EntityLivingBase previousTarget = this.target;
      this.target = this.resolveTarget();
      if (this.target != null) {
        renderMyauStyle(previousTarget);
      }
    } else if (modeVal >= 2) {
      KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
      if (killAura == null) return;

      EntityLivingBase killTarget = killAura.getTarget();
      if (killTarget != null && killAura.isEnabled()) {
        target = killTarget;
      } else if (showChatPreview) {
        target = mc.thePlayer;
      } else {
        return;
      }

      if (target instanceof EntityPlayer) {
        EntityPlayer player = (EntityPlayer) target;
        float x = (float) this.drag.position.x;
        float y = (float) this.drag.position.y;
        switch (modeVal) {
          case 2:
            renderASTHUD(player, (int) x, (int) y);
            break;
          case 3:
            renderOldNovoTHUD(player, (int) x, (int) y);
            break;
          case 4:
            renderSimpleTargetHUD(player, (int) x, (int) y);
            break;
          case 5:
            renderAstolfoTHUD(player, (int) x, (int) y);
            break;
          case 6:
            renderTurtleTHUD(player, (int) x, (int) y);
            break;
          case 7:
            renderExTargetHUD(player, (int) x, (int) y);
            break;
          case 8:
            renderBingusTargetHUD(player, (int) x, (int) y);
            break;
        }
      }
    } else {
      KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
      if (killAura == null) return;

      EntityLivingBase killTarget = killAura.getTarget();
      if (killTarget != null && killAura.isEnabled()) {
        target = killTarget;
        lastAliveMS = System.currentTimeMillis();
        fadeTimer = null;
      } else if (showChatPreview) {
        target = mc.thePlayer;
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

      HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
      boolean shaders = hud != null && hud.shaders.getValue();

      if (shaders) {
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
      }

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

  public static void renderBingusTargetHUD(EntityPlayer player, int x, int y) {
    ScaledResolution sr = new ScaledResolution(mc);
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0.0f);
    float textW =
        mc.fontRendererObj.getStringWidth(
            player.getDisplayName().getFormattedText()
                + "["
                + (int) player.getHealth()
                + "\u2764"
                + "]");
    ShapeUtil.drawRect(
        0.0f, 0.0f, Math.max(textW, 100) + 37.0f, 33.0f, new Color(0, 0, 0, 215).getRGB());
    drawHead(
        ((AbstractClientPlayer) player).getLocationSkin(),
        29,
        29,
        (player.hurtTime > 0) ? new Color(200, 30, 30) : new Color(255, 255, 255));
    final float width = 94.0f * (player.getHealth() / player.getMaxHealth());
    float barMax = Math.max(textW, 100) + width - 90.0f;
    ShapeUtil.drawRect(
        32.0f,
        27.0f,
        barMax,
        4.0f,
        getBlendColor(player.getHealth(), player.getMaxHealth()).getRGB());
    mc.fontRendererObj.drawString(
        player.getDisplayName().getUnformattedText(), (int) 33.0f, (int) 2.0f, -1);
    mc.fontRendererObj.drawString(
        EnumChatFormatting.GRAY
            + "["
            + EnumChatFormatting.WHITE
            + (int) player.getHealth()
            + EnumChatFormatting.RED
            + "\u2764"
            + EnumChatFormatting.GRAY
            + "]",
        (int)
            (mc.fontRendererObj.getStringWidth(player.getDisplayName().getUnformattedText())
                + 35.0f),
        (int) 2.0f,
        -1);
    GL11.glPushMatrix();
    java.util.List<ItemStack> stuff = new java.util.ArrayList<>();
    int cock = -2;
    for (int i = 3; i >= 0; --i) {
      ItemStack armor = player.getCurrentArmor(i);
      if (armor != null) stuff.add(armor);
    }
    if (player.getHeldItem() != null) stuff.add(player.getHeldItem());
    for (ItemStack yes : stuff) {
      if (mc.theWorld != null) {
        RenderHelper.enableGUIStandardItemLighting();
        cock += 16;
      }
      GlStateManager.pushMatrix();
      GlStateManager.disableAlpha();
      GlStateManager.clear(256);
      GlStateManager.enableBlend();
      mc.getRenderItem().renderItemIntoGUI(yes, cock + 16, 10);
      GlStateManager.disableBlend();
      GlStateManager.scale(0.5, 0.5, 0.5);
      GlStateManager.disableDepth();
      GlStateManager.disableLighting();
      GlStateManager.enableDepth();
      GlStateManager.scale(2.0f, 2.0f, 2.0f);
      GlStateManager.enableAlpha();
      GlStateManager.popMatrix();
    }
    GL11.glPopMatrix();
    GlStateManager.disableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.scale(2.0f, 2.0f, 2.0f);
    GlStateManager.popMatrix();
  }

  private static int animWidth = 0;
  private static float f6 = 0;

  public static void renderOldNovoTHUD(EntityPlayer e, int x, int y) {
    if (e == null) {
      animWidth = 0;
      return;
    }
    float bw = 135.0f;
    float bh = 45.0f;
    ShapeUtil.drawRect(
        (float) x - 1.0f,
        (float) y + 4.0f,
        x - 1.0f + bw,
        y + 4.0f + bh,
        new Color(0, 0, 0, 115).getRGB());
    mc.fontRendererObj.drawStringWithShadow(e.getName(), (float) x + 30.0f, (float) y + 13.0f, -1);
    drawArmorHUD(e, y + 8, x - 5);
    GL11.glPushMatrix();
    GlStateManager.translate((float) x, (float) y, 1.0f);
    GL11.glScalef(2.0f, 2.0f, 2.0f);
    GlStateManager.translate((float) (-x), (float) (-y), 1.0f);
    GL11.glPopMatrix();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GuiInventory.drawEntityOnScreen(x + 15, y + 40, 15, e.rotationYaw, -e.rotationPitch, e);
    f6 = 135.0f * e.getHealth() / e.getMaxHealth();
    if ((float) animWidth > f6) animWidth = getNextPostion(animWidth, (int) f6, 100.0);
    if ((float) animWidth < f6) animWidth = getNextPostion(animWidth, (int) f6, 100.0);
    ShapeUtil.drawRect(
        x - 1,
        y + 47,
        x - 1 + animWidth,
        y + 47 + 2f,
        getBlendColor(e.getHealth(), e.getMaxHealth()).getRGB());
  }

  public static void renderSimpleTargetHUD(EntityPlayer e, int x, int y) {
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    mc.fontRendererObj.drawStringWithShadow(
        e.getName(),
        new ScaledResolution(mc).getScaledWidth() / 2f
            - (mc.fontRendererObj.getStringWidth(e.getName().replaceAll("\u00a7.", "")) / 2f),
        y - 13,
        -1);
    RenderHelper.enableGUIStandardItemLighting();
    mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/icons.png"));
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDepthMask(false);
    OpenGlHelper.glBlendFunc(770, 771, 1, 0);
    GlStateManager.color(1, 1, 1);
    float srW = new ScaledResolution(mc).getScaledWidth() / 2f;
    float maxH = e.getMaxHealth() / 2.0f;
    int i = 0;
    while (i < maxH) {
      mc.ingameGUI.drawTexturedModalRect(srW - maxH * 9.5f / 2.0f + (i * 10), y, 16, 0, 9, 9);
      ++i;
    }
    i = 0;
    while (i < e.getHealth() / 2.0f) {
      mc.ingameGUI.drawTexturedModalRect(srW - maxH * 9.5f / 2.0f + (i * 10), y, 52, 0, 9, 9);
      ++i;
    }
    GL11.glDepthMask(true);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GlStateManager.disableBlend();
    GlStateManager.color(1, 1, 1);
    RenderHelper.disableStandardItemLighting();
  }

  public static void renderAstolfoTHUD(EntityPlayer e, int x, int y) {
    if (e == null) {
      animWidth = 0;
      return;
    }
    int accentColor = Themes.getCurrentTheme().getFirstColor().hashCode();
    ShapeUtil.drawRect(
        (float) x - 1.0f,
        (float) y + 4.0f,
        x - 1.0f + 200.0f,
        y + 4.0f + 45.0f,
        new Color(0, 0, 0, 190).getRGB());
    mc.fontRendererObj.drawStringWithShadow(e.getName(), (float) x + 22.0f, (float) y + 6.0f, -1);
    GL11.glPushMatrix();
    GlStateManager.translate((float) x, (float) y, 1.0f);
    GL11.glScalef(2.0f, 2.0f, 2.0f);
    GlStateManager.translate((float) (-x), (float) (-y), 1.0f);
    mc.fontRendererObj.drawStringWithShadow(
        (double) Math.round((double) (e.getHealth() / 2.0f) * 10.0) / 10.0 + " \u2764 ",
        (float) x + 10.0f,
        (float) y + 9.0f,
        accentColor);
    GL11.glPopMatrix();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GuiInventory.drawEntityOnScreen(x + 10, y + 44, 20, e.rotationYaw, -e.rotationPitch, e);
    f6 = 200.0f * e.getHealth() / e.getMaxHealth();
    if ((float) animWidth > f6) animWidth = getNextPostion(animWidth, (int) f6, 200.0);
    if ((float) animWidth < f6) animWidth = getNextPostion(animWidth, (int) f6, 200.0);
    ShapeUtil.drawRect(x - 1, y + 46, x - 1 + animWidth, y + 46 + 3.0f, accentColor);
  }

  public static void renderASTHUD(EntityPlayer e, int x, int y) {
    GL11.glPushMatrix();
    float width2 = Math.max(75, mc.fontRendererObj.getStringWidth(e.getName()) + 20);
    String healthStr2 = Math.round(e.getHealth() * 10) / 10d + " \u2764";
    GL11.glTranslatef(x, y, 0);
    ShapeUtil.drawRect(0, 0, 55 + width2, 47, new Color(0, 0, 0, 100).getRGB());
    mc.fontRendererObj.drawStringWithShadow(e.getName(), 35, 3f, Color.WHITE.getRGB());
    boolean isNaN = Float.isNaN(e.getHealth());
    float health = isNaN ? 20 : e.getHealth();
    float maxHealth = isNaN ? 20 : e.getMaxHealth();
    float healthPercent = clampValue(health / maxHealth, 0, 1);
    GlStateManager.pushMatrix();
    GlStateManager.scale(2.0, 2.0, 2.0);
    int accentColor = Themes.getCurrentTheme().getFirstColor().hashCode();
    mc.fontRendererObj.drawStringWithShadow(healthStr2, 18, 7.5f, accentColor);
    GlStateManager.popMatrix();
    ShapeUtil.drawRect(36, 36.5f, 9 + width2, 8f, reAlpha(accentColor, 0.35f));
    float barWidth = (43 + width2 - 2) - 37;
    float drawPercent = 7 + (barWidth / 100) * (healthPercent * 100);
    if (!(drawPercent + e.hurtTime > (int) (55 + width2)))
      ShapeUtil.drawRect(36, 36.5f, drawPercent + e.hurtTime, 8f, accentColor);
    ShapeUtil.drawRect(36, 36.5f, drawPercent, 8f, accentColor);
    GlStateManager.disableBlend();
    GlStateManager.enableAlpha();
    GlStateManager.resetColor();
    GlStateManager.disableBlend();
    GlStateManager.color(1, 1, 1, 1);
    GuiInventory.drawEntityOnScreen(17, 46, (int) (42 / e.height), 0, 0, e);
    GL11.glPopMatrix();
  }

  public static void renderTurtleTHUD(EntityPlayer player, int x, int y) {
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0f);
    float nameW =
        Math.max(
            mc.fontRendererObj.getStringWidth(player.getDisplayName().getUnformattedText()), 100);
    ShapeUtil.drawRect(0f, 0f, 35f + nameW, 33f, new Color(0, 0, 0, 150).getRGB());
    drawHead(
        ((AbstractClientPlayer) player).getLocationSkin(),
        29,
        29,
        player.hurtTime > 0 ? new Color(252, 157, 154) : new Color(255, 255, 255));
    float width = 94f * (player.getHealth() / player.getMaxHealth());
    ShapeUtil.drawRect(
        33f,
        30.5f,
        Math.max(
                mc.fontRendererObj.getStringWidth(player.getDisplayName().getUnformattedText()),
                100)
            + width
            - 95f,
        1.5f,
        getBlendColor(player.getHealth(), (player.getMaxHealth())).getRGB());
    mc.fontRendererObj.drawString(
        player.getDisplayName().getUnformattedText(), (int) 33.5f, (int) 2f, -1);
    GlStateManager.scale(2f, 2f, 2f);
    mc.fontRendererObj.drawString(
        "\u2764",
        (int) 30f,
        (int) (12f / 2f),
        getBlendColor(player.getHealth(), (player.getMaxHealth())).getRGB());
    mc.fontRendererObj.drawString(
        "" + (int) (player.getHealth()),
        (int) (33f / 2f),
        (int) (13f / 2f),
        getBlendColor(player.getHealth(), (player.getMaxHealth())).getRGB());
    GlStateManager.scale(0.5f, 0.5f, 0.5f);
    GlStateManager.popMatrix();
  }

  public static void renderExTargetHUD(EntityPlayer player, int x, int y) {
    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0f);
    double skeetW =
        mc.fontRendererObj.getStringWidth(player.getName()) > 70.0f
            ? (124.0f + mc.fontRendererObj.getStringWidth(player.getName()) - 70.0f)
            : 124.0;
    skeetRect(0, -2.0, skeetW, 38.0, 1.0);
    skeetRectSmall(0.0f, -2.0f, 124.0f, 38.0f, 1.0);
    mc.fontRendererObj.drawString(player.getName(), (int) 42.3f, (int) 0.3f, -1);
    final float health = player.getHealth();
    final float healthWithAbsorption = player.getHealth() + player.getAbsorptionAmount();
    final float progress = health / player.getMaxHealth();
    final Color healthColor =
        health >= 0.0f
            ? blendColors(
                    new float[] {0.0F, 0.5F, 1.0F},
                    new Color[] {Color.RED, Color.YELLOW, Color.GREEN},
                    progress)
                .brighter()
            : Color.RED;
    double cockWidth = 50.0;
    final double healthBarPos = cockWidth * (double) progress;
    rectangle(42.5, 10.3, 103, 13.5, healthColor.darker().darker().darker().darker().getRGB());
    rectangle(42.5, 10.3, 53.0 + healthBarPos + 0.5, 13.5, healthColor.getRGB());
    if (player.getAbsorptionAmount() > 0.0f) {
      rectangle(
          97.5 - (double) player.getAbsorptionAmount(),
          10.3,
          103.5,
          13.5,
          new Color(137, 112, 9).getRGB());
    }
    rectangleBordered(42.0, 9.8f, 54.0 + cockWidth, 14.0, 0.5, 0, Color.BLACK.getRGB());
    for (int dist = 1; dist < 10; ++dist) {
      double cock = cockWidth / 8.5 * (double) dist;
      rectangle(43.5 + cock, 9.8, 43.5 + cock + 0.5, 14.0, Color.BLACK.getRGB());
    }
    GlStateManager.scale(0.5, 0.5, 0.5);
    final int distance = (int) mc.thePlayer.getDistanceToEntity(player);
    final String nice = "HP: " + (int) healthWithAbsorption + " | Dist: " + distance;
    mc.fontRendererObj.drawString(nice, 85.3f, 32.3f, -1, true);
    GlStateManager.scale(2.0, 2.0, 2.0);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.enableAlpha();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GL11.glPushMatrix();
    java.util.List<ItemStack> stuff = new java.util.ArrayList<>();
    int cock = -2;
    for (int i = 3; i >= 0; --i) {
      ItemStack armor = player.getCurrentArmor(i);
      if (armor != null) stuff.add(armor);
    }
    if (player.getHeldItem() != null) stuff.add(player.getHeldItem());
    for (ItemStack yes : stuff) {
      if (mc.theWorld != null) {
        RenderHelper.enableGUIStandardItemLighting();
        cock += 16;
      }
      GlStateManager.pushMatrix();
      GlStateManager.disableAlpha();
      GlStateManager.clear(256);
      GlStateManager.enableBlend();
      mc.getRenderItem().renderItemIntoGUI(yes, cock + 28, 20);
      mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, yes, cock + 28, 20);
      renderEnchantText(yes, cock + 28, (20 + 0.5f));
      GlStateManager.disableBlend();
      GlStateManager.scale(0.5, 0.5, 0.5);
      GlStateManager.disableDepth();
      GlStateManager.disableLighting();
      GlStateManager.enableDepth();
      GlStateManager.scale(2.0f, 2.0f, 2.0f);
      GlStateManager.enableAlpha();
      GlStateManager.popMatrix();
    }
    GL11.glPopMatrix();
    GlStateManager.disableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.scale(0.31, 0.31, 0.31);
    GlStateManager.translate(73.0f, 102.0f, 40.0f);
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    drawModel(player.rotationYaw, player.rotationPitch, player);
    GlStateManager.popMatrix();
  }

  private static void drawHead(ResourceLocation skin, int width, int height, Color color) {
    GL11.glColor4f(
        color.getRed() / 255f,
        color.getGreen() / 255f,
        color.getBlue() / 255f,
        color.getAlpha() / 255f);
    mc.getTextureManager().bindTexture(skin);
    net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
        2, 2, 8F, 8F, 8, 8, width, height, 64F, 64F);
  }

  private static void drawArmorHUD(EntityPlayer player, int y, int x) {
    GL11.glPushMatrix();
    java.util.List<ItemStack> stuff = new java.util.ArrayList<>();
    for (int index = 3; index >= 0; --index) {
      ItemStack armor = player.inventory.armorInventory[index];
      if (armor != null) stuff.add(armor);
    }
    if (player.getCurrentEquippedItem() != null) stuff.add(player.getCurrentEquippedItem());
    int split = -3;
    for (ItemStack item : stuff) {
      if (mc.theWorld != null) {
        RenderHelper.enableGUIStandardItemLighting();
        split += 16;
      }
      GlStateManager.pushMatrix();
      GlStateManager.disableAlpha();
      GlStateManager.clear(256);
      GlStateManager.enableBlend();
      mc.getRenderItem().zLevel = -150.0F;
      mc.getRenderItem().renderItemAndEffectIntoGUI(item, split + x + 18, y + 17);
      mc.getRenderItem().zLevel = 0.0F;
      GlStateManager.enableBlend();
      GlStateManager.scale(0.5F, 0.5F, 0.5F);
      GlStateManager.disableDepth();
      GlStateManager.disableLighting();
      GlStateManager.enableDepth();
      GlStateManager.scale(2.0f, 2.0f, 2.0f);
      GlStateManager.enableAlpha();
      GlStateManager.popMatrix();
    }
    GL11.glPopMatrix();
  }

  private static void rectangle(double x, double y, double x1, double y1, int color) {
    ShapeUtil.drawRect((float) x, (float) y, (float) x1, (float) y1, color);
  }

  private static void rectangleBordered(
      double x, double y, double x1, double y1, double width, int internalColor, int borderColor) {
    rectangle(x + width, y + width, x1 - width, y1 - width, internalColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x + width, y, x1 - width, y + width, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x, y, x + width, y1, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x1 - width, y, x1, y1, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    rectangle(x + width, y1 - width, x1 - width, y1, borderColor);
    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
  }

  private static void skeetRect(double x, double y, double x1, double y1, double size) {
    rectangleBordered(
        x,
        y - 4.0,
        x1 + size,
        y1 + size,
        0.5,
        new Color(60, 60, 60).getRGB(),
        new Color(10, 10, 10).getRGB());
    rectangleBordered(
        x + 1.0,
        y - 3.0,
        x1 + size - 1.0,
        y1 + size - 1.0,
        1.0,
        new Color(40, 40, 40).getRGB(),
        new Color(40, 40, 40).getRGB());
    rectangleBordered(
        x + 2.5,
        y - 1.5,
        x1 + size - 2.5,
        y1 + size - 2.5,
        0.5,
        new Color(40, 40, 40).getRGB(),
        new Color(60, 60, 60).getRGB());
    rectangleBordered(
        x + 2.5,
        y - 1.5,
        x1 + size - 2.5,
        y1 + size - 2.5,
        0.5,
        new Color(22, 22, 22).getRGB(),
        new Color(255, 255, 255, 0).getRGB());
  }

  private static void skeetRectSmall(double x, double y, double x1, double y1, double size) {
    rectangleBordered(
        x + 4.35,
        y + 0.5,
        x1 + size - 84.5,
        y1 + size - 4.35,
        0.5,
        new Color(48, 48, 48).getRGB(),
        new Color(10, 10, 10).getRGB());
    rectangleBordered(
        x + 5.0,
        y + 1.0,
        x1 + size - 85.0,
        y1 + size - 5.0,
        0.5,
        new Color(17, 17, 17).getRGB(),
        new Color(255, 255, 255, 0).getRGB());
  }

  private static void drawModel(float yaw, float pitch, EntityLivingBase entityLivingBase) {
    GlStateManager.resetColor();
    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.enableColorMaterial();
    GlStateManager.pushMatrix();
    GlStateManager.translate(0.0f, 0.0f, 50.0f);
    GlStateManager.scale(-50.0f, 50.0f, 50.0f);
    GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);
    final float renderYawOffset = entityLivingBase.renderYawOffset;
    final float rotationYaw = entityLivingBase.rotationYaw;
    final float rotationPitch = entityLivingBase.rotationPitch;
    final float prevRotationYawHead = entityLivingBase.prevRotationYawHead;
    final float rotationYawHead = entityLivingBase.rotationYawHead;
    GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f);
    RenderHelper.enableStandardItemLighting();
    GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f);
    GlStateManager.rotate((float) (-Math.atan(pitch / 40.0f) * 20.0), 1.0f, 0.0f, 0.0f);
    entityLivingBase.renderYawOffset = yaw - 0.4f;
    entityLivingBase.rotationYaw = yaw - 0.2f;
    entityLivingBase.rotationPitch = pitch;
    entityLivingBase.rotationYawHead = entityLivingBase.rotationYaw;
    entityLivingBase.prevRotationYawHead = entityLivingBase.rotationYaw;
    GlStateManager.translate(0.0f, 0.0f, 0.0f);
    final RenderManager renderManager = mc.getRenderManager();
    renderManager.setPlayerViewY(180.0f);
    renderManager.setRenderShadow(false);
    renderManager.renderEntityWithPosYaw(entityLivingBase, 0.0, 0.0, 0.0, 0.0f, 1.0f);
    renderManager.setRenderShadow(true);
    entityLivingBase.renderYawOffset = renderYawOffset;
    entityLivingBase.rotationYaw = rotationYaw;
    entityLivingBase.rotationPitch = rotationPitch;
    entityLivingBase.prevRotationYawHead = prevRotationYawHead;
    entityLivingBase.rotationYawHead = rotationYawHead;
    GlStateManager.popMatrix();
    RenderHelper.disableStandardItemLighting();
    GlStateManager.disableRescaleNormal();
    GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
    GlStateManager.disableTexture2D();
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    GlStateManager.resetColor();
  }

  private static void renderEnchantText(ItemStack stack, int x, float y) {
    RenderHelper.disableStandardItemLighting();
    float enchantmentY = y + 24f;
    if (stack.getItem() instanceof ItemArmor) {
      int protectionLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
      int unbreakingLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
      int thornLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack);
      if (protectionLevel > 0) {
        drawEnchantTag("P" + getColor(protectionLevel) + protectionLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (unbreakingLevel > 0) {
        drawEnchantTag("U" + getColor(unbreakingLevel) + unbreakingLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (thornLevel > 0) {
        drawEnchantTag("T" + getColor(thornLevel) + thornLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
    }
    if (stack.getItem() instanceof ItemBow) {
      int powerLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
      int punchLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
      int flameLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack);
      int unbreakingLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
      if (powerLevel > 0) {
        drawEnchantTag("Pow" + getColor(powerLevel) + powerLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (punchLevel > 0) {
        drawEnchantTag("Pun" + getColor(punchLevel) + punchLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (flameLevel > 0) {
        drawEnchantTag("F" + getColor(flameLevel) + flameLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (unbreakingLevel > 0) {
        drawEnchantTag("U" + getColor(unbreakingLevel) + unbreakingLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
    }
    if (stack.getItem() instanceof ItemSword) {
      int sharpnessLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
      int knockbackLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack);
      int fireAspectLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
      int unbreakingLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
      if (sharpnessLevel > 0) {
        drawEnchantTag("S" + getColor(sharpnessLevel) + sharpnessLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (knockbackLevel > 0) {
        drawEnchantTag("K" + getColor(knockbackLevel) + knockbackLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (fireAspectLevel > 0) {
        drawEnchantTag("F" + getColor(fireAspectLevel) + fireAspectLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
      if (unbreakingLevel > 0) {
        drawEnchantTag("U" + getColor(unbreakingLevel) + unbreakingLevel, x * 2, enchantmentY);
        enchantmentY += 8;
      }
    }
    if (stack.getRarity() == EnumRarity.EPIC) {
      GlStateManager.pushMatrix();
      GlStateManager.disableDepth();
      GL11.glScalef(0.5f, 0.5f, 0.5f);
      drawOutlinedStringCock(
          mc.fontRendererObj,
          "God",
          x * 2,
          enchantmentY,
          new Color(255, 255, 0).getRGB(),
          new Color(100, 100, 0, 200).getRGB());
      GL11.glScalef(1.0f, 1.0f, 1.0f);
      GlStateManager.enableDepth();
      GlStateManager.popMatrix();
    }
  }

  private static void drawEnchantTag(String text, int x, float y) {
    GlStateManager.pushMatrix();
    GlStateManager.disableDepth();
    GL11.glScalef(0.5f, 0.5f, 0.5f);
    drawOutlinedStringCock(
        mc.fontRendererObj, text, x, y, -1, new Color(0, 0, 0, 220).darker().getRGB());
    GL11.glScalef(1.0f, 1.0f, 1.0f);
    GlStateManager.enableDepth();
    GlStateManager.popMatrix();
  }

  private static void drawOutlinedStringCock(
      FontRenderer fr, String s, float x, float y, int color, int outlineColor) {
    fr.drawString(s.replaceAll("\\u00a7.", ""), (int) (x - 1.0f), (int) y, outlineColor);
    fr.drawString(s.replaceAll("\\u00a7.", ""), (int) x, (int) (y - 1.0f), outlineColor);
    fr.drawString(s.replaceAll("\\u00a7.", ""), (int) (x + 1.0f), (int) y, outlineColor);
    fr.drawString(s.replaceAll("\\u00a7.", ""), (int) x, (int) (y + 1.0f), outlineColor);
    fr.drawString(s, (int) x, (int) y, color);
  }

  private static String getColor(int level) {
    if (level >= 5) return "\u00a7c";
    if (level >= 3) return "\u00a76";
    if (level >= 2) return "\u00a7e";
    return "\u00a77";
  }

  private static Color blendColors(float[] fractions, Color[] colors, float progress) {
    if (fractions.length == colors.length) {
      int[] indices = getFractionIndices(fractions, progress);
      float[] range = new float[] {fractions[indices[0]], fractions[indices[1]]};
      Color[] colorRange = new Color[] {colors[indices[0]], colors[indices[1]]};
      float max = range[1] - range[0];
      float value = progress - range[0];
      float weight = value / max;
      return new Color(
          (int)
              (colorRange[0].getRed() + (colorRange[1].getRed() - colorRange[0].getRed()) * weight),
          (int)
              (colorRange[0].getGreen()
                  + (colorRange[1].getGreen() - colorRange[0].getGreen()) * weight),
          (int)
              (colorRange[0].getBlue()
                  + (colorRange[1].getBlue() - colorRange[0].getBlue()) * weight),
          (int)
              (colorRange[0].getAlpha()
                  + (colorRange[1].getAlpha() - colorRange[0].getAlpha()) * weight));
    }
    return Color.RED;
  }

  private static int[] getFractionIndices(float[] fractions, float progress) {
    int[] indices = new int[2];
    for (int i = 0; i < fractions.length - 1; i++) {
      if (progress >= fractions[i] && progress <= fractions[i + 1]) {
        indices[0] = i;
        indices[1] = i + 1;
        break;
      }
    }
    if (progress > fractions[fractions.length - 1]) {
      indices[0] = fractions.length - 2;
      indices[1] = fractions.length - 1;
    }
    if (progress < fractions[0]) {
      indices[0] = 0;
      indices[1] = 1;
    }
    return indices;
  }

  private static Color getBlendColor(double health, double maxHealth) {
    return blendColors(
            new float[] {0.0F, 0.5F, 1.0F},
            new Color[] {new Color(108, 0, 0), new Color(255, 51, 0), Color.GREEN},
            (float) (health / maxHealth))
        .brighter();
  }

  private static int reAlpha(int color, float alpha) {
    Color c = new Color(color);
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)).getRGB();
  }

  private static int getNextPostion(int n, int n2, double d) {
    int n3 = n;
    if (n3 < n2) {
      int n4 = (int) ((double) (n2 - n3) / d);
      if (n4 < 1) n4 = 1;
      n3 += n4;
    } else if (n3 > n2) {
      int n5 = (int) ((double) (n3 - n2) / d);
      if (n5 < 1) n5 = 1;
      n3 -= n5;
    }
    return n3;
  }

  private static float clampValue(final float value, final float floor, final float cap) {
    if (value < floor) return floor;
    return Math.min(value, cap);
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
