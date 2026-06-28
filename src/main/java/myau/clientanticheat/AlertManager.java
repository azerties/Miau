package myau.clientanticheat;

import java.util.*;
import myau.Myau;
import myau.notification.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public final class AlertManager {
  private static final Map<String, AlertEntry> alerts = new LinkedHashMap<>();
  private static final long MARK_DURATION_MS = 120_000L; // 2 minutes
  private static final long COOLDOWN_MS = 2500L;
  private static final int MAX_ALERTS = 50;

  public static final class AlertEntry {
    public final String playerName;
    public final String checkName;
    public final String detail;
    public final int vl;
    public final long time;
    public final int distance;

    private AlertEntry(String playerName, String checkName, String detail, int vl, int distance) {
      this.playerName = playerName;
      this.checkName = checkName;
      this.detail = detail;
      this.vl = vl;
      this.time = System.currentTimeMillis();
      this.distance = distance;
    }
  }

  public static void flag(String playerName, String checkName, String detail, int vl) {
    if (playerName == null || playerName.isEmpty()) return;
    String key = playerName.toLowerCase(Locale.ROOT);
    AlertEntry existing = alerts.get(key);
    if (existing != null && System.currentTimeMillis() - existing.time < COOLDOWN_MS) return;

    int dist = getPlayerDistance(playerName);
    AlertEntry entry = new AlertEntry(playerName, checkName, detail, vl, dist);
    alerts.put(key, entry);
    prune();

    // Send the formatted alert
    AntiCheatAlertStyle.displayFlag(playerName, checkName, detail, vl, dist);

    // Show on-screen notification via Miau's existing notification system
    String notifTitle = "\u26a0 " + playerName;
    String notifDesc =
        checkName
            + (detail != null && !detail.isEmpty() ? " [" + detail + "]" : "")
            + " [VL:"
            + vl
            + "]";
    try {
      Minecraft.getMinecraft()
          .addScheduledTask(
              () -> {
                Myau.notificationManager
                    .builder(NotificationType.WARN)
                    .duration(5000)
                    .title(notifTitle)
                    .description(notifDesc)
                    .buildAndPublish();
              });
    } catch (Exception ignored) {
    }
  }

  public static Set<String> getMarkedNames() {
    prune();
    Set<String> names = new HashSet<>();
    for (AlertEntry e : alerts.values()) {
      names.add(e.playerName);
    }
    return names;
  }

  public static boolean hasMarkedPlayers() {
    prune();
    return !alerts.isEmpty();
  }

  public static int getNametagColor() {
    return 0xCCFF3B30;
  }

  public static void clear() {
    alerts.clear();
    AntiCheatAlertStyle.clearMarkedCheaters();
  }

  public static void clearPlayer(String playerName) {
    if (playerName != null) {
      alerts.remove(playerName.toLowerCase(Locale.ROOT));
      AntiCheatAlertStyle.unmarkCheater(playerName);
    }
  }

  private static void prune() {
    long now = System.currentTimeMillis();
    alerts.values().removeIf(e -> now - e.time > MARK_DURATION_MS);
    while (alerts.size() > MAX_ALERTS) {
      Iterator<Map.Entry<String, AlertEntry>> it = alerts.entrySet().iterator();
      if (it.hasNext()) {
        it.next();
        it.remove();
      }
    }
  }

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
}
