package myau.clientanticheat.movement.blink;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class MicroBlinkCheck {
  private final Map<String, Integer> frozenTicksMap = new HashMap<>();
  private final Map<String, CheckBuffer> buffers = new HashMap<>();
  private final Map<String, LinkedList<Double>> freezeDurationHistogram = new HashMap<>();
  private final Map<String, LinkedList<Long>> movementTimestamps = new HashMap<>();
  private final Map<String, int[]> chiSquareContingency = new HashMap<>();
  private final Map<String, CheckBuffer> entityAlignmentBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> combatCorrelationBuffers = new HashMap<>();
  private final Map<String, Long> lastAttackTime = new HashMap<>();

  private static final int HISTOGRAM_SIZE = 100;
  private static final double MIN_FREEZE_DELTA = 0.003D;
  private static final double MAX_FREEZE_DELTA = 0.04D;
  private static final double MIN_BURST_DELTA = 0.4D;
  private static final double MAX_BURST_DELTA = 8.0D;
  private static final int MIN_FREEZE_TICKS = 4;
  private static final int MAX_FREEZE_TICKS = 30;
  private static final long COMBAT_WINDOW_MS = 1500L;
  private static final double CHI_SQUARE_THRESHOLD = 7.0D;
  private static final int COLD_START_TICKS = 100;

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer entityAlignmentBuffer =
        this.entityAlignmentBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer combatCorrelationBuffer =
        this.combatCorrelationBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      this.frozenTicksMap.remove(name);
      buffer.decay(0.1D);
      entityAlignmentBuffer.decay(0.1D);
      combatCorrelationBuffer.decay(0.1D);
      return;
    }

    long now = System.currentTimeMillis();
    LinkedList<Long> moveTimestamps =
        this.movementTimestamps.computeIfAbsent(name, key -> new LinkedList<>());
    LinkedList<Double> histogram =
        this.freezeDurationHistogram.computeIfAbsent(name, key -> new LinkedList<>());

    if (data.startedSwinging()) {
      this.lastAttackTime.put(name, now);
    }

    boolean frozen =
        data.totalDelta < MIN_FREEZE_DELTA && data.yawDelta < 0.03F && data.pitchDelta < 0.03F;
    if (frozen) {
      this.frozenTicksMap.put(name, this.frozenTicksMap.getOrDefault(name, 0) + 1);
      buffer.decay(0.01D);
      return;
    }

    int frozenTicks = this.frozenTicksMap.getOrDefault(name, 0);
    this.frozenTicksMap.remove(name);

    if (frozenTicks < MIN_FREEZE_TICKS || frozenTicks > MAX_FREEZE_TICKS) {
      buffer.decay(0.1D);
      entityAlignmentBuffer.decay(0.05D);
      combatCorrelationBuffer.decay(0.1D);
      return;
    }

    boolean catchUpBurst = data.totalDelta > MIN_BURST_DELTA && data.totalDelta < MAX_BURST_DELTA;

    if (!catchUpBurst) {
      buffer.decay(0.1D);
      entityAlignmentBuffer.decay(0.05D);
      combatCorrelationBuffer.decay(0.1D);
      return;
    }

    double freezeDurationMs = (double) frozenTicks * 50.0D;
    histogram.addFirst(freezeDurationMs);
    if (histogram.size() > HISTOGRAM_SIZE) {
      histogram.removeLast();
    }
    moveTimestamps.addFirst(now);
    if (moveTimestamps.size() > 40) {
      moveTimestamps.removeLast();
    }

    if (histogram.size() >= 40) {
      long countInRange =
          histogram.stream().filter(v -> Math.abs(v - freezeDurationMs) < 15.0D).count();
      double probability = (double) countInRange / histogram.size();

      if (probability < 0.01D && histogram.size() > 50) {
        if (buffer.flag(2.0D, 6.0D)) {
          context.receiveSignal(name, "MicroBlink (Histogram Outlier)");
          buffer.reset();
        }
      } else {
        buffer.decay(0.2D);
      }
    }

    boolean nearEntity = data.nearestTarget != null && data.nearestTargetDistance < 6.0D;

    if (data.existedTicks > COLD_START_TICKS) {
      int[] contingency = this.chiSquareContingency.computeIfAbsent(name, key -> new int[4]);
      if (frozenTicks >= MIN_FREEZE_TICKS && nearEntity) {
        contingency[0]++;
      } else if (frozenTicks >= MIN_FREEZE_TICKS) {
        contingency[1]++;
      } else if (nearEntity) {
        contingency[2]++;
      } else {
        contingency[3]++;
      }

      if (contingency[0] + contingency[1] + contingency[2] + contingency[3] > 80) {
        double chi2 = computeChiSquare(contingency);
        if (chi2 > CHI_SQUARE_THRESHOLD && contingency[0] > contingency[1]) {
          if (entityAlignmentBuffer.flag(1.5D, 5.0D)) {
            context.receiveSignal(name, "MicroBlink (Entity-Aligned)");
            entityAlignmentBuffer.reset();
            for (int i = 0; i < contingency.length; i++) contingency[i] = 0;
          }
        } else {
          entityAlignmentBuffer.decay(0.1D);
        }
      } else {
        entityAlignmentBuffer.decay(0.05D);
      }
    }

    Long lastAttack = this.lastAttackTime.get(name);
    if (lastAttack != null) {
      long msSinceLastAttack = now - lastAttack;
      boolean inCombatWindow = msSinceLastAttack < COMBAT_WINDOW_MS && msSinceLastAttack >= 0;

      if (inCombatWindow && frozenTicks >= MIN_FREEZE_TICKS) {
        if (combatCorrelationBuffer.flag(1.0D, 6.0D)) {
          context.receiveSignal(name, "MicroBlink (Combat-Timed)");
          combatCorrelationBuffer.reset();
        }
      } else {
        combatCorrelationBuffer.decay(0.15D);
      }
    }

    if (catchUpBurst
        && frozenTicks >= MIN_FREEZE_TICKS
        && frozenTicks <= MAX_FREEZE_TICKS
        && data.startedSwinging()) {
      if (buffer.flag(0.5D, 5.0D)) {
        context.receiveSignal(name, "MicroBlink / LagRange");
        buffer.reset();
      }
    }
  }

  private double computeChiSquare(int[] contingency) {
    int a = contingency[0], b = contingency[1];
    int c = contingency[2], d = contingency[3];
    int total = a + b + c + d;
    if (total == 0) return 0.0;

    double row1Total = a + b;
    double row2Total = c + d;
    double col1Total = a + c;
    double col2Total = b + d;

    double expectedA = row1Total * col1Total / total;
    double expectedB = row1Total * col2Total / total;
    double expectedC = row2Total * col1Total / total;
    double expectedD = row2Total * col2Total / total;

    double chi2 = 0.0;
    if (expectedA > 0) chi2 += Math.pow(Math.abs(a - expectedA) - 0.5, 2) / expectedA;
    if (expectedB > 0) chi2 += Math.pow(Math.abs(b - expectedB) - 0.5, 2) / expectedB;
    if (expectedC > 0) chi2 += Math.pow(Math.abs(c - expectedC) - 0.5, 2) / expectedC;
    if (expectedD > 0) chi2 += Math.pow(Math.abs(d - expectedD) - 0.5, 2) / expectedD;

    return chi2;
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
    this.frozenTicksMap.clear();
    this.buffers.clear();
    this.freezeDurationHistogram.clear();
    this.movementTimestamps.clear();
    this.chiSquareContingency.clear();
    this.entityAlignmentBuffers.clear();
    this.combatCorrelationBuffers.clear();
    this.lastAttackTime.clear();
  }
}
