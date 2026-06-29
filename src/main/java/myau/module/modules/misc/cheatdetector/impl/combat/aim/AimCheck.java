package myau.module.modules.misc.cheatdetector.impl.combat.aim;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import myau.module.modules.misc.cheatdetector.Check;
import myau.module.modules.misc.cheatdetector.impl.combat.aim.subchecks.AimA;
import myau.module.modules.misc.cheatdetector.impl.combat.aim.subchecks.AimB;
import net.minecraft.entity.player.EntityPlayer;

public class AimCheck extends Check {
  private final List<Check> checks = Arrays.asList(new AimA(), new AimB());

  @Override
  public String getName() {
    return "Aim";
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
