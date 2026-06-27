package myau.clientanticheat.player.scaffold;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class ScaffoldPlacementCheck {
  private final Map<String, CheckBuffer> flickBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> snapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> microPitchBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> noRotBuffers = new HashMap<>();
  private final Map<String, Long> lastFlag = new HashMap<>();
  private final Map<String, Float> lastPitch = new HashMap<>();
  private final Map<String, Float> lastYaw = new HashMap<>();
  private final Map<String, LinkedList<BlockPos>> lastBlocksPlaced = new HashMap<>();
  private final Map<String, Double> micropitchVl = new HashMap<>();
  private final Map<String, LinkedList<Long>> placeSpeedHistory = new HashMap<>();
  private final Map<String, Long> lastPlaceTime = new HashMap<>();
  private final Map<String, Integer> lockedRotationTicks = new HashMap<>();

  private static final int ROTATION_FLICK_HISTORY = 8;

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer flickBuffer = this.flickBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer snapBuffer = this.snapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer microPitchBuffer =
        this.microPitchBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer noRotBuffer = this.noRotBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      flickBuffer.decay(0.5D);
      snapBuffer.decay(0.5D);
      microPitchBuffer.decay(0.3D);
      noRotBuffer.decay(0.3D);
      this.lockedRotationTicks.remove(name);
      return;
    }

    boolean moving = data.horizontalDelta > 0.15D;
    boolean hasBlockBelow = this.hasSolidBelow(player, world, 1.0D);
    boolean hasRecentSupport = this.hasSolidBelow(player, world, 1.35D);
    boolean nearEdge =
        player.onGround && !this.hasSolidBelowOffset(player, world, data.deltaX, data.deltaZ);
    boolean bridgeContext =
        moving && (nearEdge || !hasBlockBelow || hasRecentSupport && data.pitch > 55.0F);

    boolean sneakBridging = player.isSneaking() && nearEdge && data.pitch > 60.0F;
    float flickThresholdMult = sneakBridging ? 1.5F : 1.0F;

    if (bridgeContext
        && data.pitchDelta > 35.0F * flickThresholdMult
        && Math.abs(data.pitchAcceleration) > 30.0F * flickThresholdMult) {
      flickBuffer.flag(1.25D, 999.0D);
    } else {
      flickBuffer.decay(0.2D);
    }

    float divisorY = data.pitchDelta % 1.5F;
    if (bridgeContext && data.pitchDelta > 3.0F && divisorY == 0.0F && !sneakBridging) {
      snapBuffer.flag(1.5D, 999.0D);
    } else {
      snapBuffer.decay(0.25D);
    }

    float prevPitch = this.lastPitch.getOrDefault(name, data.pitch);
    float prevYaw = this.lastYaw.getOrDefault(name, data.yaw);
    this.lastPitch.put(name, data.pitch);
    this.lastYaw.put(name, data.yaw);
    float pitchDiff = Math.abs(data.pitch - prevPitch);

    LinkedList<Long> placeSpeedHist =
        this.placeSpeedHistory.computeIfAbsent(name, k -> new LinkedList<>());
    long now = System.currentTimeMillis();
    long lastPlace = this.lastPlaceTime.getOrDefault(name, 0L);
    long placeInterval = lastPlace > 0 ? now - lastPlace : 1000L;

    if (data.startedSwinging() && bridgeContext) {
      this.lastPlaceTime.put(name, now);
      placeSpeedHist.addFirst(placeInterval);
      if (placeSpeedHist.size() > ROTATION_FLICK_HISTORY) placeSpeedHist.removeLast();
    }

    if (placeSpeedHist.size() >= ROTATION_FLICK_HISTORY) {
      double avg = 0;
      for (long v : placeSpeedHist) avg += v;
      avg /= placeSpeedHist.size();

      boolean inOneLine = isOneLine(this.lastBlocksPlaced.get(name));
      double vl = this.micropitchVl.getOrDefault(name, 0.0D);

      if (pitchDiff > 3
          && pitchDiff < 20
          && data.pitch > 70
          && avg < 250
          && (inOneLine || placeInterval < 800)
          && !sneakBridging) {
        vl += Math.min(20, pitchDiff / 0.5);
        if (data.pitch > 89.5F) vl += 5;
        if (vl > 250) {
          microPitchBuffer.flag(2.0D, 999.0D);
          vl -= 10;
        }
      } else if (vl > 0) {
        vl *= 0.99;
        vl -= 0.01;
      }
      this.micropitchVl.put(name, vl);
    }

    if (data.startedSwinging() && bridgeContext) {
      LinkedList<BlockPos> blockHistory =
          this.lastBlocksPlaced.computeIfAbsent(name, k -> new LinkedList<>());
      BlockPos pos =
          new BlockPos(
              MathHelper.floor_double(player.posX + data.deltaX * 2),
              MathHelper.floor_double(player.posY - 1),
              MathHelper.floor_double(player.posZ + data.deltaZ * 2));
      blockHistory.addFirst(pos);
      if (blockHistory.size() > 10) blockHistory.removeLast();
    }

    boolean rotationLocked = data.yawDelta < 0.01F && data.pitchDelta < 0.01F && data.pitch > 50.0F;

    if (rotationLocked && bridgeContext) {
      int lockedTicks = this.lockedRotationTicks.getOrDefault(name, 0) + 1;
      this.lockedRotationTicks.put(name, lockedTicks);
    } else {
      int current = this.lockedRotationTicks.getOrDefault(name, 0);
      if (current > 0) {
        this.lockedRotationTicks.put(name, Math.max(0, current - 2));
      }
    }

    if (data.startedSwinging() && bridgeContext) {
      int lockedTicks = this.lockedRotationTicks.getOrDefault(name, 0);
      boolean rapidPlacing = placeInterval < 500;
      boolean isInLine = isOneLine(this.lastBlocksPlaced.get(name));

      if (lockedTicks > 6 && rapidPlacing && isInLine && !sneakBridging) {
        noRotBuffer.flag(1.5D, 999.0D);
      } else if (lockedTicks > 12 && rapidPlacing) {
        noRotBuffer.flag(1.0D, 999.0D);
      } else {
        noRotBuffer.decay(0.1D);
      }
    } else {
      noRotBuffer.decay(0.15D);
    }

    boolean failed =
        flickBuffer.get() > 5.0D
            || snapBuffer.get() > 5.0D
            || microPitchBuffer.get() > 5.0D
            || noRotBuffer.get() > 6.0D;
    if (failed) {
      long flagNow = System.currentTimeMillis();
      long last = this.lastFlag.getOrDefault(name, 0L);
      if (flagNow - last > 2500L) {
        if (flickBuffer.get() > 5.0D) context.receiveSignal(name, "Scaffold (Rotation Flick)");
        else if (snapBuffer.get() > 5.0D) context.receiveSignal(name, "Scaffold (Angle Snap)");
        else if (microPitchBuffer.get() > 5.0D)
          context.receiveSignal(name, "Scaffold (Micro Pitch)");
        else if (noRotBuffer.get() > 6.0D) context.receiveSignal(name, "Scaffold (No Rotation)");
        this.lastFlag.put(name, flagNow);
        flickBuffer.reset();
        snapBuffer.reset();
        microPitchBuffer.reset();
        noRotBuffer.reset();
      }
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data.recentlyTeleported();
  }

  private boolean isOneLine(LinkedList<BlockPos> blocks) {
    if (blocks == null || blocks.size() < 3) return false;
    int lastX = 0, lastY = 0, lastZ = 0;
    boolean lockedX = false, lockedZ = false, first = true;
    int yTolerance = 1;
    for (BlockPos pos : blocks) {
      if (!first) {
        if (lastY != pos.getY()) {
          if (yTolerance-- <= 0) return false;
        }
        if (lastX == pos.getX()) lockedX = true;
        else if (lockedX) return false;
        if (lastZ == pos.getZ()) lockedZ = true;
        else if (lockedZ) return false;
      }
      lastX = pos.getX();
      lastY = pos.getY();
      lastZ = pos.getZ();
      first = false;
    }
    return lockedX || lockedZ;
  }

  private boolean hasSolidBelow(EntityPlayer player, World world, double below) {
    for (double xOffset = -0.3D; xOffset <= 0.3D; xOffset += 0.3D) {
      for (double zOffset = -0.3D; zOffset <= 0.3D; zOffset += 0.3D) {
        BlockPos pos =
            new BlockPos(
                MathHelper.floor_double(player.posX + xOffset),
                MathHelper.floor_double(player.posY - below),
                MathHelper.floor_double(player.posZ + zOffset));
        if (!world.isAirBlock(pos)) return true;
      }
    }
    return false;
  }

  private boolean hasSolidBelowOffset(
      EntityPlayer player, World world, double motionX, double motionZ) {
    BlockPos pos =
        new BlockPos(
            MathHelper.floor_double(player.posX + motionX * 2.0D),
            MathHelper.floor_double(player.posY - 1.0D),
            MathHelper.floor_double(player.posZ + motionZ * 2.0D));
    return !world.isAirBlock(pos);
  }

  public void reset() {
    this.flickBuffers.clear();
    this.snapBuffers.clear();
    this.microPitchBuffers.clear();
    this.noRotBuffers.clear();
    this.lastFlag.clear();
    this.lastPitch.clear();
    this.lastYaw.clear();
    this.lastBlocksPlaced.clear();
    this.micropitchVl.clear();
    this.placeSpeedHistory.clear();
    this.lastPlaceTime.clear();
    this.lockedRotationTicks.clear();
  }
}
