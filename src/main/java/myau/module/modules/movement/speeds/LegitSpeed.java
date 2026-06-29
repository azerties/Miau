package myau.module.modules.movement.speeds;

import myau.event.impl.LivingUpdateEvent;
import myau.module.modules.movement.Speed;

public class LegitSpeed extends SpeedMode {
  public LegitSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {}
}
