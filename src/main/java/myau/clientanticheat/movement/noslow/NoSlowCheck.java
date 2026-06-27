package myau.clientanticheat.movement.noslow;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;

public class NoSlowCheck {
  private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> ratioBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> directionBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> consecutiveViolations = new HashMap<>();
  private final Map<String, Boolean> onSpecialBlock = new HashMap<>();

  private static final double ITEM_USE_SPEED_THRESHOLD = 0.20D;
  private static final double SPEED_THRESHOLD = 0.22D;
  private static final double SPEED_RATIO_THRESHOLD = 0.88D;
  private static final int MIN_TICKS_USING = 6;
  private static final int CONSECUTIVE_FLAG_MIN = 5;

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (data.recentlyHurt() || player.capabilities.isFlying) {
      decayAll(name);
      return;
    }

    boolean onSoulSandOrIce =
        player.worldObj.getBlockState(player.getPosition().down()).getBlock() == Blocks.soul_sand
            || player.worldObj.getBlockState(player.getPosition()).getBlock() == Blocks.ice;
    boolean isSpecialBlock = false;

    try {
      isSpecialBlock =
          onSoulSandOrIce || player.isInWater() || player.isInLava() || player.isOnLadder();
    } catch (Exception e) {
    }

    this.onSpecialBlock.put(name, isSpecialBlock);

    if (isSpecialBlock) {
      decayAll(name);
      return;
    }

    if (!data.usingItem || data.usingItemTicks < MIN_TICKS_USING) {
      decayAll(name);
      return;
    }

    CheckBuffer speedBuffer = this.speedBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer ratioBuffer = this.ratioBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer directionBuffer =
        this.directionBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    double speed = data.horizontalDelta;
    boolean movingLegit = speed > 0.01D;

    if (!movingLegit) {
      decayAll(name);
      return;
    }

    if (speed > SPEED_THRESHOLD) {
      speedBuffer.flag(1.0D, 4.0D);
    } else if (speed > ITEM_USE_SPEED_THRESHOLD) {
      speedBuffer.flag(0.5D, 4.0D);
    } else {
      speedBuffer.decay(0.3D);
    }

    if (data.lastHorizontalDelta > 0.01D) {
      double ratio = speed / data.lastHorizontalDelta;
      if (ratio > SPEED_RATIO_THRESHOLD && speed > 0.08D) {
        ratioBuffer.flag(1.0D, 5.0D);
      } else {
        ratioBuffer.decay(0.3D);
      }
    }

    if (data.yawDelta > 25.0F && speed > 0.18D) {
      directionBuffer.flag(1.0D, 6.0D);
    } else {
      directionBuffer.decay(0.25D);
    }

    if (speedBuffer.get() > 3.0D && ratioBuffer.get() > 4.0D) {
      context.receiveSignal(name, "NoSlow");
      speedBuffer.reset();
      ratioBuffer.reset();
    }
  }

  private void decayAll(String name) {
    CheckBuffer speed = this.speedBuffers.get(name);
    if (speed != null) speed.decay(0.2D);
    CheckBuffer ratio = this.ratioBuffers.get(name);
    if (ratio != null) ratio.decay(0.2D);
    CheckBuffer direction = this.directionBuffers.get(name);
    if (direction != null) direction.decay(0.15D);
  }

  public void reset() {
    this.speedBuffers.clear();
    this.ratioBuffers.clear();
    this.directionBuffers.clear();
    this.consecutiveViolations.clear();
    this.onSpecialBlock.clear();
  }
}
