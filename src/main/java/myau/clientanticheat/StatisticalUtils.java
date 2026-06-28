package myau.clientanticheat;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class StatisticalUtils {
  private StatisticalUtils() {}

  public static double mean(Collection<? extends Number> values) {
    if (values.isEmpty()) return 0.0;
    double sum = 0.0;
    for (Number v : values) {
      sum += v.doubleValue();
    }
    return sum / values.size();
  }

  public static double variance(Collection<? extends Number> values) {
    if (values.size() < 2) return 0.0;
    double m = mean(values);
    double sumSq = 0.0;
    for (Number v : values) {
      double diff = v.doubleValue() - m;
      sumSq += diff * diff;
    }
    return sumSq / values.size();
  }

  public static double standardDeviation(Collection<? extends Number> values) {
    return Math.sqrt(variance(values));
  }

  public static <T extends Number> double entropy(Collection<T> values) {
    if (values.isEmpty()) return 0.0;
    Map<Long, Integer> counts = new HashMap<>();
    int total = 0;
    for (T value : values) {
      if (value != null) {
        long key = value.longValue();
        counts.put(key, counts.getOrDefault(key, 0) + 1);
        total++;
      }
    }
    if (total == 0) return 0.0;
    double ent = 0.0;
    for (int count : counts.values()) {
      double probability = (double) count / total;
      if (probability > 0) {
        ent -= probability * log2(probability);
      }
    }
    return ent;
  }

  public static double kurtosis(Collection<? extends Number> values) {
    int n = values.size();
    if (n < 4) return 0.0;
    double m = mean(values);
    double s2 = 0.0;
    double s4 = 0.0;
    for (Number v : values) {
      double diff = v.doubleValue() - m;
      s2 += diff * diff;
      s4 += diff * diff * diff * diff;
    }
    if (s2 == 0.0) return 0.0;
    double d2 = (double) n * (n + 1.0) / ((n - 1.0) * (n - 2.0) * (n - 3.0));
    double d3 = 3.0 * Math.pow(n - 1.0, 2.0) / ((n - 2.0) * (n - 3.0));
    double variance = s2 / n;
    if (variance == 0.0) return 0.0;
    return d2 * (s4 / (variance * variance * n)) - d3;
  }

  public static double coefficientOfVariation(Collection<? extends Number> values) {
    double m = mean(values);
    if (m == 0.0) return 0.0;
    return standardDeviation(values) / Math.abs(m);
  }

  public static boolean hasRepetitivePattern(LinkedList<Double> list, double threshold) {
    int length = list.size();
    if (length < 6) return false;

    int matchCount = 0;
    int requiredMatches = length / 3;

    for (int patternLength = 2; patternLength <= length / 2; patternLength++) {
      matchCount = 0;
      for (int i = 0; i < length - patternLength; i++) {
        if (Math.abs(list.get(i) - list.get(i + patternLength)) < threshold) {
          matchCount++;
        }
      }
      if (matchCount >= requiredMatches) return true;
    }

    int consecutiveEqual = 0;
    for (int i = 0; i < length - 1; i++) {
      if (Math.abs(list.get(i) - list.get(i + 1)) <= threshold) {
        consecutiveEqual++;
        if (consecutiveEqual >= 4) return true;
      } else {
        consecutiveEqual = 0;
      }
    }
    return false;
  }

  public static double gcd(double a, double b) {
    a = Math.abs(a);
    b = Math.abs(b);
    if (a < 0.001 || b < 0.001) return Math.max(a, b);
    int iterations = 0;
    while (b > 0.001 && iterations++ < 100) {
      double temp = b;
      b = a % b;
      a = temp;
    }
    return a;
  }

  public static double sensitivityFromGcd(double gcd) {
    double f = Math.cbrt(gcd / 8.0);
    return (f - 0.2) / 0.6;
  }

  /**
   * Exponential Moving Average (EMA) alpha = smoothing factor (0-1), higher = more weight to recent
   * values
   */
  public static double exponentialMovingAverage(Collection<? extends Number> values, double alpha) {
    if (values.isEmpty()) return 0.0;
    double ema = 0.0;
    boolean first = true;
    for (Number v : values) {
      if (first) {
        ema = v.doubleValue();
        first = false;
      } else {
        ema = alpha * v.doubleValue() + (1.0 - alpha) * ema;
      }
    }
    return ema;
  }

  /** Weighted mean where newer values have higher weight */
  public static double weightedMean(List<? extends Number> values) {
    int size = values.size();
    if (size == 0) return 0.0;
    double sum = 0.0;
    double weightSum = 0.0;
    for (int i = 0; i < size; i++) {
      double w = (double) (i + 1) / size; // newer = higher weight
      sum += values.get(i).doubleValue() * w;
      weightSum += w;
    }
    return sum / weightSum;
  }

  /** Variance on a sliding window – detects sudden changes in dispersion */
  public static double slidingWindowVariance(List<? extends Number> values, int windowSize) {
    int size = values.size();
    if (size < windowSize || windowSize < 2) return 0.0;
    double sum = 0.0;
    double sumSq = 0.0;
    int start = size - windowSize;
    for (int i = start; i < size; i++) {
      double v = values.get(i).doubleValue();
      sum += v;
      sumSq += v * v;
    }
    double mean = sum / windowSize;
    return (sumSq / windowSize) - (mean * mean);
  }

  /** Auto-correlation at lag k – detects periodic patterns */
  public static double autoCorrelation(List<? extends Number> values, int lag) {
    int n = values.size();
    if (n <= lag || lag <= 0) return 0.0;
    double m = mean(values);
    double numerator = 0.0;
    double denominator = 0.0;
    for (int i = 0; i < n - lag; i++) {
      double v = values.get(i).doubleValue();
      double vLag = values.get(i + lag).doubleValue();
      numerator += (v - m) * (vLag - m);
      denominator += (v - m) * (v - m);
    }
    if (denominator == 0.0) return 0.0;
    return numerator / denominator;
  }

  /**
   * Histogram similarity (chi-squared distance) between two value lists. Lower = more similar.
   * Useful for comparing rotation distributions.
   */
  public static double histogramSimilarity(
      List<? extends Number> a, List<? extends Number> b, int bins) {
    if (a.isEmpty() || b.isEmpty()) return 1.0;
    double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    for (Number v : a) {
      double d = v.doubleValue();
      if (d < min) min = d;
      if (d > max) max = d;
    }
    for (Number v : b) {
      double d = v.doubleValue();
      if (d < min) min = d;
      if (d > max) max = d;
    }
    double range = max - min;
    if (range < 1e-10) return a.size() == b.size() ? 0.0 : 1.0;
    int[] histA = new int[bins];
    int[] histB = new int[bins];
    for (Number v : a) {
      int idx = (int) ((v.doubleValue() - min) / range * (bins - 1));
      idx = Math.max(0, Math.min(bins - 1, idx));
      histA[idx]++;
    }
    for (Number v : b) {
      int idx = (int) ((v.doubleValue() - min) / range * (bins - 1));
      idx = Math.max(0, Math.min(bins - 1, idx));
      histB[idx]++;
    }
    double chiSq = 0.0;
    for (int i = 0; i < bins; i++) {
      double sum = histA[i] + histB[i];
      if (sum > 0) {
        chiSq += (histA[i] - histB[i]) * (histA[i] - histB[i]) / sum;
      }
    }
    return chiSq / (bins * 2);
  }

  /**
   * Median Absolute Deviation – robust measure of dispersion. Low MAD = values are suspiciously
   * consistent.
   */
  public static double medianAbsoluteDeviation(List<? extends Number> values) {
    int n = values.size();
    if (n < 3) return 0.0;
    double med = median(values);
    double[] devs = new double[n];
    for (int i = 0; i < n; i++) {
      devs[i] = Math.abs(values.get(i).doubleValue() - med);
    }
    return medianArray(devs);
  }

  private static double median(List<? extends Number> values) {
    int n = values.size();
    double[] sorted = new double[n];
    int i = 0;
    for (Number v : values) sorted[i++] = v.doubleValue();
    java.util.Arrays.sort(sorted);
    if (n % 2 == 1) return sorted[n / 2];
    return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
  }

  private static double medianArray(double[] sorted) {
    int n = sorted.length;
    java.util.Arrays.sort(sorted);
    if (n % 2 == 1) return sorted[n / 2];
    return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
  }

  /** Normalize a value to [0, 1] range using min-max scaling */
  public static double normalize(double value, double min, double max) {
    if (max - min == 0) return 0.5;
    return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
  }

  /** Sigmoid function for smoothing threshold transitions */
  public static double sigmoid(double x) {
    return 1.0 / (1.0 + Math.exp(-x));
  }

  /** Weighted sum combining multiple scores */
  public static double weightedScore(double[] scores, double[] weights) {
    if (scores.length != weights.length) return 0.0;
    double sum = 0.0;
    double weightSum = 0.0;
    for (int i = 0; i < scores.length; i++) {
      sum += scores[i] * weights[i];
      weightSum += weights[i];
    }
    return weightSum > 0 ? sum / weightSum : 0.0;
  }

  private static double log2(double x) {
    return Math.log(x) / Math.log(2.0);
  }
}
