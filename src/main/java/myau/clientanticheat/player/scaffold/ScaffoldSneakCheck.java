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

public class ScaffoldSneakCheck {
  private final Map<String, CheckBuffer> supportBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> rotationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> edgeBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sneakTimingBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> roundedRotationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> towerBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> oscillationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> rainSneakConsistencyBuffers = new HashMap<>();

  private final Map<String, Long> lastFlag = new HashMap<>();

  private final Map<String, Long> lastSneakStartTick = new HashMap<>();
  private final Map<String, Long> lastPlaceTick = new HashMap<>();
  private final Map<String, Long> lastSneakDuration = new HashMap<>();
  private final Map<String, Long> sneakDuration = new HashMap<>();
  private final Map<String, Boolean> isSneaking = new HashMap<>();
  private final Map<String, Boolean> sneakChangedThisTick = new HashMap<>();
  private final Map<String, Boolean> placedThisTick = new HashMap<>();

  // Rain-style sneak consistency tracking
  private final Map<String, LinkedList<Integer>> crouchDurations = new HashMap<>();
  private final Map<String, LinkedList<Long>> sneakSwingOffsets = new HashMap<>();

  private final Map<String, LinkedList<Long>> placementSpeedHistory = new HashMap<>();
  private final Map<String, LinkedList<BlockPos>> placementBlockHistory = new HashMap<>();
  private final Map<String, Long> lastPlacementTime = new HashMap<>();

  private final Map<String, int[]> zerosBuilding = new HashMap<>();
  private final Map<String, int[]> zerosNotBuilding = new HashMap<>();
  private final Map<String, Integer> buildingIndex = new HashMap<>();

