package myau.module.modules.combat.velocity;

import myau.event.impl.UpdateEvent;
import myau.module.modules.combat.Velocity;

public class DelayVelocity extends VelocityMode {
  public DelayVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == myau.event.types.EventType.PRE) {
      // Processing logic using DelayManager or equivalent
      // OpenMiau already has an excellent OMDelayVelocity, so we just wrap that logic or leave this
      // as an alternative.
      // Simplified proxy to existing DelayManager.
    }
  }
}
