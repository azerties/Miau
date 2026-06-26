package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public class KillAuraAngleSnap {
  private final Map<String, CheckBuffer> snapPatternBuffers = new HashMap<>();
  private final Map<String, float[]> yawHistory = new HashMap<>();
  private final Map<String, CheckBuffer> silentAimBuffers = new HashMap<>();
  private final Map<String, float[]> movementYawHistory = new HashMap<>();
  private final Map<String, CheckBuffer> entitySnapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> flickBuffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    CheckBuffer snapPatternBuffer =
        this.snapPatternBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer silentAimBuffer =
        this.silentAimBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer entitySnapBuffer =
        this.entitySnapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer flickBuffer = this.flickBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    float deltaYaw = data.yawDelta;
    float deltaPitch = data.pitchDelta;

    float[] yawHist = this.yawHistory.computeIfAbsent(name, key -> new float[3]);
    yawHist[2] = yawHist[1];
    yawHist[1] = yawHist[0];
    yawHist[0] = deltaYaw;

    boolean threeTickSnap = yawHist[2] < 9.0F && yawHist[1] > 40.0F && yawHist[0] < 9.0F;
    boolean liteSnap = yawHist[2] < 9.0F && yawHist[1] > 25.0F && yawHist[0] < 9.0F;

    if (threeTickSnap) {
      snapPatternBuffer.flag(2.0D, 999.0D);
    } else if (liteSnap) {
      snapPatternBuffer.flag(1.0D, 999.0D);
    } else {
      snapPatternBuffer.decay(0.15D);
    }

    float[] movYawHist = this.movementYawHistory.computeIfAbsent(name, key -> new float[2]);
    float movementYaw = (float) (Math.atan2(data.deltaZ, data.deltaX) * 180.0 / Math.PI);
    movYawHist[1] = movYawHist[0];
    movYawHist[0] = movementYaw;

    float movementYawDelta =
        Math.abs(MathHelper.wrapAngleTo180_float(movYawHist[0] - movYawHist[1]));
    boolean movementStable = movementYawDelta < 5.0F && data.horizontalDelta > 0.1D;
    boolean aimSnapped = deltaYaw > 30.0F;

    if (movementStable
        && aimSnapped
        && data.nearestTarget != null
        && data.nearestTargetDistance < 5.0D) {
      silentAimBuffer.flag(1.5D, 999.0D);
    } else {
      silentAimBuffer.decay(0.2D);
    }

    if (data.nearestTarget != null && data.nearestTargetDistance < 6.0D && deltaYaw > 25.0F) {
      float currentYawToTarget = yawToEntity(player, data.nearestTarget);
      float lastYawToTarget =
          yawToEntity(player, data.nearestTarget)
              + MathHelper.wrapAngleTo180_float(data.lastYaw - data.yaw);

      float currentError =
          Math.abs(MathHelper.wrapAngleTo180_float(player.rotationYaw - currentYawToTarget));
      float lastError = Math.abs(MathHelper.wrapAngleTo180_float(data.lastYaw - lastYawToTarget));

      boolean wasNotLooking = lastError > 20.0F;
      boolean nowLooking = currentError < 8.0F;

      if (wasNotLooking && nowLooking) {
        entitySnapBuffer.flag(1.5D, 999.0D);
      } else {
        entitySnapBuffer.decay(0.2D);
      }
    } else {
      entitySnapBuffer.decay(0.15D);
    }

    if (deltaYaw > 95.0F || data.yawAcceleration > 65.0F) {
      flickBuffer.flag(1.0D, 999.0D);
    } else {
      flickBuffer.decay(0.3D);
    }

    float divisorX = deltaYaw % 1.5F;
    float divisorY = deltaPitch % 1.5F;
    if (deltaYaw > 5.0F && divisorX == 0.0F && divisorY == 0.0F) {
      flickBuffer.flag(1.0D, 5.0D);
    }

    if (snapPatternBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Snap Pattern)");
      snapPatternBuffer.reset();
    }
    if (silentAimBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Silent Aim)");
      silentAimBuffer.reset();
    }
    if (entitySnapBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Entity Snap)");
      entitySnapBuffer.reset();
    }
    if (flickBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Rotation Flick)");
      flickBuffer.reset();
    }
  }

  private float yawToEntity(EntityPlayer from, EntityPlayer to) {
    double dx = to.posX - from.posX;
    double dz = to.posZ - from.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  public void decay(String name) {
    CheckBuffer snapPattern = this.snapPatternBuffers.get(name);
    if (snapPattern != null) snapPattern.decay(0.1D);
    CheckBuffer silentAim = this.silentAimBuffers.get(name);
    if (silentAim != null) silentAim.decay(0.15D);
    CheckBuffer entitySnap = this.entitySnapBuffers.get(name);
    if (entitySnap != null) entitySnap.decay(0.1D);
    CheckBuffer flick = this.flickBuffers.get(name);
    if (flick != null) flick.decay(0.15D);
  }

  public void reset() {
    this.snapPatternBuffers.clear();
    this.yawHistory.clear();
    this.silentAimBuffers.clear();
    this.movementYawHistory.clear();
    this.entitySnapBuffers.clear();
    this.flickBuffers.clear();
  }
}
