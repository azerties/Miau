package myau.module.modules.combat.velocity;

import myau.event.impl.UpdateEvent;
import myau.module.modules.combat.Velocity;

public class JumpResetVelocity extends VelocityMode {
  public JumpResetVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  private boolean monitorLanding = false;

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == myau.event.types.EventType.PRE) {

      if (mc.thePlayer.hurtTime == 10 && !parent.isInLiquidOrWeb()) {
        monitorLanding = true;
      }
      if (monitorLanding && mc.thePlayer.onGround) {
        mc.thePlayer.jump();
        monitorLanding = false;
      }
      if (monitorLanding && mc.thePlayer.hurtTime == 0) {
        monitorLanding = false;
      }
    }
  }
}
