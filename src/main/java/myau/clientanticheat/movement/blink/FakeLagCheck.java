package myau.clientanticheat.movement.blink;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.entity.player.EntityPlayer;

public class FakeLagCheck {
  private final Map<String, CheckBuffer> pulseBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> timingVarianceBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> entityAlignedBuffers = new HashMap<>();
  private final Map<String, LinkedList<Double>> movementIntervalSamples = new HashMap<>();
  private final Map<String, Integer> nearEntityTickCounter = new HashMap<>();
  private final Map<String, Integer> lagTickCounter = new HashMap<>();
  private final Map<String, Boolean> wasLaggingLastTick = new HashMap<>();
  private final Map<String, Long> lastMovementTimestamp = new HashMap<>();

  private static final double FAKELAG_TIMING_VARIANCE_THRESHOLD = 2.0D;
  private static final int INTERVAL_SAMPLE_SIZE = 20;
  private static final int COMBAT_DISTANCE = 8;
  private static final double MIN_PULSE_VELOCITY = 0.65D;
  private static final double MAX_PULSE_VELOCITY = 8.0D;

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer pulseBuffer = this.pulseBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer timingVarianceBuffer =
        this.timingVarianceBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer entityAlignedBuffer =
        this.entityAlignedBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      pulseBuffer.decay(0.5D);
      timingVarianceBuffer.decay(0.3D);
      entityAlignedBuffer.decay(0.3D);
      this.nearEntityTickCounter.remove(name);
      this.lagTickCounter.remove(name);
      this.wasLaggingLastTick.remove(name);
      return;
    }

    if (data.collidedHorizontally) {
      pulseBuffer.decay(0.3D);
      timingVarianceBuffer.decay(0.2D);
      entityAlignedBuffer.decay(0.2D);
      return;
    }

    boolean pulseFluctuation =
        data.horizontalDelta > MIN_PULSE_VELOCITY
            && data.horizontalDelta < MAX_PULSE_VELOCITY
            && data.lastHorizontalDelta < 0.04D
            && data.horizontalDelta > data.lastHorizontalDelta * 12.0D;

    if (pulseFluctuation && !player.isCollidedHorizontally && data.airTicks < 8) {
      if (pulseBuffer.flag(1.0D, 4.5D)) {
        context.receiveSignal(name, "FakeLag (Pulse)");
        pulseBuffer.reset();
      }
    } else {
      pulseBuffer.decay(0.2D);
    }

    long now = System.currentTimeMillis();
    LinkedList<Double> intervals =
        this.movementIntervalSamples.computeIfAbsent(name, key -> new LinkedList<>());
    Long lastMoveTime = this.lastMovementTimestamp.get(name);

    boolean isLagging =
        data.horizontalDelta < 0.03D && data.stillTicks > 8 && !data.recentlyTeleported();

    if (isLagging) {
      this.lagTickCounter.put(name, this.lagTickCounter.getOrDefault(name, 0) + 1);
    } else {
      int lagTicks = this.lagTickCounter.getOrDefault(name, 0);
      if (lagTicks > 5 && data.totalDelta > MIN_PULSE_VELOCITY) {
        if (lastMoveTime != null) {
          long idleMs = now - lastMoveTime;
          if (idleMs > 200L && idleMs < 3000L) {
            intervals.add((double) idleMs);
            if (intervals.size() > INTERVAL_SAMPLE_SIZE) {
              intervals.removeFirst();
            }
          }
        }
      }
      this.lagTickCounter.put(name, 0);
    }

    if (data.totalDelta > 0.02D) {
      this.lastMovementTimestamp.put(name, now);
    }

    if (intervals.size() >= 8) {
      double stddev = StatisticalUtils.standardDeviation(intervals);
      double cv = StatisticalUtils.coefficientOfVariation(intervals);

      if (cv > 0.5D && stddev > FAKELAG_TIMING_VARIANCE_THRESHOLD) {
        if (timingVarianceBuffer.flag(1.5D, 5.0D)) {
          context.receiveSignal(name, "FakeLag (Timing Variance)");
          timingVarianceBuffer.reset();
          intervals.clear();
        }
      } else {
        timingVarianceBuffer.decay(0.15D);
      }
    } else {
      timingVarianceBuffer.decay(0.1D);
    }

    boolean nearEntity = data.nearestTarget != null && data.nearestTargetDistance < COMBAT_DISTANCE;

    if (nearEntity) {
      int nearTicks = this.nearEntityTickCounter.getOrDefault(name, 0);
      this.nearEntityTickCounter.put(name, nearTicks + 1);
    } else {
      int nearTicks = this.nearEntityTickCounter.getOrDefault(name, 0);
      if (nearTicks > 0) {
        this.nearEntityTickCounter.put(name, Math.max(0, nearTicks - 1));
      }
    }

    boolean wasLagging = this.wasLaggingLastTick.getOrDefault(name, false);
    this.wasLaggingLastTick.put(name, isLagging);

    boolean lagJustStarted = isLagging && !wasLagging && data.stillTicks > 8;
    boolean isNearEntity = this.nearEntityTickCounter.getOrDefault(name, 0) > 3;

    if (lagJustStarted && isNearEntity && data.stillTicks <= 20) {
      if (entityAlignedBuffer.flag(1.5D, 5.0D)) {
        context.receiveSignal(name, "FakeLag (Entity-Aligned)");
        entityAlignedBuffer.reset();
      }
    } else {
      entityAlignedBuffer.decay(0.2D);
    }

    boolean catchUpNearEntity =
        isNearEntity && data.totalDelta > MIN_PULSE_VELOCITY && lagJustStarted;

    if (catchUpNearEntity) {
      if (pulseBuffer.flag(1.5D, 4.0D)) {
        context.receiveSignal(name, "FakeLag (Combat Catch-up)");
        pulseBuffer.reset();
      }
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data.recentlyHurt();
  }

  public void reset() {
    this.pulseBuffers.clear();
    this.timingVarianceBuffers.clear();
    this.entityAlignedBuffers.clear();
    this.movementIntervalSamples.clear();
    this.nearEntityTickCounter.clear();
    this.lagTickCounter.clear();
    this.wasLaggingLastTick.clear();
    this.lastMovementTimestamp.clear();
  }
}
