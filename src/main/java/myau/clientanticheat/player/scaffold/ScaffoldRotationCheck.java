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

public class ScaffoldRotationCheck {
  private final Map<String, CheckBuffer> stabilityBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sharpRotationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> backSnapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> constantYawBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> entropyBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> gcdBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> perfectSnapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> cardinalBiasBuffers = new HashMap<>();

  private final Map<String, float[]> yawHistory = new HashMap<>();
  private final Map<String, float[]> pitchHistory = new HashMap<>();
  private final Map<String, Integer> sharpRotationCounts = new HashMap<>();
  private final Map<String, Long> sharpRotationResets = new HashMap<>();
  private final Map<String, Long> lastBlockPlacement = new HashMap<>();

  // GCD tracking
  private final Map<String, LinkedList<Float>> yawGcdHistory = new HashMap<>();
  private final Map<String, LinkedList<Float>> pitchGcdHistory = new HashMap<>();

  // Perfect snap tracking
  private final Map<String, Integer> consecutivePerfectSnaps = new HashMap<>();
  private final Map<String, Float> lastPerfectYaw = new HashMap<>();
  private final Map<String, Float> lastPerfectPitch = new HashMap<>();

  // Cardinal bias tracking
  private final Map<String, LinkedList<Float>> yawHistogram = new HashMap<>();
  private final Map<String, LinkedList<Float>> pitchHistogram = new HashMap<>();

  // Entropy tracking
  private final Map<String, LinkedList<Float>> yawEntropySamples = new HashMap<>();
  private final Map<String, LinkedList<Float>> pitchEntropySamples = new HashMap<>();

  private final Map<String, Long> lastFlag = new HashMap<>();

  private static final int HISTORY_LENGTH = 6;
  private static final int GCD_HISTORY_SIZE = 20;
  private static final int PERFECT_SNAP_COOLDOWN = 5;
  private static final int CARDINAL_HISTORY_SIZE = 30;
  private static final int ENTROPY_WINDOW = 15;

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer stabilityBuffer =
        this.stabilityBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer speedBuffer = this.speedBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sharpRotationBuffer =
        this.sharpRotationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer backSnapBuffer =
        this.backSnapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer constantYawBuffer =
        this.constantYawBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer entropyBuffer = this.entropyBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer gcdBuffer = this.gcdBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer perfectSnapBuffer =
        this.perfectSnapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer cardinalBiasBuffer =
        this.cardinalBiasBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      stabilityBuffer.decay(0.5D);
      speedBuffer.decay(0.5D);
      sharpRotationBuffer.decay(0.2D);
      backSnapBuffer.decay(0.2D);
      constantYawBuffer.decay(0.3D);
      entropyBuffer.decay(0.2D);
      gcdBuffer.decay(0.15D);
      perfectSnapBuffer.decay(0.2D);
      cardinalBiasBuffer.decay(0.15D);
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
    float sensitivityMult = sneakBridging ? 1.5F : 1.0F;

    float deltaYaw = data.yawDelta;
    float deltaPitch = data.pitchDelta;

    // ── Stability check ──
    if (bridgeContext) {
      if (deltaPitch > 0.0F && deltaPitch < 0.005F && deltaYaw > 20.0F * sensitivityMult) {
        stabilityBuffer.flag(1.0D, 4.0D);
      } else {
        stabilityBuffer.decay(0.15D);
      }

      if (deltaYaw > 150.0F && deltaPitch > 60.0F) {
        speedBuffer.flag(1.25D, 5.0D);
      } else {
        speedBuffer.decay(0.2D);
      }
    } else {
      stabilityBuffer.decay(0.4D);
      speedBuffer.decay(0.4D);
    }

    // ── Rotation history ──
    float[] yawHist = this.yawHistory.computeIfAbsent(name, k -> new float[HISTORY_LENGTH]);
    float[] pitchHist = this.pitchHistory.computeIfAbsent(name, k -> new float[HISTORY_LENGTH]);
    for (int i = 0; i < HISTORY_LENGTH - 1; i++) {
      yawHist[i] = yawHist[i + 1];
      pitchHist[i] = pitchHist[i + 1];
    }
    yawHist[HISTORY_LENGTH - 1] = player.rotationYaw;
    pitchHist[HISTORY_LENGTH - 1] = player.rotationPitch;

