package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraLatencyCheck {
  private final Map<String, CheckBuffer> freezeBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> burstBuffers = new HashMap<>();
  private final Map<String, Integer> frozenTicks = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;
    if (data.recentlyHurt() || player.isDead || player.ticksExisted < 40) return;

    CheckBuffer freezeBuffer = this.freezeBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer burstBuffer = this.burstBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    boolean frozen =
        data.totalDelta < 0.002D
            && data.yawDelta < 0.02F
            && data.pitchDelta < 0.02F
            && data.stillTicks > 3;

    if (frozen) {
      this.frozenTicks.put(name, this.frozenTicks.getOrDefault(name, 0) + 1);
      return;
    }

    int frozenBefore = this.frozenTicks.getOrDefault(name, 0);
    this.frozenTicks.remove(name);

    if (frozenBefore < 10) {
      freezeBuffer.decay(0.15D);
      burstBuffer.decay(0.15D);
      return;
    }

    boolean targetNearby = data.nearestTarget != null && data.nearestTargetDistance < 6.0D;
    boolean burst = data.totalDelta > 0.8D && data.totalDelta < 8.0D;
    boolean attacked = data.startedSwinging();

    if (targetNearby && burst && attacked && data.burstTicks > 2) {
      if (burstBuffer.flag(1.5D, 7.0D)) {
        context.receiveSignal(name, "KillAura (Latency)");
        burstBuffer.reset();
      }
    } else if (targetNearby && burst) {
      if (freezeBuffer.flag(1.0D, 6.0D)) {
        context.receiveSignal(name, "KillAura (Freeze)");
        freezeBuffer.reset();
      }
    } else {
      freezeBuffer.decay(0.2D);
      burstBuffer.decay(0.2D);
    }
  }

  public void reset() {
    this.freezeBuffers.clear();
    this.burstBuffers.clear();
    this.frozenTicks.clear();
  }
}