  private static final int PLACEMENT_SPEED_HISTORY = 10;
  private static final int ROUNDED_ROTATION_HISTORY = 60;
  private static final int CROUCH_HISTORY_SIZE = 6;
  private static final int OSCILLATION_WINDOW = 8;

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer supportBuffer = this.supportBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer rotationBuffer =
        this.rotationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer edgeBuffer = this.edgeBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sneakTimingBuffer =
        this.sneakTimingBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer speedBuffer = this.speedBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer roundedRotationBuffer =
        this.roundedRotationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer towerBuffer = this.towerBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer oscillationBuffer =
        this.oscillationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer rainSneakConsistencyBuffer =
        this.rainSneakConsistencyBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      supportBuffer.decay(0.5D);
      rotationBuffer.decay(0.5D);
      edgeBuffer.decay(0.5D);
      sneakTimingBuffer.decay(0.3D);
      speedBuffer.decay(0.3D);
      roundedRotationBuffer.decay(0.2D);
      towerBuffer.decay(0.5D);
      oscillationBuffer.decay(0.3D);
      rainSneakConsistencyBuffer.decay(0.3D);
      data.scaffoldBridge = false;
      return;
    }

    boolean moving = data.horizontalDelta > 0.15D;
    boolean hasBlockBelow = this.hasSolidBelow(player, world, 1.0D);
    boolean hasRecentSupport = this.hasSolidBelow(player, world, 1.35D);
    boolean nearEdge =
        player.onGround && !this.hasSolidBelowOffset(player, world, data.deltaX, data.deltaZ);
    boolean bridgeContext =
        moving && (nearEdge || !hasBlockBelow || hasRecentSupport && data.pitch > 55.0F);

    data.scaffoldBridge = bridgeContext;

    // ── Support check ──
    if (bridgeContext && data.horizontalDelta > 0.22D && hasRecentSupport) {
      supportBuffer.flag(1.0D, 999.0D);
    } else {
      supportBuffer.decay(0.25D);
    }

    // ── Rotation check ──
    if (bridgeContext
        && (data.yawAcceleration > 60.0F
            || data.pitchAcceleration > 25.0F
            || data.yawDelta > 140.0F)) {
      rotationBuffer.flag(1.25D, 999.0D);
    } else {
      rotationBuffer.decay(0.3D);
    }

    // ── Edge check ──
    if (nearEdge && moving && !player.isSneaking() && data.groundTicks > 3) {
      edgeBuffer.flag(1.0D, 999.0D);
    } else {
      edgeBuffer.decay(0.25D);
    }

    // ── Sneak tracking (Rain-style + enhanced) ──
    boolean sneaking = player.isSneaking();
    boolean sneakChanged = sneaking != this.isSneaking.getOrDefault(name, false);
    this.isSneaking.put(name, sneaking);
    this.sneakChangedThisTick.put(name, sneakChanged);

    if (sneakChanged && sneaking) {
      this.lastSneakStartTick.put(name, (long) data.existedTicks);
    }

    if (sneakChanged && !sneaking) {
      long dur = this.sneakDuration.getOrDefault(name, 10L);
      this.lastSneakDuration.put(name, dur);
      this.sneakDuration.put(name, 0L);

      // Rain-style: record crouch duration in history
      LinkedList<Integer> durations =
          this.crouchDurations.computeIfAbsent(name, k -> new LinkedList<>());
      durations.addFirst((int) dur);
      if (durations.size() > CROUCH_HISTORY_SIZE) durations.removeLast();
    } else if (sneaking) {
      this.sneakDuration.put(name, this.sneakDuration.getOrDefault(name, 0L) + 1);
    }

    boolean placed = data.startedSwinging();
    this.placedThisTick.put(name, placed);

    // ── Rain-style sneak + swing timing consistency ──
    // Detect: crouch for 1-2 ticks, swing on the same tick as unsneak, repeat consistently
    if (placed || sneakChanged) {
      if (placed) {
        long diff =
            this.sneakChangedThisTick.getOrDefault(name, false) && sneaking
                ? 0L
                : (long) data.existedTicks - this.lastSneakStartTick.getOrDefault(name, 0L);
        long lastDuration = this.lastSneakDuration.getOrDefault(name, 10L);
        boolean suspicious = diff <= 1 && lastDuration < 2;
        if (suspicious) {
          sneakTimingBuffer.flag(diff > 1 ? 1.5D : 2.5D, 999.0D);
        }

        // Record swing offset from sneak end for consistency check
        LinkedList<Long> swingOffsets =
            this.sneakSwingOffsets.computeIfAbsent(name, k -> new LinkedList<>());
        if (lastDuration > 0 && lastDuration < 5) {
          swingOffsets.addFirst(
              (long) data.existedTicks - this.lastSneakStartTick.getOrDefault(name, 0L));
          if (swingOffsets.size() > 5) swingOffsets.removeLast();
        }
      }

      if (sneakChanged) {
        long diff =
            placed ? 0L : (long) data.existedTicks - this.lastPlaceTick.getOrDefault(name, 0L);
        long lastDuration = this.lastSneakDuration.getOrDefault(name, 10L);
        boolean suspicious = diff <= 1 && lastDuration < 2;
        if (suspicious) {
          sneakTimingBuffer.flag(diff > 1 ? 1.5D : 2.5D, 999.0D);
        }
      }
    }

    // ── Rain-style: consistent crouch 1-2 ticks across 3+ samples ──
    {
      LinkedList<Integer> durs = this.crouchDurations.get(name);
      if (durs != null && durs.size() >= 3) {
        boolean allShort = true;
        for (int d : durs) {
          if (d > 2) {
            allShort = false;
            break;
          }
        }
        if (allShort && bridgeContext) {
          rainSneakConsistencyBuffer.flag(2.0D, 999.0D);
        } else {
          rainSneakConsistencyBuffer.decay(0.2D);
        }
      }
    }

    // ── Swing offset consistency ──
    {
      LinkedList<Long> offsets = this.sneakSwingOffsets.get(name);
      if (offsets != null && offsets.size() >= 3) {
        double mad = StatisticalUtils.medianAbsoluteDeviation(offsets);
        if (mad < 0.5 && bridgeContext) {
          // All swings at exactly same offset from sneak → suspicious
          sneakTimingBuffer.flag(1.0D, 999.0D);
        }
      }
    }

    if (placed) {
      this.lastPlaceTick.put(name, (long) data.existedTicks);
    }

    // ── Placement speed ──
    if (placed && bridgeContext) {
      long now = System.currentTimeMillis();
      long lastPlace = this.lastPlacementTime.getOrDefault(name, 0L);
      long interval = lastPlace > 0 ? now - lastPlace : 1000L;
      this.lastPlacementTime.put(name, now);

      data.placementTimestamps.addFirst(now);
      if (data.placementTimestamps.size() > 20) data.placementTimestamps.removeLast();

      data.placementIntervals.addFirst(interval);
      if (data.placementIntervals.size() > 20) data.placementIntervals.removeLast();

      LinkedList<Long> speedHistory =
          this.placementSpeedHistory.computeIfAbsent(name, k -> new LinkedList<>());
      speedHistory.addFirst(interval);
      if (speedHistory.size() > PLACEMENT_SPEED_HISTORY) speedHistory.removeLast();

      if (speedHistory.size() >= PLACEMENT_SPEED_HISTORY) {
        double avg = 0;
        for (long v : speedHistory) avg += v;
        avg /= speedHistory.size();
        double min = Long.MAX_VALUE;
        for (long v : speedHistory) if (v < min) min = v;

        boolean inOneLine = isOneLine(this.placementBlockHistory.get(name));
        boolean noSneaking = System.currentTimeMillis() - data.lastSwingTimestamp > 6000;
        float yawToNextNinety = Math.abs(player.rotationYaw) % 90;
        boolean ninetyDegreeAngle = yawToNextNinety < 10 || yawToNextNinety > 80;

        // Use weighted average (more recent = higher weight)
        double weightedAvg = StatisticalUtils.weightedMean(speedHistory);

        double minAvg;
        if (inOneLine) {
          minAvg = noSneaking ? 500 : 350;
          if (ninetyDegreeAngle) minAvg += 150;
        } else {
          minAvg = ninetyDegreeAngle || noSneaking ? 300 : 150;
        }

        // Boost: if BOTH avg and min are low, more suspicious
        double score = 1.5D;
        if (min < minAvg * 0.6 && avg < minAvg) score += 0.5D;

        if (avg < minAvg || weightedAvg < minAvg * 0.85) {
          speedBuffer.flag(score, 999.0D);
        } else {
          speedBuffer.decay(0.15D);
        }
      }

      LinkedList<BlockPos> blockHistory =
          this.placementBlockHistory.computeIfAbsent(name, k -> new LinkedList<>());
      blockHistory.addFirst(
          new BlockPos(
              MathHelper.floor_double(player.posX),
              MathHelper.floor_double(player.posY - 1),
              MathHelper.floor_double(player.posZ)));
      if (blockHistory.size() > PLACEMENT_SPEED_HISTORY) blockHistory.removeLast();
      data.blockPositionHistory.addFirst(blockHistory.getFirst());
      if (data.blockPositionHistory.size() > 20) data.blockPositionHistory.removeLast();
    }

    // ── Tower detection: rapid placement while not moving vertically/horizontally ──
    if (holdingBlock
        && placed
        && !moving
        && Math.abs(data.deltaY) < 0.1
        && player.onGround
        && data.pitch > 70.0F) {
      towerBuffer.flag(1.0D, 999.0D);
    } else {
      towerBuffer.decay(0.15D);
    }

    // ── Placement oscillation detection ──
    // Cheat clients often oscillate between fast and slow placements
    {
      LinkedList<Long> intervals = this.placementSpeedHistory.get(name);
      if (intervals != null && intervals.size() >= OSCILLATION_WINDOW) {
        double variance = StatisticalUtils.slidingWindowVariance(intervals, OSCILLATION_WINDOW);
        double autoCorr = StatisticalUtils.autoCorrelation(intervals, 1);
        boolean hasRepetitive =
            StatisticalUtils.hasRepetitivePattern(
                new LinkedList<Double>() {
                  {
                    for (Long v : intervals) add((double) v);
                  }
                },
                15.0);

        // Low variance + alternating pattern → likely oscillation cheat
        if (variance < 100 && autoCorr < -0.3 && hasRepetitive) {
          oscillationBuffer.flag(1.5D, 999.0D);
        } else {
          oscillationBuffer.decay(0.15D);
        }
      }
    }

    // ── Rounded rotation analysis ──
    float pitch = data.pitch;
    int firstZero = firstZeroInDecimal(pitch);
    boolean recentlyPlaced =
        this.lastPlacementTime.getOrDefault(name, 0L) > 0
            && System.currentTimeMillis() - this.lastPlacementTime.get(name) < 1000;

    int[] destination =
        recentlyPlaced
            ? this.zerosBuilding.computeIfAbsent(name, k -> new int[ROUNDED_ROTATION_HISTORY])
            : this.zerosNotBuilding.computeIfAbsent(name, k -> new int[ROUNDED_ROTATION_HISTORY]);
    int idx =
        recentlyPlaced
            ? this.buildingIndex.merge(name, 1, Integer::sum) - 1
            : this.buildingIndex.merge(name + "_nb", 1, Integer::sum) - 1;
    destination[idx % destination.length] = firstZero;

    int[] buildArr = this.zerosBuilding.get(name);
    int[] notBuildArr = this.zerosNotBuilding.get(name);
    if (buildArr != null
        && notBuildArr != null
        && buildArr.length >= ROUNDED_ROTATION_HISTORY
        && notBuildArr.length >= ROUNDED_ROTATION_HISTORY) {
      int avgBuild = averageOf(buildArr);
      int avgNotBuild = averageOf(notBuildArr);
      if (avgNotBuild <= 3 && avgBuild >= 6) {
        roundedRotationBuffer.flag(1.0D, 999.0D);
      } else {
        roundedRotationBuffer.decay(0.1D);
      }
    }

    // ── Combined flag ──
    boolean failed =
        supportBuffer.get() > 6.0D && rotationBuffer.get() > 3.0D
            || edgeBuffer.get() > 14.0D
            || sneakTimingBuffer.get() > 7.0D
            || speedBuffer.get() > 5.0D && edgeBuffer.get() > 5.0D
            || roundedRotationBuffer.get() > 8.0D
            || towerBuffer.get() > 8.0D
            || rainSneakConsistencyBuffer.get() > 6.0D
            || oscillationBuffer.get() > 7.0D;
    if (failed) {
      long now = System.currentTimeMillis();
      long last = this.lastFlag.getOrDefault(name, 0L);
      if (now - last > 2500L) {
        String detail = "";
        if (supportBuffer.get() > 6.0D && rotationBuffer.get() > 3.0D) detail = "Support";
        else if (edgeBuffer.get() > 14.0D) detail = "Edge";
        else if (sneakTimingBuffer.get() > 7.0D) detail = "SneakTiming";
        else if (speedBuffer.get() > 5.0D) detail = "Speed";
        else if (roundedRotationBuffer.get() > 8.0D) detail = "Rounded";
        else if (towerBuffer.get() > 8.0D) detail = "Tower";
        else if (rainSneakConsistencyBuffer.get() > 6.0D) detail = "SneakConsist";
        else if (oscillationBuffer.get() > 7.0D) detail = "Oscillation";

        context.receiveSignal(name, detail.isEmpty() ? "Scaffold" : "Scaffold (" + detail + ")");
        this.lastFlag.put(name, now);
        supportBuffer.reset();
        rotationBuffer.reset();
        edgeBuffer.reset();
        sneakTimingBuffer.reset();
        speedBuffer.reset();
        roundedRotationBuffer.reset();
        towerBuffer.reset();
        oscillationBuffer.reset();
        rainSneakConsistencyBuffer.reset();
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

  private int firstZeroInDecimal(float value) {
    String s = String.valueOf((int) ((value % 1) * 100000 + 0.5));
    int idx = s.indexOf('0');
    return idx == -1 ? 8 : idx;
  }

  private int averageOf(int[] arr) {
    int sum = 0;
    for (int v : arr) sum += v;
    return sum / arr.length;
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
    this.supportBuffers.clear();
    this.rotationBuffers.clear();
    this.edgeBuffers.clear();
    this.sneakTimingBuffers.clear();
    this.speedBuffers.clear();
    this.roundedRotationBuffers.clear();
    this.towerBuffers.clear();
    this.oscillationBuffers.clear();
    this.rainSneakConsistencyBuffers.clear();
    this.lastFlag.clear();
    this.lastSneakStartTick.clear();
    this.lastPlaceTick.clear();
    this.lastSneakDuration.clear();
    this.sneakDuration.clear();
    this.isSneaking.clear();
    this.sneakChangedThisTick.clear();
    this.placedThisTick.clear();
    this.crouchDurations.clear();
    this.sneakSwingOffsets.clear();
    this.placementSpeedHistory.clear();
    this.placementBlockHistory.clear();
    this.lastPlacementTime.clear();
    this.zerosBuilding.clear();
    this.zerosNotBuilding.clear();
    this.buildingIndex.clear();
  }
}
