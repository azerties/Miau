package myau.module.modules.misc.cheatdetector.impl.movement.motion;

import java.util.Arrays;
import java.util.List;
import myau.event.impl.PacketEvent;
import myau.module.modules.misc.cheatdetector.Check;
import myau.module.modules.misc.cheatdetector.impl.movement.motion.subchecks.MotionA;
import myau.module.modules.misc.cheatdetector.impl.movement.motion.subchecks.MotionB;
import myau.module.modules.misc.cheatdetector.impl.movement.motion.subchecks.MotionC;
import net.minecraft.entity.player.EntityPlayer;

public class MotionCheck extends Check {
  private final List<Check> checks = Arrays.asList(new MotionA(), new MotionB(), new MotionC());

  @Override
  public String getName() {
    return "Motion";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    checks.forEach(check -> check.onUpdate(player));
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    checks.forEach(check -> check.onPacket(e, player));
  }
}
