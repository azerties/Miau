package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.CheckBuffer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Blink/FakeLag detection. Flags players who stop sending movement updates (position freezes) then
 * teleport to new positions.
 */
public class BlinkCheck {
  private final Map<String, double[]> lastPositions = new HashMap<>();
  private final Map<String, CheckBuffer> blinkBuffers = new HashMap<>();
  private final Map<String, Integer> frozenTicks = new HashMap<>();

  private static final double BLINK_DISTANCE_THRESHOLD = 3.0D;
  private static final double MIN_MOVE_THRESHOLD = 0.01D;
  private static final int FROZEN_TICKS_REQUIRED = 15;

  public void check(EntityPlayer player) {
    if (player == null) return;
    String name = player.getName();
    if (name == null) return;

    double[] lastPos = lastPositions.get(name);
    CheckBuffer buf = blinkBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    if (lastPos == null) {
      lastPositions.put(name, new double[] {player.posX, player.posY, player.posZ});
      return;
    }

    double dx = player.posX - lastPos[0];
    double dy = player.posY - lastPos[1];
    double dz = player.posZ - lastPos[2];
    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

    // If barely moved -> count frozen ticks
    double moveDist = Math.hypot(dx, dz);
    if (moveDist < MIN_MOVE_THRESHOLD) {
      int frozen = frozenTicks.getOrDefault(name, 0) + 1;
      frozenTicks.put(name, frozen);

      // After being frozen long enough, check for blink teleport
      if (frozen >= FROZEN_TICKS_REQUIRED && dist > BLINK_DISTANCE_THRESHOLD) {
        if (buf.flag(2.0D, 4.0D)) {
          AlertManager.flag(
              name, "Blink", String.format("frozen %d ticks, %.1f teleport", frozen, dist), frozen);
          buf.reset();
        }
        frozenTicks.put(name, 0);
      }
    } else {
      frozenTicks.put(name, 0);
      buf.decay(0.1);
    }

    lastPositions.put(name, new double[] {player.posX, player.posY, player.posZ});
  }

  public void reset() {
    lastPositions.clear();
    blinkBuffers.clear();
    frozenTicks.clear();
  }
}
