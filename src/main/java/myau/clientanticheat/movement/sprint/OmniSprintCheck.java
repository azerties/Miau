package myau.clientanticheat.movement.sprint;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public class OmniSprintCheck {
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

    if (!data.sprinting || !data.onGround) {
      buffer.decay(0.3D);
      return;
    }

    if (data.horizontalDelta < 0.15D) {
      buffer.decay(0.2D);
      return;
    }

    float movementYaw = (float) (Math.atan2(data.deltaZ, data.deltaX) * 180.0D / Math.PI);
    float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(player.rotationYaw - movementYaw));

    if (yawDiff > 85.0F) {
      if (buffer.flag(1.0D, 999.0D)) {
        context.receiveSignal(name, "OmniSprint");
        buffer.reset();
      }
    } else {
      buffer.decay(0.2D);
    }
  }

  public void reset() {
    this.buffers.clear();
  }
}
