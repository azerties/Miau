package myau.clientanticheat.player.scaffold;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
import myau.clientanticheat.player.scaffold.ml.ScaffoldRNNModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * ML-Powered Scaffold Detection Check - OP Edition! 🚀
 *
 * <p>Combines THREE detection layers: 1. Heuristics (existing) - 25% weight 2. Statistical Models
 * (4-scale) - 25% weight 3. RNN Model (Stacked BiLSTM) - 50% weight
 *
 * <p>The RNN analyzes rotation sequences of up to 150 samples to detect subtle cheater patterns
 * invisible to heuristics.
 */
public class ScaffoldMLCheck {

  // Feature group identifiers for statistical models
  private static final String MODEL_PLACEMENT = "scaffold_placement";
  private static final String MODEL_ROTATION = "scaffold_rotation";
  private static final String MODEL_SNEAK = "scaffold_sneak";
  private static final String MODEL_SPATIAL = "scaffold_spatial";

  // Minimum samples needed
  private static final int MIN_PLACEMENT_SAMPLES = 5;
  private static final int MIN_ROTATION_SAMPLES = 10;
  private static final int RNN_MIN_SEQUENCE = 30;
  private static final int RNN_MAX_SEQUENCE = 150;

  // RNN will run every N checks (to avoid overloading ticks)
  private static final int RNN_CHECK_INTERVAL = 20;

  private final Map<String, PlayerMLData> playerData = new HashMap<>();
  private final ScaffoldScorer scorer;
  private final ScaffoldRNNModel rnnModel;

  public ScaffoldMLCheck() {
    this.scorer = new ScaffoldScorer();
    this.rnnModel = new ScaffoldRNNModel();

    // Try to load pre-trained model from resources
    try {
      InputStream modelStream = getClass().getResourceAsStream("/ml/scaffold_rnn.model");
      if (modelStream != null) {
        rnnModel.load(modelStream);
      }
    } catch (Exception ignored) {
      // Will use randomly initialized weights (still works for statistical features)
    }

    // Initialize statistical models
    ScaffoldModelManager.getModel(MODEL_PLACEMENT);
    ScaffoldModelManager.getModel(MODEL_ROTATION);
    ScaffoldModelManager.getModel(MODEL_SNEAK);
    ScaffoldModelManager.getModel(MODEL_SPATIAL);
  }

  public ScaffoldMLCheck(ScaffoldScorer scorer) {
    this();
  }

  public ScaffoldScorer getScorer() {
    return scorer;
  }

  public ScaffoldRNNModel getRnnModel() {
    return rnnModel;
  }