    // ── Sharp rotation check ──
    boolean recentPlacement =
        System.currentTimeMillis() - this.lastBlockPlacement.getOrDefault(name, 0L) < 2000;
    float rotationMovement = Math.abs(yawMotion(1, yawHist));
    boolean hit = Math.abs(rotationMovement - 180.0F) < 10.0F;
    if (hit && recentPlacement) {
      int count = this.sharpRotationCounts.getOrDefault(name, 0) + 1;
      this.sharpRotationCounts.put(name, count);

      long resetTime = this.sharpRotationResets.getOrDefault(name, System.currentTimeMillis());
      if (System.currentTimeMillis() - resetTime > 10000) {
        this.sharpRotationCounts.put(name, Math.max(0, count - 1) / 2);
        this.sharpRotationResets.put(name, System.currentTimeMillis());
      }

      if (count > 4) {
        sharpRotationBuffer.flag(2.0D, 999.0D);
      }
    }

    // ── Back-snap detection (existing) ──
    boolean alphaCondition = pitchAt(1, pitchHist) > 70;
    int pitchLimit = alphaCondition ? 20 : 40;

    if (yawMotion(0, yawHist) < 15
        && pitchMotion(0, pitchHist) < 15
        && yawMotion(1, yawHist) > 70
        && pitchMotion(1, pitchHist) > pitchLimit
        && yawMotion(2, yawHist) < 10
        && pitchMotion(2, pitchHist) < 10) {
      backSnapBuffer.flag(2.0D, 999.0D);
    }

    if (yawMotion(0, yawHist) < 8
        && pitchMotion(0, pitchHist) < 8
        && (yawMotion(1, yawHist) > 10 || pitchMotion(1, pitchHist) > 10)
        && (yawMotion(2, yawHist) > 10 || pitchMotion(2, pitchHist) > 10)
        && Math.abs(yawAt(1, yawHist) - yawAt(3, yawHist)) > 70
        && Math.abs(pitchAt(1, pitchHist) - pitchAt(3, pitchHist)) > pitchLimit
        && yawMotion(3, yawHist) < 8
        && pitchMotion(3, pitchHist) < 8) {
      backSnapBuffer.flag(1.5D, 999.0D);
    }

    if (yawMotion(1, yawHist) > 30
        && pitchMotion(1, pitchHist) > 30
        && Math.abs(yawMotion(1, yawHist) - yawMotion(2, yawHist)) < 5
        && Math.abs(pitchMotion(1, pitchHist) - pitchMotion(2, pitchHist)) < 5
        && Math.abs(yawDiff(yawAt(1, yawHist), yawAt(3, yawHist))) < 3
        && Math.abs(pitchDiff(pitchAt(1, pitchHist), pitchAt(3, pitchHist))) < 3) {
      backSnapBuffer.flag(2.5D, 999.0D);
    }

    // ── Constant yaw check (existing) ──
    if (bridgeContext && recentPlacement) {
      float yawToCardinal = Math.abs(player.rotationYaw % 90.0F);
      boolean perfectCardinal = yawToCardinal < 0.5F || yawToCardinal > 89.5F;
      boolean constantRotation = deltaYaw < 0.02F && deltaPitch < 0.02F;

      if (constantRotation && perfectCardinal && data.horizontalDelta > 0.15D) {
        constantYawBuffer.flag(0.8D, 999.0D);
      } else {
        constantYawBuffer.decay(0.15D);
      }
    } else {
      constantYawBuffer.decay(0.2D);
    }

