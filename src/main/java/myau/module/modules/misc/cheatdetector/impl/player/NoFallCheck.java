package myau.module.modules.misc.cheatdetector.impl.player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import myau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class NoFallCheck extends Check {
  private final Map<UUID, Boolean> fallingBoolMap = new HashMap<>();

  @Override
  public String getName() {
    return "No fall";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    boolean fall = fallingBoolMap.getOrDefault(uuid, false);

    if (player.fallDistance > player.getMaxFallHeight()
        && player.getAge() > 20
        && !player.capabilities.disableDamage
        && !player.capabilities.allowFlying) {
      fall = true;
    }

    if (fall
        && player.fallDistance == 0
        && player.hurtTime == 0
        && !player.isInWater()
        && player.onGround) {
      flag(player, "Not taking fall damage");
      fall = false;
    }

    fallingBoolMap.put(uuid, fall);
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    fallingBoolMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
