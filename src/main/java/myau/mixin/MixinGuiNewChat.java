package myau.mixin;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {GuiNewChat.class},
    priority = 9999)
public abstract class MixinGuiNewChat {
  private static final int OPENMYAU_DUPLICATE_CHAT_ID = 873420;
  private String openMyauLastMessage = null;
  private int openMyauDuplicateCount = 1;
  private boolean openMyauReplacingDuplicate = false;

  @Shadow @Final private net.minecraft.client.Minecraft mc;
  @Shadow @Final private java.util.List<net.minecraft.client.gui.ChatLine> drawnChatLines;
  @Shadow private int scrollPos;
  @Shadow private boolean isScrolled;

  @Shadow
  public abstract int getLineCount();

  @Shadow
  public abstract boolean getChatOpen();

  @Shadow
  public abstract float getChatScale();

  @Shadow
  public abstract int getChatWidth();

  @Shadow
  public abstract void deleteChatLine(int id);

  @Shadow
  public abstract void printChatMessageWithOptionalDeletion(
      IChatComponent chatComponent, int chatLineId);

  @Inject(
      method = {"printChatMessageWithOptionalDeletion"},
      at = @At("HEAD"),
      cancellable = true)
  private void openMyau$compactDuplicateChat(
      IChatComponent chatComponent, int chatLineId, CallbackInfo ci) {
    if (openMyauReplacingDuplicate || chatComponent == null || chatLineId != 0) {
      return;
    }

    String message = chatComponent.getUnformattedText();
    if (message == null || message.isEmpty()) {
      openMyauLastMessage = message;
      openMyauDuplicateCount = 1;
      return;
    }

    if (message.equals(openMyauLastMessage)) {
      openMyauDuplicateCount++;
      IChatComponent compacted = chatComponent.createCopy();
      compacted.appendText(" §7[x" + openMyauDuplicateCount + "]");

      ci.cancel();
      openMyauReplacingDuplicate = true;
      this.deleteChatLine(OPENMYAU_DUPLICATE_CHAT_ID);
      this.printChatMessageWithOptionalDeletion(compacted, OPENMYAU_DUPLICATE_CHAT_ID);
      openMyauReplacingDuplicate = false;
    } else {
      if (openMyauDuplicateCount > 1) {
        this.deleteChatLine(OPENMYAU_DUPLICATE_CHAT_ID);
      }
      openMyauLastMessage = message;
      openMyauDuplicateCount = 1;
    }
  }

  private myau.module.modules.render.HUD openMyauCachedHud;

  @Redirect(
      method = "drawChat",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
  private int openMyau$renderCustomChatPrefix(
      net.minecraft.client.gui.FontRenderer fontRenderer,
      String text,
      float x,
      float y,
      int color) {
    if (text != null && text.contains(myau.enums.ChatColors.PREFIX_CLEAN)) {
      if (openMyauCachedHud == null) {
        openMyauCachedHud =
            (myau.module.modules.render.HUD)
                myau.Myau.moduleManager.getModule(myau.module.modules.render.HUD.class);
      }
      if (openMyauCachedHud != null && openMyauCachedHud.isEnabled()) {
        int prefixIndex = text.indexOf(myau.enums.ChatColors.PREFIX_CLEAN);
        String before = text.substring(0, prefixIndex);
        float currentX = x;

        if (!before.isEmpty()) {
          currentX = fontRenderer.drawStringWithShadow(before, currentX, y, color);
        }

        long time = System.currentTimeMillis();
        int alpha = (color >> 24) & 0xFF;

        for (int i = 0; i < myau.enums.ChatColors.PREFIX_CLEAN.length(); i++) {
          char c = myau.enums.ChatColors.PREFIX_CLEAN.charAt(i);
          int charColor = openMyauCachedHud.getColor(time, i * 15).getRGB();
          int finalColor = (alpha << 24) | (charColor & 0x00FFFFFF);
          currentX = fontRenderer.drawStringWithShadow(String.valueOf(c), currentX, y, finalColor);
        }

        String after = text.substring(prefixIndex + myau.enums.ChatColors.PREFIX_CLEAN.length());
        if (!after.isEmpty()) {
          currentX = fontRenderer.drawStringWithShadow(after, currentX, y, color);
        }
        return (int) currentX;
      }
    }
    return fontRenderer.drawStringWithShadow(text, x, y, color);
  }

  /**
   * Redirect the first GlStateManager.translate call in drawChat to apply the opening animation Y
   * offset (Tenacity style).
   */
  @Redirect(
      method = "drawChat",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
              ordinal = 0))
  private void redirectChatTranslate(float x, float y, float z) {
    float animY = 17.0F;
    try {
      if (myau.util.client.ChatUtil.openingAnimation != null) {
        animY =
            17.0F - (16.0F * myau.util.client.ChatUtil.openingAnimation.getOutput().floatValue());
      }
    } catch (Exception e) {
      animY = 17.0F;
    }
    net.minecraft.client.renderer.GlStateManager.translate(x, animY, z);
  }

  /** Before drawChat renders, push matrix and set up blur if needed. */
  @Inject(method = "drawChat", at = @At("HEAD"))
  private void onDrawChatPre(int updateCounter, CallbackInfo ci) {
    try {
      if (mc.theWorld == null || mc.thePlayer == null) return;

      if (openMyauCachedHud == null) {
        openMyauCachedHud =
            (myau.module.modules.render.HUD)
                myau.Myau.moduleManager.getModule(myau.module.modules.render.HUD.class);
      }
    } catch (Exception e) {
      // silently ignore - don't break the render pipeline
    }
  }
}
