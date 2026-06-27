package myau.clientanticheat.movement.blink;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class BlinkCheck {
  private final Map<String, Integer> frozenTicks = new HashMap<>();
  private final Map<String, CheckBuffer> burstBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> pulseBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> timerBuffers = new HashMap<>();
  private final Map<String, long[]> timerBalanceData = new HashMap<>();
  private final Map<String, CheckBuffer> blinkLimitBuffers = new HashMap<>();

  private static final long FIFTY_MS_NANOS = 50_000_000L;
  private static final long CLOCK_ERROR_NANOS = 5_000_000L;
  private static final long TIMER_OVERFLOW_LIMIT = 180_000_000L;
  private static final int BLINK_TICK_LIMIT = 60;
  private static final int WARM_UP_TICKS = 200;

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;
    CheckBuffer burstBuffer = this.burstBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer pulseBuffer = this.pulseBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer timerBuffer = this.timerBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer blinkLimitBuffer =
        this.blinkLimitBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      this.frozenTicks.remove(name);
      burstBuffer.decay(0.5D);
      pulseBuffer.decay(0.5D);
      timerBuffer.decay(0.5D);
      blinkLimitBuffer.decay(0.5D);
      this.timerBalanceData.remove(name);
      return;
    }

    if (data.collidedHorizontally) {
      this.frozenTicks.remove(name);
      burstBuffer.decay(0.3D);
      pulseBuffer.decay(0.3D);
      timerBuffer.decay(0.3D);
      blinkLimitBuffer.decay(0.3D);
      return;
    }

    long[] balanceState =
        this.timerBalanceData.computeIfAbsent(name, key -> new long[] {System.nanoTime(), 0L, 0L});

    long now = System.nanoTime();
    long delta = now - balanceState[0];
    balanceState[0] = now;

    balanceState[1] += FIFTY_MS_NANOS + CLOCK_ERROR_NANOS - delta;

    long maxLagNanos = (long) BLINK_TICK_LIMIT * FIFTY_MS_NANOS;
    balanceState[1] = Math.max(-maxLagNanos, Math.min(balanceState[1], FIFTY_MS_NANOS * 20));

    boolean warmUpComplete = data.existedTicks > WARM_UP_TICKS;

    if (warmUpComplete && balanceState[1] > TIMER_OVERFLOW_LIMIT) {
      double ticksAhead = (double) balanceState[1] / FIFTY_MS_NANOS;
      double vl = Math.min(Math.max(ticksAhead * 2.0, 1.0), 5.0);
      if (timerBuffer.flag(vl, 4.0D)) {
        context.receiveSignal(name, "Blink (Timer)");
        timerBuffer.reset();
      }
      balanceState[1] -= FIFTY_MS_NANOS / 5;
    } else {
      timerBuffer.decay(0.05D);
    }

    if (warmUpComplete && balanceState[1] < -(long) BLINK_TICK_LIMIT * FIFTY_MS_NANOS / 2) {
      if (blinkLimitBuffer.flag(1.5D, 4.0D)) {
        context.receiveSignal(name, "Blink (Limit)");
        blinkLimitBuffer.reset();
      }
    } else {
      blinkLimitBuffer.decay(0.1D);
    }

    boolean frozen = data.totalDelta < 0.001D && data.yawDelta < 0.02F && data.pitchDelta < 0.02F;
    if (frozen) {
      this.frozenTicks.put(name, this.frozenTicks.getOrDefault(name, 0) + 1);
      burstBuffer.decay(0.1D);
      return;
    }

    int frozenBefore = this.frozenTicks.getOrDefault(name, 0);
    this.frozenTicks.remove(name);

    boolean catchUpBurst = frozenBefore >= 18 && data.totalDelta > 0.65D && data.totalDelta < 8.0D;
    boolean pulseMove =
        data.totalDelta > 2.0D && data.totalDelta < 8.0D && data.horizontalDelta > 1.0D;
    boolean heavyPulse = frozenBefore >= 5 && data.totalDelta > 1.2D && data.totalDelta < 8.0D;

    if (catchUpBurst) {
      if (burstBuffer.flag(1.5D + Math.min(1.0D, frozenBefore / 20.0D), 4.0D)) {
        context.receiveSignal(name, "Blink");
        burstBuffer.reset();
        pulseBuffer.reset();
      }
    } else {
      burstBuffer.decay(0.25D);
    }

    if (pulseMove && !player.isCollidedHorizontally && data.airTicks < 8) {
      if (pulseBuffer.flag(1.0D, 4.5D)) {
        context.receiveSignal(name, "Blink");
        pulseBuffer.reset();
      }
    } else if (heavyPulse) {
      if (pulseBuffer.flag(1.0D, 4.0D)) {
        context.receiveSignal(name, "Pulse Blink");
        pulseBuffer.reset();
      }
    } else {
      pulseBuffer.decay(0.2D);
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data.recentlyHurt();
  }

  public void reset() {
    this.frozenTicks.clear();
    this.burstBuffers.clear();
    this.pulseBuffers.clear();
    this.timerBuffers.clear();
    this.timerBalanceData.clear();
    this.blinkLimitBuffers.clear();
  }
}
