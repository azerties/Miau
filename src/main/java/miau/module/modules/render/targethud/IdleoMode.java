package miau.module.modules.render.targethud;

import java.awt.Color;
import miau.Miau;
import miau.module.modules.render.HUD;
import miau.module.modules.render.TargetHUD;
import miau.util.animation.Animation;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.render.StencilUtil;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

public class IdleoMode extends TargetHUDMode {
  private final Animation animation = new Animation(Easing.LINEAR, 180);

  public IdleoMode(TargetHUD targetHUD) {
    super(targetHUD);
  }

  @Override
  public void render(EntityLivingBase target, float x, float y) {
    Font font26 = FontRepository.getFont("inter-bold", 26);
    Font font32 = FontRepository.getFont("inter-bold", 32);
    Font font16 = FontRepository.getFont("inter-regular", 16);

    float targetWidth = Math.max(145, font26.getStringWidth(target.getName()) + 40);
    float height = 37;

    parent.drag.scale.x = targetWidth * parent.scale.getValue();
    parent.drag.scale.y = height * parent.scale.getValue();

    float scaleValue = parent.scale.getValue();
    x = (float) parent.drag.position.x / scaleValue;
    y = (float) parent.drag.position.y / scaleValue;

    GlStateManager.pushMatrix();
    GlStateManager.scale(scaleValue, scaleValue, 1.0F);

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    Color c1 = hud.getColor(0);
    Color c2 = hud.getColor(100);
    Color color = new Color(20, 18, 18, 90);
    int textColor = -1;

    RoundedUtils.drawRound(x, y, targetWidth, height, 4, color);

    if (target instanceof AbstractClientPlayer) {
      StencilUtil.initStencilToWrite();
      RoundedUtils.drawRound(x + 3, y + 3, 31, 31, 4, Color.WHITE);
      StencilUtil.readStencilBuffer(1);
      RenderUtil.resetColor();
      GlStateManager.color(1, 1, 1, 1);
      renderPlayer2D(x + 3, y + 3, 31, 31, (AbstractClientPlayer) target);
      StencilUtil.uninitStencilBuffer();
      GlStateManager.disableBlend();
    } else {
      font32.draw(
          "?",
          x + 20 - font32.getStringWidth("?") / 2f,
          y + 17 - font32.getFontHeight() / 2f,
          textColor,
          true);
    }

    font26.draw(target.getName(), x + 39, y + 5, textColor, true);

    float healthPercent =
        MathHelper.clamp_float(
            (target.getHealth() + target.getAbsorptionAmount())
                / (target.getMaxHealth() + target.getAbsorptionAmount()),
            0,
            1);

    float realHealthWidth = targetWidth - 44;
    float realHealthHeight = 3;
    animation.run(healthPercent * realHealthWidth);
    Color backgroundHealthColor = new Color(0, 0, 0, 110);
    float healthWidth = animation.getValue();

    RoundedUtils.drawRound(
        x + 39, y + height - 12, realHealthWidth, realHealthHeight, 1.5f, backgroundHealthColor);
    RoundedUtils.drawGradientHorizontal(
        x + 39, y + height - 12, healthWidth, realHealthHeight, 1.5f, c1, c2);

    GlStateManager.popMatrix();
  }
}
