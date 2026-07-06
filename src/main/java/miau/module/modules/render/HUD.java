package miau.module.modules.render;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.enums.ChatColors;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorGuiChat;
import miau.module.Module;
import miau.module.modules.render.hud.InterfaceComponent;
import miau.property.properties.*;
import miau.util.font.FontRepository;
import miau.util.render.ColorUtil;
import miau.util.render.MenuBackground;
import miau.util.render.RenderUtil;
import miau.util.render.Themes;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class HUD extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private final java.util.Map<Module, InterfaceComponent> components = new java.util.HashMap<>();
  private final java.util.List<InterfaceComponent> animatingComponents =
      new java.util.ArrayList<>();
  private final java.util.Comparator<InterfaceComponent> componentComparator =
      (a, b) -> Integer.compare(this.getModuleWidth(b.module), this.getModuleWidth(a.module));
  private final java.util.List<net.minecraft.potion.PotionEffect> sortedEffects =
      new java.util.ArrayList<>();

  private long lastMS = System.currentTimeMillis();
  private List<Module> activeModules = new ArrayList<>();

  private double lastX = 0, lastZ = 0;
  private String bpsString = "0.00";

  public final BooleanProperty showWatermark = new BooleanProperty("watermark", true);
  public final ModeProperty hudMode =
      new ModeProperty("mode", 0, new String[] {"NORMAL", "EXHIBITION"});
  public final TextProperty watermarkName =
      new TextProperty("watermark-name", "Miau", this.showWatermark::getValue);
  public final BooleanProperty showCoordinates =
      new BooleanProperty("coordinates", true, () -> this.hudMode.getValue() == 1);
  public final BooleanProperty showTime =
      new BooleanProperty("show-time", true, this.showWatermark::getValue);
  public final BooleanProperty showFps =
      new BooleanProperty("show-fps", true, this.showWatermark::getValue);
  public final BooleanProperty showPing =
      new BooleanProperty("show-ping", true, this.showWatermark::getValue);
  public final BooleanProperty showBps =
      new BooleanProperty("show-bps", true, this.showWatermark::getValue);

  public final ModeProperty colorAnimation =
      new ModeProperty("color-animation", 1, new String[] {"STATIC", "FADE", "RAINBOW"});
  public final ModeProperty modulesToShow =
      new ModeProperty("modules-to-show", 1, new String[] {"ALL", "EXCLUDE RENDER", "ONLY BOUND"});
  public final ModeProperty fontFace = new ModeProperty("Font", 1, FontRepository.FONT_NAMES);
  public final ModeProperty posX =
      new ModeProperty("position-x", 0, new String[] {"LEFT", "RIGHT"});
  public final ModeProperty posY =
      new ModeProperty("position-y", 0, new String[] {"TOP", "BOTTOM"});
  public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
  public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
  public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
  public final BooleanProperty showBar = new BooleanProperty("bar", true);
  public final BooleanProperty shadow = new BooleanProperty("shadow", true);
  public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
  public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
  public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
  public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
  public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
  public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
  public final BooleanProperty notifications = new BooleanProperty("notifications", true);

  public final IntProperty backgroundAlpha = new IntProperty("Background Alpha", 110, 0, 255);
  public final FloatProperty roundingRadius =
      new FloatProperty("Rounding Radius", 1.0F, 0.0F, 10.0F);
  public final ModeProperty menuBackground =
      new ModeProperty("Menu Background", 0, MenuBackground.NAMES);

  /**
   * Returns the total height of the visible module array for autofit calculations. Only meaningful
   * when posX == 1 (right side).
   */
  public float getModuleListHeight() {
    float itemHeight = hudMode.getValue() == 1 ? 12.0f : 10.0f;
    int count = 0;
    for (Module module : Miau.moduleManager.modules.values()) {
      if (!module.isEnabled()) continue;
      if (modulesToShow.getValue() == 1 && module.isHidden()) continue;
      if (modulesToShow.getValue() == 2 && module.getKey() == 0) continue;
      String name = module.getName().toLowerCase();
      if (name.equals("hud") || name.equals("gui") || name.equals("clickgui")) continue;
      count++;
    }
    return count * itemHeight * scale.getValue();
  }

  private InterfaceComponent getComponent(Module module) {
    return components.computeIfAbsent(module, InterfaceComponent::new);
  }

  private String getModuleName(Module module) {
    String moduleName = module.getName();
    if (this.lowerCase.getValue()) {
      moduleName = moduleName.toLowerCase(Locale.ROOT);
    }
    return moduleName;
  }

  private String[] getModuleSuffix(Module module) {
    String[] moduleSuffix = module.getSuffix();
    if (this.lowerCase.getValue()) {
      for (int i = 0; i < moduleSuffix.length; i++) {
        moduleSuffix[i] = moduleSuffix[i].toLowerCase();
      }
    }
    return moduleSuffix;
  }

  private int getModuleWidth(Module module) {
    return this.calculateStringWidth(this.getModuleName(module), this.getModuleSuffix(module));
  }

  private int calculateStringWidth(String string, String[] arr) {
    int width = getFont().getStringWidth(string);
    if (this.suffixes.getValue()) {
      for (String str : arr) {
        width += 3 + getFont().getStringWidth(str);
      }
    }
    return width;
  }

  public miau.util.font.Font getFont() {
    return FontRepository.getHudFont(18);
  }

  public HUD() {
    super("HUD", true, true);
    FontRepository.setHudFace(this.fontFace.getValue());
  }

  @Override
  public void verifyValue(String name) {
    if (name.equalsIgnoreCase("Font")) {
      FontRepository.setHudFace(this.fontFace.getValue());
      FontRepository.clearCache();
    }
  }

  public Color getColor(long time) {
    return this.getColor(time, 0L);
  }

  public Color getColor(long time, long yPos) {
    Themes theme = Themes.getCurrentTheme();

    switch (this.colorAnimation.getValue()) {
      case 0:
        return theme.getFirstColor();
      case 1:
        return theme.getAccentColor(new Vector2d(0, yPos));
      case 2:
        return ColorUtil.rainbow((int) (time * 500 / 6));
      default:
        return Color.white;
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (mc.thePlayer != null) {
        double dist = Math.hypot(mc.thePlayer.posX - lastX, mc.thePlayer.posZ - lastZ);
        bpsString =
            String.valueOf(
                miau.util.math.MathUtil.round(
                    dist * 20.0D * ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed, 2));
        lastX = mc.thePlayer.posX;
        lastZ = mc.thePlayer.posZ;
      }
    }
    if (this.isEnabled() && event.getType() == EventType.POST) {
      this.activeModules.clear();
      for (Module module : Miau.moduleManager.modules.values()) {
        if (module.isEnabled()
            && (this.modulesToShow.getValue() == 0 || !module.isHidden())
            && getComponent(module).shouldDisplay(this)) {
          this.activeModules.add(module);
        }
      }
      this.activeModules.sort(
          (a, b) -> Integer.compare(this.getModuleWidth(b), this.getModuleWidth(a)));
      try {
        Miau.clientName = ChatColors.getDynamicPrefix();
      } catch (Exception e) {
      }
    }
  }

  private String getExhibitionWatermark() {
    String customName = this.watermarkName.getValue();
    if (customName == null || customName.isEmpty()) customName = "Miau";

    int ping = 0;
    if (mc.getNetHandler() != null && mc.thePlayer != null) {
      net.minecraft.client.network.NetworkPlayerInfo playerInfo =
          mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
      if (playerInfo != null) ping = playerInfo.getResponseTime();
    }

    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a");
    String formattedTime = sdf.format(new java.util.Date());

    String text = customName.charAt(0) + "§7" + customName.substring(1);

    if (this.showTime.getValue()) text += " [§f" + formattedTime + "§7]";
    if (this.showFps.getValue()) text += " [§f" + Minecraft.getDebugFPS() + " FPS§7]";
    if (this.showPing.getValue()) text += " [§f" + ping + "ms§7]";
    if (this.showBps.getValue()) text += " [§f" + bpsString + " BPS§7]";
    return text;
  }

  @EventTarget
  public void onShaderEvent(miau.event.impl.ShaderEvent event) {
    miau.module.Module postProc =
        Miau.moduleManager.getModule(miau.module.modules.render.PostProcessing.class);
    if (this.isEnabled()
        && !mc.gameSettings.showDebugInfo
        && postProc != null
        && postProc.isEnabled()) {
      long l = System.currentTimeMillis();
      float delta = 16f; // We don't update animations here to avoid desync
      ScaledResolution sr = new ScaledResolution(mc);

      java.util.List<InterfaceComponent> animatingComponents =
          Miau.moduleManager.modules.values().stream()
              .map(this::getComponent)
              .filter(c -> c.animationTime > 0.001)
              .sorted(
                  Comparator.comparingInt((InterfaceComponent c) -> this.getModuleWidth(c.module))
                      .reversed())
              .collect(Collectors.toList());

      int pass = event.isBloom() ? 0 : 1;
      renderElements(l, delta, animatingComponents, sr, pass, false);
      renderPotions(sr, pass);
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    long currentMS = System.currentTimeMillis();
    float delta = (currentMS - lastMS);
    lastMS = currentMS;
    if (delta > 200 || delta < 0) delta = 16;
    ScaledResolution sr = new ScaledResolution(mc);

    for (Module module : Miau.moduleManager.modules.values()) {
      InterfaceComponent component = getComponent(module);
      boolean shouldBeVisible =
          module.isEnabled()
              && (this.modulesToShow.getValue() == 0 || !module.isHidden())
              && component.shouldDisplay(this);

      if (shouldBeVisible) {
        component.animationTime = (float) Math.min(1.0, component.animationTime + (delta * 0.006));
      } else {
        component.animationTime = (float) Math.max(0.0, component.animationTime - (delta * 0.006));
      }
    }

    this.animatingComponents.clear();
    for (Module module : Miau.moduleManager.modules.values()) {
      InterfaceComponent c = getComponent(module);
      if (c.animationTime > 0.001) {
        this.animatingComponents.add(c);
      }
    }
    this.animatingComponents.sort(this.componentComparator);

    boolean isMcFont = FontRepository.isMinecraftSelected();
    float heightExhibition = 9.0f + 3.0f;
    float heightNormal = 9.0f + 1.0f;
    float currentYExhibition = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
    float currentYNormal = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();

    if (this.posX.getValue() == 0) {
      if (this.posY.getValue() == 0) {
        if (this.showWatermark.getValue()) {
          float watermarkHeight = getFont().getFontHeight() + 6.0F;
          currentYExhibition += watermarkHeight;
          currentYNormal += watermarkHeight;
        }
      } else {
        float bottomOffset = 0.0F;
        if (this.hudMode.getValue() == 1
            && this.showCoordinates.getValue()
            && mc.thePlayer != null) {
          bottomOffset += getFont().getFontHeight() * 3 + 12.0F;
        }
        currentYExhibition += bottomOffset;
        currentYNormal += bottomOffset;
      }
    }

    if (this.posY.getValue() == 1) {
      currentYExhibition =
          (float) sr.getScaledHeight()
              - currentYExhibition
              - heightExhibition * this.scale.getValue();
      currentYNormal =
          (float) sr.getScaledHeight() - currentYNormal - heightNormal * this.scale.getValue();
    }

    for (InterfaceComponent component : animatingComponents) {
      float targetY = (this.hudMode.getValue() == 1) ? currentYExhibition : currentYNormal;

      if (component.position.y == 0) component.position.y = targetY;

      component.position.y =
          miau.util.math.MathUtil.lerp((float) component.position.y, targetY, 0.015f * delta);

      if (component.module.isEnabled()
          && (this.modulesToShow.getValue() == 0 || !component.module.isHidden())
          && component.shouldDisplay(this)) {
        float spacingEx =
            heightExhibition * this.scale.getValue() * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
        float spacingNorm =
            (heightNormal + (this.shadow.getValue() ? 1.0F : 0.0F))
                * this.scale.getValue()
                * (this.posY.getValue() == 0 ? 1.0F : -1.0F);
        currentYExhibition += spacingEx;
        currentYNormal += spacingNorm;
      }
    }

    if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
      String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
      if (Miau.commandManager != null && Miau.commandManager.isTypingCommand(text)) {
        RenderUtil.enableRenderState();
        RenderUtil.drawOutlineRect(
            2.0F,
            (float) (mc.currentScreen.height - 14),
            (float) (mc.currentScreen.width - 2),
            (float) (mc.currentScreen.height - 2),
            1.5F,
            0,
            this.getColor(System.currentTimeMillis()).getRGB());
        RenderUtil.disableRenderState();
      }
    }

    if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
      long l = System.currentTimeMillis();
      renderElements(l, delta, animatingComponents, sr, 2, true);
      renderPotions(sr, 2);
    }
  }

