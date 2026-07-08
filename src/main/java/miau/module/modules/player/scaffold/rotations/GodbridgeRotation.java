package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;

public class GodbridgeRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    float roundedYaw = Math.round(currentYaw / 45.0f) * 45.0f;
    scaffold.yaw = RotationUtil.quantizeAngle(roundedYaw);
    if (scaffold.pitch == 0.0F || !scaffold.canRotate) {
      scaffold.pitch = RotationUtil.quantizeAngle(79.3f);
    }
  }
}
