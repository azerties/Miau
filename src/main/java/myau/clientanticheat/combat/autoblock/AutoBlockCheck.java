package myau.clientanticheat.combat.autoblock;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class AutoBlockCheck {
  private final Map<String, CheckBuffer> toggleBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> toggleTimingBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sprintBlockBuffers = new HashMap<>();
  private final Map<String, Long> lastToggleTick = new HashMap<>();
  private final Map<String, Integer> toggleCount = new HashMap<>();
  private final Map<String, Long> lastSprintBlockTime = new HashMap<>();
  private final Map<String, Long> lastHurtTime = new HashMap<>();

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (data.recentlyHurt()) {
      this.lastHurtTime.put(name, System.currentTimeMillis());
      decayAll(name);
      return;
    }

    long now = System.currentTimeMillis();
    Long lastHurt = this.lastHurtTime.get(name);
    if (lastHurt != null && now - lastHurt < 1000L) {
      decayAll(name);
      return;
    }

    CheckBuffer toggleBuffer = this.toggleBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer toggleTimingBuffer =
        this.toggleTimingBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sprintBlockBuffer =
        this.sprintBlockBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (data.usingItem) {
      boolean toggle = data.blocking != data.lastBlocking;
      if (toggle) {
        Long lastToggle = this.lastToggleTick.get(name);
        this.lastToggleTick.put(name, currentTick);

        if (lastToggle != null && currentTick - lastToggle <= 2L) {
          boolean rapidCond = lastToggle != null && currentTick - lastToggle <= 2L;
          if (rapidCond) {
            toggleBuffer.flag(0.5D, 5.0D);
          }
          toggleCount.merge(name, 1, Integer::sum);
          if (data.lastBlockToggleTick > 0 && currentTick - data.lastBlockToggleTick <= 2L) {
            int toggles = this.toggleCount.getOrDefault(name, 0);
            if (toggles > 3) {
              toggleTimingBuffer.flag(1.0D, 5.0D);
            }
          }
        } else {
          int current = this.toggleCount.getOrDefault(name, 0);
          if (current > 0) {
            this.toggleCount.put(name, Math.max(0, current - 1));
          }
        }
      }

      boolean sprintBlock =
          data.sprinting
              && data.blocking
              && data.horizontalDelta > 0.22D
              && data.blockingTicks > 5;

      if (sprintBlock) {
        if (!this.lastSprintBlockTime.containsKey(name)) {
          this.lastSprintBlockTime.put(name, now);
        }
        long blockDuration = now - this.lastSprintBlockTime.get(name);
        if (blockDuration > 500L) {
          sprintBlockBuffer.flag(1.0D, 5.0D);
        }
      } else {
        this.lastSprintBlockTime.remove(name);
        sprintBlockBuffer.decay(0.25D);
      }
    } else {
      this.lastToggleTick.remove(name);
      this.toggleCount.remove(name);
      toggleBuffer.decay(0.15D);
      toggleTimingBuffer.decay(0.1D);
      sprintBlockBuffer.decay(0.15D);
    }

    if (toggleBuffer.get() > 4.0D) {
      context.receiveSignal(name, "AutoBlock (Rapid Toggle)");
      toggleBuffer.reset();
    }
    if (toggleTimingBuffer.get() > 4.0D) {
      context.receiveSignal(name, "AutoBlock (Toggle Timing)");
      toggleTimingBuffer.reset();
    }
    if (sprintBlockBuffer.get() > 4.0D) {
      context.receiveSignal(name, "AutoBlock (Sprint Block)");
      sprintBlockBuffer.reset();
    }
  }

  private void decayAll(String name) {
    CheckBuffer toggle = this.toggleBuffers.get(name);
    if (toggle != null) toggle.decay(0.2D);
    CheckBuffer timing = this.toggleTimingBuffers.get(name);
    if (timing != null) timing.decay(0.2D);
    CheckBuffer sprint = this.sprintBlockBuffers.get(name);
    if (sprint != null) sprint.decay(0.2D);
  }

  public void reset() {
    this.toggleBuffers.clear();
    this.toggleTimingBuffers.clear();
    this.sprintBlockBuffers.clear();
    this.lastToggleTick.clear();
    this.toggleCount.clear();
    this.lastSprintBlockTime.clear();
    this.lastHurtTime.clear();
  }
}
