package myau.clientanticheat.combat.autoclicker;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.entity.player.EntityPlayer;

public class ClickSpeedCheck {
  private final Map<String, LinkedList<Long>> clickTimestamps = new HashMap<>();
  private final Map<String, LinkedList<Long>> clickIntervals = new HashMap<>();

  private final Map<String, Double> deviationVl = new HashMap<>();
  private final Map<String, Double> entropyVl = new HashMap<>();
  private final Map<String, Double> kurtosisVl = new HashMap<>();
  private final Map<String, CheckBuffer> fluctuationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> repetitiveBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> equalDelayBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> cpsBuffers = new HashMap<>();

  private final Map<String, LinkedList<Integer>> cpsWindowHistory = new HashMap<>();
  private final Map<String, LinkedList<Double>> deviationSamples = new HashMap<>();
  private final Map<String, LinkedList<Long>> spikeTimestamps = new HashMap<>();
  private final Map<String, LinkedList<Long>> dropTimestamps = new HashMap<>();
  private final Map<String, Double> lastCps = new HashMap<>();

  private final Map<String, LinkedList<Double>> intervalDifferenceHistory = new HashMap<>();

  private final Map<String, Integer> consecutiveEqualDelays = new HashMap<>();
  private final Map<String, Long> lastDelayTick = new HashMap<>();
  private final Map<String, Long> lastSwingTimestamp = new HashMap<>();

  private static final int CLICK_SAMPLE_SIZE = 100;
  private static final int STDDEV_COLLECTION_SIZE = 50;
  private static final int CPS_WINDOW_TICKS = 20;
  private static final int MIN_CLICKS_FOR_ANALYSIS = 25;
  private static final long EQUAL_DELAY_TOLERANCE = 1L;
  private static final long BUFFER_TIMEOUT_MS = 4000L;
  private static final int DEVIATION_META_SAMPLE_SIZE = 3;

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    if (data == null || !data.startedSwinging()) {
      return;
    }

    String name = player.getName();
    if (name == null) return;

    if (data.usingItem || data.usingItemTicks > 0) {
      return;
    }

    if (data.breakingBlock || data.recentlyBrokeBlock()) {
      clearPlayerAnalysis(name);
      return;
    }

    long nowMs = System.currentTimeMillis();
    Long lastSwing = this.lastSwingTimestamp.get(name);
    this.lastSwingTimestamp.put(name, nowMs);

    if (lastSwing != null && nowMs - lastSwing > BUFFER_TIMEOUT_MS) {
      clearPlayerAnalysis(name);
      return;
    }

    LinkedList<Long> timestamps =
        this.clickTimestamps.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> intervals = this.clickIntervals.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Integer> cpsHistory =
        this.cpsWindowHistory.computeIfAbsent(name, k -> new LinkedList<>());

    CheckBuffer fluctuationBuffer =
        this.fluctuationBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer repetitiveBuffer =
        this.repetitiveBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer equalDelayBuffer =
        this.equalDelayBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer cpsBuffer = this.cpsBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    fluctuationBuffer.decay(0.03D);
    repetitiveBuffer.decay(0.03D);

    timestamps.addFirst(currentTick);
    while (timestamps.size() > CLICK_SAMPLE_SIZE) {
      timestamps.removeLast();
    }

    if (timestamps.size() >= 2) {
      long interval = timestamps.get(0) - timestamps.get(1);
      if (interval > 0) {
        intervals.addFirst(interval);
        while (intervals.size() > CLICK_SAMPLE_SIZE) {
          intervals.removeLast();
        }
      }
    }

    int cps = 0;
    long cutoff = currentTick - CPS_WINDOW_TICKS;
    for (long ts : timestamps) {
      if (ts >= cutoff) cps++;
    }
    cpsHistory.addFirst(cps);
    while (cpsHistory.size() > 50) {
      cpsHistory.removeLast();
    }

    if (intervals.size() < MIN_CLICKS_FOR_ANALYSIS) {
      return;
    }

