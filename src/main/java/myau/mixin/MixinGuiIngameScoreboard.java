package myau.mixin;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import myau.Myau;
import myau.module.modules.render.HUD;
import myau.module.modules.render.Scoreboard;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngameScoreboard {

  @Shadow
  protected abstract void renderScoreboard(ScoreObjective objective, ScaledResolution scaledRes);

  private static boolean isRenderingBloom = false;
  private static boolean openMyauCustomScoreboardRendered = false;

  @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
  private void onRenderScoreboardPre(
      ScoreObjective objective, ScaledResolution scaledRes, CallbackInfo ci) {
    openMyauCustomScoreboardRendered = false;

    if (!isRenderingBloom) {
      HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
      if (hud != null && hud.isEnabled() && hud.shaders.getValue()) {
        isRenderingBloom = true;

        myau.util.shader.BlurUtils.prepareBloom();
        this.renderScoreboard(objective, scaledRes);
        myau.util.shader.BlurUtils.bloomEnd(5, 24.0f);

        this.renderScoreboard(objective, scaledRes);

        isRenderingBloom = false;
        ci.cancel();
        return;
      }
    }

    if (Myau.moduleManager != null) {
      Scoreboard scoreboardMod = (Scoreboard) Myau.moduleManager.getModule(Scoreboard.class);
      if (scoreboardMod != null && scoreboardMod.isEnabled()) {
        scoreboardMod.updateBounds(scaledRes);
        GlStateManager.pushMatrix();
        GlStateManager.translate(
            (float) scoreboardMod.drag.position.x - scoreboardMod.defaultX,
            (float) scoreboardMod.drag.position.y - scoreboardMod.defaultY,
            0.0f);

        ci.cancel();
        renderTenacityScoreboard(objective, scaledRes, scoreboardMod);
        openMyauCustomScoreboardRendered = true;

        GlStateManager.popMatrix();
      }
    }
  }

  @Inject(method = "renderScoreboard", at = @At("RETURN"))
  private void onRenderScoreboardPost(
      ScoreObjective objective, ScaledResolution scaledRes, CallbackInfo ci) {
    if (openMyauCustomScoreboardRendered) {
      openMyauCustomScoreboardRendered = false;
      return;
    }
    if (Myau.moduleManager != null) {
      Scoreboard scoreboardMod = (Scoreboard) Myau.moduleManager.getModule(Scoreboard.class);
      if (scoreboardMod != null && scoreboardMod.isEnabled()) {
        GlStateManager.popMatrix();
      }
    }
  }

  /**
   * Tenacity-style scoreboard rendering with semi-transparent background, optional red numbers, and
   * text shadow support.
   */
  private void renderTenacityScoreboard(
      ScoreObjective objective, ScaledResolution scaledRes, Scoreboard scoreboardMod) {
    net.minecraft.scoreboard.Scoreboard scoreboard = objective.getScoreboard();
    Collection<Score> collection = scoreboard.getSortedScores(objective);
    List<Score> list = new ArrayList<>();
    for (Score score : collection) {
      if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
        list.add(score);
      }
    }

    if (list.size() > 15) {
      list = list.subList(list.size() - 15, list.size());
    }

    int imageY = scaledRes.getScaledHeight() / 2 + list.size() * getFontHeight() / 3;
    int lineCount = 0;

    int maxWidth = getStringWidth(objective.getDisplayName());
    for (Score score : list) {
      ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
      String name =
          ScorePlayerTeam.formatPlayerName(team, score.getPlayerName())
              + ": "
              + score.getScorePoints();
      maxWidth = Math.max(maxWidth, getStringWidth(name));
    }

    float xOffset = scaledRes.getScaledWidth() - maxWidth - 3;
    Color bgColor = new Color(0, 0, 0, 75);

    for (Score score : list) {
      lineCount++;
      ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
      String playerName = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
      int y = imageY - lineCount * getFontHeight();
      int rightEdge = scaledRes.getScaledWidth() - 3 + 2;

      Gui.drawRect((int) (xOffset - 2), y, rightEdge, y + getFontHeight(), bgColor.getRGB());

      boolean shadow = scoreboardMod.textShadow.getValue();
      drawString(playerName, xOffset, y, -1, shadow);

      String scoreText;
      if (scoreboardMod.redNumbers.getValue()) {
        scoreText = EnumChatFormatting.RED + "" + score.getScorePoints();
      } else {
        scoreText = " " + score.getScorePoints();
      }
      drawString(scoreText, rightEdge - getStringWidth(scoreText), y, -1, shadow);

      if (lineCount == list.size()) {
        String title = objective.getDisplayName();
        int titleY = y - getFontHeight() - 1;
        Gui.drawRect((int) (xOffset - 2), titleY, rightEdge, y - 1, bgColor.getRGB());
        GlStateManager.enableBlend();
        Gui.drawRect((int) (xOffset - 2), y - 1, rightEdge, y, bgColor.getRGB());

        float titleX = xOffset + maxWidth / 2.0F - getStringWidth(title) / 2.0F;
        drawString(title, titleX, titleY, -1, shadow);
      }
    }
  }

  private int getFontHeight() {
    return net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
  }

  private int getStringWidth(String text) {
    return net.minecraft.client.Minecraft.getMinecraft().fontRendererObj.getStringWidth(text);
  }

  private void drawString(String text, double x, double y, int color, boolean shadow) {
    if (shadow) {
      net.minecraft.client.Minecraft.getMinecraft()
          .fontRendererObj
          .drawStringWithShadow(text, (float) x, (float) y, color);
    } else {
      net.minecraft.client.Minecraft.getMinecraft()
          .fontRendererObj
          .drawString(text, (int) x, (int) y, color);
    }
  }
}
