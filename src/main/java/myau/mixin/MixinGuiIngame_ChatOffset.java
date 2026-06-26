package myau.mixin;

import myau.util.client.ChatUtil;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tenacity-style HUD chat offset: when chat opens with animation, the player stats, exp bar, horse
 * jump bar, hotbar, and selected item name slide up by 15 pixels * animation progress.
 *
 * <p>PERFORMANCE CRITICAL: Animation value is cached ONCE at the first HEAD inject and reused
 * across all 10 method hooks, eliminating 9 redundant calls to
 * ChatUtil.openingAnimation.getOutput() which internally calls System.currentTimeMillis().
 */
@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame_ChatOffset {

  @Unique private static float openMyau$cachedAnim = 0.0f;

  @Unique
  private void openMyau$pushAnimMatrix() {
    if (openMyau$cachedAnim > 0.001f) {
      GlStateManager.pushMatrix();
      GlStateManager.translate(0, -15 * openMyau$cachedAnim, 0);
    }
  }

  @Unique
  private void openMyau$popAnimMatrix() {
    if (openMyau$cachedAnim > 0.001f) {
      GlStateManager.popMatrix();
    }
  }

  // First method called in render order - cache animation here
  @Inject(method = "renderPlayerStats", at = @At("HEAD"))
  private void onRenderPlayerStatsPre(ScaledResolution scaledRes, CallbackInfo ci) {
    openMyau$cachedAnim = ChatUtil.openingAnimation.getOutput().floatValue();
    openMyau$pushAnimMatrix();
  }

  @Inject(method = "renderPlayerStats", at = @At("RETURN"))
  private void onRenderPlayerStatsPost(ScaledResolution scaledRes, CallbackInfo ci) {
    openMyau$popAnimMatrix();
  }

  @Inject(method = "renderExpBar", at = @At("HEAD"))
  private void onRenderExpBarPre(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMyau$pushAnimMatrix();
  }

  @Inject(method = "renderExpBar", at = @At("RETURN"))
  private void onRenderExpBarPost(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMyau$popAnimMatrix();
  }

  @Inject(method = "renderHorseJumpBar", at = @At("HEAD"))
  private void onRenderHorseJumpBarPre(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMyau$pushAnimMatrix();
  }

  @Inject(method = "renderHorseJumpBar", at = @At("RETURN"))
  private void onRenderHorseJumpBarPost(ScaledResolution scaledRes, int x, CallbackInfo ci) {
    openMyau$popAnimMatrix();
  }

  @Inject(method = "renderTooltip", at = @At("HEAD"))
  private void onRenderTooltipPre(ScaledResolution scaledRes, float partialTicks, CallbackInfo ci) {
    openMyau$pushAnimMatrix();
  }

  @Inject(method = "renderTooltip", at = @At("RETURN"))
  private void onRenderTooltipPost(
      ScaledResolution scaledRes, float partialTicks, CallbackInfo ci) {
    openMyau$popAnimMatrix();
  }

  @Inject(method = "renderSelectedItem", at = @At("HEAD"))
  private void onRenderSelectedItemPre(ScaledResolution scaledRes, CallbackInfo ci) {
    openMyau$pushAnimMatrix();
  }

  @Inject(method = "renderSelectedItem", at = @At("RETURN"))
  private void onRenderSelectedItemPost(ScaledResolution scaledRes, CallbackInfo ci) {
    openMyau$popAnimMatrix();
  }
}
