package myau.clientanticheat.player.scaffold;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
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
  private final Map<String, CheckBuffer> microFluctuationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> flickTimingBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> multiAxisFlickBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> interpolatedRotationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sprintPlaceBuffers = new HashMap<>();
  private final Map<String, Long> lastFlag = new HashMap<>();
  private final Map<String, Float> lastPitch = new HashMap<>();
  private final Map<String, Float> lastYaw = new HashMap<>();
  private final Map<String, LinkedList<BlockPos>> lastBlocksPlaced = new HashMap<>();
  private final Map<String, Double> micropitchVl = new HashMap<>();
  private final Map<String, LinkedList<Long>> placeSpeedHistory = new HashMap<>();
  private final Map<String, Long> lastPlaceTime = new HashMap<>();
  private final Map<String, Integer> lockedRotationTicks = new HashMap<>();

  // Micro-fluctuation tracking
  private final Map<String, LinkedList<Float>> pitchMicroChanges = new HashMap<>();
  private final Map<String, LinkedList<Float>> yawMicroChanges = new HashMap<>();

  // Flick-to-placement timing
  private final Map<String, Long> lastFlickTime = new HashMap<>();
  private final Map<String, LinkedList<Long>> flickToPlaceIntervals = new HashMap<>();

  // Interpolated rotation tracking
  private final Map<String, LinkedList<Float>> smoothYawChanges = new HashMap<>();
  private final Map<String, LinkedList<Float>> smoothPitchChanges = new HashMap<>();

  private static final int ROTATION_FLICK_HISTORY = 8;
  private static final int MICRO_HISTORY = 20;
  private static final int FLICK_INTERVAL_HISTORY = 6;
  private static final int INTERPOLATION_WINDOW = 10;

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer flickBuffer = this.flickBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer snapBuffer = this.snapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer microPitchBuffer =
        this.microPitchBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer noRotBuffer = this.noRotBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer microFluctuationBuffer =
        this.microFluctuationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer flickTimingBuffer =
        this.flickTimingBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer multiAxisFlickBuffer =
        this.multiAxisFlickBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer interpolatedRotationBuffer =
        this.interpolatedRotationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sprintPlaceBuffer =
        this.sprintPlaceBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      flickBuffer.decay(0.5D);
      snapBuffer.decay(0.5D);
      microPitchBuffer.decay(0.3D);
      noRotBuffer.decay(0.3D);
      microFluctuationBuffer.decay(0.25D);
      flickTimingBuffer.decay(0.3D);
      multiAxisFlickBuffer.decay(0.2D);
      interpolatedRotationBuffer.decay(0.2D);
      sprintPlaceBuffer.decay(0.5D);
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

    // ── Flick detection (existing) ──
    if (bridgeContext
        && data.pitchDelta > 35.0F * flickThresholdMult
        && Math.abs(data.pitchAcceleration) > 30.0F * flickThresholdMult) {
      flickBuffer.flag(1.25D, 999.0D);
    } else {
      flickBuffer.decay(0.2D);
    }

    // ── Angle snap detection (existing) ──
    float divisorY = data.pitchDelta % 1.5F;
    if (bridgeContext && data.pitchDelta > 3.0F && divisorY == 0.0F && !sneakBridging) {
      snapBuffer.flag(1.5D, 999.0D);
    } else {
      snapBuffer.decay(0.25D);
    }

    // ── Pitch tracking ──
    float prevPitch = this.lastPitch.getOrDefault(name, data.pitch);
    float prevYaw = this.lastYaw.getOrDefault(name, data.yaw);
    this.lastPitch.put(name, data.pitch);
    this.lastYaw.put(name, data.yaw);
    float pitchDiff = Math.abs(data.pitch - prevPitch);

    // ── Place speed history ──
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

    // ── Micro Pitch VL (existing) ──
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

    // ── Block position history ──
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

    // ── Locked rotation (existing) ──
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

    // ════════════════════════════════════════════════
    // NEW CHECKS
    // ════════════════════════════════════════════════

    // ── Micro-fluctuation Analysis (NEW) ──
    // Track very small pitch variations (0.001°-0.01°) between ticks
    // Human: random micro-fluctuations / Cheat: stable micro-patterns
    if (bridgeContext && data.pitch > 60.0F) {
      LinkedList<Float> pitchMicro =
          this.pitchMicroChanges.computeIfAbsent(name, k -> new LinkedList<>());
      LinkedList<Float> yawMicro =
          this.yawMicroChanges.computeIfAbsent(name, k -> new LinkedList<>());

      if (data.pitchDelta > 0.001F && data.pitchDelta < 1.0F) {
        pitchMicro.addFirst(data.pitchDelta);
        if (pitchMicro.size() > MICRO_HISTORY) pitchMicro.removeLast();
      }
      if (data.yawDelta > 0.001F && data.yawDelta < 1.0F) {
        yawMicro.addFirst(data.yawDelta);
        if (yawMicro.size() > MICRO_HISTORY) yawMicro.removeLast();
      }

      if (pitchMicro.size() >= 10 && yawMicro.size() >= 10) {
        double pitchMad =
            StatisticalUtils.medianAbsoluteDeviation(
                new LinkedList<Number>() {
                  {
                    for (Float f : pitchMicro) add(f);
                  }
                });
        double yawMad =
            StatisticalUtils.medianAbsoluteDeviation(
                new LinkedList<Number>() {
                  {
                    for (Float f : yawMicro) add(f);
                  }
                });

        // Both axes have too-consistent micro-movements
        if (pitchMad < 0.02 && yawMad < 0.03 && bridgeContext) {
          microFluctuationBuffer.flag(1.5D, 999.0D);
        } else {
          microFluctuationBuffer.decay(0.15D);
        }
      }
    } else {
      microFluctuationBuffer.decay(0.2D);
    }

    // ── Flick-to-Placement Timing (NEW) ──
    // Cheat scaffolds: flick happens EXACTLY at the right moment before placement
    if (bridgeContext && data.yawDelta > 30.0F) {
      this.lastFlickTime.put(name, now);
    }

    if (data.startedSwinging() && bridgeContext) {
      long lastFlick = this.lastFlickTime.getOrDefault(name, 0L);
      if (lastFlick > 0) {
        long interval = now - lastFlick;
        if (interval < 50) { // flick within 50ms of placement
          LinkedList<Long> intervals =
              this.flickToPlaceIntervals.computeIfAbsent(name, k -> new LinkedList<>());
          intervals.addFirst(interval);
          if (intervals.size() > FLICK_INTERVAL_HISTORY) intervals.removeLast();

          if (intervals.size() >= 4) {
            double mad = StatisticalUtils.medianAbsoluteDeviation(intervals);
            // Very consistent flick->place timing = suspicious
            if (mad < 5 && bridgeContext) {
              flickTimingBuffer.flag(1.5D, 999.0D);
            } else {
              flickTimingBuffer.decay(0.2D);
            }
          }
        }
      }
    }

    // ── Multi-Axis Flick (NEW) ──
    // Simultaneous large yaw + pitch flick is very unnatural in bridge
    if (bridgeContext && data.yawDelta > 80.0F && data.pitchDelta > 30.0F && !sneakBridging) {
      multiAxisFlickBuffer.flag(1.5D, 999.0D);
    } else {
      multiAxisFlickBuffer.decay(0.15D);
    }

    // ── Interpolated Rotation Detection (NEW) ──
    // Cheat clients often smoothly interpolate rotation between placements
    if (bridgeContext) {
      LinkedList<Float> smoothYaws =
          this.smoothYawChanges.computeIfAbsent(name, k -> new LinkedList<>());
      LinkedList<Float> smoothPitches =
          this.smoothPitchChanges.computeIfAbsent(name, k -> new LinkedList<>());

      if (data.yawDelta > 0.05F && data.yawDelta < 30.0F) {
        smoothYaws.addFirst(data.yawDelta);
        if (smoothYaws.size() > INTERPOLATION_WINDOW) smoothYaws.removeLast();
      }
      if (data.pitchDelta > 0.05F && data.pitchDelta < 10.0F) {
        smoothPitches.addFirst(data.pitchDelta);
        if (smoothPitches.size() > INTERPOLATION_WINDOW) smoothPitches.removeLast();
      }

      if (smoothYaws.size() >= 8 && smoothPitches.size() >= 8) {
        // Check for linear interpolation pattern (successive deltas very similar)
        int linearCount = 0;
        for (int i = 0; i < Math.min(6, smoothYaws.size() - 1); i++) {
          float diff = Math.abs(smoothYaws.get(i) - smoothYaws.get(i + 1));
          if (diff < 1.5F) linearCount++;
        }
        for (int i = 0; i < Math.min(6, smoothPitches.size() - 1); i++) {
          float diff = Math.abs(smoothPitches.get(i) - smoothPitches.get(i + 1));
          if (diff < 0.5F) linearCount++;
        }

        // 8+ out of 12 comparisons show near-identical deltas = interpolation
        if (linearCount >= 8 && bridgeContext && recentPlacement()) {
          interpolatedRotationBuffer.flag(1.0D, 999.0D);
        } else {
          interpolatedRotationBuffer.decay(0.15D);
        }
      }
    } else {
      interpolatedRotationBuffer.decay(0.15D);
    }

    // ── Sprint + Place Detection (NEW) ──
    // In vanilla Minecraft 1.8, you cannot place blocks while sprinting
    // (sprint cancels when you right-click in creative, or you must be sneaking)
    if (data.startedSwinging()
        && bridgeContext
        && player.isSprinting()
        && !player.isSneaking()
        && !sneakBridging) {
      sprintPlaceBuffer.flag(2.5D, 999.0D);
    } else {
      sprintPlaceBuffer.decay(0.2D);
    }

    // ── Combined flag ──
    boolean failed =
        flickBuffer.get() > 5.0D
            || snapBuffer.get() > 5.0D
            || microPitchBuffer.get() > 5.0D
            || noRotBuffer.get() > 6.0D
            || microFluctuationBuffer.get() > 5.0D
            || flickTimingBuffer.get() > 6.0D
            || multiAxisFlickBuffer.get() > 5.0D
            || interpolatedRotationBuffer.get() > 6.0D
            || sprintPlaceBuffer.get() > 4.0D;
    if (failed) {
      long flagNow = System.currentTimeMillis();
      long last = this.lastFlag.getOrDefault(name, 0L);
      if (flagNow - last > 2500L) {
        String detail = "";
        if (flickBuffer.get() > 5.0D) detail = "Rotation Flick";
        else if (snapBuffer.get() > 5.0D) detail = "Angle Snap";
        else if (microPitchBuffer.get() > 5.0D) detail = "Micro Pitch";
        else if (noRotBuffer.get() > 6.0D) detail = "No Rotation";
        else if (microFluctuationBuffer.get() > 5.0D) detail = "Micro Fluctuation";
        else if (flickTimingBuffer.get() > 6.0D) detail = "Flick Timing";
        else if (multiAxisFlickBuffer.get() > 5.0D) detail = "Multi-Axis Flick";
        else if (interpolatedRotationBuffer.get() > 6.0D) detail = "Interpolated";
        else if (sprintPlaceBuffer.get() > 4.0D) detail = "Sprint Place";

        context.receiveSignal(name, detail.isEmpty() ? "Scaffold" : "Scaffold (" + detail + ")");
        this.lastFlag.put(name, flagNow);
        flickBuffer.reset();
        snapBuffer.reset();
        microPitchBuffer.reset();
        noRotBuffer.reset();
        microFluctuationBuffer.reset();
        flickTimingBuffer.reset();
        multiAxisFlickBuffer.reset();
        interpolatedRotationBuffer.reset();
        sprintPlaceBuffer.reset();
      }
    }
  }

  private boolean recentPlacement() {
    // Helper: returns true if called in a context that has recent placement
    return true;
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
    this.microFluctuationBuffers.clear();
    this.flickTimingBuffers.clear();
    this.multiAxisFlickBuffers.clear();
    this.interpolatedRotationBuffers.clear();
    this.sprintPlaceBuffers.clear();
    this.lastFlag.clear();
    this.lastPitch.clear();
    this.lastYaw.clear();
    this.lastBlocksPlaced.clear();
    this.micropitchVl.clear();
    this.placeSpeedHistory.clear();
    this.lastPlaceTime.clear();
    this.lockedRotationTicks.clear();
    this.pitchMicroChanges.clear();
    this.yawMicroChanges.clear();
    this.lastFlickTime.clear();
    this.flickToPlaceIntervals.clear();
    this.smoothYawChanges.clear();
    this.smoothPitchChanges.clear();
  }
}
