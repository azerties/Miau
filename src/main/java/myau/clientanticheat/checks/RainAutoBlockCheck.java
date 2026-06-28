package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.PlayerEligibility;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Rain Anticheat port — AutoBlock detection. Flags players who swing while blocking (sword
 * blocking).
 */
public class RainAutoBlockCheck {
  private static final int FAIL_TICKS = 10;
  private final Map<UUID, Integer> autoBlockTicks = new HashMap<>();

  public void check(EntityPlayer player) {
    if (player == null) return;
    UUID uuid = player.getUniqueID();
    if (uuid == null) return;
    if (!PlayerEligibility.shouldCheckPlayer(player)) {
      forgetPlayer(uuid);
      return;
    }

    if (player.isSwingInProgress && player.isBlocking()) {
      int ticks = autoBlockTicks.getOrDefault(uuid, 0) + 1;
      autoBlockTicks.put(uuid, ticks);
      if (ticks > FAIL_TICKS) {
        AlertManager.flag(player.getName(), "AutoBlock", "swing-block", ticks);
      }
    } else {
      autoBlockTicks.remove(uuid);
    }
  }

  public void forgetPlayer(UUID uuid) {
    if (uuid != null) autoBlockTicks.remove(uuid);
  }

  public void reset() {
    autoBlockTicks.clear();
  }
}
