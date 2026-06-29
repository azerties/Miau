package myau.module.modules.misc.cheatdetector.impl.combat;

import myau.event.impl.PacketEvent;
import myau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class VelocityCheck extends Check {

  @Override
  public String getName() {
    return "Velocity";
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    if (Math.hypot(player.motionX, player.motionZ) == 0.0
        && player.hurtTime < 6
        && player.hurtTime > 2
        && !mc.theWorld.checkBlockCollision(
            player.getEntityBoundingBox().expand(0.05, 0.0, 0.05))) {
      flag(player, "Invalid velocity");
    }
  }
}
