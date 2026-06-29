package myau.module.modules.misc.cheatdetector.impl.player.scaffold.subchecks;

import static myau.module.modules.misc.cheatdetector.impl.player.scaffold.ScaffoldCheck.blocksPlacedMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import myau.module.modules.misc.cheatdetector.Check;
import myau.util.time.TimerUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;

public class ScaffoldC extends Check {
  private final Map<UUID, Double> lastYMap = new HashMap<>();
  private final Map<UUID, Boolean> onGroundMap = new HashMap<>();
  private final Map<UUID, Boolean> wasBridgingMap = new HashMap<>();
  private final Map<UUID, TimerUtil> bridgingTimerMap = new HashMap<>();
  private final TimerUtil flagTimer = new TimerUtil();

  @Override
  public String getName() {
    return "Scaffold C";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    int blocksPlaced = blocksPlacedMap.getOrDefault(uuid, 0);
    TimerUtil bridgingTimer = bridgingTimerMap.computeIfAbsent(uuid, k -> new TimerUtil());

    boolean bridgingCheck =
        player.rotationPitch > 70
            && player.getHeldItem() != null
            && player.getHeldItem().getItem() instanceof ItemBlock;

    if (bridgingCheck) {
      bridgingTimer.reset();
    }

    // to make sure you don't false when not placing blocks
    if (bridgingTimer.hasTimeElapsed(1000L)) {
      blocksPlaced = 0;
      bridgingTimer.reset();
    }

    // might false for breezily and shit like that, but I don't care
    if (blocksPlaced > 6 && flagTimer.hasTimeElapsed(500)) {
      flag(player, "Suspicious block placement");
      flagTimer.reset();
    }
    bridgingTimerMap.put(uuid, bridgingTimer);
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    bridgingTimerMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    bridgingTimerMap.values().removeIf(timerUtils -> timerUtils.hasTimeElapsed(1000L));
  }
}
