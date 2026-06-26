package myau.clientanticheat.combat.autoclicker;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class ClickSpeedCheck {
  private final Map<String, Queue<Long>> playerClicks = new HashMap<>();

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    if (data == null || !data.startedSwinging()) {
      return;
    }

    String name = player.getName();
    if (name == null) return;

    Queue<Long> clickTimestamps = playerClicks.computeIfAbsent(name, k -> new LinkedList<>());

    while (!clickTimestamps.isEmpty() && currentTick - clickTimestamps.peek() > 20) {
      clickTimestamps.poll();
    }

    clickTimestamps.add(currentTick);

    if (clickTimestamps.size() > 20) {
      context.receiveSignal(name, "AutoClicker");
      clickTimestamps.clear();
    }
  }

  public void reset() {
    playerClicks.clear();
  }
}