    if (intervals.size() >= STDDEV_COLLECTION_SIZE) {
      double stddev = StatisticalUtils.standardDeviation(intervals);
      LinkedList<Double> devSamples =
          this.deviationSamples.computeIfAbsent(name, k -> new LinkedList<>());
      devSamples.addFirst(stddev);
      if (devSamples.size() > 5) devSamples.removeLast();

      if (devSamples.size() >= DEVIATION_META_SAMPLE_SIZE) {
        double metaStd = StatisticalUtils.standardDeviation(devSamples);
        double vl = this.deviationVl.getOrDefault(name, 0.0D);
        if (stddev < 0.8D && metaStd < 15.0D) {
          int vlAdd = stddev < 0.5D ? 3 : 2;
          vl += vlAdd;
          if (vl > 4) {
            context.receiveSignal(name, "AutoClicker (Deviation)");
            devSamples.clear();
            vl *= 0.5D;
          }
        } else if (vl > 0) {
          vl -= 0.2D;
          vl *= 0.9D;
        }
        this.deviationVl.put(name, Math.max(0.0D, vl));
      }
    }

    if (intervals.size() >= CLICK_SAMPLE_SIZE) {
      double ent = StatisticalUtils.entropy(intervals);
      double vl = this.entropyVl.getOrDefault(name, 0.0D);
      if (ent >= 0.35D && ent <= 1.0D) {
        vl += 2;
        if (vl > 3) {
          context.receiveSignal(name, "AutoClicker (Entropy)");
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.2D;
        vl *= 0.98D;
      }
      this.entropyVl.put(name, Math.max(0.0D, vl));
    }

    if (intervals.size() >= 25) {
      double kurt = StatisticalUtils.kurtosis(intervals);
      double normalizedKurt = kurt / 1000.0D;
      double vl = this.kurtosisVl.getOrDefault(name, 0.0D);
      if (normalizedKurt < 6.0D && normalizedKurt > 0.0D) {
        vl++;
        if (vl > 15) {
          context.receiveSignal(name, "AutoClicker (Kurtosis)");
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.1D;
        vl *= 0.98D;
      }
      this.kurtosisVl.put(name, Math.max(0.0D, vl));
    }

    if (intervals.size() >= 10) {
      double currentCps = 20.0D / sumOf(intervals, 10) * 50.0D;
      double previousCps = this.lastCps.getOrDefault(name, currentCps);
      this.lastCps.put(name, currentCps);

      double cpsDiff = currentCps - previousCps;
      LinkedList<Long> spikes = this.spikeTimestamps.computeIfAbsent(name, k -> new LinkedList<>());
      LinkedList<Long> drops = this.dropTimestamps.computeIfAbsent(name, k -> new LinkedList<>());

      if (cpsDiff > 0.45D) {
        spikes.addFirst(nowMs);
        if (spikes.size() > 10) spikes.removeLast();
      }
      if (cpsDiff < -0.45D) {
        drops.addFirst(nowMs);
        if (drops.size() > 10) drops.removeLast();
      }

      if (!spikes.isEmpty() && nowMs - spikes.getLast() > 10000) spikes.clear();
      if (!drops.isEmpty() && nowMs - drops.getLast() > 10000) drops.clear();

      if (spikes.size() >= 3) {
        double spikeStd = timestampStdDev(spikes);
        if (spikeStd < 1200.0D) {
          if (fluctuationBuffer.flag(1.5D, 6.0D)) {
            context.receiveSignal(name, "AutoClicker (Fluctuation)");
            fluctuationBuffer.reset();
            spikes.clear();
          }
        } else {
          fluctuationBuffer.decay(0.2D);
        }
      }
      if (drops.size() >= 3) {
        double dropStd = timestampStdDev(drops);
        if (dropStd < 1200.0D) {
          if (fluctuationBuffer.flag(1.5D, 6.0D)) {
            context.receiveSignal(name, "AutoClicker (Fluctuation)");
            fluctuationBuffer.reset();
            drops.clear();
          }
        } else {
          fluctuationBuffer.decay(0.2D);
        }
      }
    }

    LinkedList<Double> diffHistory =
        this.intervalDifferenceHistory.computeIfAbsent(name, k -> new LinkedList<>());

    if (intervals.size() >= 4) {
      for (int i = 0; i < intervals.size() - 1 && diffHistory.size() < 50; i++) {
        if (i >= diffHistory.size()) {
          double diff = Math.abs((double) (intervals.get(i) - intervals.get(i + 1)));
          diffHistory.addFirst(diff);
        }
      }
      while (diffHistory.size() > 50) {
        diffHistory.removeLast();
      }

      if (diffHistory.size() >= 20) {
        if (StatisticalUtils.hasRepetitivePattern(diffHistory, 0.3D)) {
          if (repetitiveBuffer.flag(1.5D, 6.0D)) {
            context.receiveSignal(name, "AutoClicker (Repetitive)");
            repetitiveBuffer.reset();
          }
        } else {
          repetitiveBuffer.decay(0.2D);
        }
      }
    }

    if (intervals.size() >= 2) {
      long currentDelay = intervals.getFirst();
      Long lastDelay = this.lastDelayTick.get(name);

      if (lastDelay != null && lastDelay >= 0) {
        long diff = Math.abs(currentDelay - lastDelay);
        if (diff <= EQUAL_DELAY_TOLERANCE && currentDelay > 0 && currentDelay < 10) {
          int consecutive = this.consecutiveEqualDelays.getOrDefault(name, 0) + 1;
          this.consecutiveEqualDelays.put(name, consecutive);

          int streakLimit = 12;
          if (currentDelay > 1) streakLimit += 3;
          if (currentDelay > 2) streakLimit += 3;
          if (currentDelay > 3) streakLimit += 3;

          if (consecutive > streakLimit) {
            if (equalDelayBuffer.flag(2.0D, 6.0D)) {
              context.receiveSignal(name, "AutoClicker (Equal Delay)");
              equalDelayBuffer.reset();
              this.consecutiveEqualDelays.put(name, 0);
            }
          }
        } else {
          int consecutive = this.consecutiveEqualDelays.getOrDefault(name, 0);
          if (consecutive > 0) {
            this.consecutiveEqualDelays.put(name, Math.max(0, consecutive - 2));
          }
          equalDelayBuffer.decay(0.15D);
        }
      }

      this.lastDelayTick.put(name, currentDelay);
    }

    if (cps > 25) {
      if (cpsBuffer.flag(1.0D, 4.0D)) {
        context.receiveSignal(name, "AutoClicker (CPS)");
        cpsBuffer.reset();
      }
    } else {
      cpsBuffer.decay(0.3D);
    }
  }

  private double sumOf(LinkedList<Long> intervals, int count) {
    double sum = 0;
    int i = 0;
    for (long v : intervals) {
      if (i++ >= count) break;
      sum += v;
    }
    return sum;
  }

  private double timestampStdDev(LinkedList<Long> timestamps) {
    if (timestamps.size() < 2) return Double.MAX_VALUE;
    double sum = 0;
    for (long ts : timestamps) sum += ts;
    double mean = sum / timestamps.size();
    double variance = 0;
    for (long ts : timestamps) {
      double diff = ts - mean;
      variance += diff * diff;
    }
    return Math.sqrt(variance / timestamps.size());
  }

  private void clearPlayerAnalysis(String name) {
    LinkedList<Long> ts = this.clickTimestamps.get(name);
    if (ts != null) ts.clear();
    LinkedList<Long> iv = this.clickIntervals.get(name);
    if (iv != null) iv.clear();
    LinkedList<Double> ds = this.deviationSamples.get(name);
    if (ds != null) ds.clear();
    LinkedList<Long> sp = this.spikeTimestamps.get(name);
    if (sp != null) sp.clear();
    LinkedList<Long> dp = this.dropTimestamps.get(name);
    if (dp != null) dp.clear();
    this.lastCps.remove(name);
    this.deviationVl.remove(name);
    this.entropyVl.remove(name);
    this.kurtosisVl.remove(name);
  }

  public void reset() {
    this.clickTimestamps.clear();
    this.clickIntervals.clear();
    this.deviationVl.clear();
    this.entropyVl.clear();
    this.kurtosisVl.clear();
    this.fluctuationBuffers.clear();
    this.repetitiveBuffers.clear();
    this.equalDelayBuffers.clear();
    this.cpsBuffers.clear();
    this.cpsWindowHistory.clear();
    this.deviationSamples.clear();
    this.spikeTimestamps.clear();
    this.dropTimestamps.clear();
    this.lastCps.clear();
    this.intervalDifferenceHistory.clear();
    this.consecutiveEqualDelays.clear();
    this.lastDelayTick.clear();
    this.lastSwingTimestamp.clear();
  }
}
