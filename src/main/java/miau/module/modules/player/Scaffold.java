package miau.module.modules.player;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.module.Module;
import miau.module.modules.movement.LongJump;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.module.modules.player.scaffold.features.EagleFeature;
import miau.module.modules.player.scaffold.features.KeepYFeature;
import miau.module.modules.player.scaffold.features.MultiPlaceFeature;
import miau.module.modules.player.scaffold.features.SafeWalkFeature;
import miau.module.modules.player.scaffold.features.TowerFeature;
import miau.module.modules.player.scaffold.rotations.RotationHandler;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.math.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.shader.RoundedUtils;
import miau.util.world.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

public class Scaffold extends Module {
  private final Map<BlockPos, Long> highlight = new HashMap<>();
  private float animationProgress = 0f;
  private int totalBlockCount = 0;
  private ItemStack renderItemStack = null;
  public static final Minecraft mc = Minecraft.getMinecraft();
  private static final double[] placeOffsets =
      new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375,
        0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
      };
  public int rotationTick = 0;
  public int lastSlot = -1;
  public int blockCount = -1;
  public float yaw = -180.0F;
  public float pitch = 0.0F;
  public boolean canRotate = false;
  public int towerTick = 0;
  public int towerDelay = 0;
  public int stage = 0;
  public int startY = 256;
  public boolean shouldKeepY = false;
  public boolean towering = false;
  public EnumFacing targetFacing = null;
  public int safeStuckTicks = 0;
  public int safeStuckDelayTicks = 0;
  public double safePrevMotionY = 0.0;
  public double savedMotionX;
  public double savedMotionY;
  public double savedMotionZ;
  public boolean safeStuckActive = false;
  public boolean snapRotating = false;
  public boolean placedThisTick = false;

  public float lastSnapPlaceYaw = Float.NaN;
  public float lastSnapPlacePitch = Float.NaN;
  public static class ScaffoldOptions {
    public final ModeProperty moveFix =
        new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT"});
    public final ModeProperty sprintMode =
        new ModeProperty("sprint", 0, new String[] {"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
  }
  public final ScaffoldOptions options = new ScaffoldOptions();

  private boolean shouldStopSprint() {
    if (this.isTowering()) {
      return false;
    } else {
      boolean stage =
          this.keepYFeature.keepY.getValue() == 1
              || this.keepYFeature.keepY.getValue() == 3
              || this.keepYFeature.keepY.getValue() == 4;
      return (!stage || this.stage <= 0) && this.options.sprintMode.getValue() == 0;
    }
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) {
      return false;
    } else {
      LongJump longJump = (LongJump) Miau.moduleManager.modules.get(LongJump.class);
      return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
    }
  }

  private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
    double offset = 0.0;
    EnumFacing enumFacing = null;
    for (EnumFacing facing : EnumFacing.VALUES) {
      if (facing != EnumFacing.DOWN) {
        BlockPos pos = blockPos1.offset(facing);
        if (pos.getY() <= blockPos3.getY()) {
          double distance =
              pos.distanceSqToCenter(
                  (double) blockPos3.getX() + 0.5,
                  (double) blockPos3.getY() + 0.5,
                  (double) blockPos3.getZ() + 0.5);
          if (enumFacing == null
              || distance < offset
              || distance == offset && facing == EnumFacing.UP) {
            offset = distance;
            enumFacing = facing;
          }
        }
      }
    }
    return enumFacing;
  }

  public BlockData getBlockData() {
    int startY = MathHelper.floor_double(mc.thePlayer.posY);
    BlockPos targetPos =
        new BlockPos(
            MathHelper.floor_double(mc.thePlayer.posX),
            (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
            MathHelper.floor_double(mc.thePlayer.posZ));
    if (!BlockUtil.isReplaceable(targetPos)) {
      return null;
    } else {
      ArrayList<BlockPos> positions = new ArrayList<>();
      for (int x = -4; x <= 4; x++) {
        for (int y = -4; y <= 0; y++) {
          for (int z = -4; z <= 4; z++) {
            BlockPos pos = targetPos.add(x, y, z);
            if (!BlockUtil.isReplaceable(pos)
                && !BlockUtil.isInteractable(pos)
                && !(mc.thePlayer.getDistance(
                        (double) pos.getX() + 0.5,
                        (double) pos.getY() + 0.5,
                        (double) pos.getZ() + 0.5)
                    > (double) mc.playerController.getBlockReachDistance())
                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
              for (EnumFacing facing : EnumFacing.VALUES) {
                if (facing != EnumFacing.DOWN) {
                  BlockPos blockPos = pos.offset(facing);
                  if (BlockUtil.isReplaceable(blockPos)) {
                    positions.add(pos);
                  }
                }
              }
            }
          }
        }
      }
      if (positions.isEmpty()) {
        return null;
      } else {
        positions.sort(
            Comparator.comparingDouble(
                o ->
                    o.distanceSqToCenter(
                        (double) targetPos.getX() + 0.5,
                        (double) targetPos.getY() + 0.5,
                        (double) targetPos.getZ() + 0.5)));
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
      }
    }
  }

  public void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {

    if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
      if (mc.playerController.onPlayerRightClick(
          mc.thePlayer,
          mc.theWorld,
          mc.thePlayer.inventory.getCurrentItem(),
          blockPos,
          enumFacing,
          vec3)) {
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
          this.blockCount--;
        }
        this.placedThisTick = true;
        this.highlight.put(blockPos.offset(enumFacing), System.currentTimeMillis());

        this.eagleFeature.onBlockPlaced();
        if (this.options.swing.getValue()) {
          mc.thePlayer.swingItem();
        } else {
          PacketUtil.sendPacket(new C0APacketAnimation());
        }
      }
    }
  }

  private MovingObjectPosition getPlacementMop(BlockData blockData, float yaw, float pitch) {
    MovingObjectPosition mop =
        RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
    if (mop == null
        || mop.typeOfHit != MovingObjectType.BLOCK
        || !mop.getBlockPos().equals(blockData.blockPos())
        || mop.sideHit != blockData.facing()) {
      return null;
    }
    return mop;
  }

  private boolean isDuplicateSnapRotation(float yaw, float pitch) {
    return !Float.isNaN(this.lastSnapPlaceYaw)
        && Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.lastSnapPlaceYaw)) < 0.35F;
  }

  private float[] getSnapRotation(BlockData blockData, float yaw, float pitch) {
    float baseYaw = RotationUtil.quantizeAngle(yaw);
    float basePitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(pitch, -90.0F, 90.0F));

    if (!this.isDuplicateSnapRotation(baseYaw, basePitch)) {
      return new float[] {baseYaw, basePitch};
    }

    for (int i = 0; i < 24; i++) {
      float yawStep = 0.35F + 0.075F * (float) (i / 2);
      float pitchStep = 0.025F + 0.01F * (float) (i / 3);
      float testYaw = RotationUtil.quantizeAngle(baseYaw + (i % 2 == 0 ? yawStep : -yawStep));
      float testPitch =
          RotationUtil.quantizeAngle(
              MathHelper.clamp_float(
                  basePitch + (i % 4 < 2 ? pitchStep : -pitchStep), -90.0F, 90.0F));

      if (!this.isDuplicateSnapRotation(testYaw, testPitch)
          && this.getPlacementMop(blockData, testYaw, testPitch) != null) {
        return new float[] {testYaw, testPitch};
      }
    }

    return null;
  }

  private void rememberSnapRotation() {
    this.lastSnapPlaceYaw = this.yaw;
    this.lastSnapPlacePitch = this.pitch;
  }

  private EnumFacing yawToFacing(float yaw) {
    if (yaw < -135.0F || yaw > 135.0F) {
      return EnumFacing.NORTH;
    } else if (yaw < -45.0F) {
      return EnumFacing.EAST;
    } else {
      return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
    }
  }

  private double distanceToEdge(EnumFacing enumFacing) {
    switch (enumFacing) {
      case NORTH:
        return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
      case EAST:
        return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
      case SOUTH:
        return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
      case WEST:
      default:
        return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    }
  }

  private float getSpeed() {
    if (!mc.thePlayer.onGround) {
      return (float) this.options.airMotion.getValue() / 100.0F;
    } else {
      return MoveUtil.getSpeedLevel() > 0
          ? (float) this.options.speedMotion.getValue() / 100.0F
          : (float) this.options.groundMotion.getValue() / 100.0F;
    }
  }

  private double getRandomOffset() {
    return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
  }

  private float getCurrentYaw() {
    return MoveUtil.adjustYaw(
        mc.thePlayer.rotationYaw,
        (float) MoveUtil.getForwardValue(),
        (float) MoveUtil.getLeftValue());
  }

  public boolean isDiagonal(float yaw) {
    float absYaw = Math.abs(yaw % 90.0F);
    return absYaw > 20.0F && absYaw < 70.0F;
  }

  public boolean isTowering() {
    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepY =
          this.keepYFeature.keepY.getValue() == 2 || this.keepYFeature.keepY.getValue() == 4;
      boolean tower = this.towerFeature.tower.getValue() == 2;
      return keepY && this.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
    } else {
      return false;
    }
  }

  private final List<ScaffoldComponent> components = new ArrayList<>();
  public final EagleFeature eagleFeature;
  public final KeepYFeature keepYFeature;
  public final MultiPlaceFeature multiPlaceFeature;
  public final SafeWalkFeature safeWalkFeature;
  public final TowerFeature towerFeature;
  public final RotationHandler rotationHandler;

  public Scaffold() {
    super("Scaffold", false);
    this.eagleFeature = new EagleFeature(this);
    this.keepYFeature = new KeepYFeature(this);
    this.multiPlaceFeature = new MultiPlaceFeature(this);
    this.safeWalkFeature = new SafeWalkFeature(this);
    this.towerFeature = new TowerFeature(this);
    this.rotationHandler = new RotationHandler(this);

    this.components.add(this.eagleFeature);
    this.components.add(this.keepYFeature);
    this.components.add(this.multiPlaceFeature);
    this.components.add(this.safeWalkFeature);
    this.components.add(this.towerFeature);
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    return java.util.Arrays.asList(
        rotationHandler.rotationMode,
        options.moveFix,
        options.sprintMode,
        options.groundMotion,
        options.airMotion,
        options.speedMotion,
        towerFeature.tower,
        towerFeature.hypixeltower,
        towerFeature.safe,
        towerFeature.safeStuckDelayTicksProperty,
        keepYFeature.keepY,
        keepYFeature.keepYonPress,
        keepYFeature.disableWhileJumpActive,
        multiPlaceFeature.multiplace,
        safeWalkFeature.safeWalk,
        options.swing,
        options.itemSpoof,
        options.blockCounter,
        eagleFeature.eagle,
        eagleFeature.edgeDistance,
        eagleFeature.sneakDelay,
        eagleFeature.blocksPerSneak
    );
  }

  public int getSlot() {
    return this.lastSlot;
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      // Cache block count for rendering
      int cachedCount = 0;
      ItemStack cachedStack = null;
      ItemStack held = Miau.slotComponent.getItemStack();
      if (held != null && held.getItem() instanceof ItemBlock) {
        cachedStack = held;
      }
      for (int i = 0; i < 9; i++) {
        ItemStack invStack = mc.thePlayer.inventory.getStackInSlot(i);
        if (invStack != null && invStack.stackSize > 0 && invStack.getItem() instanceof ItemBlock) {
          Block block = ((ItemBlock) invStack.getItem()).getBlock();
          if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
            cachedCount += invStack.stackSize;
            if (cachedStack == null) cachedStack = invStack;
          }
        }
      }
      this.totalBlockCount = cachedCount;
      this.renderItemStack = cachedStack;

      this.placedThisTick = false;

      if (this.safeStuckDelayTicks > 0) {
        this.safeStuckDelayTicks--;
        if (this.safeStuckDelayTicks <= 0) {
          this.safeStuckTicks = 1;
        }
      }
      if (this.safeStuckTicks > 0) {
        if (!this.safeStuckActive) {
          this.savedMotionX = mc.thePlayer.motionX;
          this.savedMotionY = mc.thePlayer.motionY;
          this.savedMotionZ = mc.thePlayer.motionZ;
          this.safeStuckActive = true;
        }
        Miau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
      } else if (this.safeStuckActive) {
        Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        mc.thePlayer.motionX = this.savedMotionX;
        mc.thePlayer.motionY = this.savedMotionY;
        mc.thePlayer.motionZ = this.savedMotionZ;
        this.safeStuckActive = false;
      }
      if (this.rotationTick > 0) {
        this.rotationTick--;
      }

      this.components.forEach(c -> c.onUpdate(event));

      if (this.canPlace()) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
        this.blockCount = Math.min(this.blockCount, count);
        if (this.blockCount <= 0) {
          int slot = mc.thePlayer.inventory.currentItem;
          if (this.blockCount == 0) {
            slot--;
          }
          for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (ItemUtil.isBlock(candidate)) {
              mc.thePlayer.inventory.currentItem = hotbarSlot;
              this.blockCount = candidate.stackSize;
              break;
            }
          }
        }
        float currentYaw = this.getCurrentYaw();
        float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
        float diagonalYaw =
            this.isDiagonal(currentYaw)
                ? yawDiffTo180
                : RotationUtil.wrapAngleDiff(
                    currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                    event.getYaw());
        boolean snapMode = false;
        this.snapRotating = false;
        if (!this.canRotate) {
          this.rotationHandler.handleInitialRotation(event, currentYaw, yawDiffTo180, diagonalYaw);
        }
        BlockData blockData = this.getBlockData();

        Vec3 hitVec = null;
        if (blockData != null) {
          double[] x = placeOffsets;
          double[] y = placeOffsets;
          double[] z = placeOffsets;
          switch (blockData.facing()) {
            case NORTH:
              z = new double[] {0.0};
              break;
            case EAST:
              x = new double[] {1.0};
              break;
            case SOUTH:
              z = new double[] {1.0};
              break;
            case WEST:
              x = new double[] {0.0};
              break;
            case DOWN:
              y = new double[] {0.0};
              break;
            case UP:
              y = new double[] {1.0};
          }
          float bestYaw = -180.0F;
          float bestPitch = 0.0F;
          float bestDiff = 0.0F;
          for (double dx : x) {
            for (double dy : y) {
              for (double dz : z) {
                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                double relY =
                    (double) blockData.blockPos().getY()
                        + dy
                        - mc.thePlayer.posY
                        - (double) mc.thePlayer.getEyeHeight();
                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                float[] rotations =
                    RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                MovingObjectPosition mop =
                    RotationUtil.rayTrace(
                        rotations[0],
                        rotations[1],
                        mc.playerController.getBlockReachDistance(),
                        1.0F);
                if (mop != null
                    && mop.typeOfHit == MovingObjectType.BLOCK
                    && mop.getBlockPos().equals(blockData.blockPos())
                    && mop.sideHit == blockData.facing()) {
                  float totalDiff =
                      Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                  if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                    bestYaw = rotations[0];
                    bestPitch = rotations[1];
                    bestDiff = totalDiff;
                    hitVec = mop.hitVec;
                  }
                }
              }
            }
          }
          if (bestYaw != -180.0F || bestPitch != 0.0F) {
            this.yaw = bestYaw;
            this.pitch = bestPitch;
            this.canRotate = true;
          }
        }
        boolean towerRotating = this.towering || this.isTowering();
        boolean snapAlreadyLooking = false;
        boolean snapCanPlace = true;
        if (snapMode && !towerRotating && blockData != null) {
          MovingObjectPosition currentMop =
              this.getPlacementMop(blockData, event.getYaw(), event.getPitch());
          if (currentMop != null) {
            float[] snapRotation =
                this.getSnapRotation(blockData, event.getYaw(), event.getPitch());
            if (snapRotation == null) {
              snapCanPlace = false;
              hitVec = null;
            } else {
              this.yaw = snapRotation[0];
              this.pitch = snapRotation[1];
              this.canRotate = true;
              MovingObjectPosition snapMop = this.getPlacementMop(blockData, this.yaw, this.pitch);
              hitVec = snapMop != null ? snapMop.hitVec : currentMop.hitVec;
              this.snapRotating = true;
              int snapDelay = 1;
              if (this.rotationTick > snapDelay) {
                this.rotationTick = snapDelay;
              }
            }
          } else if (hitVec != null && this.canRotate) {
            float[] snapRotation = this.getSnapRotation(blockData, this.yaw, this.pitch);
            if (snapRotation == null) {
              snapCanPlace = false;
              hitVec = null;
            } else {
              this.yaw = snapRotation[0];
              this.pitch = snapRotation[1];
              MovingObjectPosition snapMop = this.getPlacementMop(blockData, this.yaw, this.pitch);
              if (snapMop != null) {
                hitVec = snapMop.hitVec;
              }
              this.snapRotating = true;
              int snapDelay = 1;
              if (this.rotationTick > snapDelay) {
                this.rotationTick = snapDelay;
              }
            }
          }
        }
        if (this.canRotate
            && MoveUtil.isForwardPressed()
            && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
          switch (this.rotationHandler.rotationMode.getValue()) {
            case 2:
              this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
              break;
            case 3:
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
          }
        }
        this.rotationHandler.handleUpdateRotation(
            event, yawDiffTo180, diagonalYaw, snapMode, towerRotating);

        if (blockData != null
            && hitVec != null
            && snapCanPlace
            && (this.rotationTick <= 0 || snapAlreadyLooking)) {
          this.place(blockData.blockPos(), blockData.facing(), hitVec);
          if (snapMode) {
            this.rememberSnapRotation();
          }
        }
        if (this.targetFacing != null) {
          if (this.rotationTick <= 0 && !this.placedThisTick) {
            int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
            int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
            int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
            BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
            hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
            this.place(belowPlayer, this.targetFacing, hitVec);
          }
          this.targetFacing = null;
        } else if ((this.keepYFeature.keepY.getValue() == 3
                || this.keepYFeature.keepY.getValue() == 4)
            && this.stage > 0
            && !mc.thePlayer.onGround) {
          int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
          if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
            this.shouldKeepY = true;
            blockData = this.getBlockData();
            if (blockData != null && this.rotationTick <= 0 && !this.placedThisTick) {
              MovingObjectPosition mop = this.getPlacementMop(blockData, this.yaw, this.pitch);
              if (mop != null) {
                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
              }
            }
          }
        }
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      if (this.safeStuckTicks > 0) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        return;
      }
      this.components.forEach(c -> c.onStrafe(event));
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.safeStuckTicks > 0) {
        mc.thePlayer.movementInput.moveForward = 0.0f;
        mc.thePlayer.movementInput.moveStrafe = 0.0f;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
        return;
      }

      if (this.options.moveFix.getValue() == 1
          && RotationState.isActived()
          && RotationState.getPriority() == 3.0F
          && MoveUtil.isForwardPressed()) {
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
      }
      if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
        mc.thePlayer.movementInput.jump = true;
      }
      this.components.forEach(miau.module.modules.player.scaffold.ScaffoldComponent::onMoveInput);
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      if (this.safeStuckTicks > 0) {
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
        this.safeStuckTicks--;
      }
      float speed = this.getSpeed();
      if (speed != 1.0F) {
        if (mc.thePlayer.movementInput.moveForward != 0.0F
            && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
          mc.thePlayer.movementInput.moveForward =
              mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
          mc.thePlayer.movementInput.moveStrafe =
              mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
        }
        mc.thePlayer.movementInput.moveForward *= speed;
        mc.thePlayer.movementInput.moveStrafe *= speed;
      }
      if (this.shouldStopSprint()) {
        mc.thePlayer.setSprinting(false);
      }

      if (this.towerFeature.safe.getValue()
          && this.towerFeature.tower.getValue() == 2
          && mc.gameSettings.keyBindJump.isKeyDown()) {
        float moveYaw = this.getCurrentYaw();
        boolean diagonal = this.isDiagonal(moveYaw);
        if (diagonal && !mc.thePlayer.onGround) {
          double motionY = mc.thePlayer.motionY;
          if (this.safePrevMotionY > 0.0 && motionY <= 0.0) {
            double motionXZ =
                Math.sqrt(
                    mc.thePlayer.motionX * mc.thePlayer.motionX
                        + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
            double motionXZSpeedBps = motionXZ * 20.0;
            if (this.safeStuckDelayTicks <= 0
                && this.safeStuckTicks <= 0
                && motionXZSpeedBps >= 4.67) {
              this.safeStuckDelayTicks = this.towerFeature.safeStuckDelayTicksProperty.getValue();
            }
          }
          this.safePrevMotionY = motionY;
        } else {
          this.safePrevMotionY = mc.thePlayer.motionY;
        }
      } else {
        this.safePrevMotionY = mc.thePlayer.motionY;
      }
    }
  }

  @EventTarget
  public void onSafeWalk(SafeWalkEvent event) {
    this.components.forEach(c -> c.onSafeWalk(event));
  }

  @EventTarget
  public void onRender(Render2DEvent event) {
    if (!this.isEnabled() || !this.options.blockCounter.getValue()) return;

    ScaledResolution sr = new ScaledResolution(mc);
    float width = sr.getScaledWidth();
    float height = sr.getScaledHeight();

    this.animationProgress += ((this.isEnabled() ? 1f : 0f) - this.animationProgress) * 0.1f;

    if (this.animationProgress <= 0.01f) return;

    ItemStack itemStack = this.renderItemStack;
    int count = this.totalBlockCount;

    if (itemStack == null) return;

    String countStr = String.valueOf(count);
    float textWidth = mc.fontRendererObj.getStringWidth(countStr);
    float boxWidth = 30 + textWidth;
    float boxHeight = 20;

    float x = width / 2f + 10;
    float y = height / 2f + 10;

    GlStateManager.pushMatrix();
    GlStateManager.translate(x, y, 0);
    GlStateManager.scale(this.animationProgress, this.animationProgress, 1);
    GlStateManager.translate(-x, -y, 0);

    RoundedUtils.drawRound(x, y, boxWidth, boxHeight, 3, new java.awt.Color(0, 0, 0, 150));
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (int) x + 2, (int) y + 2);
    RenderHelper.disableStandardItemLighting();

    mc.fontRendererObj.drawString(
        countStr, (int) (x + 22), (int) (y + 6), java.awt.Color.WHITE.getRGB());

    GlStateManager.popMatrix();
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      this.lastSlot = event.setSlot(this.lastSlot);
      event.setCancelled(true);
    }
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      this.lastSlot = mc.thePlayer.inventory.currentItem;
    } else {
      this.lastSlot = -1;
    }
    this.blockCount = -1;
    this.rotationTick = 3;
    this.yaw = -180.0F;
    this.pitch = 0.0F;
    this.canRotate = false;
    this.towerTick = 0;
    this.towerDelay = 0;
    this.towering = false;
    this.safeStuckTicks = 0;
    this.safeStuckDelayTicks = 0;
    this.safePrevMotionY = 0.0;
    this.safeStuckActive = false;
    this.snapRotating = false;

    this.lastSnapPlaceYaw = Float.NaN;
    this.lastSnapPlacePitch = Float.NaN;

    this.components.forEach(miau.module.modules.player.scaffold.ScaffoldComponent::onEnable);
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null && this.lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.lastSlot;
    }
    Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    if (this.safeStuckActive && mc.thePlayer != null) {
      mc.thePlayer.motionX = this.savedMotionX;
      mc.thePlayer.motionY = this.savedMotionY;
      mc.thePlayer.motionZ = this.savedMotionZ;
    }
    this.safeStuckTicks = 0;
    this.safeStuckDelayTicks = 0;
    this.safePrevMotionY = 0.0;
    this.safeStuckActive = false;

    this.components.forEach(miau.module.modules.player.scaffold.ScaffoldComponent::onDisable);
  }

  public int getBlockCount() {
    return this.blockCount;
  }

  public static class BlockData {
    private final BlockPos blockPos;
    private final EnumFacing facing;

    public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
      this.blockPos = blockPos;
      this.facing = enumFacing;
    }

    public BlockPos blockPos() {
      return this.blockPos;
    }

    public EnumFacing facing() {
      return this.facing;
    }
  }
}
