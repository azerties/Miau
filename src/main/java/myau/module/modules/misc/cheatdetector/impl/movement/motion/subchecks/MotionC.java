package myau.module.modules.misc.cheatdetector.impl.movement.motion.subchecks;

import myau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class MotionC extends Check {

  @Override
  public String getName() {
    return "Motion C";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player != mc.thePlayer) {
      // dfgg
    }
  }
}
