package myau.clientanticheat.movement.velocity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class VelocityCheck {
  private final Map<String, CheckBuffer> horizontalBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> verticalBuffers = new HashMap<>();
  private final Map<String, LinkedList<Double>> verticalHistory = new HashMap<>();
  private final Map<String, LinkedList<Double>> horizontalHistory = new HashMap<>();

  private static final int VELOCITY_HISTORY_SIZE = 10;

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (data.recentlyHurt()) {
      return;
    }

    if (data.collidedHorizontally || player.isCollidedVertically) {
      decayAll(name);
      return;
    }

    if (player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.capabilities.isFlying) {
      decayAll(name);
      return;
    }

    CheckBuffer horizontalBuffer =
        this.horizontalBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer verticalBuffer =
        this.verticalBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    LinkedList<Double> verticalSamples =
        this.verticalHistory.computeIfAbsent(name, key -> new LinkedList<>());
    LinkedList<Double> horizontalSamples =
        this.horizontalHistory.computeIfAbsent(name, key -> new LinkedList<>());

    double verticalDelta = Math.abs(data.deltaY);
    double horizontalDelta = data.horizontalDelta;

    if (data.hurtTicks > 0 && data.hurtTicks < 20) {
      if (verticalDelta > 0.01D) {
        verticalSamples.addFirst(verticalDelta);
        if (verticalSamples.size() > VELOCITY_HISTORY_SIZE) verticalSamples.removeLast();

        if (verticalSamples.size() >= 3 && data.groundTicks < 5) {
          double expectedVertical =
              verticalSamples.get(1) > 0.3D ? verticalSamples.get(1) * 0.9D : 0.35D;
          double minExpected = expectedVertical * 0.7D;

          if (verticalDelta < minExpected && verticalDelta > 0.01D) {
            double flagWeight = Math.min(3.0D, (minExpected - verticalDelta) * 5.0D + 1.0D);
            if (verticalBuffer.flag(flagWeight, 3.5D)) {
              context.receiveSignal(name, "Velocity (Vertical)");
              verticalBuffer.reset();
              verticalSamples.clear();
            }
          } else {
            verticalBuffer.decay(0.25D);
          }
        }
      } else {
        verticalBuffer.decay(0.15D);
      }

      if (horizontalDelta > 0.25D && data.deltaY < -0.01D) {
        horizontalSamples.addFirst(horizontalDelta);
        if (horizontalSamples.size() > VELOCITY_HISTORY_SIZE) horizontalSamples.removeLast();

        if (horizontalSamples.size() >= 3) {
          double averageHorizontal = 0;
          for (double v : horizontalSamples) averageHorizontal += v;
          averageHorizontal /= horizontalSamples.size();
          double ratio = horizontalDelta / averageHorizontal;

          if (ratio < 0.5D && ratio > 0.01D && averageHorizontal > 0.1D) {
            double flagWeight = Math.min(3.0D, (0.5D - ratio) * 10.0D + 1.0D);
            if (horizontalBuffer.flag(flagWeight, 4.0D)) {
              context.receiveSignal(name, "Velocity (Horizontal)");
              horizontalBuffer.reset();
              horizontalSamples.clear();
            }
          } else {
            horizontalBuffer.decay(0.3D);
          }
        }
      } else {
        horizontalBuffer.decay(0.15D);
      }
    } else {
      if (data.groundTicks > 3) {
        verticalBuffer.decay(0.2D);
        horizontalBuffer.decay(0.2D);
      }
    }

    if (verticalBuffer.get() > 3.0D && horizontalBuffer.get() > 3.0D) {
      context.receiveSignal(name, "Velocity (Combined)");
      verticalBuffer.reset();
      horizontalBuffer.reset();
    }
  }

  private void decayAll(String name) {
    CheckBuffer h = this.horizontalBuffers.get(name);
    if (h != null) h.decay(0.2D);
    CheckBuffer v = this.verticalBuffers.get(name);
    if (v != null) v.decay(0.2D);
  }

  public void reset() {
    this.horizontalBuffers.clear();
    this.verticalBuffers.clear();
    this.verticalHistory.clear();
    this.horizontalHistory.clear();
  }
}
