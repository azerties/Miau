package myau.module.modules.misc.cheatdetector.impl.movement.noslow;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import myau.module.modules.misc.cheatdetector.Check;
import myau.module.modules.misc.cheatdetector.impl.movement.noslow.subchecks.NoSlowA;
import myau.module.modules.misc.cheatdetector.impl.movement.noslow.subchecks.NoSlowB;
import net.minecraft.entity.player.EntityPlayer;

public class NoSlowCheck extends Check {
  private final List<Check> checks = Arrays.asList(new NoSlowA(), new NoSlowB());

  @Override
  public String getName() {
    return "No slow";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    checks.forEach(check -> check.onUpdate(player));
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    checks.forEach(check -> check.cleanup(onlineUUIDs));
  }
}