    // ── GCD Analysis (NEW) ──
    // Clean GCD = low-entropy rotation like aimbot
    if (bridgeContext && (deltaYaw > 0.05F || deltaPitch > 0.05F)) {
      LinkedList<Float> yawGcds = this.yawGcdHistory.computeIfAbsent(name, k -> new LinkedList<>());
      LinkedList<Float> pitchGcds =
          this.pitchGcdHistory.computeIfAbsent(name, k -> new LinkedList<>());

      if (deltaYaw > 0.05F && deltaPitch > 0.05F) {
        float gcd = (float) StatisticalUtils.gcd(deltaYaw, deltaPitch);
        yawGcds.addFirst(gcd);
        if (yawGcds.size() > GCD_HISTORY_SIZE) yawGcds.removeLast();
      }

      if (yawGcds.size() >= 10) {
        double mad = StatisticalUtils.medianAbsoluteDeviation(yawGcds);
        // Very low MAD = suspiciously consistent GCD = aimbot-like rotation
        if (mad < 0.01 && bridgeContext) {
          gcdBuffer.flag(1.0D, 999.0D);
        } else {
          gcdBuffer.decay(0.15D);
        }
      }
    } else {
      gcdBuffer.decay(0.1D);
    }

    // ── Entropy Analysis (NEW) ──
    // Human rotation has high entropy; cheat rotation has low entropy
    if (bridgeContext && (deltaYaw > 0.01F || deltaPitch > 0.01F)) {
      LinkedList<Float> yawSamples =
          this.yawEntropySamples.computeIfAbsent(name, k -> new LinkedList<>());
      LinkedList<Float> pitchSamples =
          this.pitchEntropySamples.computeIfAbsent(name, k -> new LinkedList<>());

      yawSamples.addFirst(deltaYaw);
      pitchSamples.addFirst(deltaPitch);
      if (yawSamples.size() > ENTROPY_WINDOW) yawSamples.removeLast();
      if (pitchSamples.size() > ENTROPY_WINDOW) pitchSamples.removeLast();

      if (yawSamples.size() >= ENTROPY_WINDOW && recentPlacement) {
        double yawEntropy =
            StatisticalUtils.entropy(
                new LinkedList<Long>() {
                  {
                    for (Float f : yawSamples) add(f.longValue());
                  }
                });
        double pitchEntropy =
            StatisticalUtils.entropy(
                new LinkedList<Long>() {
                  {
                    for (Float f : pitchSamples) add(f.longValue());
                  }
                });

        // Very low entropy on both axes = rotation pattern too uniform
        if (yawEntropy < 0.5 && pitchEntropy < 0.3 && bridgeContext) {
          entropyBuffer.flag(1.5D, 999.0D);
        } else {
          entropyBuffer.decay(0.15D);
        }
      }
    } else {
      entropyBuffer.decay(0.1D);
    }

    // ── Perfect Snap Detection (NEW) ──
    // Consecutive perfect 180° snaps = scaffold module behavior
    if (bridgeContext && recentPlacement) {
      float yawMotion1 = yawMotion(1, yawHist);
      float pitchMotion1 = pitchMotion(1, pitchHist);

      boolean isPerfectSnap =
          Math.abs(yawMotion1 - 180.0F) < 0.5F || Math.abs(yawMotion1 - 90.0F) < 0.5F;

      if (isPerfectSnap) {
        int count = this.consecutivePerfectSnaps.getOrDefault(name, 0) + 1;
        this.consecutivePerfectSnaps.put(name, count);

        if (count >= 3) {
          perfectSnapBuffer.flag(1.5D, 999.0D);
        }
      } else {
        this.consecutivePerfectSnaps.put(name, 0);
      }
    } else {
      int current = this.consecutivePerfectSnaps.getOrDefault(name, 0);
      if (current > 0) {
        this.consecutivePerfectSnaps.put(name, Math.max(0, current - 1));
      }
    }

    // ── Cardinal Direction Bias (NEW) ──
    // Cheat clients often snap to cardinal angles (0, 90, 180, 270)
    if (bridgeContext && deltaYaw > 0.5F) {
      float normalizedYaw = player.rotationYaw % 360;
      if (normalizedYaw < 0) normalizedYaw += 360;

      LinkedList<Float> yawSamples =
          this.yawHistogram.computeIfAbsent(name, k -> new LinkedList<>());
      yawSamples.addFirst(normalizedYaw);
      if (yawSamples.size() > CARDINAL_HISTORY_SIZE) yawSamples.removeLast();

      if (yawSamples.size() >= 10) {
        int cardinalHits = 0;
        for (float y : yawSamples) {
          float distToCardinal = Math.abs(y % 90);
          if (distToCardinal < 3 || distToCardinal > 87) {
            cardinalHits++;
          }
        }
        double ratio = (double) cardinalHits / yawSamples.size();
        if (ratio > 0.7 && bridgeContext) {
          cardinalBiasBuffer.flag(1.0D, 999.0D);
        } else {
          cardinalBiasBuffer.decay(0.15D);
        }
      }
    } else {
      cardinalBiasBuffer.decay(0.12D);
    }