  private static final class PlayerMLData {
    // Raw rotation sequence for RNN (yawDelta, pitchDelta, horizontalDelta)
    final LinkedList<Vec3> rotationSequence = new LinkedList<>();
    // Placement intervals sequence
    final LinkedList<Double> placementSequence = new LinkedList<>();
    // Statistical model scores
    double placementAnomaly = 0;
    double rotationAnomaly = 0;
    double sneakAnomaly = 0;
    double spatialAnomaly = 0;
    int checkCounter = 0;
    long lastRnnCheck = 0;
    double lastRNNProbability = 0.5;
  }

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || isExempt(player, data)) {
      playerData.remove(name);
      return;
    }

    PlayerMLData mlData = playerData.computeIfAbsent(name, k -> new PlayerMLData());
    mlData.checkCounter++;

    // ── Collect rotation sequence for RNN ──
    if (data.yawDelta > 0.01F || data.pitchDelta > 0.01F) {
      mlData.rotationSequence.addFirst(
          new Vec3(data.yawDelta, data.pitchDelta, data.horizontalDelta));
      if (mlData.rotationSequence.size() > RNN_MAX_SEQUENCE) {
        mlData.rotationSequence.removeLast();
      }
    }

    // ── Collect placement intervals ──
    if (data.startedSwinging()) {
      long now = System.currentTimeMillis();
      if (!data.placementTimestamps.isEmpty()) {
        double interval = now - data.placementTimestamps.getFirst();
        mlData.placementSequence.addFirst(interval);
        if (mlData.placementSequence.size() > 50) {
          mlData.placementSequence.removeLast();
        }
      }
    }

    // ── Statistical Model Analysis (every 5 ticks) ──
    if (mlData.checkCounter % 5 == 0) {
      performStatisticalCheck(name, data, mlData);
    }

    // ── Rotation Sequence Analysis (every 10 ticks) ──
    if (mlData.checkCounter % 10 == 0 && mlData.rotationSequence.size() >= MIN_ROTATION_SAMPLES) {
      performRotationSequenceAnalysis(name, mlData);
    }

    // ── RNN Model Inference (every RNN_CHECK_INTERVAL ticks, with enough samples) ──
    if (mlData.checkCounter % RNN_CHECK_INTERVAL == 0
        && mlData.rotationSequence.size() >= RNN_MIN_SEQUENCE) {
      performRNNInference(name, mlData);
    }

    // ── Update scorer with all components ──
    // Heuristic score from scaffold checks (fed by the other scaffold checks)
    double heuristicComponent = scorer.getHeuristicScore(name);

    // Statistical component (0-100)
    double statisticalComponent =
        (mlData.placementAnomaly
            + mlData.rotationAnomaly
            + mlData.sneakAnomaly
            + mlData.spatialAnomaly);

    // ML component (RNN probability expressed as 0-100)
    double mlComponent = (mlData.lastRNNProbability - 0.5) * 200; // 0.5→0, 0.75→50, 1.0→100
    mlComponent = Math.max(0, Math.min(100, mlComponent));

    scorer.updateStatistical(name, statisticalComponent);
    scorer.updateML(name, mlComponent);

    // Combined alert with new weights: Heuristic 25%, Statistical 25%, ML 50%
    scorer.evaluateAndAlert(name, "Scaffold (ML)", context);
  }

  /** Full RNN inference on rotation sequence. */
  private void performRNNInference(String name, PlayerMLData mlData) {
    List<Vec3> seq = new ArrayList<>(mlData.rotationSequence);
    if (seq.size() < RNN_MIN_SEQUENCE) return;

    // Convert Vec3 list to raw double[][] of [yawDelta, pitchDelta]
    double[][] rawVecs = new double[Math.min(seq.size(), RNN_MAX_SEQUENCE)][2];
    for (int i = 0; i < rawVecs.length; i++) {
      rawVecs[i][0] = seq.get(i).xCoord; // yawDelta
      rawVecs[i][1] = seq.get(i).yCoord; // pitchDelta
    }

    // Run RNN prediction
    ScaffoldRNNModel.RNNResult result = rnnModel.analyze(rawVecs);
    mlData.lastRNNProbability = result.probability;
    mlData.lastRnnCheck = System.currentTimeMillis();

    // Update ML score in scorer
    double mlScore = (result.probability - 0.5) * 200;
    mlScore = Math.max(0, Math.min(100, mlScore));
    scorer.updateML(name, mlScore);
  }

  /** Statistical check using 4-scale learned models. */
  private void performStatisticalCheck(String name, PlayerCheckData data, PlayerMLData mlData) {
    ScaffoldModelManager.ScaffoldStatisticalModel placementModel =
        ScaffoldModelManager.getModel(MODEL_PLACEMENT);
    ScaffoldModelManager.ScaffoldStatisticalModel rotationModel =
        ScaffoldModelManager.getModel(MODEL_ROTATION);
    ScaffoldModelManager.ScaffoldStatisticalModel sneakModel =
        ScaffoldModelManager.getModel(MODEL_SNEAK);
    ScaffoldModelManager.ScaffoldStatisticalModel spatialModel =
        ScaffoldModelManager.getModel(MODEL_SPATIAL);

    // Placement features
    List<Double> placementFeatures = ScaffoldFeatureExtractor.extractPlacementFeatures(data);
    double placementScore = 0;
    int pCount = 0;
    if (!data.placementIntervals.isEmpty()) {
      double weightedAvg = StatisticalUtils.weightedMean(data.placementIntervals);
      placementScore += placementModel.check(weightedAvg);
      pCount++;

      long minInterval = Long.MAX_VALUE;
      for (long v : data.placementIntervals) if (v < minInterval) minInterval = v;
      placementScore += placementModel.check(minInterval);
      pCount++;

      double std =
          StatisticalUtils.standardDeviation(
              new LinkedList<Number>() {
                {
                  for (Long v : data.placementIntervals) add(v);
                }
              });
      placementScore += placementModel.check(std);
      pCount++;
    }
    mlData.placementAnomaly = pCount > 0 ? (placementScore / pCount) * 100 : 0;
    scorer.updateStatistical(name, mlData.placementAnomaly);

    // Rotation features
    if (data.scaffoldYawDeltas.size() >= 5) {
      List<Double> rotFeatures = ScaffoldFeatureExtractor.extractRotationFeatures(data);
      double rotScore = 0;
      for (double f : rotFeatures) rotScore += rotationModel.check(f);
      mlData.rotationAnomaly = (rotScore / rotFeatures.size()) * 100;
    }

    // Learn from current data if not anomalous (unsupervised)
    if (mlData.placementAnomaly < 30 && !data.placementIntervals.isEmpty()) {
      placementModel.push(StatisticalUtils.weightedMean(data.placementIntervals), false);
    }
  }

  /** Analyze rotation sequence using statistical methods (entropy, auto-correlation, MAD). */
  private void performRotationSequenceAnalysis(String name, PlayerMLData mlData) {
    LinkedList<Vec3> seq = mlData.rotationSequence;
    if (seq.size() < MIN_ROTATION_SAMPLES) return;

    LinkedList<Double> yawSeq = new LinkedList<>();
    LinkedList<Double> pitchSeq = new LinkedList<>();
    for (Vec3 v : seq) {
      yawSeq.addFirst(v.xCoord);
      pitchSeq.addFirst(v.yCoord);
    }

    double score = 0.0;
    int metrics = 0;

    // 1. Entropy
    double yawEntropy =
        StatisticalUtils.entropy(
            new LinkedList<Long>() {
              {
                for (Double d : yawSeq) add(d.longValue());
              }
            });
    double pitchEntropy =
        StatisticalUtils.entropy(
            new LinkedList<Long>() {
              {
                for (Double d : pitchSeq) add(d.longValue());
              }
            });
    if (yawEntropy < 1.0) score += 15;
    if (pitchEntropy < 0.5) score += 20;
    metrics += 2;

    // 2. Auto-correlation
    double yawAutoCorr = StatisticalUtils.autoCorrelation(yawSeq, 1);
    double pitchAutoCorr = StatisticalUtils.autoCorrelation(pitchSeq, 1);
    if (Math.abs(yawAutoCorr) > 0.5) score += 10;
    if (Math.abs(pitchAutoCorr) > 0.5) score += 10;
    metrics += 2;

    // 3. Consistency (MAD)
    double yawMad = StatisticalUtils.medianAbsoluteDeviation(yawSeq);
    double pitchMad = StatisticalUtils.medianAbsoluteDeviation(pitchSeq);
    if (yawMad < 0.5) score += 10;
    if (pitchMad < 0.1) score += 15;
    metrics += 2;

    // 4. Cardinal direction bias
    int cardinalCount = 0;
    for (Double y : yawSeq) {
      double dist = Math.abs(y % 90);
      if (dist < 5 || dist > 85) cardinalCount++;
    }
    double cardinalRatio = (double) cardinalCount / yawSeq.size();
    if (cardinalRatio > 0.6) score += 15;
    metrics++;

    double finalScore = metrics > 0 ? score / metrics : 0;
    mlData.rotationAnomaly = Math.min(100, finalScore * 5);
    scorer.updateStatistical(name, mlData.rotationAnomaly);
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data.recentlyTeleported();
  }

  public void reset() {
    playerData.clear();
  }
}
