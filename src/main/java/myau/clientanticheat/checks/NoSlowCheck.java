package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.CheckBuffer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * NoSlow detection. Flags players who move at sprint speed while using items (eating, blocking,
 * bowing).
 */
public class NoSlowCheck {
  private final Map<String, CheckBuffer> noSlowBuffers = new HashMap<>();
  private final Map<String, Integer> suspiciousTicks = new HashMap<>();

  private static final double MIN_SPEED_THRESHOLD = 0.18D;
  private static final int REQUIRED_TICKS = 10;

  public void check(EntityPlayer player) {
    if (player == null) return;
    String name = player.getName();
    if (name == null) return;

    boolean usingItem = player.isUsingItem() || player.isBlocking();
    if (!usingItem) {
      Integer st = suspiciousTicks.get(name);
      if (st != null && st > 0) {
        suspiciousTicks.put(name, Math.max(0, st - 1));
      }
      return;
    }

    double speed = Math.hypot(player.posX - player.lastTickPosX, player.posZ - player.lastTickPosZ);

    // If moving fast while using item
    if (speed > MIN_SPEED_THRESHOLD) {
      int ticks = suspiciousTicks.getOrDefault(name, 0) + 1;
      suspiciousTicks.put(name, ticks);

      if (ticks >= REQUIRED_TICKS) {
        CheckBuffer buf = noSlowBuffers.computeIfAbsent(name, k -> new CheckBuffer());
        if (buf.flag(1.0D, 5.0D)) {
          AlertManager.flag(name, "NoSlow", String.format("%.2f speed", speed), ticks);
          buf.reset();
        }
        suspiciousTicks.put(name, 0);
      }
    } else {
      suspiciousTicks.put(name, 0);
    }
  }

  public void reset() {
    noSlowBuffers.clear();
    suspiciousTicks.clear();
  }
}
