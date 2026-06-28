package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.CheckBuffer;
import net.minecraft.entity.player.EntityPlayer;

/** Velocity (anti-knockback) detection. Flags abnormally low vertical/horizontal knockback. */
public class VelocityCheck {
  private final Map<String, CheckBuffer> verticalBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> horizontalBuffers = new HashMap<>();
  private final Map<String, Double> lastHurtY = new HashMap<>();
  private final Map<String, Double> lastHurtH = new HashMap<>();
  private final Map<String, Long> lastHurtTime = new HashMap<>();

  private static final double MIN_KB_THRESHOLD_Y = 0.28D;
  private static final double MIN_KB_THRESHOLD_H = 0.10D;
  private static final long HURT_TIMEOUT = 1000L;

  public void check(EntityPlayer player) {
    if (player == null || player.hurtTime <= 0) return;
    String name = player.getName();
    if (name == null) return;

    long now = System.currentTimeMillis();
    Long lastHurt = lastHurtTime.get(name);
    if (lastHurt != null && now - lastHurt < 500L) return;
    lastHurtTime.put(name, now);

    // Track vertical velocity during hurt
    double velY = player.motionY;
    double velH = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);

    Double lastY = lastHurtY.get(name);
    Double lastH = lastHurtH.get(name);

    if (lastY != null && lastH != null) {
      // If knockback is too small, flag
      if (velY > -0.01D && velY < 0.01D && velH < 0.01D) {
        // Full anti-knockback
        CheckBuffer buf = verticalBuffers.computeIfAbsent(name, k -> new CheckBuffer());
        if (buf.flag(2.0D, 4.0D)) {
          AlertManager.flag(name, "Velocity", "100% reduction", 100);
          buf.reset();
        }
      } else if (velY > -MIN_KB_THRESHOLD_Y && velY > 0) {
        // Reduced vertical kb
        CheckBuffer buf = verticalBuffers.computeIfAbsent(name, k -> new CheckBuffer());
        if (buf.flag(1.0D, 5.0D)) {
          AlertManager.flag(name, "Velocity", "vertical reduced", (int) ((1.0 - velY / 0.4) * 100));
          buf.reset();
        }
      }

      if (velH < MIN_KB_THRESHOLD_H && velY > -MIN_KB_THRESHOLD_Y) {
        CheckBuffer buf = horizontalBuffers.computeIfAbsent(name, k -> new CheckBuffer());
        if (buf.flag(1.0D, 5.0D)) {
          AlertManager.flag(
              name, "Velocity", "horizontal reduced", (int) ((1.0 - velH / 0.2) * 100));
          buf.reset();
        }
      }

      // Decay
      CheckBuffer vb = verticalBuffers.get(name);
      if (vb != null) vb.decay(0.1);
      CheckBuffer hb = horizontalBuffers.get(name);
      if (hb != null) hb.decay(0.1);
    }

    lastHurtY.put(name, velY);
    lastHurtH.put(name, velH);
  }

  public void reset() {
    verticalBuffers.clear();
    horizontalBuffers.clear();
    lastHurtY.clear();
    lastHurtH.clear();
    lastHurtTime.clear();
  }
}
