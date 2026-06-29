package myau.clientanticheat.player.scaffold;

import myau.clientanticheat.ClientAntiCheatContext;

/**
 * Multi-tier scoring system that combines heuristic, statistical, and ML scores. Produces a single
 * confidence score [0, 100] for each player.
 *
 * <p>Scoring weights (configurable): - Heuristic: 40% - Statistical: 35% - ML: 25%
 */
public class ScaffoldScorer {

  // Scoring weights - OP Edition: ML takes 50%!
  private static final double HEURISTIC_WEIGHT = 0.25;
  private static final double STATISTICAL_WEIGHT = 0.25;
  private static final double ML_WEIGHT = 0.50;

  // Thresholds
  private static final double FLAG_THRESHOLD = 75.0;
  private static final double ALERT_THRESHOLD = 85.0;
  private static final long ALERT_COOLDOWN_MS = 2500L;

  // Per-player state
  private final java.util.Map<String, PlayerScore> playerScores = new java.util.HashMap<>();
  private final java.util.Map<String, Long> lastAlertTime = new java.util.HashMap<>();

  private static final class PlayerScore {
    double heuristicScore;
    double statisticalScore;
    double mlScore;
    int sampleCount;

    double getTotal() {
      return heuristicScore * HEURISTIC_WEIGHT
          + statisticalScore * STATISTICAL_WEIGHT
          + mlScore * ML_WEIGHT;
    }
  }

  /** Update heuristic score for a player (called every tick from check methods) */
  public void updateHeuristic(String name, double score) {
    PlayerScore ps = playerScores.computeIfAbsent(name, k -> new PlayerScore());
    // EMA smooth the heuristic score
    ps.heuristicScore = ps.heuristicScore * 0.7 + score * 0.3;
    ps.sampleCount++;
  }

  /** Update statistical score (called periodically from statistical analysis) */
  public void updateStatistical(String name, double score) {
    PlayerScore ps = playerScores.computeIfAbsent(name, k -> new PlayerScore());
    ps.statisticalScore = ps.statisticalScore * 0.6 + score * 0.4;
  }

  /** Update ML score (called when ML model produces a prediction) */
  public void updateML(String name, double score) {
    PlayerScore ps = playerScores.computeIfAbsent(name, k -> new PlayerScore());
    ps.mlScore = score;
  }

  /** Get current total score for a player */
  public double getTotalScore(String name) {
    PlayerScore ps = playerScores.get(name);
    return ps != null ? ps.getTotal() : 0.0;
  }

  /** Get current heuristic score */
  public double getHeuristicScore(String name) {
    PlayerScore ps = playerScores.get(name);
    return ps != null ? ps.heuristicScore : 0.0;
  }

  /**
   * Evaluate all scores and trigger alert if threshold is exceeded. Returns true if an alert was
   * triggered.
   */
  public boolean evaluateAndAlert(String name, String prefix, ClientAntiCheatContext context) {
    PlayerScore ps = playerScores.get(name);
    if (ps == null || ps.sampleCount < 5) return false;

    double total = ps.getTotal();
    if (total < FLAG_THRESHOLD) {
      // Decay scores if below threshold
      ps.heuristicScore *= 0.98;
      ps.statisticalScore *= 0.97;
      ps.mlScore *= 0.95;
      return false;
    }

    // Check cooldown
    long now = System.currentTimeMillis();
    long lastAlert = lastAlertTime.getOrDefault(name, 0L);
    if (now - lastAlert < ALERT_COOLDOWN_MS) {
      return false;
    }

    String level;
    if (total >= ALERT_THRESHOLD) {
      level = "§cCRITICAL";
    } else {
      level = "§eWARNING";
    }

    // Build detail string
    StringBuilder detail = new StringBuilder();
    detail.append(
        String.format(
            " §7[§fH:%.0f S:%.0f M:%.0f§7] §8», ",
            ps.heuristicScore, ps.statisticalScore, ps.mlScore));

    if (ps.heuristicScore > 30) detail.append("§cHeuristic ");
    if (ps.statisticalScore > 25) detail.append("§6Statistical ");
    if (ps.mlScore > 20) detail.append("§eML ");

    context.receiveSignal(name, prefix + " (" + level + detail + ")");
    lastAlertTime.put(name, now);

    // Reset scores after alert
    ps.heuristicScore = 0;
    ps.statisticalScore = 0;
    ps.mlScore = 0;

    return true;
  }

  /** Remove a player from scoring (cleanup) */
  public void removePlayer(String name) {
    playerScores.remove(name);
    lastAlertTime.remove(name);
  }

  /** Reset all scores */
  public void reset() {
    playerScores.clear();
    lastAlertTime.clear();
  }
}
