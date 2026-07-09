package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;

public class BackwardsRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    if (scaffold.yaw == -180.0F && scaffold.pitch == 0.0F) {
      scaffold.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
      scaffold.pitch = RotationUtil.quantizeAngle(85.0F);
    } else {
      scaffold.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
    }
    scaffold.bridgeYaw = scaffold.yaw;
  }
}
