package myau.module.modules.misc.cheatdetector.impl.movement.noslow.subchecks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import myau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class NoSlowB extends Check {
  private final Map<UUID, Integer> motionBufferMap = new HashMap<>();

  @Override
  public String getName() {
    return "NoSlow B";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    int motionBuffer = motionBufferMap.getOrDefault(player.getUniqueID(), 0);

    if (player.isUsingItem() && player.hurtTime == 0) {
      // NCP ass check
      if ((Math.hypot(player.motionX, player.motionZ) > 0.15 && player.onGround)
          || (!player.onGround && Math.hypot(player.motionX, player.motionZ) > 0.3)) {
        motionBuffer++;

        if (motionBuffer > 5) {
          flag(player, "Moving too fast while using an item");
          motionBuffer = 0;
        }
      }

      motionBufferMap.put(uuid, motionBuffer);
    }
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    motionBufferMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
