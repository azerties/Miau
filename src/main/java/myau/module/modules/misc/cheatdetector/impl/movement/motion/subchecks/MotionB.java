package myau.module.modules.misc.cheatdetector.impl.movement.motion.subchecks;

import myau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class MotionB extends Check {

  @Override
  public String getName() {
    return "Motion B";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.capabilities.isFlying) {
      return;
    }

    if (player.motionY == 0
        && !player.onGround
        && Math.hypot(player.motionX, player.motionZ) > 0.05) {
      flag(player, "Ignoring gravity");
    }
  }
}
