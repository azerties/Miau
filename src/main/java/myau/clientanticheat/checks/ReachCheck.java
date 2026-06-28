package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.CheckBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;

/**
 * Reach detection via hitbox raytrace analysis. Inspired by RavenbS network-level reach patterns.
 */
public class ReachCheck {
  private final Map<String, CheckBuffer> reachBuffers = new HashMap<>();
  private static final double HITBOX_EXPANSION = 0.5D;
  private static final double BASE_REACH = 3.8D;

  public void check(
      EntityPlayer player,
      double distanceToTarget,
      Vec3 eyesPos,
      Vec3 lookVec,
      AxisAlignedBB targetBB) {
    if (player == null || distanceToTarget > 7.0D) return;
    String name = player.getName();
    if (name == null) return;

    CheckBuffer buffer = reachBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    Vec3 maxReachVec =
        eyesPos.addVector(
            lookVec.xCoord * BASE_REACH * 2.0D,
            lookVec.yCoord * BASE_REACH * 2.0D,
            lookVec.zCoord * BASE_REACH * 2.0D);

    AxisAlignedBB expandedBB = targetBB.expand(HITBOX_EXPANSION, 0.1D, HITBOX_EXPANSION);
    MovingObjectPosition rayTraceResult = expandedBB.calculateIntercept(eyesPos, maxReachVec);

    if (rayTraceResult != null) {
      double distance = eyesPos.distanceTo(rayTraceResult.hitVec);
      if (distance > BASE_REACH) {
        double flagWeight = Math.min(2.0D, (distance - BASE_REACH) * 2.0D);
        if (buffer.flag(flagWeight, 6.0D)) {
          AlertManager.flag(
              name, "Reach", String.format("%.2f blocks", distance), (int) (distance * 10));
          buffer.reset();
        }
      } else {
        buffer.decay(0.15D);
      }
    } else {
      buffer.decay(0.05D);
    }
  }

  public void reset() {
    reachBuffers.clear();
  }
}
