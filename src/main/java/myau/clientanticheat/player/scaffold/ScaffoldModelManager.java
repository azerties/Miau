package myau.clientanticheat.player.scaffold;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages lightweight statistical models for scaffold detection. Follows MX Project's FactoryML
 * pattern: - Load/save models from files - Cache models by ID - Fallback if model doesn't exist
 *
 * <p>Each model is a 4-scale statistical analyzer (like MX's DataML) that tracks mean/variance at
 * multiple time scales.
 */
public class ScaffoldModelManager {

  private static final int MAGIC = 0x534D4C01; // "SML1"
  private static final int VERSION = 1;
  private static final int NUM_SCALES = 4;
  private static final int STACK_SIZE = 32;

  private static final Map<String, ScaffoldStatisticalModel> MODEL_CACHE = new HashMap<>();
  private static Path modelDir = null;

  private ScaffoldModelManager() {}

  /** Initialize model directory */
  public static void init(Path directory) {
    modelDir = directory;
    if (!Files.exists(directory)) {
      try {
        Files.createDirectories(directory);
      } catch (Exception ignored) {
      }
    }
  }

  /** Get or create a model for the given name */
  public static ScaffoldStatisticalModel getModel(String name) {
    return MODEL_CACHE.computeIfAbsent(
        name,
        key -> {
          ScaffoldStatisticalModel model = loadFromFile(name);
          if (model == null) {
            model = new ScaffoldStatisticalModel(NUM_SCALES, STACK_SIZE);
            saveToFile(name, model);
          }
          return model;
        });
  }

  /** Save model to file */
  public static void saveToFile(String name, ScaffoldStatisticalModel model) {
    if (modelDir == null) return;
    Path file = modelDir.resolve(name + ".sml");
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeInt(NUM_SCALES);
      out.writeInt(STACK_SIZE);
      for (int i = 0; i < NUM_SCALES; i++) {
        for (int j = 0; j < STACK_SIZE; j++) {
          out.writeDouble(model.scaleMeans[i][j]);
          out.writeDouble(model.scaleVars[i][j]);
        }
        out.writeInt(model.scaleCounts[i]);
        out.writeDouble(model.scaleDecays[i]);
      }
    } catch (Exception ignored) {
    }
  }

  /** Load model from file */
  public static ScaffoldStatisticalModel loadFromFile(String name) {
    if (modelDir == null) return null;
    Path file = modelDir.resolve(name + ".sml");
    if (!Files.exists(file)) return null;

    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
      int magic = in.readInt();
      int ver = in.readInt();
      if (magic != MAGIC || ver != VERSION) return null;
      int numScales = in.readInt();
      int stackSize = in.readInt();
      if (numScales != NUM_SCALES || stackSize != STACK_SIZE) return null;

      ScaffoldStatisticalModel model = new ScaffoldStatisticalModel(numScales, stackSize);
      for (int i = 0; i < numScales; i++) {
        for (int j = 0; j < stackSize; j++) {
          model.scaleMeans[i][j] = in.readDouble();
          model.scaleVars[i][j] = in.readDouble();
        }
        model.scaleCounts[i] = in.readInt();
        model.scaleDecays[i] = in.readDouble();
      }
      return model;
    } catch (Exception e) {
      return null;
    }
  }

  /** Remove model from cache */
  public static void removeModel(String name) {
    MODEL_CACHE.remove(name);
  }

  /** Clear all cached models */
  public static void clear() {
    MODEL_CACHE.clear();
  }

  /**
   * Lightweight statistical model that tracks mean/variance at multiple time scales. Inspired by MX
   * Project's DataML + StatisticML pattern.
   */
  public static class ScaffoldStatisticalModel {
    public final double[][] scaleMeans;
    public final double[][] scaleVars;
    public final int[] scaleCounts;
    public final double[] scaleDecays;
    private final int stackSize;
    private int totalSamples;

    public ScaffoldStatisticalModel(int numScales, int stackSize) {
      this.stackSize = stackSize;
      this.scaleMeans = new double[numScales][stackSize];
      this.scaleVars = new double[numScales][stackSize];
      this.scaleCounts = new int[numScales];
      this.scaleDecays = new double[numScales];
      this.totalSamples = 0;

      // Scale decays: 0.5, 0.25, 0.125, 0.0625 (matches MX DataML pattern)
      for (int i = 0; i < numScales; i++) {
        scaleDecays[i] = Math.pow(0.5, i + 1);
      }
    }

    /** Push a feature value into the model */
    public void push(double value, boolean isCheat) {
      double label = isCheat ? 1.0 : 0.0;
      for (int s = 0; s < scaleMeans.length; s++) {
        int idx = scaleCounts[s] % stackSize;
        double decay = scaleDecays[s];

        // EMA update for mean
        if (scaleCounts[s] == 0) {
          scaleMeans[s][idx] = value;
          scaleVars[s][idx] = 0.1;
        } else {
          double diff = value - scaleMeans[s][idx];
          scaleMeans[s][idx] += decay * diff;
          scaleVars[s][idx] += decay * (diff * diff - scaleVars[s][idx]);
        }
        scaleCounts[s]++;
      }
      totalSamples++;
    }

    /**
     * Check a feature value against learned distribution. Returns unusual score (0 = normal, 1+ =
     * suspicious).
     */
    public double check(double value) {
      double score = 0.0;
      int activeScales = 0;

      for (int s = 0; s < scaleMeans.length; s++) {
        if (scaleCounts[s] < 3) continue;
        activeScales++;

        int idx = (scaleCounts[s] - 1) % stackSize;
        double mean = scaleMeans[s][idx];
        double var = Math.max(0.001, scaleVars[s][idx]);
        double std = Math.sqrt(var);

        // Z-score: how many standard deviations from mean
        double zScore = Math.abs(value - mean) / std;

        // Z-score contribution to unusual score
        if (zScore > 3.0) score += 0.3;
        else if (zScore > 2.0) score += 0.15;
        else if (zScore > 1.5) score += 0.05;
      }

      return activeScales > 0 ? score / activeScales : 0.0;
    }

    public int getTotalSamples() {
      return totalSamples;
    }
  }
}
