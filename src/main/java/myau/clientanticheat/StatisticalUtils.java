package myau.clientanticheat;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
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

  private static double log2(double x) {
    return Math.log(x) / Math.log(2.0);
  }
}