    // ── Individual flagging (backward compatible) ──
    if (stabilityBuffer.get() > 3.0D) {
      context.receiveSignal(name, "Scaffold (Rotation Stability)");
      stabilityBuffer.reset();
    }
    if (speedBuffer.get() > 4.0D) {
      context.receiveSignal(name, "Scaffold (Rotation Speed)");
      speedBuffer.reset();
    }
    if (sharpRotationBuffer.get() > 3.0D) {
      context.receiveSignal(name, "Scaffold (Sharp Rotation)");
      sharpRotationBuffer.reset();
    }
    if (backSnapBuffer.get() > 4.0D) {
      context.receiveSignal(name, "Scaffold (Back Snap)");
      backSnapBuffer.reset();
    }
    if (constantYawBuffer.get() > 7.0D) {
      context.receiveSignal(name, "Scaffold (Constant Yaw)");
      constantYawBuffer.reset();
    }

    // ── New combined flags ──
    if (entropyBuffer.get() > 5.0D) {
      context.receiveSignal(name, "Scaffold (Low Entropy)");
      entropyBuffer.reset();
    }
    if (gcdBuffer.get() > 5.0D) {
      context.receiveSignal(name, "Scaffold (Clean GCD)");
      gcdBuffer.reset();
    }
    if (perfectSnapBuffer.get() > 4.0D) {
      context.receiveSignal(name, "Scaffold (Perfect Snap)");
      perfectSnapBuffer.reset();
    }
    if (cardinalBiasBuffer.get() > 7.0D) {
      context.receiveSignal(name, "Scaffold (Cardinal Bias)");
      cardinalBiasBuffer.reset();
    }
  }

  private float yawAt(int age, float[] history) {
    int idx = HISTORY_LENGTH - age - 1;
    return idx < 0 || idx >= history.length ? 0 : history[idx];
  }

  private float pitchAt(int age, float[] history) {
    int idx = HISTORY_LENGTH - age - 1;
    return idx < 0 || idx >= history.length ? 0 : history[idx];
  }

  private float yawMotion(int age, float[] yawHist) {
    int idx = HISTORY_LENGTH - age - 1;
    if (idx < 1 || idx >= yawHist.length) return 0;
    return Math.abs(yawDiff(yawHist[idx], yawHist[idx - 1]));
  }

  private float pitchMotion(int age, float[] pitchHist) {
    int idx = HISTORY_LENGTH - age - 1;
    if (idx < 1 || idx >= pitchHist.length) return 0;
    return Math.abs(pitchDiff(pitchHist[idx], pitchHist[idx - 1]));
  }

  private static float yawDiff(float a, float b) {
    float phi = Math.abs(b - a) % 360;
    return phi > 180 ? 360 - phi : phi;
  }

  private static float pitchDiff(float a, float b) {
    return Math.abs(a - b);
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data.recentlyTeleported();
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
    this.stabilityBuffers.clear();
    this.speedBuffers.clear();
    this.sharpRotationBuffers.clear();
    this.yawHistory.clear();
    this.pitchHistory.clear();
    this.sharpRotationCounts.clear();
    this.sharpRotationResets.clear();
    this.lastBlockPlacement.clear();
    this.backSnapBuffers.clear();
    this.constantYawBuffers.clear();
    this.entropyBuffers.clear();
    this.gcdBuffers.clear();
    this.perfectSnapBuffers.clear();
    this.cardinalBiasBuffers.clear();
    this.yawGcdHistory.clear();
    this.pitchGcdHistory.clear();
    this.consecutivePerfectSnaps.clear();
    this.lastPerfectYaw.clear();
    this.lastPerfectPitch.clear();
    this.yawHistogram.clear();
    this.pitchHistogram.clear();
    this.yawEntropySamples.clear();
    this.pitchEntropySamples.clear();
    this.lastFlag.clear();
  }
}
