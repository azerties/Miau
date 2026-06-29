package myau.module.modules.misc.cheatdetector.impl.combat.aim.subchecks;

import myau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class AimA extends Check {

  @Override
  public String getName() {
    return "Aim A";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (Math.abs(player.rotationYawHead - player.prevRotationYawHead) > 175
        && player.swingProgress != 0) {
      flag(player, "Impossible yaw change");
    }
  }
}
