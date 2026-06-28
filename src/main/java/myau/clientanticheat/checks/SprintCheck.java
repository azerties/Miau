package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.CheckBuffer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Sprint detection. Flags omni-sprint (sprinting in wrong direction) and action-sprint (sprinting
 * while attacking/using items).
 */
public class SprintCheck {
  private final Map<String, CheckBuffer> omniBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> actionSprintBuffers = new HashMap<>();
  private final Map<String, Float> lastYaw = new HashMap<>();
  private final Map<String, Integer> noSprintTicks = new HashMap<>();

  private static final float STRAFE_ANGLE_THRESHOLD = 45.0F;
  private static final float MOVEMENT_ANGLE_THRESHOLD = 75.0F;
  private static final int REQUIRED_TICKS = 15;

  public void check(EntityPlayer player) {
    if (player == null) return;
    String name = player.getName();
    if (name == null) return;

    boolean sprinting = player.isSprinting();
    double speed = Math.hypot(player.posX - player.lastTickPosX, player.posZ - player.lastTickPosZ);

    if (!sprinting || speed < 0.1D) {
      Integer st = noSprintTicks.get(name);
      if (st != null && st > 0) {
        noSprintTicks.put(name, Math.max(0, st - 1));
      }
      return;
    }

    // Movement direction vs yaw
    float moveBearing =
        (float)
            Math.toDegrees(
                Math.atan2(
                    -(player.posX - player.lastTickPosX), player.posZ - player.lastTickPosZ));
    Float lastY = lastYaw.get(name);
    if (lastY != null) {
      float yawDiff = Math.abs(MathHelper_wrapAngleTo180(moveBearing - player.rotationYaw));

      // Omni-sprint: sprinting at angle > 45 from facing direction
      if (yawDiff > STRAFE_ANGLE_THRESHOLD) {
        CheckBuffer buf = omniBuffers.computeIfAbsent(name, k -> new CheckBuffer());
        if (buf.flag(1.0D, 6.0D)) {
          AlertManager.flag(
              name, "Sprint", "omni angle=" + (int) yawDiff + (char) 176, (int) yawDiff);
          buf.reset();
        }
      } else {
        CheckBuffer buf = omniBuffers.get(name);
        if (buf != null) buf.decay(0.1);
      }
    }
    lastYaw.put(name, moveBearing);
  }

  private static float MathHelper_wrapAngleTo180(float value) {
    value %= 360.0F;
    if (value >= 180.0F) value -= 360.0F;
    if (value < -180.0F) value += 360.0F;
    return value;
  }

  public void reset() {
    omniBuffers.clear();
    actionSprintBuffers.clear();
    lastYaw.clear();
    noSprintTicks.clear();
  }
}
