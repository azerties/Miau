package myau.clientanticheat.player.scaffold;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.util.BlockPos;

/**
 * Transforms raw player data into feature vectors for ML-based scaffold detection. Follows the MX
 * Project pattern: extract multiple ObjectML (one per feature type) that can be consumed by
 * statistical models and a lightweight RNN.
 */
public class ScaffoldFeatureExtractor {

  private static final int MAX_FEATURES = 16;

  private ScaffoldFeatureExtractor() {}

  /**
   * Extract placement interval features: the time between consecutive block placements. Cheat
   * clients often have abnormally consistent or impossibly fast intervals.
   */
  public static List<Double> extractPlacementFeatures(PlayerCheckData data) {
    List<Double> features = new ArrayList<>(MAX_FEATURES);
    LinkedList<Long> intervals = data.placementIntervals;

    if (intervals == null || intervals.isEmpty()) {
      for (int i = 0; i < MAX_FEATURES; i++) features.add(0.0);
      return features;
    }

    // 1. Average placement speed (weighted toward recent)
    features.add(StatisticalUtils.weightedMean(intervals));

    // 2. Minimum interval (fastest placement)
    long min = Long.MAX_VALUE;
    for (long v : intervals) if (v < min) min = v;
    features.add((double) min);

    // 3. Maximum interval
    long max = 0;
    for (long v : intervals) if (v > max) max = v;
    features.add((double) max);

    // 4. Standard deviation
    features.add(
        StatisticalUtils.standardDeviation(
            new LinkedList<Number>() {
              {
                for (Long v : intervals) add(v);
              }
            }));

    // 5. Coefficient of variation
    features.add(
        StatisticalUtils.coefficientOfVariation(
            new LinkedList<Number>() {
              {
                for (Long v : intervals) add(v);
              }
            }));

    // 6. Sliding window variance (last 5)
    features.add(
        StatisticalUtils.slidingWindowVariance(
            new LinkedList<Number>() {
              {
                for (Long v : intervals) add(v);
              }
            },
            Math.min(5, intervals.size())));

    // 7. Auto-correlation at lag 1
    features.add(
        StatisticalUtils.autoCorrelation(
            new LinkedList<Number>() {
              {
                for (Long v : intervals) add(v);
              }
            },
            1));

    // 8. Recent burst count (intervals < 100ms in last 10)
    int burst = 0;
    int count = 0;
    for (long v : intervals) {
      if (count++ >= 10) break;
      if (v < 100) burst++;
    }
    features.add((double) burst);

    // 9. Recent slow count (intervals > 500ms)
    int slow = 0;
    count = 0;
    for (long v : intervals) {
      if (count++ >= 10) break;
      if (v > 500) slow++;
    }
    features.add((double) slow);

    // 10. Median
    features.add(
        StatisticalUtils.medianAbsoluteDeviation(
            new LinkedList<Number>() {
              {
                for (Long v : intervals) add(v);
              }
            }));

    // 11-16: Pad with normalized values
    for (int i = features.size(); i < MAX_FEATURES; i++) features.add(0.0);

    return features;
  }

  /**
   * Extract rotation features: yaw and pitch deltas during scaffold. Cheat scaffold rotations are
   * often too smooth, too consistent, or snap perfectly.
   */
  public static List<Double> extractRotationFeatures(PlayerCheckData data) {
    List<Double> features = new ArrayList<>(MAX_FEATURES);
    LinkedList<Float> yawDeltas = data.scaffoldYawDeltas;
    LinkedList<Float> pitchDeltas = data.scaffoldPitchDeltas;

    if (yawDeltas == null || yawDeltas.isEmpty() || pitchDeltas == null || pitchDeltas.isEmpty()) {
      for (int i = 0; i < MAX_FEATURES; i++) features.add(0.0);
      return features;
    }

    // 1. Average yaw delta
    double yawAvg = 0;
    for (float f : yawDeltas) yawAvg += f;
    yawAvg /= yawDeltas.size();
    features.add(yawAvg);

    // 2. Average pitch delta
    double pitchAvg = 0;
    for (float f : pitchDeltas) pitchAvg += f;
    pitchAvg /= pitchDeltas.size();
    features.add(pitchAvg);

    // 3. Yaw standard deviation
    features.add(
        StatisticalUtils.standardDeviation(
            new LinkedList<Number>() {
              {
                for (Float f : yawDeltas) add(f.doubleValue());
              }
            }));

    // 4. Pitch standard deviation
    features.add(
        StatisticalUtils.standardDeviation(
            new LinkedList<Number>() {
              {
                for (Float f : pitchDeltas) add(f.doubleValue());
              }
            }));

    // 5. Yaw entropy (low = suspicious)
    features.add(
        StatisticalUtils.entropy(
            new LinkedList<Long>() {
              {
                for (Float f : yawDeltas) add(f.longValue());
              }
            }));

    // 6. Pitch entropy
    features.add(
        StatisticalUtils.entropy(
            new LinkedList<Long>() {
              {
                for (Float f : pitchDeltas) add(f.longValue());
              }
            }));

    // 7. Yaw/pitch correlation (GCD)
    if (yawDeltas.size() >= 5 && pitchDeltas.size() >= 5) {
      float gcdSum = 0;
      int gcdCount = 0;
      int maxGcd = Math.min(yawDeltas.size(), pitchDeltas.size());
      for (int i = 0; i < maxGcd; i++) {
        if (yawDeltas.get(i) > 0.05F && pitchDeltas.get(i) > 0.05F) {
          gcdSum += StatisticalUtils.gcd(yawDeltas.get(i), pitchDeltas.get(i));
          gcdCount++;
        }
      }
      features.add(gcdCount > 0 ? (double) gcdSum / gcdCount : 0.0);
    } else {
      features.add(0.0);
    }

    // 8. Cardinal snap ratio (how often yaw aligns to 0/90/180/270)
    int cardinal = 0;
    for (float y : yawDeltas) {
      float dist = Math.abs(y % 90);
      if (dist < 3 || dist > 87) cardinal++;
    }
    features.add(yawDeltas.isEmpty() ? 0.0 : (double) cardinal / yawDeltas.size());

    // 9. Sharp rotation count (> 150° yaw change)
    int sharp = 0;
    for (float y : yawDeltas) if (y > 150) sharp++;
    features.add(yawDeltas.isEmpty() ? 0.0 : (double) sharp / yawDeltas.size());

    // 10. Micro-adjustment count (tiny pitch changes < 0.01°)
    int micro = 0;
    for (float p : pitchDeltas) if (p > 0 && p < 0.01F) micro++;
    features.add(pitchDeltas.isEmpty() ? 0.0 : (double) micro / pitchDeltas.size());

    // 11-16: Pad
    for (int i = features.size(); i < MAX_FEATURES; i++) features.add(0.0);

    return features;
  }

