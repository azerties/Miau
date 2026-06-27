package myau.clientanticheat.combat.killaura;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class KillAuraRotationSpeed {
  private final Map<String, Long> lastAttackTicks = new HashMap<>();
  private final Map<String, CheckBuffer> rateBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> aimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> linearAimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> accuracyBuffers = new HashMap<>();
  private final Map<String, Queue<Double>> hitAccuracySamples = new HashMap<>();
  private final Map<String, CheckBuffer> preAttackBuffers = new HashMap<>();
  private final Map<String, float[]> preAttackYawDeltaHistory = new HashMap<>();

  private static final int ACCURACY_SAMPLE_SIZE = 25;
  private static final double HIGH_ACCURACY_THRESHOLD = 0.95D;

  public void check(
      EntityPlayer player,
      World world,
      PlayerCheckData data,
      long currentTick,
      ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    if (data.recentlyHurt()) {
      this.decay(name);
      return;
    }

    EntityPlayer target = data.nearestTarget;
    if (target == null || data.nearestTargetDistance > 6.0D) {
      this.decay(name);
      return;
    }

    CheckBuffer rateBuffer = this.rateBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer aimBuffer = this.aimBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer linearAimBuffer =
        this.linearAimBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer accuracyBuffer =
        this.accuracyBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer preAttackBuffer =
        this.preAttackBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    long lastAttack = this.lastAttackTicks.getOrDefault(name, currentTick - 20L);
    long delay = currentTick - lastAttack;
    this.lastAttackTicks.put(name, currentTick);

    if (delay > 0L && delay < 3L) {
      rateBuffer.flag(1.0D, 999.0D);
    } else {
      rateBuffer.decay(0.4D);
    }

    float yawError =
        Math.abs(MathHelper.wrapAngleTo180_float(this.yawTo(player, target) - player.rotationYaw));
    float pitchError = Math.abs(this.pitchTo(player, target) - player.rotationPitch);
    if (yawError > 45.0F || pitchError > 35.0F) {
      aimBuffer.flag(1.25D, 999.0D);
    } else {
      aimBuffer.decay(0.5D);
    }

    if (data.yawDelta > 2.0F && Math.abs(data.yawAcceleration) < 0.005F) {
      linearAimBuffer.flag(1.0D, 7.0D);
    } else {
      linearAimBuffer.decay(0.25D);
    }

    Queue<Double> accuracySamples =
        this.hitAccuracySamples.computeIfAbsent(name, key -> new ArrayDeque<>());

    double totalAngleError = yawError + pitchError;
    double accuracy = Math.max(0.0, 1.0 - (totalAngleError / 90.0));
    accuracySamples.add(accuracy);

    if (accuracySamples.size() >= ACCURACY_SAMPLE_SIZE) {
      double avgAccuracy = 0.0;
      for (double sample : accuracySamples) {
        avgAccuracy += sample;
      }
      avgAccuracy /= accuracySamples.size();

      if (avgAccuracy > HIGH_ACCURACY_THRESHOLD) {
        accuracyBuffer.flag(1.5D, 999.0D);
      }
      double varianceSum = 0.0;
      for (double sample : accuracySamples) {
        varianceSum += (sample - avgAccuracy) * (sample - avgAccuracy);
      }
      double variance = varianceSum / accuracySamples.size();
      if (variance < 0.001D && avgAccuracy > 0.88D) {
        accuracyBuffer.flag(1.0D, 999.0D);
      } else {
        accuracyBuffer.decay(0.25D);
      }
      accuracySamples.clear();
    }

    float[] preAttackHistory =
        this.preAttackYawDeltaHistory.computeIfAbsent(name, key -> new float[4]);
    preAttackHistory[3] = preAttackHistory[2];
    preAttackHistory[2] = preAttackHistory[1];
    preAttackHistory[1] = preAttackHistory[0];
    preAttackHistory[0] = data.yawDelta;

    boolean preAttackPattern =
        preAttackHistory[3] < 3.0F
            && preAttackHistory[2] < 3.0F
            && preAttackHistory[1] > 25.0F
            && preAttackHistory[0] < 3.0F
            && delay <= 2L;

    if (preAttackPattern) {
      preAttackBuffer.flag(1.5D, 999.0D);
    } else {
      preAttackBuffer.decay(0.2D);
    }

    if (rateBuffer.get() > 5.0D && aimBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Aim)");
      rateBuffer.reset();
      aimBuffer.reset();
    }
    if (linearAimBuffer.get() > 5.0D) {
      context.receiveSignal(name, "KillAura (Robotic Aim)");
      linearAimBuffer.reset();
    }
    if (accuracyBuffer.get() > 4.0D) {
      context.receiveSignal(name, "KillAura (Accuracy)");
      accuracyBuffer.reset();
    }
    if (preAttackBuffer.get() > 4.0D) {
      context.receiveSignal(name, "KillAura (Pre-Attack)");
      preAttackBuffer.reset();
    }
  }

  public void decay(String name) {
    CheckBuffer rate = this.rateBuffers.get(name);
    if (rate != null) rate.decay(0.2D);
    CheckBuffer aim = this.aimBuffers.get(name);
    if (aim != null) aim.decay(0.2D);
    CheckBuffer linear = this.linearAimBuffers.get(name);
    if (linear != null) linear.decay(0.15D);
    CheckBuffer accuracy = this.accuracyBuffers.get(name);
    if (accuracy != null) accuracy.decay(0.15D);
    CheckBuffer preAttack = this.preAttackBuffers.get(name);
    if (preAttack != null) preAttack.decay(0.15D);
  }

  public void reset() {
    this.lastAttackTicks.clear();
    this.rateBuffers.clear();
    this.aimBuffers.clear();
    this.linearAimBuffers.clear();
    this.accuracyBuffers.clear();
    this.hitAccuracySamples.clear();
    this.preAttackBuffers.clear();
    this.preAttackYawDeltaHistory.clear();
  }

  private float yawTo(EntityPlayer player, EntityPlayer target) {
    double dx = target.posX - player.posX;
    double dz = target.posZ - player.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private float pitchTo(EntityPlayer player, EntityPlayer target) {
    Vec3 eyes = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    Vec3 targetEyes = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
    double dx = targetEyes.xCoord - eyes.xCoord;
    double dy = targetEyes.yCoord - eyes.yCoord;
    double dz = targetEyes.zCoord - eyes.zCoord;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    return (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
  }
}
