package myau.mixin;

import myau.Myau;
import myau.enums.ChatColors;
import myau.module.modules.misc.AntiObfuscate;
import myau.module.modules.misc.NickHider;
import myau.module.modules.render.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = FontRenderer.class, priority = 9999)
public abstract class MixinFontRenderer {

  @Shadow
  public abstract int drawString(String text, float x, float y, int color, boolean dropShadow);

  @Shadow public int FONT_HEIGHT;

  private boolean isCustomRendering = false;

  private void updateHeight() {
    if (Myau.moduleManager == null) return;
    this.FONT_HEIGHT = myau.util.font.ClientFontManager.isMinecraftSelected() ? 9 : 9;
  }

  @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), ordinal = 0, argsOnly = true)
  private String modifyGetStringWidth(String string) {
    if (string == null || Myau.moduleManager == null) return string;

    AntiObfuscate antiObfuscate =
        (AntiObfuscate) Myau.moduleManager.modules.get(AntiObfuscate.class);
    if (antiObfuscate != null && antiObfuscate.isEnabled()) {
      string = antiObfuscate.stripObfuscated(string);
    }

    NickHider nickHider = (NickHider) Myau.moduleManager.modules.get(NickHider.class);
    if (nickHider != null && nickHider.isEnabled()) {
      string = nickHider.replaceNick(string);
    }

    return string;
  }

  @Inject(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
  public void onDrawString(
      String text,
      float x,
      float y,
      int color,
      boolean dropShadow,
      CallbackInfoReturnable<Integer> cir) {
    updateHeight();
    if (isCustomRendering || text == null || text.isEmpty() || Myau.moduleManager == null) {
      return;
    }

    GL11.glColor4f(1f, 1f, 1f, 1f);
    GlStateManager.color(1f, 1f, 1f, 1f);

    AntiObfuscate antiObfuscate =
        (AntiObfuscate) Myau.moduleManager.modules.get(AntiObfuscate.class);
    if (antiObfuscate != null && antiObfuscate.isEnabled()) {
      text = antiObfuscate.stripObfuscated(text);
    }

    NickHider nickHider = (NickHider) Myau.moduleManager.modules.get(NickHider.class);
    if (nickHider != null && nickHider.isEnabled()) {
      text = nickHider.replaceNick(text);
    }

    Minecraft mc = Minecraft.getMinecraft();
    HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);

    String targetName = "";
    if (mc.thePlayer != null) {
      targetName = mc.thePlayer.getName();
      if (nickHider != null && nickHider.isEnabled()) {
        targetName =
            EnumChatFormatting.getTextWithoutFormattingCodes(
                ChatColors.formatColor(nickHider.protectName.getValue()));
      }
    }

    if (!myau.util.font.ClientFontManager.isMinecraftSelected()) {
      isCustomRendering = true;
      try {
        if (!targetName.isEmpty() && text.contains(targetName)) {
          cir.setReturnValue(drawTrueRGBCustomName(text, x, y, color, dropShadow, targetName, hud));
        } else {
          myau.util.font.Font font = myau.util.font.Fonts.MAIN.get(18);
          font.draw(text, x, y, color, dropShadow);
          cir.setReturnValue((int) (x + font.width(text)));
        }
      } finally {
        isCustomRendering = false;
      }
      return;
    }

    if (!targetName.isEmpty() && text.contains(targetName)) {
      isCustomRendering = true;
      int result = drawTrueRGBName(text, x, y, color, dropShadow, targetName, hud);
      isCustomRendering = false;
      cir.setReturnValue(result);
    }
  }

  @Inject(method = "getStringWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
  public void onGetStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
    updateHeight();
    if (isCustomRendering) return;
    if (!myau.util.font.ClientFontManager.isMinecraftSelected() && Myau.moduleManager != null) {
      if (text == null) {
        cir.setReturnValue(0);
        return;
      }

      isCustomRendering = true;
      try {
        AntiObfuscate antiObfuscate =
            (AntiObfuscate) Myau.moduleManager.modules.get(AntiObfuscate.class);
        if (antiObfuscate != null && antiObfuscate.isEnabled()) {
          text = antiObfuscate.stripObfuscated(text);
        }

        NickHider nickHider = (NickHider) Myau.moduleManager.modules.get(NickHider.class);
        if (nickHider != null && nickHider.isEnabled()) {
          text = nickHider.replaceNick(text);
        }

        myau.util.font.Font font = myau.util.font.Fonts.MAIN.get(18);
        cir.setReturnValue(font.width(text));
      } finally {
        isCustomRendering = false;
      }
    }
  }

  @Inject(method = "getCharWidth(C)I", at = @At("HEAD"), cancellable = true)
  public void onGetCharWidth(char character, CallbackInfoReturnable<Integer> cir) {
    updateHeight();
    if (isCustomRendering) return;
    if (!myau.util.font.ClientFontManager.isMinecraftSelected()) {
      isCustomRendering = true;
      try {
        myau.util.font.Font font = myau.util.font.Fonts.MAIN.get(18);
        cir.setReturnValue(font.width(String.valueOf(character)));
      } finally {
        isCustomRendering = false;
      }
    }
  }

  private int drawTrueRGBCustomName(
      String text,
      float x,
      float y,
      int originalColor,
      boolean dropShadow,
      String targetName,
      HUD hud) {
    myau.util.font.Font font = myau.util.font.Fonts.MAIN.get(18);
    float currentX = x;
    String remaining = text;
    int index = remaining.indexOf(targetName);
    long time = System.currentTimeMillis();
    String currentFormat = "";

    while (index != -1) {
      String before = remaining.substring(0, index);
      if (!before.isEmpty()) {
        String textToDraw = currentFormat + before;
        font.draw(textToDraw, currentX, y, originalColor, dropShadow);
        currentX += font.width(textToDraw);
        currentFormat = FontRenderer.getFormatFromString(textToDraw);
      }

      for (int i = 0; i < targetName.length(); i++) {
        String charStr = String.valueOf(targetName.charAt(i));
        int charColor = hud != null ? hud.getColor(time, i * 15L).getRGB() : -1;
        font.draw(charStr, currentX, y, charColor, dropShadow);
        currentX += font.width(charStr);
      }

      remaining = remaining.substring(index + targetName.length());
      index = remaining.indexOf(targetName);
    }

    if (!remaining.isEmpty()) {
      font.draw(currentFormat + remaining, currentX, y, originalColor, dropShadow);
      currentX += font.width(currentFormat + remaining);
    }

    return (int) currentX;
  }

  private int drawTrueRGBName(
      String text,
      float x,
      float y,
      int originalColor,
      boolean dropShadow,
      String targetName,
      HUD hud) {
    float currentX = x;
    String remaining = text;
    int index = remaining.indexOf(targetName);
    long time = System.currentTimeMillis();
    String currentFormat = "";

    while (index != -1) {
      String before = remaining.substring(0, index);
      if (!before.isEmpty()) {
        String textToDraw = currentFormat + before;
        currentX = this.drawString(textToDraw, currentX, y, originalColor, dropShadow);
        currentFormat = FontRenderer.getFormatFromString(textToDraw);
      }

      for (int i = 0; i < targetName.length(); i++) {
        String charStr = String.valueOf(targetName.charAt(i));

        int charColor = hud != null ? hud.getColor(time, i * 15L).getRGB() : -1;

        currentX = this.drawString(charStr, currentX, y, charColor, dropShadow);
      }

      remaining = remaining.substring(index + targetName.length());
      index = remaining.indexOf(targetName);
    }

    if (!remaining.isEmpty()) {
      this.drawString(currentFormat + remaining, currentX, y, originalColor, dropShadow);
    }

    return (int) currentX;
  }
}
