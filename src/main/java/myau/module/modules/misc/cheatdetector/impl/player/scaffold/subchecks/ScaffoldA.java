package myau.module.modules.misc.cheatdetector.impl.player.scaffold.subchecks;

import static myau.module.modules.misc.cheatdetector.impl.player.scaffold.ScaffoldCheck.blocksPlacedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import myau.module.modules.misc.cheatdetector.Check;
import myau.util.player.MoveUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;

public class ScaffoldA extends Check {

  @Override
  public String getName() {
    return "Scaffold A";
  }

  private final Map<UUID, Float> cachedYawMap = new HashMap<>();
  private final Map<UUID, Integer> yawSnapCountMap = new HashMap<>();

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    float cachedYaw = cachedYawMap.getOrDefault(uuid, 0.0f);
    int blocksPlaced = blocksPlacedMap.getOrDefault(uuid, 0);
    int yawSnapCount = yawSnapCountMap.getOrDefault(uuid, 0);

    boolean bridgingCheck =
        player.rotationPitch > 70
            && player.getHeldItem() != null
            && player.getHeldItem().getItem() instanceof ItemBlock;

    if (bridgingCheck) {
      if (!MoveUtil.isMoving()
          || mc.gameSettings.keyBindJump.isKeyDown()
          || player.isSneaking()
          || !player.onGround
          || MoveUtil.getSpeed() < 0.1) blocksPlaced = 0;
      if (Math.abs(player.rotationYawHead - cachedYaw) > 45 && !player.isSneaking()) {
        yawSnapCount++;

        if (yawSnapCount > 2) {
          flag(player, "Suspicious yaw change");
          yawSnapCount = 0;
        }
      }
    }

    if (blocksPlaced > 7) {
      yawSnapCount = 0;
    }

    cachedYaw = player.rotationYawHead;

    cachedYawMap.put(uuid, cachedYaw);
    yawSnapCountMap.put(uuid, yawSnapCount);
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    cachedYawMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    yawSnapCountMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
