package myau.module.modules.player;

import com.google.common.base.CaseFormat;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import myau.Myau;
import myau.enums.ChatColors;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.impl.*;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.module.modules.render.BedESP;
import myau.module.modules.render.HUD;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.animation.*;
import myau.util.client.*;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.player.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class BedNuker extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final TimerUtil timer = new TimerUtil();
  private final ArrayList<BlockPos> bedWhitelist = new ArrayList<BlockPos>();
  private final Color colorRed = new Color(ChatColors.RED.toAwtColor());
  private final Color colorYellow = new Color(ChatColors.YELLOW.toAwtColor());
  private final Color colorGreen = new Color(ChatColors.GREEN.toAwtColor());

  private final ArrayDeque<BlockPos> bfsQueue = new ArrayDeque<BlockPos>(64);
  private final HashSet<BlockPos> bfsVisited = new HashSet<BlockPos>(128);
  private final ArrayList<BlockPos> bfsCandidates = new ArrayList<BlockPos>(32);

  private BlockPos targetBed = null;
  private int breakStage = 0;
  private int tickCounter = 0;
  private float breakProgress = 0.0F;
  private boolean isBed = false;
  private int savedSlot = -1;
  private boolean breaking = false;
  private boolean waitingForStart = false;
  private int instantDelay = 0;

  private int targetSwitchDelay = 0;

  private BlockPos previousTarget = null;

  private int lastSyncedSlot = -1;

  private HitResult currentHit = null;

  public final ModeProperty mode =
      new ModeProperty("mode", 0, new String[] {"LEGIT", "SWAP", "INSTANT"});
  public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 6.0F);
  public final PercentProperty speed = new PercentProperty("speed", 0);
  public final BooleanProperty groundSpeed = new BooleanProperty("ground-spoof", false);
  public final ModeProperty ignoreVelocity =
      new ModeProperty("ignore-velocity", 0, new String[] {"NONE", "CANCEL", "DELAY"});
  public final BooleanProperty surroundings = new BooleanProperty("surroundings", true);
  public final BooleanProperty toolCheck = new BooleanProperty("tool-check", true);
  public final BooleanProperty whiteList = new BooleanProperty("whitelist", true);
  public final BooleanProperty swing = new BooleanProperty("swing", true);
  public final ModeProperty moveFix =
      new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT", "STRICT"});
  public final ModeProperty showTarget =
      new ModeProperty("show-target", 1, new String[] {"NONE", "DEFAULT", "HUD"});
  public final ModeProperty showProgress =
      new ModeProperty("show-progress", 1, new String[] {"NONE", "DEFAULT", "HUD"});
  public final PercentProperty fastBreakNormal = new PercentProperty("fastbreak-normal", 0);
  public final PercentProperty fastBreakBed = new PercentProperty("fastbreak-bed", 0);
  public final FloatProperty airMultiplier = new FloatProperty("air-multiplier", 1.0f, 0.0f, 3.0f);
  public final FloatProperty bedFov = new FloatProperty("bed-fov", 180.0f, 30.0f, 360.0f);
  public final BooleanProperty colorCheck = new BooleanProperty("color-check", true);
  public final IntProperty whitelistScanRadius = new IntProperty("whitelist-radius", 12, 4, 64);

  private static class HitResult {
    final Vec3 hitVec;
    final EnumFacing facing;
    final double distance;

    HitResult(Vec3 hitVec, EnumFacing facing, double distance) {
      this.hitVec = hitVec;
      this.facing = facing;
      this.distance = distance;
    }
  }

  private void resetBreaking() {
    if (this.targetBed != null) {
      mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), this.targetBed, -1);
    }
    this.targetBed = null;
    this.currentHit = null;
    this.breakStage = 0;
    this.tickCounter = 0;
    this.breakProgress = 0.0F;
    this.isBed = false;
    this.breaking = false;
    this.instantDelay = 0;
  }

  private void restoreSlot() {
    if (this.savedSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.savedSlot;
      this.syncHeldItem();
      this.savedSlot = -1;
    }
  }

  private void syncHeldItem() {
    int currentPlayerItem =
        ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
    if (mc.thePlayer.inventory.currentItem != currentPlayerItem) {
      mc.thePlayer.stopUsingItem();
    }
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
  }

  private void forceSyncHeldItem(int slot) {
    if (this.lastSyncedSlot != slot) {
      PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
      this.lastSyncedSlot = slot;
    }
  }

  private float getBreakThreshold() {
    float threshold = 1.0F - 0.3F * ((float) this.speed.getValue().intValue() / 100.0F);
    if (this.targetBed != null) {
      if (this.isBed) {
        threshold -= this.fastBreakBed.getValue().floatValue();
      } else {
        threshold -= this.fastBreakNormal.getValue().floatValue();
      }
    }
    return Math.max(0.01F, threshold);
  }

  private float calcProgress() {
    if (this.targetBed == null) {
      return 0.0F;
    } else {
      float progress = this.breakProgress;
      if (this.groundSpeed.getValue()) {
        int slot =
            ItemUtil.findInventorySlot(
                mc.thePlayer.inventory.currentItem,
                mc.theWorld.getBlockState(this.targetBed).getBlock());
        progress =
            (float) this.tickCounter
                * this.getBreakDelta(
                    mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, true);
      }
      return Math.min(1.0F, progress / this.getBreakThreshold());
    }
  }

  private boolean hasProperTool(Block block) {
    Material material = block.getMaterial();
    if (material != Material.iron && material != Material.anvil && material != Material.rock) {
      return true;
    } else {
      for (int i = 0; i < 9; i++) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
        if (stack != null) {
          Item item = stack.getItem();
          if (item instanceof ItemPickaxe) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private float getDigSpeed(IBlockState iBlockState, int slot, boolean onGround) {
    ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
    float digSpeed = item == null ? 1.0F : item.getItem().getDigSpeed(item, iBlockState);
    if (digSpeed > 1.0F) {
      int enchantmentLevel =
          EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
      if (enchantmentLevel > 0) {
        digSpeed += (float) (enchantmentLevel * enchantmentLevel + 1);
      }
    }
    if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
      digSpeed *=
          1.0F
              + (float) (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1)
                  * 0.2F;
    }
    if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
      switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
        case 0:
          digSpeed *= 0.3F;
          break;
        case 1:
          digSpeed *= 0.09F;
          break;
        case 2:
          digSpeed *= 0.0027F;
          break;
        default:
          digSpeed *= 8.1E-4F;
      }
    }
    if (mc.thePlayer.isInsideOfMaterial(Material.water)
        && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
      digSpeed /= 5.0F;
    }
    if (!onGround) {
      digSpeed /= 5.0F;
    }
    return digSpeed;
  }

  boolean canHarvest(Block block, int slot) {
    if (block.getMaterial().isToolNotRequired()) {
      return true;
    } else {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
      return stack != null && stack.canHarvestBlock(block);
    }
  }

  private float getBreakDelta(
      IBlockState iBlockState, BlockPos blockPos, int slot, boolean onGround) {
    Block block = iBlockState.getBlock();
    float hardness = block.getBlockHardness(mc.theWorld, blockPos);
    float boost = this.canHarvest(block, slot) ? 30.0F : 100.0F;
    return hardness < 0.0F
        ? 0.0F
        : this.getDigSpeed(iBlockState, slot, onGround) / hardness / boost;
  }

  private float calcBlockStrength(BlockPos blockPos) {
    IBlockState blockState = mc.theWorld.getBlockState(blockPos);
    int slot =
        ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, blockState.getBlock());
    return this.getBreakDelta(blockState, blockPos, slot, mc.thePlayer.onGround);
  }

  /**
   * Computes the best (closest, most visible) hit vector and facing for a target block. Scans all 6
   * faces of the block's actual collision AABB, finds the closest reachable point to the player's
   * eyes, and validates it via raytrace.
   *
   * <p>The face point is inset by a small epsilon (0.005) from edges to avoid corner-case precision
   * issues that cause raytrace to miss the face entirely.
   */
  private HitResult computeBestHit(BlockPos pos) {
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    double maxRange = this.range.getValue().doubleValue();
    double maxRangeSq = maxRange * maxRange;

    IBlockState state = mc.theWorld.getBlockState(pos);
    Block block = state.getBlock();
    AxisAlignedBB aabb = block.getCollisionBoundingBox(mc.theWorld, pos, state);
    if (aabb == null) {
      aabb = new AxisAlignedBB(pos, pos.add(1, 1, 1));
    }

    HitResult best = null;
    double bestDistSq = Double.MAX_VALUE;

    for (EnumFacing face : EnumFacing.values()) {

      BlockPos adjacent = pos.offset(face);
      Block adjBlock = mc.theWorld.getBlockState(adjacent).getBlock();
      if (adjBlock.isFullBlock() && !(adjBlock instanceof BlockBed)) {
        continue;
      }

      Vec3 facePoint = closestPointOnFace(aabb, face, eyes);
      double distSq = eyes.squareDistanceTo(facePoint);

      if (distSq > maxRangeSq || distSq < 0.001) continue;

      float[] rots = RotationUtil.calculate(facePoint);

      MovingObjectPosition mop = RotationUtil.rayTrace(rots[0], rots[1], maxRange, 1.0f);

      if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK && mop.getBlockPos().equals(pos)) {

        if (distSq < bestDistSq) {
          bestDistSq = distSq;
          Vec3 hitVec = (mop.hitVec != null) ? mop.hitVec : facePoint;
          best = new HitResult(hitVec, mop.sideHit, Math.sqrt(distSq));
        }
      }
    }

    return best;
  }

  /**
   * Returns the closest point on a given face of the AABB to the reference point. Points are inset
   * by FACE_EPSILON from edges to prevent precision misses.
   */
  private static final double FACE_EPSILON = 0.005;

  private Vec3 closestPointOnFace(AxisAlignedBB aabb, EnumFacing face, Vec3 point) {
    double cx = clamp(point.xCoord, aabb.minX + FACE_EPSILON, aabb.maxX - FACE_EPSILON);
    double cy = clamp(point.yCoord, aabb.minY + FACE_EPSILON, aabb.maxY - FACE_EPSILON);
    double cz = clamp(point.zCoord, aabb.minZ + FACE_EPSILON, aabb.maxZ - FACE_EPSILON);

    switch (face) {
      case DOWN:
        return new Vec3(cx, aabb.minY, cz);
      case UP:
        return new Vec3(cx, aabb.maxY, cz);
      case NORTH:
        return new Vec3(cx, cy, aabb.minZ);
      case SOUTH:
        return new Vec3(cx, cy, aabb.maxZ);
      case WEST:
        return new Vec3(aabb.minX, cy, cz);
      case EAST:
        return new Vec3(aabb.maxX, cy, cz);
      default:
        return new Vec3(cx, cy, cz);
    }
  }

  private static double clamp(double val, double min, double max) {
    return val < min ? min : (val > max ? max : val);
  }

  private BlockPos findRaytraceObstruction(BlockPos target) {
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    double maxRangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();

    IBlockState state = mc.theWorld.getBlockState(target);
    Block block = state.getBlock();
    AxisAlignedBB aabb = block.getCollisionBoundingBox(mc.theWorld, target, state);
    if (aabb == null) {
      aabb = new AxisAlignedBB(target, target.add(1, 1, 1));
    }

    ArrayList<Vec3> candidates = this.buildRaytraceSamplePoints(aabb);

    BlockPos obstruction = null;

    for (Vec3 candidate : candidates) {
      if (eyes.squareDistanceTo(candidate) > maxRangeSq) continue;

      MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, candidate);

      if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) {

        return null;
      }

      BlockPos hitPos = mop.getBlockPos();
      if (hitPos.equals(target)) {

        return null;
      }

      Block hitBlock = mc.theWorld.getBlockState(hitPos).getBlock();
      if (hitBlock == Blocks.air
          || hitBlock instanceof BlockBed
          || hitBlock.getBlockHardness(mc.theWorld, hitPos) < 0) {
        return null;
      }

      if (obstruction == null) {
        obstruction = hitPos;
      }
    }

    return obstruction;
  }

  /**
   * Builds a set of sample points distributed across all 6 faces of the AABB. Each face gets 5
   * points: 4 corners (inset by INSET) and the center. Returns 30 well-distributed points for
   * robust line-of-sight checking from any viewing angle.
   */
  private ArrayList<Vec3> buildRaytraceSamplePoints(AxisAlignedBB aabb) {
    ArrayList<Vec3> points = new ArrayList<Vec3>();
    final double INSET = 0.01;

    double minX = aabb.minX, minY = aabb.minY, minZ = aabb.minZ;
    double maxX = aabb.maxX, maxY = aabb.maxY, maxZ = aabb.maxZ;
    double cx = (minX + maxX) * 0.5, cy = (minY + maxY) * 0.5, cz = (minZ + maxZ) * 0.5;

    points.add(new Vec3(minX + INSET, minY, minZ + INSET));
    points.add(new Vec3(maxX - INSET, minY, minZ + INSET));
    points.add(new Vec3(minX + INSET, minY, maxZ - INSET));
    points.add(new Vec3(maxX - INSET, minY, maxZ - INSET));
    points.add(new Vec3(cx, minY, cz));

    points.add(new Vec3(minX + INSET, maxY, minZ + INSET));
    points.add(new Vec3(maxX - INSET, maxY, minZ + INSET));
    points.add(new Vec3(minX + INSET, maxY, maxZ - INSET));
    points.add(new Vec3(maxX - INSET, maxY, maxZ - INSET));
    points.add(new Vec3(cx, maxY, cz));

    points.add(new Vec3(minX + INSET, minY + INSET, minZ));
    points.add(new Vec3(maxX - INSET, minY + INSET, minZ));
    points.add(new Vec3(minX + INSET, maxY - INSET, minZ));
    points.add(new Vec3(maxX - INSET, maxY - INSET, minZ));
    points.add(new Vec3(cx, cy, minZ));

    points.add(new Vec3(minX + INSET, minY + INSET, maxZ));
    points.add(new Vec3(maxX - INSET, minY + INSET, maxZ));
    points.add(new Vec3(minX + INSET, maxY - INSET, maxZ));
    points.add(new Vec3(maxX - INSET, maxY - INSET, maxZ));
    points.add(new Vec3(cx, cy, maxZ));

    points.add(new Vec3(minX, minY + INSET, minZ + INSET));
    points.add(new Vec3(minX, maxY - INSET, minZ + INSET));
    points.add(new Vec3(minX, minY + INSET, maxZ - INSET));
    points.add(new Vec3(minX, maxY - INSET, maxZ - INSET));
    points.add(new Vec3(minX, cy, cz));

    points.add(new Vec3(maxX, minY + INSET, minZ + INSET));
    points.add(new Vec3(maxX, maxY - INSET, minZ + INSET));
    points.add(new Vec3(maxX, minY + INSET, maxZ - INSET));
    points.add(new Vec3(maxX, maxY - INSET, maxZ - INSET));
    points.add(new Vec3(maxX, cy, cz));

    return points;
  }

  /**
   * Computes a fallback hit on the target block by scanning all 6 faces and picking the closest
   * exposed face's center point (inset by 0.49 from the block center). Used when computeBestHit
   * fails due to difficult viewing angles (e.g., off-center trench positions).
   */
  private HitResult computeFallbackHit(BlockPos pos) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double maxRange = this.range.getValue().doubleValue();
    double maxRangeSq = maxRange * maxRange;
    HitResult bestHit = null;
    double bestDistSq = Double.MAX_VALUE;
    for (EnumFacing face : EnumFacing.values()) {
      Block adj = mc.theWorld.getBlockState(pos.offset(face)).getBlock();
      if (adj == Blocks.air || adj instanceof BlockAir) {
        Vec3 hitVec =
            new Vec3(
                pos.getX() + 0.5 + face.getFrontOffsetX() * 0.49,
                pos.getY() + 0.5 + face.getFrontOffsetY() * 0.49,
                pos.getZ() + 0.5 + face.getFrontOffsetZ() * 0.49);
        double distSq = eye.squareDistanceTo(hitVec);
        if (distSq < bestDistSq && distSq <= maxRangeSq) {
          bestDistSq = distSq;
          bestHit = new HitResult(hitVec, face, Math.sqrt(distSq));
        }
      }
    }
    return bestHit;
  }

  private boolean tryClearObstruction() {
    if (this.targetBed == null) return false;
    BlockPos obstruction = this.findRaytraceObstruction(this.targetBed);
    if (obstruction != null
        && PlayerUtil.canReach(obstruction, this.range.getValue().doubleValue())) {

      this.targetBed = obstruction;
      this.currentHit = this.computeBestHit(this.targetBed);
      this.breakStage = 0;
      this.tickCounter = 0;
      this.breakProgress = 0.0F;
      this.isBed = false;
      return this.currentHit != null;
    }

    HitResult fallbackHit = this.computeFallbackHit(this.targetBed);
    if (fallbackHit != null) {
      this.currentHit = fallbackHit;
      return true;
    }
    return false;
  }

  private BlockPos bfsFindOutermostDefenseBlock(BlockPos[] bedPair) {

    this.bfsQueue.clear();
    this.bfsVisited.clear();
    this.bfsCandidates.clear();

    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double rangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();

    for (BlockPos bp : bedPair) {
      if (this.bfsVisited.add(bp)) {
        this.bfsQueue.add(bp);
      }
    }

    int maxLayers = 10;
    for (int layer = 0; layer < maxLayers && !this.bfsQueue.isEmpty(); layer++) {
      int layerSize = this.bfsQueue.size();

      for (int i = 0; i < layerSize; i++) {
        BlockPos current = this.bfsQueue.poll();
        if (current == null) break;

        for (EnumFacing f : EnumFacing.values()) {
          BlockPos neighbor = current.offset(f);
          if (!this.bfsVisited.add(neighbor)) continue;

          Block nb = mc.theWorld.getBlockState(neighbor).getBlock();

          if (nb == Blocks.air || nb instanceof BlockAir) continue;

          if (nb instanceof BlockBed) {
            this.bfsQueue.add(neighbor);
            continue;
          }

          if (nb.getBlockHardness(mc.theWorld, neighbor) < 0) continue;

          boolean hasExposedFace = false;
          for (EnumFacing check : EnumFacing.values()) {
            Block adj = mc.theWorld.getBlockState(neighbor.offset(check)).getBlock();
            if (adj == Blocks.air || adj instanceof BlockAir) {

              hasExposedFace = true;
              break;
            }
          }

          if (hasExposedFace) {

            Vec3 hitVec =
                new Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5);
            if (eye.squareDistanceTo(hitVec) <= rangeSq) {
              this.bfsCandidates.add(neighbor);
            }

            this.bfsQueue.add(neighbor);
          } else {

            this.bfsQueue.add(neighbor);
          }
        }
      }

      if (!this.bfsCandidates.isEmpty()) {
        return pickBestCandidate(this.bfsCandidates, bedPair);
      }
    }

    return null;
  }

  /** Picks the best candidate from a list based on score (break speed + distance). */
  private BlockPos pickBestCandidate(ArrayList<BlockPos> candidates, BlockPos[] bedPair) {
    BlockPos bestPos = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (int i = 0, n = candidates.size(); i < n; i++) {
      BlockPos pos = candidates.get(i);
      double score = this.scoreBlockTarget(pos, false, 0);
      score += this.getLoSScoreModifier(pos, bedPair);
      if (score < bestScore) {
        bestScore = score;
        bestPos = pos;
      }
    }
    return bestPos;
  }

  /**
   * Returns a score bonus (lower = better) for blocks close to the player→bed line-of-sight.
   * Ensures multi-block walls are broken in correct LOS order.
   */
  private double getLoSScoreModifier(BlockPos candidate, BlockPos[] bedPair) {
    if (bedPair == null) return 0;
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 bc = this.bedCenter(bedPair);
    Vec3 candCenter =
        new Vec3(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);

    Vec3 los = bc.subtract(eyes);
    double losLen = los.lengthVector();
    if (losLen < 1e-6) return 0;

    Vec3 toCand = candCenter.subtract(eyes);

    Vec3 cross =
        new Vec3(
            los.yCoord * toCand.zCoord - los.zCoord * toCand.yCoord,
            los.zCoord * toCand.xCoord - los.xCoord * toCand.zCoord,
            los.xCoord * toCand.yCoord - los.yCoord * toCand.xCoord);
    double crossMag =
        Math.sqrt(
            cross.xCoord * cross.xCoord
                + cross.yCoord * cross.yCoord
                + cross.zCoord * cross.zCoord);
    double perpDist = crossMag / losLen;

    double dot =
        toCand.xCoord * los.xCoord + toCand.yCoord * los.yCoord + toCand.zCoord * los.zCoord;
    if (dot < 0) return 100;

    return perpDist * 0.3;
  }

  private BlockPos[] resolveBedPair(BlockPos bedPos) {
    IBlockState state = mc.theWorld.getBlockState(bedPos);
    if (!(state.getBlock() instanceof BlockBed)) return null;
    BlockBed.EnumPartType part = state.getValue(BlockBed.PART);
    EnumFacing facing = state.getValue(BlockBed.FACING);
    BlockPos foot =
        (part == BlockBed.EnumPartType.FOOT) ? bedPos : bedPos.offset(facing.getOpposite());
    BlockPos head = foot.offset(facing);
    return new BlockPos[] {foot, head};
  }

  private boolean isBedExposed(BlockPos[] pair) {
    for (BlockPos bp : pair) {
      for (EnumFacing f : EnumFacing.values()) {
        if (mc.theWorld.getBlockState(bp.offset(f)).getBlock() == Blocks.air) {
          return true;
        }
      }
    }
    return false;
  }

  private Vec3 bedCenter(BlockPos[] pair) {
    double minX = Math.min(pair[0].getX(), pair[1].getX());
    double minY = Math.min(pair[0].getY(), pair[1].getY());
    double minZ = Math.min(pair[0].getZ(), pair[1].getZ());
    double maxX = Math.max(pair[0].getX(), pair[1].getX()) + 1.0;
    double maxY = Math.max(pair[0].getY(), pair[1].getY()) + 1.0;
    double maxZ = Math.max(pair[0].getZ(), pair[1].getZ()) + 1.0;
    return new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
  }

  private boolean inBedFov(Vec3 center) {
    float fov = this.bedFov.getValue();
    if (fov >= 360.0f) return true;
    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 look = mc.thePlayer.getLook(1.0f);
    Vec3 to = center.subtract(eyes);
    double len = to.lengthVector();
    if (len < 1e-6) return true;
    to = new Vec3(to.xCoord / len, to.yCoord / len, to.zCoord / len);
    double dot = look.xCoord * to.xCoord + look.yCoord * to.yCoord + look.zCoord * to.zCoord;
    double ang = Math.acos(MathHelper.clamp_double(dot, -1.0, 1.0)) * (180.0 / Math.PI);
    return ang <= fov * 0.5;
  }

  private BlockPos findNearestBed() {
    return this.findTargetBed(
        mc.thePlayer.posX,
        mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
        mc.thePlayer.posZ);
  }

  /**
   * Scans for the nearest enemy bed. Separates exposed and covered beds. For exposed beds, targets
   * the bed block itself. For covered beds, uses BFS layer-by-layer to find the outermost defense
   * block.
   */
  private BlockPos findTargetBed(double x, double y, double z) {
    ArrayList<BlockPos[]> exposedBeds = new ArrayList<BlockPos[]>();
    ArrayList<BlockPos[]> coveredBeds = new ArrayList<BlockPos[]>();
    BlockPos ownBedPos = null;
    myau.module.modules.minigames.BedwarUtils.TeamBedColor ownTeamColor = null;
    if (this.colorCheck.getValue()) {
      ownTeamColor = myau.module.modules.minigames.BedwarUtils.detectOwnTeamColor();
      ownBedPos = myau.module.modules.minigames.BedwarUtils.getTrackedOwnBed();
    }
    int sX = MathHelper.floor_double(x);
    int sY = MathHelper.floor_double(y);
    int sZ = MathHelper.floor_double(z);
    int rangeInt = (int) Math.ceil(this.range.getValue().doubleValue()) + 1;
    HashSet<Long> seenPairs = new HashSet<Long>();

    for (int i = sX - rangeInt; i <= sX + rangeInt; i++) {
      for (int j = sY - rangeInt; j <= sY + rangeInt; j++) {
        for (int k = sZ - rangeInt; k <= sZ + rangeInt; k++) {
          BlockPos newPos = new BlockPos(i, j, k);
          if (this.whiteList.getValue() && this.bedWhitelist.contains(newPos)) continue;
          Block block = mc.theWorld.getBlockState(newPos).getBlock();
          if (!(block instanceof BlockBed)) continue;
          BlockPos[] pair = this.resolveBedPair(newPos);
          if (pair == null) continue;

          long pairKey = ((long) pair[0].hashCode() << 32) | (pair[1].hashCode() & 0xFFFFFFFFL);
          if (!seenPairs.add(pairKey)) continue;

          if (ownBedPos != null) {
            BlockPos[] ownPair = this.resolveBedPair(ownBedPos);
            if (ownPair != null) {
              boolean matchesFoot = pair[0].equals(ownPair[0]) && pair[1].equals(ownPair[1]);
              boolean matchesHead = pair[0].equals(ownPair[1]) && pair[1].equals(ownPair[0]);
              if (matchesFoot || matchesHead) continue;
            }
          }
          Vec3 footCenter =
              new Vec3(pair[0].getX() + 0.5, pair[0].getY() + 0.5, pair[0].getZ() + 0.5);
          Vec3 headCenter =
              new Vec3(pair[1].getX() + 0.5, pair[1].getY() + 0.5, pair[1].getZ() + 0.5);
          Vec3 combinedCenter = this.bedCenter(pair);

          if (this.mode.getValue() != 0
              && !this.inBedFov(footCenter)
              && !this.inBedFov(headCenter)
              && !this.inBedFov(combinedCenter)) continue;
          if (!PlayerUtil.isBlockWithinReach(pair[0], x, y, z, this.range.getValue().doubleValue())
              && !PlayerUtil.isBlockWithinReach(
                  pair[1], x, y, z, this.range.getValue().doubleValue())) {
            continue;
          }
          if (this.isBedExposed(pair)) {
            exposedBeds.add(pair);
          } else {
            coveredBeds.add(pair);
          }
        }
      }
    }

    for (BlockPos[] pair : exposedBeds) {
      BlockPos target = this.pickBestBedBlock(pair);
      if (target != null) return target;
    }

    for (BlockPos[] pair : coveredBeds) {
      BlockPos target = this.bfsFindOutermostDefenseBlock(pair);
      if (target != null) return target;
    }

    return null;
  }

  private BlockPos pickBestBedBlock(BlockPos[] pair) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    double rangeSq = this.range.getValue().doubleValue() * this.range.getValue().doubleValue();
    BlockPos bestPos = null;
    double bestScore = Double.POSITIVE_INFINITY;

    for (BlockPos bp : pair) {
      Vec3 center = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
      if (eye.squareDistanceTo(center) > rangeSq) continue;

      HitResult hit = this.computeBestHit(bp);

      if (hit == null) {
        hit = this.computeFallbackHit(bp);
      }

      if (hit == null) continue;

      double score =
          this.scoreBlockTarget(bp, true, bp.equals(this.targetBed) ? this.breakProgress : 0);
      if (score < bestScore) {
        bestScore = score;
        bestPos = bp;
      }
    }

    if (bestPos == null) {
      for (BlockPos bp : pair) {
        BlockPos obstruction = this.findRaytraceObstruction(bp);
        if (obstruction != null
            && PlayerUtil.canReach(obstruction, this.range.getValue().doubleValue())) {
          return obstruction;
        }
      }

      BlockPos closestBlock = null;
      double closestDistSq = Double.MAX_VALUE;
      for (BlockPos bp : pair) {
        Vec3 center = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        double distSq = eye.squareDistanceTo(center);
        if (distSq <= rangeSq && distSq < closestDistSq) {
          for (EnumFacing f : EnumFacing.values()) {
            if (mc.theWorld.getBlockState(bp.offset(f)).getBlock() == Blocks.air) {
              closestDistSq = distSq;
              closestBlock = bp;
              break;
            }
          }
        }
      }
      return closestBlock;
    }

    return bestPos;
  }

  private double scoreBlockTarget(BlockPos pos, boolean isBedBlock, float currentProgress) {
    Block block = mc.theWorld.getBlockState(pos).getBlock();
    int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, block);
    if (slot == -1) slot = mc.thePlayer.inventory.currentItem;
    float digRate = this.getDigSpeed(mc.theWorld.getBlockState(pos), slot, mc.thePlayer.onGround);
    float hardness = block.getBlockHardness(mc.theWorld, pos);
    if (hardness < 0) return Double.POSITIVE_INFINITY;
    float boost = this.canHarvest(block, slot) ? 30.0F : 100.0F;
    float rate = digRate / hardness / boost;
    if (rate <= 0) return Double.POSITIVE_INFINITY;
    double timeEst = 1.0 / rate;
    if (pos.equals(this.targetBed) && currentProgress > 0.02f) {
      timeEst -= currentProgress * 12.0;
    }
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    timeEst +=
        eye.squareDistanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
            * 0.002;
    return timeEst;
  }

  private void doSwing() {
    if (this.swing.getValue()) {
      mc.thePlayer.swingItem();
    } else {
      PacketUtil.sendPacket(new C0APacketAnimation());
    }
  }

  private Color getProgressColor(int mode) {
    switch (mode) {
      case 1:
        float progress = this.calcProgress();
        if (progress <= 0.5F) {
          return ColorUtil.interpolate(progress / 0.5F, this.colorRed, this.colorYellow);
        }
        return ColorUtil.interpolate((progress - 0.5F) / 0.5F, this.colorYellow, this.colorGreen);
      case 2:
        return ((HUD) Myau.moduleManager.modules.get(HUD.class))
            .getColor(System.currentTimeMillis());
      default:
        return new Color(-1);
    }
  }

  public BedNuker() {
    super("BedNuker", false);
  }

  /**
   * Returns true as soon as a target is acquired. This ensures rotations are applied immediately
   * upon target lock, preventing head-desync.
   */
  public boolean isReady() {
    return this.targetBed != null;
  }

  public boolean isBreaking() {
    return this.targetBed != null && this.breaking;
  }

  @EventTarget(Priority.LOWEST)
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
    if (autoBlockIn.isEnabled()) return;

    if (this.targetBed != null) {
      if (mc.theWorld.isAirBlock(this.targetBed)
          || !PlayerUtil.canReach(this.targetBed, this.range.getValue().doubleValue())) {
        this.restoreSlot();
        this.resetBreaking();
      } else if (this.mode.getValue() == 0) {
        this.currentHit = this.computeBestHit(this.targetBed);
        if (this.currentHit == null) {
          if (this.tryClearObstruction()) {
            this.processDigging(event);
            return;
          }
          this.restoreSlot();
          this.resetBreaking();
        } else {
          this.processDigging(event);
          return;
        }
      } else {
        this.processDigging(event);
        return;
      }
    }

    if (this.instantDelay > 0) {
      this.instantDelay--;
      if (this.targetBed == null) {
        Myau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
      }
      return;
    }

    if (mc.thePlayer.capabilities.allowEdit && this.instantDelay == 0) {
      BlockPos newTarget = this.findNearestBed();

      if (newTarget != null) {
        this.targetBed = newTarget;
        this.previousTarget = newTarget;
        this.breakStage = 0;
        this.tickCounter = 0;
        this.breakProgress = 0.0F;
        this.currentHit = null;
        this.isBed = mc.theWorld.getBlockState(this.targetBed).getBlock() instanceof BlockBed;

        this.processDigging(event);
        return;
      }

      this.restoreSlot();
    }

    if (this.targetBed == null) {
      Myau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
    }
  }

  private void processDigging(UpdateEvent event) {
    this.currentHit = this.computeBestHit(this.targetBed);

    if (this.currentHit == null) {
      if (this.mode.getValue() == 0) {

        if (!this.tryClearObstruction()) {
          this.restoreSlot();
          this.resetBreaking();
          return;
        }

      } else {
        Vec3 center =
            new Vec3(
                (double) this.targetBed.getX() + 0.5,
                (double) this.targetBed.getY() + 0.5,
                (double) this.targetBed.getZ() + 0.5);
        float[] rots = RotationUtil.calculate(center);
        event.setRotation(rots[0], rots[1], 5);
        if (this.moveFix.getValue() != 0) {
          event.setPervRotation(rots[0], 5);
        } else {
          event.setPervRotation(mc.thePlayer.rotationYaw, 5);
        }

        EnumFacing fallbackFacing = EnumFacing.UP;
        int slot =
            ItemUtil.findInventorySlot(
                mc.thePlayer.inventory.currentItem,
                mc.theWorld.getBlockState(this.targetBed).getBlock());
        switch (this.breakStage) {
          case 0:
            this.handleStage0(slot, fallbackFacing);
            break;
          case 1:
            this.handleStage1(slot, fallbackFacing);
            break;
          case 2:
            this.handleStage2();
            break;
        }
        return;
      }
    }

    float[] rots = RotationUtil.calculate(this.currentHit.hitVec);
    event.setRotation(rots[0], rots[1], 5);

    if (this.moveFix.getValue() != 0) {
      event.setPervRotation(rots[0], 5);
    } else {
      event.setPervRotation(mc.thePlayer.rotationYaw, 5);
    }

    EnumFacing effectiveFacing = this.currentHit.facing;
    int slot =
        ItemUtil.findInventorySlot(
            mc.thePlayer.inventory.currentItem,
            mc.theWorld.getBlockState(this.targetBed).getBlock());

    switch (this.breakStage) {
      case 0:
        this.handleStage0(slot, effectiveFacing);
        break;
      case 1:
        this.handleStage1(slot, effectiveFacing);
        break;
      case 2:
        this.handleStage2();
        break;
    }
  }

  private void handleStage0(int slot, EnumFacing facing) {
    if (this.mode.getValue() == 0) {
      if (this.savedSlot == -1) {
        this.savedSlot = mc.thePlayer.inventory.currentItem;
      }
      if (mc.thePlayer.inventory.currentItem != slot) {
        mc.thePlayer.inventory.currentItem = slot;
        this.syncHeldItem();
        this.forceSyncHeldItem(slot);
      }
    }

    if (this.mode.getValue() == 2) {

      int prevSlot = mc.thePlayer.inventory.currentItem;
      if (slot != prevSlot) {
        mc.thePlayer.inventory.currentItem = slot;
        this.forceSyncHeldItem(slot);
      }

      this.doSwing();
      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(Action.START_DESTROY_BLOCK, this.targetBed, facing));
      this.doSwing();
      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, this.targetBed, facing));

      mc.playerController.onPlayerDestroyBlock(this.targetBed, facing);
      mc.theWorld.playAuxSFX(
          2001, this.targetBed, Block.getStateId(mc.theWorld.getBlockState(this.targetBed)));
      mc.theWorld.setBlockToAir(this.targetBed);

      if (slot != prevSlot) {
        mc.thePlayer.inventory.currentItem = prevSlot;
        this.forceSyncHeldItem(prevSlot);
      }

      this.breakStage = 2;
    } else if (!mc.thePlayer.isUsingItem()) {

      this.doSwing();
      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(Action.START_DESTROY_BLOCK, this.targetBed, facing));
      this.doSwing();
      mc.effectRenderer.addBlockHitEffects(this.targetBed, facing);
      this.breakStage = 1;
    }
  }

  /**
   * Stage 1: Accumulate break progress, send STOP_DESTROY when threshold reached.
   *
   * <p>For SWAP mode: tool switch happens right before STOP_DESTROY_BLOCK, with explicit C09 packet
   * sync to prevent tool-mismatch rejection.
   */
  private void handleStage1(int slot, EnumFacing facing) {
    this.breaking = true;
    this.tickCounter++;

    float breakDelta =
        this.getBreakDelta(
            mc.theWorld.getBlockState(this.targetBed), this.targetBed, slot, mc.thePlayer.onGround);
    if (!mc.thePlayer.onGround && this.airMultiplier.getValue() != 1.0f) {
      breakDelta *= this.airMultiplier.getValue();
    }
    this.breakProgress += breakDelta;

    float tick = (float) this.tickCounter;
    IBlockState blockState = mc.theWorld.getBlockState(this.targetBed);
    boolean canBreak = mc.thePlayer.onGround && this.groundSpeed.getValue();
    float delta = tick * this.getBreakDelta(blockState, this.targetBed, slot, canBreak);

    mc.effectRenderer.addBlockHitEffects(this.targetBed, facing);

    float threshold = this.getBreakThreshold();
    if (this.breakProgress >= threshold || delta >= threshold) {
      if (this.mode.getValue() == 1) {

        int prevSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        this.syncHeldItem();

        this.forceSyncHeldItem(slot);

        if (mc.thePlayer.isUsingItem()) {
          mc.thePlayer.inventory.currentItem = (mc.thePlayer.inventory.currentItem + 1) % 9;
          this.syncHeldItem();
          this.forceSyncHeldItem(mc.thePlayer.inventory.currentItem);
        }
        this.savedSlot = prevSlot;
      }

      this.breaking = false;

      PacketUtil.sendPacket(
          new C07PacketPlayerDigging(Action.STOP_DESTROY_BLOCK, this.targetBed, facing));
      this.doSwing();

      IBlockState blockState_ = mc.theWorld.getBlockState(this.targetBed);
      Block block = blockState_.getBlock();
      if (block.getMaterial() != Material.air) {
        mc.theWorld.playAuxSFX(2001, this.targetBed, Block.getStateId(blockState_));
        mc.theWorld.setBlockToAir(this.targetBed);
      }
      if (block instanceof BlockBed) {
        this.timer.reset();
      }

      this.breakStage = 2;
    }
  }

  /** Stage 2: Cleanup — restore original slot, reset state. */
  private void handleStage2() {
    this.restoreSlot();
    this.resetBreaking();
    if (this.mode.getValue() == 2) {
      this.instantDelay = 20;
    }
  }

  @EventTarget
  public void onPlayerUpdate(PlayerUpdateEvent event) {
    if (this.isEnabled()) {
      if (this.isBreaking()
          && !Myau.playerStateManager.attacking
          && !Myau.playerStateManager.digging
          && !Myau.playerStateManager.placing
          && !Myau.playerStateManager.swinging) {
        this.doSwing();
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.moveFix.getValue() == 1
          && RotationState.isActived()
          && RotationState.getPriority() == 5.0F
          && MoveUtil.isForwardPressed()) {
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
      }

      if (this.moveFix.getValue() == 2
          && RotationState.isActived()
          && RotationState.getPriority() == 5.0F) {
        MoveUtil.fixMovement(RotationState.getSmoothedYaw());
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onKnockback(KnockbackEvent event) {
    if (this.isEnabled() && !event.isCancelled() && !(event.getY() <= 0.0)) {
      if (this.ignoreVelocity.getValue() == 1 && this.targetBed != null) {
        event.setCancelled(true);
        event.setX(mc.thePlayer.motionX);
        event.setY(mc.thePlayer.motionY);
        event.setZ(mc.thePlayer.motionZ);
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (this.isEnabled()) {
      if (this.targetBed != null && (!this.isBed || !this.surroundings.getValue())) {
        if (this.showProgress.getValue() != 0) {
          HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
          float scale = hud.scale.getValue();
          String text = String.format("%d%%", (int) (this.calcProgress() * 100.0F));
          GlStateManager.pushMatrix();
          GlStateManager.scale(scale, scale, 0.0F);
          GlStateManager.disableDepth();
          GlStateManager.enableBlend();
          GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          int width = mc.fontRendererObj.getStringWidth(text);
          mc.fontRendererObj.drawString(
              text,
              (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / scale
                  - (float) width / 2.0F,
              (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 2.0F / scale,
              this.getProgressColor(this.showProgress.getValue()).getRGB() & 16777215 | -1090519040,
              hud.shadow.getValue());
          GlStateManager.disableBlend();
          GlStateManager.enableDepth();
          GlStateManager.popMatrix();
        }
      }
    }
  }

  @EventTarget(Priority.LOW)
  public void onRender3D(Render3DEvent event) {
    if (this.isEnabled() && this.targetBed != null && !mc.theWorld.isAirBlock(this.targetBed)) {
      mc.theWorld.sendBlockBreakProgress(
          mc.thePlayer.getEntityId(), this.targetBed, (int) (this.calcProgress() * 10.0F) - 1);
      if (this.showTarget.getValue() != 0) {
        BedESP bedESP = (BedESP) Myau.moduleManager.modules.get(BedESP.class);
        Color color = this.getProgressColor(this.showTarget.getValue());
        RenderUtil.enableRenderState();
        BlockPos target = this.targetBed;
        double newHeight = this.isBed ? bedESP.getBlockHeight() : 1.0;
        int r = color.getRed();
        int g = color.getBlue();
        int b = color.getGreen();
        RenderUtil.drawBlockBox(target, newHeight, r, b, g);
        RenderUtil.disableRenderState();
      }
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    this.waitingForStart = false;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!event.isCancelled()) {
      if (event.getPacket() instanceof S02PacketChat) {
        String text = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
        if (text.contains("\u00a7e\u00a7lProtect your bed and destroy the enemy bed")
            || text.contains("\u00a7e\u00a7lDestroy the enemy bed and then eliminate them")) {
          this.waitingForStart = true;
        }
      }
      if (event.getPacket() instanceof S08PacketPlayerPosLook && this.waitingForStart) {
        this.waitingForStart = false;
        this.bedWhitelist.clear();
        final int radius = this.whitelistScanRadius.getValue();
        this.scheduler.schedule(
            () -> {
              int sX = MathHelper.floor_double(mc.thePlayer.posX);
              int sY =
                  MathHelper.floor_double(mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
              int sZ = MathHelper.floor_double(mc.thePlayer.posZ);
              for (int i = sX - radius; i <= sX + radius; i++) {
                for (int j = sY - radius; j <= sY + radius; j++) {
                  for (int k = sZ - radius; k <= sZ + radius; k++) {
                    BlockPos blockPos = new BlockPos(i, j, k);
                    Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                    if (block instanceof BlockBed) {
                      this.bedWhitelist.add(blockPos);
                    }
                  }
                }
              }
              if (this.colorCheck.getValue()) {
                BlockPos trackedOwn = myau.module.modules.minigames.BedwarUtils.getTrackedOwnBed();
                if (trackedOwn != null) {
                  this.bedWhitelist.remove(trackedOwn);
                  BlockPos[] ownPair = this.resolveBedPair(trackedOwn);
                  if (ownPair != null) {
                    this.bedWhitelist.remove(ownPair[0]);
                    this.bedWhitelist.remove(ownPair[1]);
                  }
                }
              }
            },
            1L,
            TimeUnit.SECONDS);
      }
      if (this.isEnabled()
          && this.targetBed != null
          && this.ignoreVelocity.getValue() == 2
          && Myau.delayManager.getDelayModule() != DelayModules.BED_NUKER) {
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
          S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
          if (packet.getEntityID() == mc.thePlayer.getEntityId() && packet.getMotionY() > 0) {
            Myau.delayManager.delay(DelayModules.BED_NUKER);
            Myau.delayManager.delayedPacket.offer(packet);
            event.setCancelled(true);
          }
        }
        if (event.getPacket() instanceof S27PacketExplosion) {
          S27PacketExplosion explosion = (S27PacketExplosion) event.getPacket();
          if (explosion.func_149149_c() != 0.0F
              || explosion.func_149144_d() != 0.0F
              || explosion.func_149147_e() != 0.0F) {
            Myau.delayManager.delay(DelayModules.BED_NUKER);
            Myau.delayManager.delayedPacket.offer(explosion);
            event.setCancelled(true);
          }
        }
      }
    }
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) {
      if (this.isReady()
          || this.targetBed != null
              && mc.objectMouseOver != null
              && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      if (this.isReady()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) {
      if (this.isReady()
          || this.targetBed != null
              && mc.objectMouseOver != null
              && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      if (this.savedSlot != -1) {
        event.setCancelled(true);
      }
    }
  }

  @Override
  public void onDisabled() {
    this.resetBreaking();
    this.savedSlot = -1;
    this.currentHit = null;
    this.previousTarget = null;
    this.targetSwitchDelay = 0;
    this.lastSyncedSlot = -1;
    Myau.delayManager.setDelayState(false, DelayModules.BED_NUKER);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
