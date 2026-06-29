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
  private final Map<String, LinkedList<Long>> jitterSamples = new HashMap<>();
  private final Map<String, LinkedList<Double>> deviationSamples = new HashMap<>();
  private final Map<String, LinkedList<Integer>> cpsWindowHistory = new HashMap<>();
  private final Map<String, LinkedList<Long>> spikeTimestamps = new HashMap<>();
  private final Map<String, LinkedList<Long>> dropTimestamps = new HashMap<>();
  private final Map<String, LinkedList<Double>> intervalDiffHistory = new HashMap<>();

  private final Map<String, Double> deviationVl = new HashMap<>();
  private final Map<String, Double> entropyVl = new HashMap<>();
  private final Map<String, Double> kurtosisVl = new HashMap<>();
  private final Map<String, Double> autocorrVl = new HashMap<>();
  private final Map<String, Double> clusterVl = new HashMap<>();
  private final Map<String, Double> jitterVl = new HashMap<>();

  private final Map<String, CheckBuffer> fluctuationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> dropBuffers =
      new HashMap<>(); // was sharing fluctuationBuffer with spikes — fixed
  private final Map<String, CheckBuffer> repetitiveBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> equalDelayBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> cpsBuffers = new HashMap<>();

  private final Map<String, Double> lastCps = new HashMap<>();
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

  // Lowered from 25 — sustained >16 CPS in 1.8.9 is humanly very difficult
  private static final int SUSPICIOUS_CPS = 16;
  // Below this stddev, timing is machine-like
  private static final double MACHINE_STDDEV = 0.9;
  // Human CV (stddev/mean) is typically > 0.10; autoclickers are near 0
  private static final double MIN_HUMAN_CV = 0.10;
  // Autoclickers produce LOW entropy (< ~0.5); humans produce > 1.5
  // Previous threshold (0.35–1.0) incorrectly flagged legitimate players
  private static final double LOW_ENTROPY_THRESHOLD = 0.5;
  // If >82% of intervals fall within ±1 tick of the mode → clustering
  private static final double CLUSTER_RATIO_MAX = 0.82;
  // Mean absolute consecutive-interval difference; < 0.8 ticks = no human jitter
  private static final double MIN_HUMAN_JITTER = 0.8;

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    if (data == null || !data.startedSwinging()) return;
    String name = player.getName();
    if (name == null) return;
    if (data.usingItem || data.usingItemTicks > 0) return;
    if (data.breakingBlock || data.recentlyBrokeBlock()) {
      clearPlayerAnalysis(name);
      return;
    }

    long nowMs = System.currentTimeMillis();
    Long lastSwing = lastSwingTimestamp.get(name);
    lastSwingTimestamp.put(name, nowMs);
    if (lastSwing != null && nowMs - lastSwing > BUFFER_TIMEOUT_MS) {
      clearPlayerAnalysis(name);
      return;
    }

    LinkedList<Long> timestamps = clickTimestamps.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> intervals = clickIntervals.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> jitters = jitterSamples.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Integer> cpsHistory =
        cpsWindowHistory.computeIfAbsent(name, k -> new LinkedList<>());

    CheckBuffer fluctuationBuffer =
        fluctuationBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer dropBuffer = dropBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer repetitiveBuffer = repetitiveBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer equalDelayBuffer = equalDelayBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer cpsBuffer = cpsBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    fluctuationBuffer.decay(0.03D);
    dropBuffer.decay(0.03D);
    repetitiveBuffer.decay(0.03D);

    timestamps.addFirst(currentTick);
    while (timestamps.size() > CLICK_SAMPLE_SIZE) timestamps.removeLast();

    if (timestamps.size() >= 2) {
      long interval = timestamps.get(0) - timestamps.get(1);
      if (interval > 0) {
        if (!intervals.isEmpty()) jitters.addFirst(Math.abs(interval - intervals.getFirst()));
        intervals.addFirst(interval);
        while (intervals.size() > CLICK_SAMPLE_SIZE) intervals.removeLast();
        while (jitters.size() > CLICK_SAMPLE_SIZE) jitters.removeLast();
      }
    }

    int cps = 0;
    long cutoff = currentTick - CPS_WINDOW_TICKS;
    for (long ts : timestamps) if (ts >= cutoff) cps++;
    cpsHistory.addFirst(cps);
    while (cpsHistory.size() > 50) cpsHistory.removeLast();

    if (intervals.size() < MIN_CLICKS_FOR_ANALYSIS) return;

    /* ── 1. Deviation + Coefficient-of-Variation ─────────────────────────────
    Requires BOTH low stddev AND low CV to reduce false positives on players
    who happen to click consistently in a short window.                      */
    if (intervals.size() >= STDDEV_COLLECTION_SIZE) {
      double stddev = StatisticalUtils.standardDeviation(intervals);
      double mean = meanLong(intervals);
      double cv = mean > 0 ? stddev / mean : 0;

      LinkedList<Double> devSamples =
          deviationSamples.computeIfAbsent(name, k -> new LinkedList<>());
      devSamples.addFirst(stddev);
      if (devSamples.size() > 5) devSamples.removeLast();

      if (devSamples.size() >= DEVIATION_META_SAMPLE_SIZE) {
        double metaStd = StatisticalUtils.standardDeviation(devSamples);
        double vl = deviationVl.getOrDefault(name, 0.0D);
        if (stddev < MACHINE_STDDEV && metaStd < 12.0D && cv < MIN_HUMAN_CV) {
          vl += stddev < 0.5D ? 4 : stddev < 0.7D ? 3 : 2;
          if (vl > 5) {
            context.receiveSignal(
                name, "AutoClicker (Deviation) sd=" + fmt(stddev) + " cv=" + fmt(cv));
            devSamples.clear();
            vl *= 0.5D;
          }
        } else if (vl > 0) {
          vl -= 0.3D;
          vl *= 0.88D;
        }
        deviationVl.put(name, Math.max(0, vl));
      }
    }

    /* ── 2. Entropy — flag LOW entropy only (autoclickers), not mid-range ────
    Previous check (0.35–1.0) incorrectly caught legit players who have
    moderate entropy. Real autoclickers have entropy near 0 (single value)
    or at most ~0.4. Humans typically produce entropy > 1.5.                */
    if (intervals.size() >= CLICK_SAMPLE_SIZE) {
      double ent = StatisticalUtils.entropy(intervals);
      double vl = entropyVl.getOrDefault(name, 0.0D);
      if (ent < LOW_ENTROPY_THRESHOLD) {
        vl += 1.5 + (LOW_ENTROPY_THRESHOLD - ent) * 3.0;
        if (vl > 4) {
          context.receiveSignal(name, "AutoClicker (Entropy) ent=" + fmt(ent));
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.25D;
        vl *= 0.95D;
      }
      entropyVl.put(name, Math.max(0, vl));
    }

    /* ── 3. Kurtosis — platykurtic (< -0.8) or extreme leptokurtic (> 15) ───
    Previous code divided by 1000 and checked 0–6, which is wrong.
    Excess kurtosis: normal ≈ 0, uniform (autoclicker) < 0,
    single-value autoclicker → very high positive.                           */
    if (intervals.size() >= 40) {
      double kurt = StatisticalUtils.kurtosis(intervals);
      double vl = kurtosisVl.getOrDefault(name, 0.0D);
      if (kurt < -0.8D) {
        // Platykurtic: clicks are too uniformly distributed — machine-like
        vl += 1.5;
        if (vl > 12) {
          context.receiveSignal(name, "AutoClicker (Kurtosis/flat) k=" + fmt(kurt));
          vl *= 0.5D;
        }
      } else if (kurt > 15.0D) {
        // Extreme leptokurtic: nearly all clicks at the same interval
        vl += 2.0;
        if (vl > 10) {
          context.receiveSignal(name, "AutoClicker (Kurtosis/peak) k=" + fmt(kurt));
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.15D;
        vl *= 0.97D;
      }
      kurtosisVl.put(name, Math.max(0, vl));
    }

    /* ── 4. Interval Cluster Ratio — > 82% of clicks at mode ± 1 tick ───────
    Distinct from stddev: catches autoclickers that jitter ±1 to evade
    deviation checks while still clustering at a fixed rate.                 */
    if (intervals.size() >= 40) {
      double ratio = clusterRatio(intervals, 1L);
      double vl = clusterVl.getOrDefault(name, 0.0D);
      if (ratio > CLUSTER_RATIO_MAX) {
        vl += 2.0 + (ratio - CLUSTER_RATIO_MAX) * 10.0;
        if (vl > 6) {
          context.receiveSignal(name, "AutoClicker (Cluster) ratio=" + fmt(ratio));
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.2D;
        vl *= 0.92D;
      }
      clusterVl.put(name, Math.max(0, vl));
    }

    /* ── 5. Jitter — mean |Δinterval| between consecutive clicks ─────────────
    Humans have natural hand tremor: consecutive click gaps vary by ≥ 1 tick.
    Autoclickers have near-zero variance between consecutive intervals.       */
    if (jitters.size() >= 30) {
      double mj = meanLong(jitters);
      double vl = jitterVl.getOrDefault(name, 0.0D);
      if (mj < MIN_HUMAN_JITTER) {
        vl += 2.0 + (MIN_HUMAN_JITTER - mj) * 3.0;
        if (vl > 5) {
          context.receiveSignal(name, "AutoClicker (Jitter) j=" + fmt(mj));
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.2D;
        vl *= 0.92D;
      }
      jitterVl.put(name, Math.max(0, vl));
    }

    /* ── 6. Lag-1 Autocorrelation — near 1.0 = each click predicts the next ─
    Human clicking is not autocorrelated. Autoclickers produce strongly
    correlated sequences (same interval → same next interval).               */
    if (intervals.size() >= 50) {
      double ac = autocorrelation(intervals, 1);
      double vl = autocorrVl.getOrDefault(name, 0.0D);
      if (ac > 0.85D) {
        vl += 1.5;
        if (vl > 8) {
          context.receiveSignal(name, "AutoClicker (Autocorr) r=" + fmt(ac));
          vl *= 0.5D;
        }
      } else if (vl > 0) {
        vl -= 0.1D;
        vl *= 0.97D;
      }
      autocorrVl.put(name, Math.max(0, vl));
    }

    /* ── 7. CPS Fluctuation — periodic spikes / drops ────────────────────────
    Bug fix: drops were using fluctuationBuffer instead of a separate buffer,
    causing spikes and drops to interfere with each other's VL tracking.     */
    if (intervals.size() >= 10) {
      double cur = 20.0D / sumOf(intervals, 10) * 50.0D;
      double prev = lastCps.getOrDefault(name, cur);
      lastCps.put(name, cur);
      double diff = cur - prev;

      LinkedList<Long> spikes = spikeTimestamps.computeIfAbsent(name, k -> new LinkedList<>());
      LinkedList<Long> drops = dropTimestamps.computeIfAbsent(name, k -> new LinkedList<>());

      if (diff > 0.45D) {
        spikes.addFirst(nowMs);
        if (spikes.size() > 10) spikes.removeLast();
      }
      if (diff < -0.45D) {
        drops.addFirst(nowMs);
        if (drops.size() > 10) drops.removeLast();
      }
      if (!spikes.isEmpty() && nowMs - spikes.getLast() > 10000) spikes.clear();
      if (!drops.isEmpty() && nowMs - drops.getLast() > 10000) drops.clear();

      if (spikes.size() >= 3 && timestampStdDev(spikes) < 1200) {
        if (fluctuationBuffer.flag(1.5D, 6.0D)) {
          context.receiveSignal(name, "AutoClicker (Fluctuation/Spike)");
          fluctuationBuffer.reset();
          spikes.clear();
        }
      } else fluctuationBuffer.decay(0.2D);

      if (drops.size() >= 3 && timestampStdDev(drops) < 1200) {
        if (dropBuffer.flag(1.5D, 6.0D)) {
          context.receiveSignal(name, "AutoClicker (Fluctuation/Drop)");
          dropBuffer.reset();
          drops.clear();
        }
      } else dropBuffer.decay(0.2D);
    }

    /* ── 8. Repetitive Interval-Difference Pattern ───────────────────────────  */
    LinkedList<Double> diffHist =
        intervalDiffHistory.computeIfAbsent(name, k -> new LinkedList<>());
    if (intervals.size() >= 4) {
      for (int i = 0; i < intervals.size() - 1 && diffHist.size() < 50; i++) {
        if (i >= diffHist.size())
          diffHist.addFirst(Math.abs((double) (intervals.get(i) - intervals.get(i + 1))));
      }
      while (diffHist.size() > 50) diffHist.removeLast();
      if (diffHist.size() >= 20) {
        if (StatisticalUtils.hasRepetitivePattern(diffHist, 0.3D)) {
          if (repetitiveBuffer.flag(1.5D, 6.0D)) {
            context.receiveSignal(name, "AutoClicker (Repetitive)");
            repetitiveBuffer.reset();
          }
        } else repetitiveBuffer.decay(0.2D);
      }
    }

    /* ── 9. Equal-Delay Streak ───────────────────────────────────────────────
    Extended upper bound from < 10 to < 15 ticks to also catch slower
    autoclickers (~4–6 CPS range) that use larger fixed intervals.           */
    if (intervals.size() >= 2) {
      long cur = intervals.getFirst();
      Long lastDel = lastDelayTick.get(name);
      if (lastDel != null && lastDel >= 0) {
        if (Math.abs(cur - lastDel) <= EQUAL_DELAY_TOLERANCE && cur > 0 && cur < 15) {
          int consecutive = consecutiveEqualDelays.getOrDefault(name, 0) + 1;
          consecutiveEqualDelays.put(name, consecutive);
          int limit =
              10 + (cur > 1 ? 3 : 0) + (cur > 2 ? 3 : 0) + (cur > 3 ? 3 : 0) + (cur > 5 ? 4 : 0);
          if (consecutive > limit && equalDelayBuffer.flag(2.0D, 5.0D)) {
            context.receiveSignal(
                name, "AutoClicker (EqualDelay) streak=" + consecutive + " d=" + cur);
            equalDelayBuffer.reset();
            consecutiveEqualDelays.put(name, 0);
          }
        } else {
          int c = consecutiveEqualDelays.getOrDefault(name, 0);
          if (c > 0) consecutiveEqualDelays.put(name, Math.max(0, c - 2));
          equalDelayBuffer.decay(0.15D);
        }
      }
      lastDelayTick.put(name, cur);
    }

    /* ── 10. Raw CPS Hard Cap ────────────────────────────────────────────────
    Previous threshold (25) was far too lenient. Sustained > 16 CPS in 1.8.9
    is humanly near-impossible; flag weight scales with excess CPS.          */
    if (cps > SUSPICIOUS_CPS) {
      if (cpsBuffer.flag(1.0 + (cps - SUSPICIOUS_CPS) * 0.2, 4.0D)) {
        context.receiveSignal(name, "AutoClicker (CPS) cps=" + cps);
        cpsBuffer.reset();
      }
    } else cpsBuffer.decay(0.3D);
  }

  /* ─── Helpers ─────────────────────────────────────────────────────────────── */

  private double meanLong(LinkedList<Long> l) {
    if (l.isEmpty()) return 0;
    double s = 0;
    for (long v : l) s += v;
    return s / l.size();
  }

  /** Fraction of intervals within ±tol ticks of the distribution mode */
  private double clusterRatio(LinkedList<Long> intervals, long tol) {
    Map<Long, Integer> freq = new HashMap<>();
    for (long v : intervals) freq.merge(v, 1, Integer::sum);
    long mode = 0;
    int max = 0;
    for (Map.Entry<Long, Integer> e : freq.entrySet()) {
      if (e.getValue() > max) {
        max = e.getValue();
        mode = e.getKey();
      }
    }
    int cnt = 0;
    for (long v : intervals) if (Math.abs(v - mode) <= tol) cnt++;
    return (double) cnt / intervals.size();
  }

  /** Pearson lag-k autocorrelation coefficient */
  private double autocorrelation(LinkedList<Long> intervals, int lag) {
    if (intervals.size() < lag + 2) return 0;
    long[] a = new long[intervals.size()];
    int i = 0;
    for (long v : intervals) a[i++] = v;
    double mean = 0;
    for (long v : a) mean += v;
    mean /= a.length;
    double num = 0, den = 0;
    for (int j = 0; j < a.length - lag; j++) num += (a[j] - mean) * (a[j + lag] - mean);
    for (long v : a) {
      double d = v - mean;
      den += d * d;
    }
    return den == 0 ? 0 : num / den;
  }

  private double sumOf(LinkedList<Long> iv, int count) {
    double s = 0;
    int i = 0;
    for (long v : iv) {
      if (i++ >= count) break;
      s += v;
    }
    return s;
  }

  private double timestampStdDev(LinkedList<Long> ts) {
    if (ts.size() < 2) return Double.MAX_VALUE;
    double sum = 0;
    for (long t : ts) sum += t;
    double mean = sum / ts.size(), var = 0;
    for (long t : ts) {
      double d = t - mean;
      var += d * d;
    }
    return Math.sqrt(var / ts.size());
  }

  private static String fmt(double v) {
    return String.format("%.3f", v);
  }

  private void clearPlayerAnalysis(String name) {
    clear(clickTimestamps.get(name));
    clear(clickIntervals.get(name));
    clear(jitterSamples.get(name));
    clear(deviationSamples.get(name));
    clear(spikeTimestamps.get(name));
    clear(dropTimestamps.get(name));
    clear(intervalDiffHistory.get(name));
    deviationVl.remove(name);
    entropyVl.remove(name);
    kurtosisVl.remove(name);
    autocorrVl.remove(name);
    clusterVl.remove(name);
    jitterVl.remove(name);
    lastCps.remove(name);
    consecutiveEqualDelays.remove(name);
    lastDelayTick.remove(name);
  }

  private void clear(LinkedList<?> l) {
    if (l != null) l.clear();
  }

  public void reset() {
    clickTimestamps.clear();
    clickIntervals.clear();
    jitterSamples.clear();
    deviationSamples.clear();
    cpsWindowHistory.clear();
    spikeTimestamps.clear();
    dropTimestamps.clear();
    intervalDiffHistory.clear();
    deviationVl.clear();
    entropyVl.clear();
    kurtosisVl.clear();
    autocorrVl.clear();
    clusterVl.clear();
    jitterVl.clear();
    fluctuationBuffers.clear();
    dropBuffers.clear();
    repetitiveBuffers.clear();
    equalDelayBuffers.clear();
    cpsBuffers.clear();
    lastCps.clear();
    consecutiveEqualDelays.clear();
    lastDelayTick.clear();
    lastSwingTimestamp.clear();
  }
}