  /** Extract sneak pattern features for LegitScaffold detection. */
  public static List<Double> extractSneakFeatures(PlayerCheckData data) {
    List<Double> features = new ArrayList<>(MAX_FEATURES);
    LinkedList<Long> sneakToggles = data.sneakToggleTimestamps;

    // Basic sneak stats
    features.add((double) data.sneakToggleCount);
    features.add((double) data.scaffoldTicks);

    if (sneakToggles == null || sneakToggles.isEmpty()) {
      for (int i = features.size(); i < MAX_FEATURES; i++) features.add(0.0);
      return features;
    }

    // Sneak toggle frequency
    double togglesPerSecond =
        sneakToggles.size() > 1
            ? (double) sneakToggles.size()
                / ((sneakToggles.getFirst() - sneakToggles.getLast()) / 1000.0)
            : 0;
    features.add(togglesPerSecond);

    // Pad
    for (int i = features.size(); i < MAX_FEATURES; i++) features.add(0.0);
    return features;
  }

  /** Extract spatial features: block placement pattern analysis. */
  public static List<Double> extractSpatialFeatures(PlayerCheckData data) {
    List<Double> features = new ArrayList<>(MAX_FEATURES);
    LinkedList<BlockPos> blocks = data.blockPositionHistory;

    if (blocks == null || blocks.size() < 3) {
      for (int i = 0; i < MAX_FEATURES; i++) features.add(0.0);
      return features;
    }

    // 1. How much X varies (0 = straight line along Z)
    int xRange = 0;
    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
    for (BlockPos p : blocks) {
      if (p.getX() < minX) minX = p.getX();
      if (p.getX() > maxX) maxX = p.getX();
    }
    xRange = maxX - minX;
    features.add((double) xRange);

    // 2. How much Z varies
    int zRange = 0;
    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
    for (BlockPos p : blocks) {
      if (p.getZ() < minZ) minZ = p.getZ();
      if (p.getZ() > maxZ) maxZ = p.getZ();
    }
    zRange = maxZ - minZ;
    features.add((double) zRange);

    // 3. Y variance (going up/down)
    int yRange = 0;
    int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
    for (BlockPos p : blocks) {
      if (p.getY() < minY) minY = p.getY();
      if (p.getY() > maxY) maxY = p.getY();
    }
    yRange = maxY - minY;
    features.add((double) yRange);

    // 4. Total blocks placed (normalized)
    features.add((double) blocks.size());

    // Pad
    for (int i = features.size(); i < MAX_FEATURES; i++) features.add(0.0);
    return features;
  }

  /** Combine all features into a single feature vector for ML inference. */
  public static double[] extractAllFeatures(PlayerCheckData data) {
    List<Double> placement = extractPlacementFeatures(data);
    List<Double> rotation = extractRotationFeatures(data);
    List<Double> sneak = extractSneakFeatures(data);
    List<Double> spatial = extractSpatialFeatures(data);

    // Combine into a 64-dim feature vector
    double[] all = new double[64];
    int idx = 0;
    for (double v : placement) if (idx < 64) all[idx++] = v;
    for (double v : rotation) if (idx < 64) all[idx++] = v;
    for (double v : sneak) if (idx < 64) all[idx++] = v;
    for (double v : spatial) if (idx < 64) all[idx++] = v;
    return all;
  }
}
