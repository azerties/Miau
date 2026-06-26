package myau.clientanticheat.player.scaffold;

import java.util.HashMap;
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

public class ScaffoldRotationCheck {
  private final Map<String, CheckBuffer> stabilityBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sharpRotationBuffers = new HashMap<>();
  private final Map<String, float[]> yawHistory = new HashMap<>();
  private final Map<String, float[]> pitchHistory = new HashMap<>();
  private final Map<String, Integer> sharpRotationCounts = new HashMap<>();
  private final Map<String, Long> sharpRotationResets = new HashMap<>();
  private final Map<String, Long> lastBlockPlacement = new HashMap<>();
  private final Map<String, CheckBuffer> backSnapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> constantYawBuffers = new HashMap<>();

  private static final int HISTORY_LENGTH = 6;

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

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      stabilityBuffer.decay(0.5D);
      speedBuffer.decay(0.5D);
      sharpRotationBuffer.decay(0.2D);
      backSnapBuffer.decay(0.2D);
      constantYawBuffer.decay(0.3D);
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

    if (bridgeContext) {
      if (deltaPitch > 0.0F && deltaPitch < 0.01F && deltaYaw > 20.0F * sensitivityMult) {
        stabilityBuffer.flag(1.0D, 4.0D);
      } else {
        stabilityBuffer.decay(0.15D);
      }

      if (deltaYaw > 120.0F && deltaPitch > 45.0F) {
        speedBuffer.flag(1.25D, 5.0D);
      } else {
        speedBuffer.decay(0.2D);
      }
    } else {
      stabilityBuffer.decay(0.4D);
      speedBuffer.decay(0.4D);
    }

    float[] yawHist = this.yawHistory.computeIfAbsent(name, k -> new float[HISTORY_LENGTH]);
    float[] pitchHist = this.pitchHistory.computeIfAbsent(name, k -> new float[HISTORY_LENGTH]);
    for (int i = 0; i < HISTORY_LENGTH - 1; i++) {
      yawHist[i] = yawHist[i + 1];
      pitchHist[i] = pitchHist[i + 1];
    }
    yawHist[HISTORY_LENGTH - 1] = player.rotationYaw;
    pitchHist[HISTORY_LENGTH - 1] = player.rotationPitch;

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
    if (backSnapBuffer.get() > 3.5D) {
      context.receiveSignal(name, "Scaffold (Back Snap)");
      backSnapBuffer.reset();
    }
    if (constantYawBuffer.get() > 6.0D) {
      context.receiveSignal(name, "Scaffold (Constant Yaw)");
      constantYawBuffer.reset();
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
  }
}
