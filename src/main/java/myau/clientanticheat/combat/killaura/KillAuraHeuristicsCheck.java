package myau.clientanticheat.combat.killaura;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraHeuristicsCheck {
  private final Map<String, CheckBuffer> constantAimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> moduloBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sensitivityBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> stdDevBuffers = new HashMap<>();
  private final Map<String, Queue<Float>> yawDeltaSamples = new HashMap<>();
  private final Map<String, Queue<Float>> pitchDeltaSamples = new HashMap<>();
  private final Map<String, CheckBuffer> exactRotationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> moduloResetBuffers = new HashMap<>();
  private final Map<String, Float> lastGcdValues = new HashMap<>();

  private static final int STDDEV_SAMPLE_SIZE = 40;
  private static final double LOW_STDDEV_THRESHOLD = 0.3D;
  private static final double CONSISTENT_STDDEV_THRESHOLD = 0.9D;

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    if (data.recentlyHurt() || data.collidedHorizontally) {
      this.decay(name);
      return;
    }

    CheckBuffer constantAimBuffer =
        this.constantAimBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer moduloBuffer = this.moduloBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sensitivityBuffer =
        this.sensitivityBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer stdDevBuffer = this.stdDevBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer exactRotationBuffer =
        this.exactRotationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer moduloResetBuffer =
        this.moduloResetBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    float deltaYaw = data.yawDelta;
    float deltaPitch = data.pitchDelta;
    float accelYaw = Math.abs(data.yawAcceleration);
    float accelPitch = Math.abs(data.pitchAcceleration);

    if (deltaYaw > 2.0F && accelYaw < 0.0005F) {
      constantAimBuffer.flag(1.0D, 6.0D);
    } else {
      constantAimBuffer.decay(0.15D);
    }

    float divisorX = deltaYaw % 1.5F;
    float divisorY = deltaPitch % 1.5F;
    float divisorGcdX = deltaYaw % 0.05F;
    float divisorGcdY = deltaPitch % 0.05F;

    if (deltaYaw > 5.0F && (Math.abs(divisorX) < 0.001F || Math.abs(divisorY) < 0.001F)) {
      moduloBuffer.flag(1.0D, 6.0D);
    } else if (deltaYaw > 2.0F
        && Math.abs(divisorGcdX) < 0.001F
        && Math.abs(divisorGcdY) < 0.001F) {
      moduloBuffer.flag(0.5D, 6.0D);
    } else {
      moduloBuffer.decay(0.25D);
    }

    if (data.sensitivityChangeCount > 5) {
      sensitivityBuffer.flag(1.0D, 8.0D);
    } else {
      sensitivityBuffer.decay(0.2D);
    }

    Queue<Float> yawSamples = this.yawDeltaSamples.computeIfAbsent(name, key -> new ArrayDeque<>());
    Queue<Float> pitchSamples =
        this.pitchDeltaSamples.computeIfAbsent(name, key -> new ArrayDeque<>());

    if (deltaYaw > 0.5F) {
      yawSamples.add(deltaYaw);
      pitchSamples.add(deltaPitch);

      if (yawSamples.size() >= STDDEV_SAMPLE_SIZE) {
        double yawStdDev = StatisticalUtils.standardDeviation(yawSamples);
        double pitchStdDev = StatisticalUtils.standardDeviation(pitchSamples);

        if (yawStdDev < LOW_STDDEV_THRESHOLD
            && pitchStdDev < LOW_STDDEV_THRESHOLD
            && deltaYaw > 3.0F) {
          stdDevBuffer.flag(2.0D, 6.0D);
        } else if (yawStdDev < CONSISTENT_STDDEV_THRESHOLD
            && pitchStdDev < CONSISTENT_STDDEV_THRESHOLD
            && StatisticalUtils.coefficientOfVariation(yawSamples) < 0.06D) {
          stdDevBuffer.flag(1.0D, 6.0D);
        } else {
          stdDevBuffer.decay(0.4D);
        }
        yawSamples.clear();
        pitchSamples.clear();
      }
    }

    if (data.nearestTarget != null && data.nearestTargetDistance < 6.0D) {
      float perfectYaw = yawTo(player, data.nearestTarget);
      float perfectPitch = pitchTo(player, data.nearestTarget);
      float yawError =
          Math.abs(
              net.minecraft.util.MathHelper.wrapAngleTo180_float(player.rotationYaw - perfectYaw));
      float pitchError = Math.abs(player.rotationPitch - perfectPitch);

      if (yawError < 0.08F && pitchError < 0.08F && deltaYaw > 5.0F) {
        exactRotationBuffer.flag(1.5D, 5.0D);
      } else if (yawError < 0.3F && pitchError < 0.3F && deltaYaw > 15.0F) {
        exactRotationBuffer.flag(1.0D, 5.0D);
      } else {
        exactRotationBuffer.decay(0.3D);
      }
    }

    Float lastGcd = this.lastGcdValues.get(name);
    if (data.lastSensitivityGcd > 0.001F) {
      if (lastGcd != null && lastGcd > 0.001F) {
        float gcdDiff = Math.abs(data.lastSensitivityGcd - lastGcd);
        if (gcdDiff > 0.05F && deltaYaw > 3.0F) {
          float moduloOfOldGcd = deltaYaw % lastGcd;
          if (moduloOfOldGcd < 0.01F || Math.abs(moduloOfOldGcd - lastGcd) < 0.01F) {
            moduloResetBuffer.flag(1.5D, 5.0D);
          }
        } else {
          moduloResetBuffer.decay(0.15D);
        }
      }
      this.lastGcdValues.put(name, data.lastSensitivityGcd);
    }

    if (constantAimBuffer.get() > 5.0D) {
      context.receiveSignal(name, "KillAura (Constant Aim)");
      constantAimBuffer.reset();
    }
    if (moduloBuffer.get() > 5.0D) {
      context.receiveSignal(name, "KillAura (Modulo)");
      moduloBuffer.reset();
    }
    if (sensitivityBuffer.get() > 6.0D) {
      context.receiveSignal(name, "KillAura (Sensitivity)");
      sensitivityBuffer.reset();
    }
    if (stdDevBuffer.get() > 5.0D) {
      context.receiveSignal(name, "KillAura (Rotation StdDev)");
      stdDevBuffer.reset();
    }
    if (exactRotationBuffer.get() > 4.0D) {
      context.receiveSignal(name, "KillAura (Exact Rotation)");
      exactRotationBuffer.reset();
    }
    if (moduloResetBuffer.get() > 4.0D) {
      context.receiveSignal(name, "KillAura (Modulo Reset)");
      moduloResetBuffer.reset();
    }
  }

  public void decay(String name) {
    CheckBuffer constantAim = this.constantAimBuffers.get(name);
    if (constantAim != null) constantAim.decay(0.2D);
    CheckBuffer modulo = this.moduloBuffers.get(name);
    if (modulo != null) modulo.decay(0.2D);
    CheckBuffer sensitivity = this.sensitivityBuffers.get(name);
    if (sensitivity != null) sensitivity.decay(0.15D);
    CheckBuffer stdDev = this.stdDevBuffers.get(name);
    if (stdDev != null) stdDev.decay(0.15D);
    CheckBuffer exact = this.exactRotationBuffers.get(name);
    if (exact != null) exact.decay(0.15D);
    CheckBuffer moduloReset = this.moduloResetBuffers.get(name);
    if (moduloReset != null) moduloReset.decay(0.15D);
  }

  private float yawTo(EntityPlayer from, EntityPlayer to) {
    double dx = to.posX - from.posX;
    double dz = to.posZ - from.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private float pitchTo(EntityPlayer from, EntityPlayer to) {
    double dx = to.posX - from.posX;
    double dy = (to.posY + to.getEyeHeight()) - (from.posY + from.getEyeHeight());
    double dz = to.posZ - from.posZ;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    return (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
  }

  public void reset() {
    this.constantAimBuffers.clear();
    this.moduloBuffers.clear();
    this.sensitivityBuffers.clear();
    this.stdDevBuffers.clear();
    this.yawDeltaSamples.clear();
    this.pitchDeltaSamples.clear();
    this.exactRotationBuffers.clear();
    this.moduloResetBuffers.clear();
    this.lastGcdValues.clear();
  }
}
