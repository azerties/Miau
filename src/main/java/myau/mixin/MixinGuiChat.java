package myau.mixin;

import java.awt.*;
import java.io.IOException;
import myau.util.animation.Direction;
import myau.util.animation.impl.DecelerateAnimation;
import myau.util.client.ChatUtil;
import myau.util.shader.RoundedUtils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiChat.class)
public abstract class MixinGuiChat extends GuiScreen {

  @Shadow protected GuiTextField inputField;
  @Shadow private String historyBuffer;
  @Shadow private int sentHistoryCursor;

  @Shadow
  public abstract void autocompletePlayerNames();

  @Shadow
  public abstract void getSentHistory(int msgPos);

  @Unique private static float openMyau$cachedAnimOut;

  @Inject(method = "initGui", at = @At("HEAD"))
  private void onInitGui(CallbackInfo ci) {
    ChatUtil.openingAnimation = new DecelerateAnimation(175, 1);
    ChatUtil.openingAnimation.reset();
  }

  @Inject(method = "onGuiClosed", at = @At("HEAD"))
  private void onGuiClosedInject(CallbackInfo ci) {
    ChatUtil.openingAnimation.setDirection(Direction.BACKWARDS);
  }

  @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
  private void onKeyTyped(char typedChar, int keyCode, CallbackInfo ci) throws IOException {
    if (keyCode == 1) { // Escape
      ChatUtil.openingAnimation.setDirection(Direction.BACKWARDS);
      ci.cancel();
    } else if (keyCode == 28 || keyCode == 156) { // Enter
      String s = this.inputField.getText().trim();
      if (s.length() > 0) {
        this.sendChatMessage(s);
      }
      ChatUtil.openingAnimation.setDirection(Direction.BACKWARDS);
      ci.cancel();
    }
  }

  @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
  private void onDrawScreenHead(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
    // Cache animation state once for both HEAD and RETURN
    openMyau$cachedAnimOut = ChatUtil.openingAnimation.getOutput().floatValue();

    if (ChatUtil.openingAnimation.finished(Direction.BACKWARDS)) {
      this.mc.displayGuiScreen((GuiScreen) null);
      ci.cancel();
      return;
    }
    // Set the inputField Y position based on animation for the original drawTextBox call
    this.inputField.yPosition = (int) (this.height - (12 * openMyau$cachedAnimOut));
  }

  @Inject(method = "drawScreen", at = @At("RETURN"))
  private void onDrawScreenReturn(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
    if (openMyau$cachedAnimOut < 0.001f) return;

    float bgHeight = 14 * openMyau$cachedAnimOut;
    float bgY = this.height - bgHeight;

    // Draw rounded chat background with semi-transparent black
    GlStateManager.disableDepth();
    RoundedUtils.drawRound(2, bgY, this.width - 4, 12, 4, new Color(0, 0, 0, 150));
    GlStateManager.enableDepth();
  }
}
