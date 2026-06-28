package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.entity.player.EntityPlayer;

/**
 * AutoClicker detection — CPS analysis, deviation, entropy, clustering. Inspired by RavenbS
 * detection patterns.
 */
public class ClickerCheck {
  private final Map<String, LinkedList<Long>> clickTimestamps = new HashMap<>();
  private final Map<String, LinkedList<Long>> clickIntervals = new HashMap<>();
  private final Map<String, LinkedList<Long>> jitterSamples = new HashMap<>();
  private final Map<String, LinkedList<Double>> deviationSamples = new HashMap<>();
  private final Map<String, CheckBuffer> deviationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> entropyBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> cpsBuffers = new HashMap<>();
  private final Map<String, Long> lastSwingTimestamp = new HashMap<>();
  private final Map<String, Double> lastDeviationVl = new HashMap<>();
  private final Map<String, Double> lastEntropyVl = new HashMap<>();

  private static final int CLICK_SAMPLE_SIZE = 100;
  private static final int STDDEV_COLLECTION_SIZE = 50;
  private static final int MIN_CLICKS_FOR_ANALYSIS = 25;
  private static final long BUFFER_TIMEOUT_MS = 4000L;
  private static final int SUSPICIOUS_CPS = 16;
  private static final double MACHINE_STDDEV = 0.9;
  private static final double MIN_HUMAN_CV = 0.10;
  private static final double LOW_ENTROPY_THRESHOLD = 0.5;
  private static final double MIN_HUMAN_JITTER = 0.8;

  public void check(EntityPlayer player, long currentTick) {
    if (player == null) return;
    String name = player.getName();
    if (name == null) return;

    // Only check on swing start
    if (!(player.swingProgress > 0.0F && player.swingProgressInt > 0)) {
      // Check was held for swing tick
    }

    boolean swinging = player.swingProgress > 0.0F;
    boolean prevSwinging = lastSwingTimestamp.containsKey(name);
    if (!swinging) {
      if (prevSwinging) lastSwingTimestamp.remove(name);
      return;
    }

    long now = System.currentTimeMillis();
    Long lastSwing = lastSwingTimestamp.get(name);
    lastSwingTimestamp.put(name, now);
    if (lastSwing != null && now - lastSwing > BUFFER_TIMEOUT_MS) {
      clearPlayerAnalysis(name);
      return;
    }
    if (lastSwing == null) return;

    if (player.isUsingItem() || player.isBlocking()) return;

    LinkedList<Long> timestamps = clickTimestamps.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> intervals = clickIntervals.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> jitters = jitterSamples.computeIfAbsent(name, k -> new LinkedList<>());
    CheckBuffer cpsBuffer = cpsBuffers.computeIfAbsent(name, k -> new CheckBuffer());

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
    long cutoff = currentTick - 20;
    for (long ts : timestamps) if (ts >= cutoff) cps++;

    if (cps > SUSPICIOUS_CPS) {
      if (cpsBuffer.flag(1.0 + (cps - SUSPICIOUS_CPS) * 0.2, 4.0)) {
        AlertManager.flag(name, "AutoClicker", "cps=" + cps, cps);
        cpsBuffer.reset();
      }
    } else cpsBuffer.decay(0.3);

    if (intervals.size() < MIN_CLICKS_FOR_ANALYSIS) return;

    // Deviation + CV
    if (intervals.size() >= STDDEV_COLLECTION_SIZE) {
      double stddev = StatisticalUtils.standardDeviation(intervals);
      double mean = meanLong(intervals);
      double cv = mean > 0 ? stddev / mean : 0;

      LinkedList<Double> devSamples =
          deviationSamples.computeIfAbsent(name, k -> new LinkedList<>());
      devSamples.addFirst(stddev);
      if (devSamples.size() > 5) devSamples.removeLast();

      if (devSamples.size() >= 3) {
        double metaStd = StatisticalUtils.standardDeviation(devSamples);
        double vl = lastDeviationVl.getOrDefault(name, 0.0);
        if (stddev < MACHINE_STDDEV && metaStd < 12.0 && cv < MIN_HUMAN_CV) {
          vl += stddev < 0.5 ? 4 : stddev < 0.7 ? 3 : 2;
          if (vl > 5) {
            AlertManager.flag(name, "AutoClicker", "deviation sd=" + fmt(stddev), (int) vl);
            devSamples.clear();
            vl *= 0.5;
          }
        } else if (vl > 0) {
          vl -= 0.3;
          vl *= 0.88;
        }
        lastDeviationVl.put(name, Math.max(0, vl));
      }
    }

    // Entropy
    if (intervals.size() >= CLICK_SAMPLE_SIZE) {
      double ent = StatisticalUtils.entropy(intervals);
      double vl = lastEntropyVl.getOrDefault(name, 0.0);
      if (ent < LOW_ENTROPY_THRESHOLD) {
        vl += 1.5 + (LOW_ENTROPY_THRESHOLD - ent) * 3.0;
        if (vl > 4) {
          AlertManager.flag(name, "AutoClicker", "entropy=" + fmt(ent), (int) vl);
          vl *= 0.5;
        }
      } else if (vl > 0) {
        vl -= 0.25;
        vl *= 0.95;
      }
      lastEntropyVl.put(name, Math.max(0, vl));
    }

    // Jitter
    if (jitters.size() >= 30) {
      double mj = meanLong(jitters);
      if (mj < MIN_HUMAN_JITTER) {
        AlertManager.flag(
            name, "AutoClicker", "jitter=" + fmt(mj), (int) ((MIN_HUMAN_JITTER - mj) * 10));
      }
    }
  }

  private double meanLong(LinkedList<Long> l) {
    if (l.isEmpty()) return 0;
    double s = 0;
    for (long v : l) s += v;
    return s / l.size();
  }

  private void clearPlayerAnalysis(String name) {
    clear(clickTimestamps.get(name));
    clear(clickIntervals.get(name));
    clear(jitterSamples.get(name));
    clear(deviationSamples.get(name));
    lastDeviationVl.remove(name);
    lastEntropyVl.remove(name);
  }

  private void clear(LinkedList<?> l) {
    if (l != null) l.clear();
  }

  private static String fmt(double v) {
    return String.format("%.3f", v);
  }

  public void reset() {
    clickTimestamps.clear();
    clickIntervals.clear();
    jitterSamples.clear();
    deviationSamples.clear();
    deviationBuffers.clear();
    entropyBuffers.clear();
    cpsBuffers.clear();
    lastSwingTimestamp.clear();
    lastDeviationVl.clear();
    lastEntropyVl.clear();
  }
}
