package myau.clientanticheat.movement.sprint;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class ActionSprintCheck {
  private final Map<String, CheckBuffer> buffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (data.recentlyHurt()) {
      CheckBuffer buffer = this.buffers.get(name);
      if (buffer != null) buffer.decay(0.3D);
      return;
    }

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (!data.usingItem || data.usingItemTicks < 5) {
      buffer.decay(0.25D);
      return;
    }

    boolean usingItemWhileSprinting =
        data.sprinting && data.usingItem && data.horizontalDelta > 0.1D;

    if (usingItemWhileSprinting) {
      if (buffer.flag(1.0D, 999.0D)) {
        context.receiveSignal(name, "ActionSprint");
        buffer.reset();
      }
    } else {
      buffer.decay(0.25D);
    }
  }

  public void reset() {
    this.buffers.clear();
  }
}
