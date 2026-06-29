package myau.clientanticheat;

import java.util.LinkedList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

public class PlayerCheckData {
  private static final double TELEPORT_DISTANCE_THRESHOLD = 8.0D;
  private static final int TELEPORT_EXEMPT_TICKS = 6;
  private static final int MAX_SINCE_HURT_TICKS = 999;
  private static final int RECENT_HURT_TICKS = 12;
  private static final int INITIAL_EXEMPT_TICKS = 5;

  public final String name;

  public double lastX;
  public double lastY;
  public double lastZ;
  public double x;
  public double y;
  public double z;
  public double deltaX;
  public double deltaY;
  public double deltaZ;
  public double horizontalDelta;
  public double lastHorizontalDelta;
  public double totalDelta;

  public float yaw;
  public float pitch;
  public float lastYaw;
  public float lastPitch;
  public float yawDelta;
  public float pitchDelta;
  public float lastYawDelta;
  public float lastPitchDelta;
  public float yawAcceleration;
  public float pitchAcceleration;

  public boolean onGround;
  public boolean lastOnGround;
  public boolean lastUsingItem;
  public boolean usingItem;
  public boolean lastSwinging;
  public boolean swinging;
  public boolean breakingBlock;
  public boolean collidedHorizontally;
  public int groundTicks;
  public int airTicks;
  public int hurtTicks;
  public int sinceHurtTicks = 999;
  public int teleportTicks;
  public int existedTicks;
  public int stillTicks;
  public int burstTicks;
  public int usingItemTicks;
  public int swingTicks;
  public int sinceBreakTicks = 999;
  public float observedFallDistance;
  public final LinkedList<AxisAlignedBB> history = new LinkedList<>();

  public long lastSwingTimestamp;
  public long lastAttackTick;
  public int swingCountWindow;
  public final LinkedList<Long> swingTimestamps = new LinkedList<>();
  public final LinkedList<Long> movementTimestamps = new LinkedList<>();
  public boolean lastBlocking;
  public boolean blocking;
  public int blockingTicks;
  public long lastBlockToggleTick;
  public int sprintToggleCount;
  public int sneakToggleCount;
  public boolean lastSprinting;
  public boolean sprinting;
  public boolean lastSneaking;
  public boolean sneaking;
  public int heldItemSlot;
  public int lastHeldItemSlot;
  public int heldItemChangeTicks;
  public float lastSensitivityGcd;
  public int sensitivityChangeCount;
  public final LinkedList<Float> yawGcdSamples = new LinkedList<>();
  public EntityPlayer nearestTarget;
  public double nearestTargetDistance;

  // ── Scaffold tracking fields ──
  /** Timestamps of recent block placements (millis) */
  public final LinkedList<Long> placementTimestamps = new LinkedList<>();

  /** Recent yaw deltas during scaffold (bridge context) */
  public final LinkedList<Float> scaffoldYawDeltas = new LinkedList<>();

  /** Recent pitch deltas during scaffold (bridge context) */
  public final LinkedList<Float> scaffoldPitchDeltas = new LinkedList<>();

  /** Block positions placed in scaffold mode */
  public final LinkedList<BlockPos> blockPositionHistory = new LinkedList<>();

  /** Timestamps when sneak toggled */
  public final LinkedList<Long> sneakToggleTimestamps = new LinkedList<>();

  /** Tick when last scaffold swing occurred */
  public int lastScaffoldSwingTick;

  /** Ticks spent in scaffold (bridge) mode */
  public int scaffoldTicks;

  /** Whether player is currently in scaffold bridge context */
  public boolean scaffoldBridge;

  /** Last n placement intervals in ms (for speed analysis) */
  public final LinkedList<Long> placementIntervals = new LinkedList<>();

  /** Current streak of placement bursts (rapid consecutive) */
  public int placementBurst;

  /** Last known held block count */
  public int lastBlockCount;

  public PlayerCheckData(EntityPlayer player) {
    this.name = player.getName();
    this.x = this.lastX = player.posX;
    this.y = this.lastY = player.posY;
    this.z = this.lastZ = player.posZ;
    this.yaw = this.lastYaw = player.rotationYaw;
    this.pitch = this.lastPitch = player.rotationPitch;
    this.onGround = this.lastOnGround = player.onGround;
    this.usingItem =
        this.lastUsingItem = player.isUsingItem() || player.isBlocking() || player.isEating();
    this.swinging = this.lastSwinging = player.swingProgress > 0.0F;
  }

