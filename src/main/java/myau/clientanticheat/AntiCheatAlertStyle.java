package myau.clientanticheat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import myau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

/**
 * Simplified anticheat alert. Format:
 *
 * <p>{@code [Miau] &lt;teamColor&gt;playerName &7detected for &bKillAura &8[silent(snap)] &7[&aVL:
 * &f69&7] - 5m}
 *
 * <p>The client prefix + username color is handled automatically by ChatUtil. Username color is
 * pulled from the player's Scoreboard team prefix (works on any server with team-colored names).
 * Distance is computed from the local player to the suspect.
 */
public class AntiCheatAlertStyle {

  // ── Marked cheaters (for nametag overlay) ───────────────────────────────

  private static final Map<String, MarkedCheater> markedCheaters = new HashMap<>();
  private static final long MARK_DURATION_MS = 120_000L; // 2 minutes

  private static final class MarkedCheater {
    final String playerName;
    final String checkName;
    final int vl;
    final long markedAt;

    MarkedCheater(String playerName, String checkName, int vl) {
      this.playerName = playerName;
      this.checkName = checkName;
      this.vl = vl;
      this.markedAt = System.currentTimeMillis();
    }

    boolean isExpired() {
      return System.currentTimeMillis() - markedAt > MARK_DURATION_MS;
    }
  }

  // ── Main alert method ──────────────────────────────────────────────────

  /**
   * Display a simple anticheat alert.
   *
   * @param playerName suspect name
   * @param cheatName type of cheat (KillAura, Scaffold, etc.)
   * @param detail sub-type / component detail
   * @param vl violation level
   */
  public static void displayFlag(String playerName, String cheatName, String detail, int vl) {
    if (playerName == null || playerName.isEmpty()) return;

    String teamColor = getPlayerTeamColor(playerName);
    String normalized = normalizeCheatName(cheatName);
    // VL: scale vl to a friendly number (aimVl/10)
    int visualVl = Math.max(1, vl);

    // Mark for nametag overlay
    markCheater(playerName, normalized, visualVl);

    StringBuilder sb = new StringBuilder();
    // Username in team color
    sb.append(teamColor).append(playerName);
    // " detected for "
    sb.append(" &7detected for &b").append(normalized);
    // [detail]
    if (detail != null
        && !detail.isEmpty()
        && !"behavior anomaly".equalsIgnoreCase(detail)
        && !"vl".equalsIgnoreCase(detail)) {
      sb.append(" &8[").append(detail).append("&8]");
    }
    // [VL: number]
    sb.append(" &7[&aVL: &f").append(visualVl).append("&7]");
    // - Xm
    int distance = getPlayerDistance(playerName);
    if (distance >= 0) {
      sb.append(" &7- &f").append(distance).append("m");
    }

    ChatUtil.display(sb.toString());
  }

  // ── Cheat name normaliser ──────────────────────────────────────────────

  /** Normalise cheat names so "KillAura (Constant Aim)" becomes just "KillAura". */
  public static String normalizeCheatName(String cheatName) {
    if (cheatName == null) return "Unknown";
    // Strip parenthesised detail like "KillAura (Constant Aim)"
    int paren = cheatName.indexOf(" (");
    String name = paren > 0 ? cheatName.substring(0, paren) : cheatName;
    // Normalise common variations
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.startsWith("killaura") || lower.startsWith("killaura")) return "KillAura";
    if (lower.startsWith("scaffold")) return "Scaffold";
    if (lower.startsWith("autoblock")) return "AutoBlock";
    if (lower.startsWith("reach")) return "Reach";
    if (lower.startsWith("velocity")) return "Velocity";
    if (lower.startsWith("noslow")) return "NoSlow";
    if (lower.startsWith("blink")) return "Blink";
    if (lower.startsWith("fakelag")) return "FakeLag";
    if (lower.startsWith("sprint")) return "Sprint";
    if (lower.startsWith("autoclicker")) return "AutoClicker";
    return name;
  }

  // ── Nametag overlay helpers ────────────────────────────────────────────

  public static void markCheater(String playerName, String checkName, int vl) {
    String key = playerName.toLowerCase(Locale.ROOT);
    markedCheaters.put(key, new MarkedCheater(playerName, checkName, vl));
    pruneExpired();
  }

  public static boolean hasMarkedCheaters() {
    pruneExpired();
    return !markedCheaters.isEmpty();
  }

  public static Set<String> getMarkedCheaterNames() {
    pruneExpired();
    Set<String> names = new HashSet<>();
    for (MarkedCheater m : markedCheaters.values()) {
      names.add(m.playerName);
    }
    return names;
  }

  public static int getNametagColor() {
    return 0xCCFF3B30;
  }

  public static void clearMarkedCheaters() {
    markedCheaters.clear();
  }

  // ── Private helpers ────────────────────────────────────────────────────

  /** Get the player's team colour from the Scoreboard (e.g., "&c" for red). */
  private static String getPlayerTeamColor(String playerName) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null) return "&f";
    Scoreboard board = mc.theWorld.getScoreboard();
    if (board == null) return "&f";
    ScorePlayerTeam team = board.getPlayersTeam(playerName);
    if (team != null) {
      String prefix = team.getColorPrefix();
      if (prefix != null) {
        // Extract the last §-style colour from the prefix
        int idx = prefix.lastIndexOf('\u00A7');
        if (idx >= 0 && idx + 1 < prefix.length()) {
          return "&" + prefix.charAt(idx + 1);
        }
      }
    }
    return "&f"; // default white
  }

  /** Distance in integer blocks from the local player to the suspect. */
  private static int getPlayerDistance(String playerName) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null || mc.theWorld == null) return -1;
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (p.getName().equals(playerName)) {
        return (int) Math.round(mc.thePlayer.getDistanceToEntity(p));
      }
    }
    return -1;
  }

  private static void pruneExpired() {
    markedCheaters.values().removeIf(MarkedCheater::isExpired);
  }
}
