package myau.clientanticheat.combat.reach;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class HitboxRaytraceCheck {
  private final Map<String, CheckBuffer> reachBuffers = new HashMap<>();
  private final Map<String, LinkedList<Double>> movementVariance = new HashMap<>();
  private final Map<String, Vec3> lastEyesPosition = new HashMap<>();

  private static final double HITBOX_EXPANSION = 0.5D;
  private static final double BASE_REACH = 3.8D;
  private static final double MOVEMENT_TOLERANCE_MULTIPLIER = 2.0D;

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      return;
    }

    CheckBuffer buffer = this.reachBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    EntityPlayer target = data.nearestTarget;
    if (target == null || data.nearestTargetDistance > 7.0D) {
      buffer.decay(0.2D);
      return;
    }

    Vec3 eyesPos = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    Double variance = calculateMovementVariance(player);

    AxisAlignedBB targetBB =
        target.getEntityBoundingBox().expand(HITBOX_EXPANSION, 0.1D, HITBOX_EXPANSION);
    targetBB =
        targetBB.offset(
            (target.posX - target.lastTickPosX) * 2.0D,
            (target.posY - target.lastTickPosY) * 2.0D,
            (target.posZ - target.lastTickPosZ) * 2.0D);

    Vec3 lookVec = player.getLook(1.0F);
    Vec3 maxReachVec =
        eyesPos.addVector(
            lookVec.xCoord * BASE_REACH * 2.0D,
            lookVec.yCoord * BASE_REACH * 2.0D,
            lookVec.zCoord * BASE_REACH * 2.0D);

    MovingObjectPosition rayTraceResult = targetBB.calculateIntercept(eyesPos, maxReachVec);

    if (rayTraceResult != null) {
      double distance = eyesPos.distanceTo(rayTraceResult.hitVec);

      double extraTolerance = variance != null ? variance * MOVEMENT_TOLERANCE_MULTIPLIER : 0.0D;
      double maxReach = BASE_REACH + extraTolerance;

      if (distance > maxReach) {
        double flagWeight = Math.min(2.0D, (distance - maxReach) * 2.0D);
        if (buffer.flag(flagWeight, 6.0D)) {
          context.receiveSignal(
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

  private Double calculateMovementVariance(EntityPlayer player) {
    AxisAlignedBB bb = player.getEntityBoundingBox();
    if (bb == null) return null;

    double speed = Math.hypot(player.posX - player.lastTickPosX, player.posZ - player.lastTickPosZ);
    if (speed > 0.01D) {
      return speed * 0.5D;
    }
    return 0.0D;
  }

  public void reset() {
    this.reachBuffers.clear();
    this.movementVariance.clear();
    this.lastEyesPosition.clear();
  }
}