  public void update(EntityPlayer player) {
    this.existedTicks++;
    this.lastX = this.x;
    this.lastY = this.y;
    this.lastZ = this.z;
    this.lastYaw = this.yaw;
    this.lastPitch = this.pitch;
    this.lastOnGround = this.onGround;
    this.lastHorizontalDelta = this.horizontalDelta;
    this.lastYawDelta = this.yawDelta;
    this.lastPitchDelta = this.pitchDelta;
    this.lastUsingItem = this.usingItem;
    this.lastSwinging = this.swinging;

    this.x = player.posX;
    this.y = player.posY;
    this.z = player.posZ;
    this.yaw = player.rotationYaw;
    this.pitch = player.rotationPitch;
    this.onGround = player.onGround;
    this.usingItem = player.isUsingItem() || player.isBlocking() || player.isEating();
    this.swinging = player.swingProgress > 0.0F;
    this.collidedHorizontally = player.isCollidedHorizontally;

    boolean wasBreaking = this.breakingBlock;
    this.breakingBlock = player.isSwingInProgress && !this.swinging && player.swingProgressInt > 0;
    if (wasBreaking && !this.breakingBlock) {
      this.sinceBreakTicks = 0;
    } else if (!this.breakingBlock) {
      this.sinceBreakTicks = Math.min(MAX_SINCE_HURT_TICKS, this.sinceBreakTicks + 1);
    }

    this.deltaX = this.x - this.lastX;
    this.deltaY = this.y - this.lastY;
    this.deltaZ = this.z - this.lastZ;
    this.horizontalDelta = Math.hypot(this.deltaX, this.deltaZ);
    this.totalDelta =
        Math.sqrt(
            this.deltaX * this.deltaX + this.deltaY * this.deltaY + this.deltaZ * this.deltaZ);
    this.yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(this.yaw - this.lastYaw));
    this.pitchDelta = Math.abs(this.pitch - this.lastPitch);
    this.yawAcceleration = Math.abs(this.yawDelta - this.lastYawDelta);
    this.pitchAcceleration = Math.abs(this.pitchDelta - this.lastPitchDelta);

    this.teleportTicks =
        this.totalDelta > TELEPORT_DISTANCE_THRESHOLD
            ? TELEPORT_EXEMPT_TICKS
            : Math.max(0, this.teleportTicks - 1);
    this.groundTicks = this.onGround ? this.groundTicks + 1 : 0;
    this.airTicks = this.onGround ? 0 : this.airTicks + 1;
    this.hurtTicks = player.hurtTime > 0 ? player.hurtTime : Math.max(0, this.hurtTicks - 1);
    this.sinceHurtTicks =
        player.hurtTime > 0 ? 0 : Math.min(MAX_SINCE_HURT_TICKS, this.sinceHurtTicks + 1);
    this.usingItemTicks = this.usingItem ? this.usingItemTicks + 1 : 0;
    this.swingTicks = this.swinging ? this.swingTicks + 1 : 0;
    this.stillTicks =
        this.totalDelta < 0.003D && this.yawDelta < 0.05F && this.pitchDelta < 0.05F
            ? this.stillTicks + 1
            : 0;
    this.burstTicks =
        this.totalDelta > 0.8D && this.totalDelta < TELEPORT_DISTANCE_THRESHOLD
            ? this.burstTicks + 1
            : 0;

    if (!this.onGround && this.deltaY < 0.0D) {
      this.observedFallDistance += (float) -this.deltaY;
    } else if (this.onGround) {
      this.observedFallDistance = 0.0F;
    }

    this.history.addFirst(player.getEntityBoundingBox());
    if (this.history.size() > 20) {
      this.history.removeLast();
    }

    long now = System.currentTimeMillis();
    if (this.swinging && !this.lastSwinging) {
      this.lastSwingTimestamp = now;
      this.swingTimestamps.addFirst(now);
      while (this.swingTimestamps.size() > 100) {
        this.swingTimestamps.removeLast();
      }
    }

    this.movementTimestamps.addFirst(now);
    while (this.movementTimestamps.size() > 40) {
      this.movementTimestamps.removeLast();
    }

    this.lastBlocking = this.blocking;
    this.blocking = player.isBlocking();
    if (this.blocking) {
      this.blockingTicks++;
    } else {
      this.blockingTicks = 0;
    }
    if (this.blocking != this.lastBlocking) {
      this.lastBlockToggleTick = player.ticksExisted;
    }

    this.lastSprinting = this.sprinting;
    this.sprinting = player.isSprinting();
    this.lastSneaking = this.sneaking;
    this.sneaking = player.isSneaking();
    if (this.sprinting != this.lastSprinting) {
      this.sprintToggleCount++;
    } else {
      this.sprintToggleCount = 0;
    }
    if (this.sneaking != this.lastSneaking) {
      this.sneakToggleCount++;
    } else {
      this.sneakToggleCount = 0;
    }

    this.lastHeldItemSlot = this.heldItemSlot;
    this.heldItemSlot = player.inventory.currentItem;
    if (this.heldItemSlot != this.lastHeldItemSlot) {
      this.heldItemChangeTicks++;
    } else {
      this.heldItemChangeTicks = Math.max(0, this.heldItemChangeTicks - 1);
    }

    if (this.yawDelta > 0.05F && this.pitchDelta > 0.05F) {
      float gcd = (float) gcd(this.yawDelta, this.pitchDelta);
      if (gcd > 0.001F) {
        this.yawGcdSamples.addFirst(gcd);
        while (this.yawGcdSamples.size() > 40) {
          this.yawGcdSamples.removeLast();
        }
        if (this.lastSensitivityGcd > 0.001F && Math.abs(gcd - this.lastSensitivityGcd) > 0.01F) {
          this.sensitivityChangeCount++;
        } else {
          this.sensitivityChangeCount = Math.max(0, this.sensitivityChangeCount - 1);
        }
        this.lastSensitivityGcd = gcd;
      }
    }

