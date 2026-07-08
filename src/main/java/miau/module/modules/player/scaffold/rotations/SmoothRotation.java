package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldUtils;
import miau.util.math.RandomUtil;
import miau.util.player.RotationUtil;
import net.minecraft.util.MathHelper;

public class SmoothRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    if (scaffold.yaw == -180.0F && scaffold.pitch == 0.0F) {
      scaffold.yaw = RotationUtil.quantizeAngle(diagonalYaw);
      scaffold.pitch = RotationUtil.quantizeAngle(85.0F);
    } else {
      float targetYaw = ScaffoldUtils.isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180;
      float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - scaffold.yaw);
      float pitchDiff = MathHelper.wrapAngleTo180_float(85.0F - scaffold.pitch);
      float yawTolerance =
          scaffold.rotationTick >= 2
              ? RandomUtil.nextFloat(
                  scaffold.options.tellystartrotationminspeed.getValue(),
                  scaffold.options.tellystartrotationmaxspeed.getValue())
              : RandomUtil.nextFloat(
                  scaffold.options.tellynormalrotationminspeed.getValue(),
                  scaffold.options.tellynormalrotationmaxspeed.getValue());
      float pitchTolerance =
          scaffold.rotationTick >= 2
              ? RandomUtil.nextFloat(
                  scaffold.options.tellystartrotationminspeed.getValue(),
                  scaffold.options.tellystartrotationmaxspeed.getValue())
              : RandomUtil.nextFloat(
                  scaffold.options.tellynormalrotationminspeed.getValue(),
                  scaffold.options.tellynormalrotationmaxspeed.getValue());
      scaffold.yaw =
          RotationUtil.quantizeAngle(scaffold.yaw + RotationUtil.clampAngle(yawDiff, yawTolerance));
      scaffold.pitch =
          RotationUtil.quantizeAngle(
              scaffold.pitch + RotationUtil.clampAngle(pitchDiff, pitchTolerance));
    }
  }
}