<<<<<<< HEAD
  private void renderPotions(ScaledResolution sr, int renderPass) {
    if (mc.thePlayer != null) {
      java.util.Collection<net.minecraft.potion.PotionEffect> effects =
          mc.thePlayer.getActivePotionEffects();
      if (!effects.isEmpty()) {
        miau.util.font.Font font = getFont();
        float drawY = sr.getScaledHeight() - 3;
        java.util.List<net.minecraft.potion.PotionEffect> sortedEffects = new ArrayList<>(effects);
        sortedEffects.sort(
=======
      this.sortedEffects.clear();
      if (mc.thePlayer != null) {
        this.sortedEffects.addAll(mc.thePlayer.getActivePotionEffects());
        miau.util.font.Font font = getFont();
        this.sortedEffects.sort(
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
            (a, b) -> {
              String nameA = net.minecraft.client.resources.I18n.format(a.getEffectName());
              String nameB = net.minecraft.client.resources.I18n.format(b.getEffectName());
              String timeA = net.minecraft.potion.Potion.getDurationString(a);
              String timeB = net.minecraft.potion.Potion.getDurationString(b);
              String textA =
                  (lowerCase.getValue() ? nameA.toLowerCase() : nameA)
                      + (a.getAmplifier() > 0 ? " " + (a.getAmplifier() + 1) : "")
                      + " §7"
                      + timeA;
              String textB =
                  (lowerCase.getValue() ? nameB.toLowerCase() : nameB)
                      + (b.getAmplifier() > 0 ? " " + (b.getAmplifier() + 1) : "")
                      + " §7"
                      + timeB;
              return Float.compare(-font.getStringWidth(textA), -font.getStringWidth(textB));
            });
<<<<<<< HEAD
=======
      }

      if (this.shaders.getValue()) {
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b

        for (net.minecraft.potion.PotionEffect effect : sortedEffects) {
          net.minecraft.potion.Potion potion =
              net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
          if (potion == null) continue;

          String name = net.minecraft.client.resources.I18n.format(potion.getName());
          if (lowerCase.getValue()) name = name.toLowerCase();
          if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);

          String time = net.minecraft.potion.Potion.getDurationString(effect);
          String text = name + " §7" + time;
          int textWidth = font.getStringWidth(text);
          float drawX = sr.getScaledWidth() - 2;

          drawY -= (font.height() + 1.5f);

          float pX = drawX - textWidth - 14 - 2;
          float pY = drawY;
          float pW = textWidth + 14 + 4;
          float pH = font.height() + 1.5f;

          if (renderPass == 0) {
            miau.util.shader.RoundedUtils.drawRound(
                pX - 1f, pY - 1f, pW + 2f, pH + 2f, 4f, new Color(81, 99, 149, 80));
          } else if (renderPass == 1) {
            miau.util.shader.RoundedUtils.drawRound(pX, pY, pW, pH, 4f, new Color(0, 0, 0, 150));
          } else {
            if (this.backgroundAlpha.getValue() > 0) {
              miau.util.shader.RoundedUtils.drawRound(
                  pX, pY, pW, pH, 4f, new Color(0, 0, 0, this.backgroundAlpha.getValue()));
            }
            int effectColor = potion.getLiquidColor() | 0xFF000000;
            font.drawWithShadow(text, drawX - textWidth - 1, drawY, effectColor);

            if (potion.hasStatusIcon()) {
              GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
              GlStateManager.enableBlend();
              mc.getTextureManager()
                  .bindTexture(
                      new net.minecraft.util.ResourceLocation(
                          "textures/gui/container/inventory.png"));
              int iconIndex = potion.getStatusIconIndex();
              net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                  (int) (drawX - textWidth - 14),
                  (int) drawY,
                  (iconIndex % 8) * 18,
                  198 + (iconIndex / 8) * 18,
                  18,
                  18,
                  9,
                  9,
                  256,
                  256);
              GlStateManager.disableBlend();
            }
          }
        }
      }
    }
  }

  private void renderElements(
      long l,
      float delta,
      java.util.List<InterfaceComponent> animatingComponents,
      ScaledResolution sr,
      int renderPass,
      boolean updateState) {
    boolean isMcFont = FontRepository.isMinecraftSelected();
    float heightExhibition = 9.0f + 3.0f;
    float heightNormal = 9.0f + 1.0f;
    if (this.showWatermark.getValue()) {
      String watermark = getExhibitionWatermark();
      if (watermark != null) {
        try {
          float wX = 3.0f;
          float wY = 3.0f;
          float wW = mc.fontRendererObj.getStringWidth(watermark);
          float wH = mc.fontRendererObj.FONT_HEIGHT;

          if (renderPass == 0) {
            miau.util.shader.RoundedUtils.drawRound(
                wX - 2.0f, wY - 2.0f, wW + 4.0f, wH + 4.0f, 4f, new Color(81, 99, 149, 80));
          } else if (renderPass == 1) {
            miau.util.shader.RoundedUtils.drawRound(
                wX - 1.0f, wY - 1.0f, wW + 2.0f, wH + 2.0f, 4f, new Color(0, 0, 0, 150));
          } else {
            if (this.backgroundAlpha.getValue() > 0) {
              miau.util.shader.RoundedUtils.drawRound(
                  wX - 1.0f,
                  wY - 1.0f,
                  wW + 2.0f,
                  wH + 2.0f,
                  4f,
                  new Color(0, 0, 0, this.backgroundAlpha.getValue()));
            }
            mc.fontRendererObj.drawStringWithShadow(watermark, wX, wY, this.getColor(l).getRGB());
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    if (this.hudMode.getValue() == 1) {
      if (this.showCoordinates.getValue() && mc.thePlayer != null) {
        String posX2 = String.valueOf(Math.round(mc.thePlayer.posX));
        String posY2 = String.valueOf(Math.round(mc.thePlayer.posY));
        String posZ2 = String.valueOf(Math.round(mc.thePlayer.posZ));
        float yCoord = sr.getScaledHeight() - 10;
        float fontHeight = getFont().getFontHeight();
        int colour = this.getColor(l).getRGB();
        getFont().drawWithShadow("X: §7" + posX2, 3.0F, yCoord - fontHeight * 2, colour);
        getFont().drawWithShadow("Y: §7" + posY2, 3.0F, yCoord - fontHeight, colour);
        getFont().drawWithShadow("Z: §7" + posZ2, 3.0F, yCoord, colour);
      }

      float height = heightExhibition;
      float x = (float) this.offsetX.getValue();
      if (this.posX.getValue() == 1) x = (float) sr.getScaledWidth() - x;

      GlStateManager.pushMatrix();
      GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

      for (InterfaceComponent component : animatingComponents) {
        Module module = component.module;
        String moduleName = this.getModuleName(module);
        String[] moduleSuffix = this.getModuleSuffix(module);
        float totalWidth =
            (float)
                (this.calculateStringWidth(moduleName, moduleSuffix)
                    - (this.shadow.getValue() ? 0 : 1));

        double animProgress = component.animationTime;
        float drawY = (float) component.position.y / this.scale.getValue();
        float baseX = x / this.scale.getValue();
        float targetX;
        boolean shouldBeVisible =
            module.isEnabled()
                && (this.modulesToShow.getValue() == 0 || !module.isHidden())
                && component.shouldDisplay(this);

        if (this.posX.getValue() == 1) {
          targetX = baseX - totalWidth;
          if (!shouldBeVisible) targetX += totalWidth + 20;
        } else {
          targetX = baseX;
          if (!shouldBeVisible) targetX -= totalWidth + 20;
        }

        if (component.position.x == 5000)
          component.position.x = this.posX.getValue() == 1 ? targetX + 50 : targetX - 50;

        if (updateState) {
          component.position.x =
              miau.util.math.MathUtil.lerp((float) component.position.x, targetX, 0.015f * delta);
        }
        float drawX = (float) component.position.x;

        int alpha = (int) (255 * animProgress);
        long finalY = (long) component.position.y;
        int color = (alpha << 24) | (this.getColor(l, finalY).getRGB() & 0x00FFFFFF);
<<<<<<< HEAD
        int bgAlphaVal = (int) (this.backgroundAlpha.getValue() * animProgress);
=======
        int alphaVal = (int) (this.backgroundAlpha.getValue() * animProgress);
        int bgColor = (alphaVal << 24);
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b

        if (renderPass == 0) {
          miau.util.shader.RoundedUtils.drawRound(
              drawX - 3.0F,
              drawY - 3.0F,
              totalWidth + 6.0F,
              height + 4.0F,
              4f,
              new Color(81, 99, 149, 80));
        } else if (renderPass == 1) {
          miau.util.shader.RoundedUtils.drawRound(
              drawX - 2.0F,
              drawY - 2.0F,
              totalWidth + 4.0F,
              height + 2.0F,
              4f,
              new Color(0, 0, 0, 150));
        } else {
          if (this.backgroundAlpha.getValue() > 0) {
            miau.util.shader.RoundedUtils.drawRound(
                drawX - 2.0F,
                drawY - 2.0F,
                totalWidth + 4.0F,
                height + 2.0F,
                4f,
                new Color(0, 0, 0, bgAlphaVal));
          }
          if (this.showBar.getValue()) {
            RenderUtil.enableRenderState();
            if (this.posX.getValue() == 0)
              RenderUtil.drawRect(
                  drawX - 3.0F, drawY - 2.0F, drawX - 2.0F, drawY + height - 2.0F, color);
            else
              RenderUtil.drawRect(
                  drawX + totalWidth + 2.0F,
                  drawY - 2.0F,
                  drawX + totalWidth + 3.0F,
                  drawY + height - 2.0F,
                  color);
            RenderUtil.disableRenderState();
          }

          getFont().drawWithShadow(moduleName, drawX, drawY, color);

          if (this.suffixes.getValue() && moduleSuffix.length > 0) {
            float suffixX = drawX + getFont().getStringWidth(moduleName) + 2.0F;
            int suffixColor = ((int) (170 * animProgress) << 24) | 0x00AAAAAA;
            for (String str : moduleSuffix) {
              getFont().drawWithShadow(str, suffixX, drawY, suffixColor);
              suffixX += getFont().getStringWidth(str) + 2.0F;
            }
          }
        }
      }
      GlStateManager.popMatrix();
    } else {
      float height = heightNormal;
      float x =
          (float) this.offsetX.getValue()
              + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F))
                  * this.scale.getValue();
      if (this.posX.getValue() == 1) x = (float) sr.getScaledWidth() - x;

      GlStateManager.pushMatrix();
      GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

      for (InterfaceComponent component : animatingComponents) {
        Module module = component.module;
        String moduleName = this.getModuleName(module);
        String[] moduleSuffix = this.getModuleSuffix(module);
        float totalWidth =
            (float)
                (this.calculateStringWidth(moduleName, moduleSuffix)
                    - (this.shadow.getValue() ? 0 : 1));

        double animProgress = component.animationTime;
        float drawY = (float) component.position.y / this.scale.getValue();
        float baseX = x / this.scale.getValue();
        float targetX;
        boolean shouldBeVisible =
            module.isEnabled()
                && (this.modulesToShow.getValue() == 0 || !module.isHidden())
                && component.shouldDisplay(this);

        if (this.posX.getValue() == 1) {
          targetX = baseX - totalWidth;
          if (!shouldBeVisible) targetX += totalWidth + 20;
        } else {
          targetX = baseX;
          if (!shouldBeVisible) targetX -= totalWidth + 20;
        }

        if (component.position.x == 5000)
          component.position.x = this.posX.getValue() == 1 ? targetX + 50 : targetX - 50;

        if (updateState) {
          component.position.x =
              miau.util.math.MathUtil.lerp((float) component.position.x, targetX, 0.015f * delta);
        }
        float drawX = (float) component.position.x;

        int alpha = (int) (255 * animProgress);

        long finalY = (long) component.position.y;
        int color = (alpha << 24) | (this.getColor(l, finalY).getRGB() & 0x00FFFFFF);
<<<<<<< HEAD
        int bgAlphaVal = (int) (this.backgroundAlpha.getValue() * animProgress);
=======
        int alphaVal = (int) (this.backgroundAlpha.getValue() * animProgress);
        int bgColor = (alphaVal << 24);
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b

        if (renderPass == 0) {
          miau.util.shader.RoundedUtils.drawRound(
              drawX - 2.0F,
              drawY - 2.0F,
              totalWidth + 4.0F,
              height + 4.0F,
              4f,
              new Color(81, 99, 149, 80));
        } else if (renderPass == 1) {
          miau.util.shader.RoundedUtils.drawRound(
              drawX - 1.0F,
              drawY - 1.0F,
              totalWidth + 2.0F,
              height + 2.0F,
              4f,
              new Color(0, 0, 0, 150));
        } else {
          if (this.backgroundAlpha.getValue() > 0) {
            miau.util.shader.RoundedUtils.drawRound(
                drawX - 1.0F,
                drawY - 1.0F,
                totalWidth + 2.0F,
                height + 2.0F,
                4f,
                new Color(0, 0, 0, bgAlphaVal));
          }

          if (this.showBar.getValue()) {
            RenderUtil.enableRenderState();
            if (this.shadow.getValue()) {
              RenderUtil.drawRect(
                  drawX + (this.posX.getValue() == 0 ? -3.0F : totalWidth + 1.0F),
                  drawY - (this.posY.getValue() == 0 ? (finalY == 0L ? 1.0F : 0.0F) : 1.0F),
                  drawX + (this.posX.getValue() == 0 ? -2.0F : totalWidth + 2.0F),
                  drawY
                      + height
                      + (this.posY.getValue() == 0 ? 1.0F : (finalY == 0L ? 1.0F : 0.0F)),
                  color);
            } else {
              RenderUtil.drawRect(
                  drawX + (this.posX.getValue() == 0 ? -2.0F : totalWidth + 1.0F),
                  drawY - (this.posY.getValue() == 0 ? (finalY == 0L ? 1.0F : 0.0F) : 0.0F),
                  drawX + (this.posX.getValue() == 0 ? -1.0F : totalWidth + 2.0F),
                  drawY
                      + height
                      + (this.posY.getValue() == 0 ? 0.0F : (finalY == 0L ? 1.0F : 0.0F)),
                  color);
            }
            RenderUtil.disableRenderState();
          }

          GlStateManager.disableDepth();

          if (this.shadow.getValue()) getFont().drawWithShadow(moduleName, drawX, drawY, color);
          else
            getFont()
                .draw(
                    moduleName,
                    drawX,
                    drawY + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                    color,
                    false);

          if (this.suffixes.getValue() && moduleSuffix.length > 0) {
            float width = (float) getFont().getStringWidth(moduleName) + 3.0F;
            int suffixColor = ((int) (160 * animProgress) << 24) | 0x00AAAAAA;
            for (String string : moduleSuffix) {
              if (this.shadow.getValue())
                getFont().drawWithShadow(string, drawX + width, drawY, suffixColor);
              else
                getFont()
                    .draw(
                        string,
                        drawX + width,
                        drawY + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                        suffixColor,
                        false);
              width +=
                  (float) getFont().getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
            }
          }
        }
      }

      if (this.blinkTimer.getValue() && renderPass == 2) {
        BlinkModules blinkingModule = Miau.blinkManager.getBlinkingModule();
        if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
          long movementPacketSize = Miau.blinkManager.countMovement();
          if (movementPacketSize > 0L) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            getFont()
                .draw(
                    String.valueOf(movementPacketSize),
                    (float) sr.getScaledWidth() / 2.0F / this.scale.getValue()
                        - (float) getFont().getStringWidth(String.valueOf(movementPacketSize))
                            / 2.0F,
                    (float) sr.getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                    this.getColor(l, 0L).getRGB() & 16777215 | -1090519040,
                    this.shadow.getValue());
            GlStateManager.disableBlend();
          }
        }
      }
      GlStateManager.enableDepth();
      GlStateManager.popMatrix();
    }
<<<<<<< HEAD
=======

    if (mc.thePlayer != null) {
      if (!this.sortedEffects.isEmpty()) {
        miau.util.font.Font font = getFont();
        float drawY = sr.getScaledHeight() - 3;

        for (net.minecraft.potion.PotionEffect effect : this.sortedEffects) {
          net.minecraft.potion.Potion potion =
              net.minecraft.potion.Potion.potionTypes[effect.getPotionID()];
          if (potion == null) continue;

          String name = net.minecraft.client.resources.I18n.format(potion.getName());
          if (lowerCase.getValue()) name = name.toLowerCase();
          if (effect.getAmplifier() > 0) name += " " + (effect.getAmplifier() + 1);

          String time = net.minecraft.potion.Potion.getDurationString(effect);
          String text = name + " §7" + time;
          int textWidth = font.getStringWidth(text);
          float drawX = sr.getScaledWidth() - 2;

          drawY -= (font.height() + 1.5f);

          int effectColor = potion.getLiquidColor() | 0xFF000000;
          font.drawWithShadow(text, drawX - textWidth - 1, drawY, effectColor);

          if (potion.hasStatusIcon()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            mc.getTextureManager()
                .bindTexture(
                    new net.minecraft.util.ResourceLocation(
                        "textures/gui/container/inventory.png"));
            int iconIndex = potion.getStatusIconIndex();
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                (int) (drawX - textWidth - 14),
                (int) drawY,
                (iconIndex % 8) * 18,
                198 + (iconIndex / 8) * 18,
                18,
                18,
                9,
                9,
                256,
                256);
            GlStateManager.disableBlend();
          }
        }
      }
    }
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
  }

  private String toRoman(int value) {
    switch (value) {
      case 2:
        return "II";
      case 3:
        return "III";
      case 4:
        return "IV";
      case 5:
        return "V";
      default:
        return String.valueOf(value);
    }
  }
}
