package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraNoSwingCheck {
  private final Map<String, CheckBuffer> buffers = new HashMap<>();
  private final Map<String, Integer> lastTargetHurtTime = new HashMap<>();
  private final Map<String, LinkedList<Integer>> swingProgressHistory = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    LinkedList<Integer> swingHistory =
        this.swingProgressHistory.computeIfAbsent(name, key -> new LinkedList<>());
    swingHistory.addFirst(player.swingProgressInt);
    if (swingHistory.size() > 5) swingHistory.removeLast();

    if (data.nearestTarget != null && data.nearestTargetDistance < 4.5D) {
      EntityPlayer target = data.nearestTarget;
      int prevHurtTime = this.lastTargetHurtTime.getOrDefault(name, 0);
      this.lastTargetHurtTime.put(name, target.hurtTime);

      boolean targetJustHurt = prevHurtTime == 0 && target.hurtTime > 0;

      if (targetJustHurt) {
        boolean noSwingRecent = true;
        for (int sw : swingHistory) {
          if (sw > 0) {
            noSwingRecent = false;
            break;
          }
        }

        if (noSwingRecent) {
          if (buffer.flag(2.0D, 6.0D)) {
            context.receiveSignal(name, "KillAura (No Swing)");
            buffer.reset();
          }
        } else {
          buffer.decay(0.5D);
        }
      }
    } else {
      buffer.decay(0.1D);
    }
  }

  public void reset() {
    this.buffers.clear();
    this.lastTargetHurtTime.clear();
    this.swingProgressHistory.clear();
  }
}