    this.nearestTarget = null;
    this.nearestTargetDistance = Double.MAX_VALUE;
    if (player.worldObj != null) {
      for (EntityPlayer target : player.worldObj.playerEntities) {
        if (target == player || target.isDead || target.getName() == null) continue;
        double dist = player.getDistanceSqToEntity(target);
        if (dist < this.nearestTargetDistance) {
          this.nearestTargetDistance = dist;
          this.nearestTarget = target;
        }
      }
      this.nearestTargetDistance = Math.sqrt(this.nearestTargetDistance);
    }
  }

  // ── Rain Anticheat tracking fields (mirrors PlayerData) ──
  public double speed;
  public int aboveVoidTicks;
  public int fastTick;
  public int autoBlockTicks;
  public int rainTicksExisted;
  public int lastSneakTick;
  public int sneakTicks;
  public int noSlowTicks;
  public double rainPosX;
  public double rainPosY;
  public double rainPosZ;
  public double serverPosX;
  public double serverPosY;
  public double serverPosZ;
  public boolean rainSneaking; // NEVER assigned (match Rain where 'sneaking' stays false always)
  private int resetTick;

  // ── Rain Anticheat update (mirrors PlayerData.update() EXACTLY) ──

  /** Call after main update(). Mirrors Rain's PlayerData.update() pixel-perfect. */
  public void updateRainData(EntityPlayer player) {
    int ticksExisted = player.ticksExisted;
    this.rainPosX = player.posX - player.lastTickPosX;
    this.rainPosY = player.posY - player.lastTickPosY;
    this.rainPosZ = player.posZ - player.lastTickPosZ;
    this.speed = Math.max(Math.abs(this.rainPosX), Math.abs(this.rainPosZ));

    // 20-tick reset cycle
    if (ticksExisted - this.resetTick >= 20) {
      this.fastTick = 0;
      this.resetTick = ticksExisted;
    }
    if (this.speed >= 0.3) {
      ++this.fastTick;
      this.rainTicksExisted = ticksExisted;
    } else {
      this.fastTick = 0;
    }

    // aboveVoidTicks = ticksExisted when Y delta >= 0.1
    if (Math.abs(this.rainPosY) >= 0.1) {
      this.aboveVoidTicks = ticksExisted;
    }

    // lastSneakTick = ticksExisted when sneaking
    if (player.isSneaking()) {
      this.lastSneakTick = ticksExisted;
    }

    // autoBlock
    if (player.isSwingInProgress && player.isBlocking()) {
      ++this.autoBlockTicks;
    } else {
      this.autoBlockTicks = 0;
    }

    // noSlow
    if (player.isSprinting() && player.isUsingItem()) {
      ++this.noSlowTicks;
    } else {
      this.noSlowTicks = 0;
    }

    // sneakTicks (Rain matches pitch>=70, holding block, swingProgressInt==1)
    if (player.rotationPitch >= 70.0F
        && player.getHeldItem() != null
        && player.getHeldItem().getItem() instanceof ItemBlock) {
      if (player.swingProgressInt == 1) {
        // In Rain: 'this.sneaking' is NEVER assigned (stays false),
        // so '!this.sneaking && entityPlayer.isSneaking()'
        // is functionally just 'entityPlayer.isSneaking()'
        if (player.isSneaking()) {
          ++this.sneakTicks;
        } else {
          this.sneakTicks = 0;
        }
      }
    } else {
      this.sneakTicks = 0;
    }
  }

  public void updateSneak(EntityPlayer player) {
    this.sneaking = player.isSneaking();
  }

  public void updateServerPos(EntityPlayer player) {
    this.serverPosX = player.serverPosX / 32.0;
    this.serverPosY = player.serverPosY / 32.0;
    this.serverPosZ = player.serverPosZ / 32.0;
  }

  public boolean recentlyTeleported() {
    return this.teleportTicks > 0 || this.existedTicks < INITIAL_EXEMPT_TICKS;
  }

  public boolean recentlyHurt() {
    return this.sinceHurtTicks <= RECENT_HURT_TICKS || this.hurtTicks > 0;
  }

  public boolean recentlyBrokeBlock() {
    return this.sinceBreakTicks < 60;
  }

  public boolean startedSwinging() {
    return this.swinging && !this.lastSwinging;
  }

  public boolean startedUsingItem() {
    return this.usingItem && !this.lastUsingItem;
  }

  private static double gcd(double a, double b) {
    a = Math.abs(a);
    b = Math.abs(b);
    if (a < 0.001 || b < 0.001) return Math.max(a, b);
    int iterations = 0;
    while (b > 0.001 && iterations++ < 100) {
      double temp = b;
      b = a % b;
      a = temp;
    }
    return a;
  }
}
