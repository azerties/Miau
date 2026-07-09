package miau.module.modules.player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import miau.module.modules.player.scaffold.ScaffoldPlacementUtil;
import miau.module.modules.player.scaffold.ScaffoldPlacementUtil.PlacementAim;
import miau.module.modules.player.scaffold.ScaffoldUtils;
import miau.module.modules.player.scaffold.features.*;
import miau.module.modules.player.scaffold.rotations.RotationHandler;
import miau.module.modules.render.PostProcessing;
import miau.property.Property;
import miau.property.properties.*;
import miau.util.client.KeyBindUtil;
import miau.util.font.FontRepository;
import miau.util.math.RandomUtil;
import miau.util.player.*;
import miau.util.shader.RoundedUtils;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.WorldSettings.GameType;

public class Scaffold extends Module {
  public static final Minecraft mc = Minecraft.getMinecraft();
  private static final int ROTATION_SNAP = 6;
  private static final int ROTATION_BETA = 7;
  public static final double[] placeOffsets =
      new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375,
        0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
      };

  // ─── Components (must be initialized before options) ────
  public final RotationHandler rotationHandler = new RotationHandler(this);
  public final KeepYFeature keepYFeature = new KeepYFeature(this);
  public final TowerFeature towerFeature = new TowerFeature(this);
  public final SneakFeature sneakFeature = new SneakFeature(this);
  public final SafeWalkFeature safeWalkFeature = new SafeWalkFeature(this);
  public final BetaFeature betaFeature = new BetaFeature(this);
  public final MultiPlaceFeature multiPlaceFeature = new MultiPlaceFeature(this);
  public final GodbridgeFeature godbridgeFeature = new GodbridgeFeature(this);
  public final BlockSafeFeature blockSafeFeature = new BlockSafeFeature(this);
  private final List<ScaffoldComponent> components = new ArrayList<>();

  public final ScaffoldOptions options = new ScaffoldOptions();

  public class ScaffoldOptions {
    public final FloatProperty tellystartrotationminspeed =
        new FloatProperty(
            "telly-start-rotation-min-speed",
            40.0F,
            1.0F,
            180.0F,
            () ->
                keepYFeature.keepY.getValue() == 3
                    || keepYFeature.keepY.getValue() == 4
                    || keepYFeature.keepY.getValue() == 5);
    public final FloatProperty tellystartrotationmaxspeed =
        new FloatProperty(
            "telly-start-rotation-max-speed",
            95.0F,
            1.0F,
            180.0F,
            () ->
                keepYFeature.keepY.getValue() == 3
                    || keepYFeature.keepY.getValue() == 4
                    || keepYFeature.keepY.getValue() == 5);
    public final FloatProperty tellynormalrotationminspeed =
        new FloatProperty(
            "telly-normal-rotation-min-speed",
            30.0F,
            1.0F,
            180.0F,
            () ->
                keepYFeature.keepY.getValue() == 3
                    || keepYFeature.keepY.getValue() == 4
                    || keepYFeature.keepY.getValue() == 5);
    public final FloatProperty tellynormalrotationmaxspeed =
        new FloatProperty(
            "telly-normal-rotation-max-speed",
            35.0F,
            1.0F,
            180.0F,
            () ->
                keepYFeature.keepY.getValue() == 3
                    || keepYFeature.keepY.getValue() == 4
                    || keepYFeature.keepY.getValue() == 5);
    public final BooleanProperty movementCorrection =
        new BooleanProperty("movement-correction", true);
    public final ModeProperty sprintMode =
        new ModeProperty("sprint", 0, new String[] {"NONE", "VANILLA", "OFF_GROUND", "ON_GROUND"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    public final FloatProperty placeDelay = new FloatProperty("place-delay", 0.0F, 0.0F, 5.0F);

    public List<Property<?>> getProperties() {
      List<Property<?>> list = new ArrayList<>();
      list.add(tellystartrotationminspeed);
      list.add(tellystartrotationmaxspeed);
      list.add(tellynormalrotationminspeed);
      list.add(tellynormalrotationmaxspeed);
      list.add(movementCorrection);
      list.add(sprintMode);
      list.add(groundMotion);
      list.add(airMotion);
      list.add(speedMotion);
      list.add(blockCounter);
      list.add(placeDelay);
      return list;
    }
  }

  // State fields
  public int rotationTick = 0;
  public int lastSlot = -1;
  public int blockCount = -1;
  public float animationProgress = 0f;
  public long lastFrame = System.currentTimeMillis();
  public float yaw = -180.0F;
  public float pitch = 0.0F;
  public boolean canRotate = false;
  public int towerTick = 0;
  public int towerDelay = 0;
  public int stage = 0;
  public boolean shouldKeepY = false;
  public boolean placedThisTick = false;
  public boolean snapRotating = false;
  public float lastSnapPlaceYaw = Float.NaN;
  public float lastSnapPlacePitch = Float.NaN;
  public boolean towering = false;
  public EnumFacing targetFacing = null;
  public int startY = 0;
  public int safeStuckTicks = 0;
  public int safeStuckDelayTicks = 0;
  public boolean safeStuckActive = false;
  public double savedMotionX, savedMotionY, savedMotionZ;
  public double safePrevMotionY = 0.0;
  public float placeYaw, placePitch;
  public float bridgeYaw = Float.NaN;
  public float lastMoveFixPacketYaw = Float.NaN;
  public float lastPlacedAbsPacketYawDelta = Float.NaN;
  public int duplicatePlaceRotNudgeSign = 1;

  public Scaffold() {
    super("Scaffold", false);
    components.add(sneakFeature);
    components.add(keepYFeature);
    components.add(multiPlaceFeature);
    components.add(safeWalkFeature);
    components.add(godbridgeFeature);
    components.add(towerFeature);
    components.add(betaFeature);
    components.add(blockSafeFeature);
  }

  public int getSlot() {
    return this.lastSlot;
  }

  public int getBlockCount() {
    return this.blockCount;
  }

  public float getSpeed() {
    if (!mc.thePlayer.onGround) return (float) options.airMotion.getValue() / 100.0F;
    return MoveUtil.getSpeedLevel() > 0
        ? (float) options.speedMotion.getValue() / 100.0F
        : (float) options.groundMotion.getValue() / 100.0F;
  }

  public float getCurrentYaw() {
    return MoveUtil.adjustYaw(
        mc.thePlayer.rotationYaw,
        (float) MoveUtil.getForwardValue(),
        (float) MoveUtil.getLeftValue());
  }

  public boolean isTowering() {
    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepYActive =
          keepYFeature.keepY.getValue() == 3
              || keepYFeature.keepY.getValue() == 4
              || keepYFeature.keepY.getValue() == 5;
      boolean towerActive = towerFeature.tower.getValue() == 3;
      return keepYActive && this.stage > 0
          || towerActive && mc.gameSettings.keyBindJump.isKeyDown();
    }
    return false;
  }

  public boolean isRightClickHeld() {
    return mc.gameSettings != null && mc.gameSettings.keyBindUseItem.isKeyDown();
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
    LongJump longJump = (LongJump) Miau.moduleManager.modules.get(LongJump.class);
    return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
  }

  private boolean shouldStopSprint() {
    if (betaFeature.isBetaMode() && !betaFeature.isBetaTellyMode()) return true;
    if (isTowering()) return false;
    int k = keepYFeature.keepY.getValue();
    boolean stageActive = k == 1 || k == 2 || k == 3 || k == 5;
    if ((!stageActive || this.stage <= 0) && options.sprintMode.getValue() == 0) return true;
    int sprint = options.sprintMode.getValue();
    if (sprint == 2 && mc.thePlayer.onGround) return true;
    if (sprint == 3 && !mc.thePlayer.onGround) return true;
    return false;
  }

  private void applySprintMode() {
    if (shouldStopSprint()) return;
    int sprint = options.sprintMode.getValue();
    if (sprint >= 1 && sprint <= 3) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
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
              || (distance == offset && facing == EnumFacing.UP)) {
            offset = distance;
            enumFacing = facing;
          }
        }
      }
    }
    return enumFacing;
  }

  public BlockData getBlockData() {
    int sy = MathHelper.floor_double(mc.thePlayer.posY);
    BlockPos targetPos =
        new BlockPos(
            MathHelper.floor_double(mc.thePlayer.posX),
            (this.stage != 0 && !this.shouldKeepY ? Math.min(sy, this.startY) : sy) - 1,
            MathHelper.floor_double(mc.thePlayer.posZ));
    if (!BlockUtil.isReplaceable(targetPos)) return null;

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
              if (facing != EnumFacing.DOWN && BlockUtil.isReplaceable(pos.offset(facing))) {
                positions.add(pos);
                break;
              }
            }
          }
        }
      }
    }
    if (positions.isEmpty()) return null;
    positions.sort(
        Comparator.comparingDouble(
            o ->
                o.distanceSqToCenter(
                    (double) targetPos.getX() + 0.5,
                    (double) targetPos.getY() + 0.5,
                    (double) targetPos.getZ() + 0.5)));
    BlockPos bpos = positions.get(0);
    EnumFacing facing = getBestFacing(bpos, targetPos);
    return facing == null ? null : new BlockData(bpos, facing);
  }

  private MovingObjectPosition getPlacementMop(BlockData blockData, float yaw, float pitch) {
    return ScaffoldPlacementUtil.verifyPlacement(blockData, yaw, pitch);
  }

  private boolean isDuplicateSnapRotation(float yaw, float pitch) {
    return !Float.isNaN(this.lastSnapPlaceYaw)
        && Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.lastSnapPlaceYaw)) < 0.35F;
  }

  private float[] getSnapRotation(BlockData blockData, float yaw, float pitch) {
    float baseYaw = RotationUtil.quantizeAngle(yaw);
    float basePitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(pitch, -90.0F, 90.0F));
    if (!isDuplicateSnapRotation(baseYaw, basePitch)) return new float[] {baseYaw, basePitch};

    for (int i = 0; i < 24; i++) {
      float yawStep = 0.35F + 0.075F * (float) (i / 2);
      float pitchStep = 0.025F + 0.01F * (float) (i / 3);
      float testYaw = RotationUtil.quantizeAngle(baseYaw + (i % 2 == 0 ? yawStep : -yawStep));
      float testPitch =
          RotationUtil.quantizeAngle(
              MathHelper.clamp_float(
                  basePitch + (i % 4 < 2 ? pitchStep : -pitchStep), -90.0F, 90.0F));
      if (!isDuplicateSnapRotation(testYaw, testPitch)
          && getPlacementMop(blockData, testYaw, testPitch) != null)
        return new float[] {testYaw, testPitch};
    }
    return null;
  }

  private void rememberSnapRotation() {
    this.lastSnapPlaceYaw = this.yaw;
    this.lastSnapPlacePitch = this.pitch;
  }

  public void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
    if (!betaFeature.canBetaPlaceNow()) return;
    ItemStack activeItem = Miau.slotComponent.getItemStack();
    if (activeItem != null && ItemUtil.isBlock(activeItem) && this.blockCount > 0) {
      if (mc.playerController.onPlayerRightClick(
          mc.thePlayer, mc.theWorld, activeItem, blockPos, enumFacing, vec3)) {
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
        this.placedThisTick = true;
        if (betaFeature.isBetaMode()) {
          betaFeature.betaPlaceCooldown = 1;
          betaFeature.betaPlaceTicks = 0;
        }
        sneakFeature.placements--;
        for (ScaffoldComponent comp : components) comp.onBlockPlaced();
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (!isEnabled() || event.getType() != EventType.PRE) return;

    this.placedThisTick = false;
    betaFeature.onUpdate(event);
    blockSafeFeature.onUpdate(event);

    if (this.safeStuckDelayTicks > 0) {
      this.safeStuckDelayTicks--;
      if (this.safeStuckDelayTicks <= 0) this.safeStuckTicks = 1;
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

    if (this.rotationTick > 0) this.rotationTick--;

    if (mc.thePlayer.onGround) sneakFeature.ticksOnAir = 0;
    else sneakFeature.ticksOnAir++;
    sneakFeature.calculateSneaking();

    towerFeature.onUpdate(event);
    keepYFeature.onUpdate(event);

    if (!canPlace()) return;

    ItemStack stack = Miau.slotComponent.getItemStack();
    int count = (stack != null && stack.getItem() instanceof ItemBlock) ? stack.stackSize : 0;
    this.blockCount = Math.min(this.blockCount, count);
    if (this.blockCount <= 0) {
      int slot = Miau.slotComponent.getItemIndex();
      if (this.blockCount == 0) slot--;
      for (int i = slot; i > slot - 9; i--) {
        int hotbarSlot = (i % 9 + 9) % 9;
        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
        if (candidate != null && candidate.getItem() instanceof ItemBlock) {
          Miau.slotComponent.setSlot(hotbarSlot);
          this.blockCount = candidate.stackSize;
          break;
        }
      }
    }

    float currentYaw = getCurrentYaw();
    float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
    float diagonalYaw =
        ScaffoldUtils.isDiagonal(currentYaw)
            ? yawDiffTo180
            : RotationUtil.wrapAngleDiff(
                currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                event.getYaw());

    int rotMode = rotationHandler.rotationMode.getValue();
    boolean snapMode = rotMode == ROTATION_SNAP;
    boolean betaMode = rotMode == ROTATION_BETA;
    this.snapRotating = false;

    if (!this.canRotate) {
      rotationHandler.handleInitialRotation(event, currentYaw, yawDiffTo180, diagonalYaw);
    }

    BlockData blockData = getBlockData();
    Vec3 hitVec = null;

    if (blockData != null) {
      float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
      PlacementAim aim =
          ScaffoldPlacementUtil.resolveAim(blockData, baseYaw, this.pitch, placeOffsets);
      if (aim != null) {
        this.yaw = aim.yaw;
        this.pitch = aim.pitch;
        hitVec = aim.hitVec;
        this.canRotate = true;
      } else if (betaMode) {
        this.canRotate = false;
      }
    }

    boolean towerRotating = this.towering || isTowering();
    boolean snapCanPlace = true;
    if (snapMode && !towerRotating && blockData != null) {
      MovingObjectPosition currentMop =
          getPlacementMop(blockData, event.getYaw(), event.getPitch());
      if (currentMop != null) {
        float[] snapRotation = getSnapRotation(blockData, event.getYaw(), event.getPitch());
        if (snapRotation == null) {
          snapCanPlace = false;
          hitVec = null;
        } else {
          this.yaw = snapRotation[0];
          this.pitch = snapRotation[1];
          this.canRotate = true;
          MovingObjectPosition snapMop = getPlacementMop(blockData, this.yaw, this.pitch);
          hitVec = snapMop != null ? snapMop.hitVec : currentMop.hitVec;
          this.snapRotating = true;
          if (this.rotationTick > 1) this.rotationTick = 1;
        }
      } else if (hitVec != null && this.canRotate) {
        float[] snapRotation = getSnapRotation(blockData, this.yaw, this.pitch);
        if (snapRotation == null) {
          snapCanPlace = false;
          hitVec = null;
        } else {
          this.yaw = snapRotation[0];
          this.pitch = snapRotation[1];
          MovingObjectPosition snapMop = getPlacementMop(blockData, this.yaw, this.pitch);
          if (snapMop != null) hitVec = snapMop.hitVec;
          this.snapRotating = true;
          if (this.rotationTick > 1) this.rotationTick = 1;
        }
      }
    }

    if (this.canRotate
        && MoveUtil.isForwardPressed()
        && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F
        && blockData != null
        && (rotMode == 2 || rotMode == 3)) {
      float styleYaw = rotMode == 2 ? yawDiffTo180 : diagonalYaw;
      float[] bridgeGcd =
          RotationUtil.flexRotation(styleYaw, this.pitch, event.getYaw(), event.getPitch());
      this.bridgeYaw = bridgeGcd[0];
      this.yaw = bridgeGcd[0];
      this.pitch = bridgeGcd[1];
      PlacementAim styled =
          ScaffoldPlacementUtil.resolveAim(blockData, bridgeGcd[0], bridgeGcd[1], placeOffsets);
      if (styled == null) {
        styled =
            ScaffoldPlacementUtil.resolveAim(
                blockData, event.getYaw(), event.getPitch(), placeOffsets);
      }
      if (styled != null) {
        this.yaw = styled.yaw;
        this.pitch = styled.pitch;
        this.placePitch = styled.pitch;
        hitVec = styled.hitVec;
      } else {
        hitVec = null;
      }
    }

    // Beta non-telly: apply godbridge rotation for automatic godbridging
    if (betaMode && !betaFeature.isBetaTellyMode() && this.canRotate && blockData != null) {
      if (MoveUtil.isForwardPressed()) {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        float gYaw = getGodbridgeYaw(forward, strafe, mc.thePlayer.rotationYaw);
        float styleYaw = RotationUtil.quantizeAngle(gYaw);
        float stylePitch = RotationUtil.quantizeAngle(75.0F);
        PlacementAim betaAim =
            ScaffoldPlacementUtil.resolveAim(blockData, styleYaw, stylePitch, placeOffsets);
        if (betaAim != null) {
          this.yaw = betaAim.yaw;
          this.pitch = betaAim.pitch;
          hitVec = betaAim.hitVec;
        } else {
          this.yaw = styleYaw;
          this.pitch = stylePitch;
        }
      }
    }

    boolean willPlaceThisTick =
        blockData != null
            && hitVec != null
            && snapCanPlace
            && this.rotationTick <= 0
            && this.sneakFeature.ticksOnAir
                >= RandomUtil.nextFloat(
                    options.placeDelay.getValue(), options.placeDelay.getSecondValue());
    rotationHandler.handleUpdateRotation(
        event, yawDiffTo180, diagonalYaw, snapMode, towerRotating, willPlaceThisTick);

    if (betaMode && blockData != null && hitVec != null) {
      MovingObjectPosition verifiedMop =
          getPlacementMop(
              blockData, Float.isNaN(this.bridgeYaw) ? this.yaw : this.bridgeYaw, this.placePitch);
      if (verifiedMop == null) {
        verifiedMop = getPlacementMop(blockData, this.yaw, this.pitch);
      }
      hitVec = verifiedMop != null ? verifiedMop.hitVec : null;
    }

    if (willPlaceThisTick) {
      place(blockData.blockPos, blockData.facing, hitVec);
      if (snapMode) rememberSnapRotation();

      if (multiPlaceFeature.multiplace.getValue() && !snapMode) {
        for (int i = 0; i < 3; i++) {
          blockData = getBlockData();
          if (blockData == null) break;
          MovingObjectPosition mop = getPlacementMop(blockData, this.yaw, this.pitch);
          if (mop != null) {
            place(blockData.blockPos, blockData.facing, mop.hitVec);
          } else {
            hitVec = BlockUtil.getClickVec(blockData.blockPos, blockData.facing);
            double dx = hitVec.xCoord - mc.thePlayer.posX;
            double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
            double dz = hitVec.zCoord - mc.thePlayer.posZ;
            float[] rots =
                RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
            if (!(Math.abs(rots[0] - this.yaw) < 120.0F)
                || !(Math.abs(rots[1] - this.pitch) < 60.0F)) break;
            mop = getPlacementMop(blockData, rots[0], rots[1]);
            if (mop == null) break;
            place(blockData.blockPos, blockData.facing, mop.hitVec);
          }
        }
      }
    }

    if (this.targetFacing != null) {
      if (betaMode) this.targetFacing = null;
      else if (this.rotationTick <= 0 && !this.placedThisTick) {
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos belowPlayer = new BlockPos(px, py - 1, pz);
        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
        place(belowPlayer, this.targetFacing, hitVec);
      }
      this.targetFacing = null;
    } else if ((keepYFeature.keepY.getValue() == 2
            || keepYFeature.keepY.getValue() == 3
            || keepYFeature.keepY.getValue() == 5)
        && this.stage > 0
        && !mc.thePlayer.onGround) {
      int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
      if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
        this.shouldKeepY = true;
        if (keepYFeature.keepY.getValue() != 5) {
          blockData = getBlockData();
          if (blockData != null && this.rotationTick <= 0 && !this.placedThisTick) {
            MovingObjectPosition mop = getPlacementMop(blockData, this.yaw, this.pitch);
            if (mop != null) place(blockData.blockPos, blockData.facing, mop.hitVec);
          }
        }
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (!isEnabled()) return;
    if (this.safeStuckTicks > 0) {
      event.setForward(0.0F);
      event.setStrafe(0.0F);
      return;
    }
    if (betaFeature.isBetaMode() && !betaFeature.isBetaTellyMode()) {
      this.towerTick = 0;
      this.towerDelay = 0;
      if (!(keepYFeature.keepY.getValue() == 3
          || keepYFeature.keepY.getValue() == 4
          || keepYFeature.keepY.getValue() == 5)) return;
    }
    towerFeature.onStrafe(event);
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!isEnabled()) return;
    if (this.safeStuckTicks > 0) {
      mc.thePlayer.movementInput.moveForward = 0.0f;
      mc.thePlayer.movementInput.moveStrafe = 0.0f;
      mc.thePlayer.movementInput.jump = false;
      mc.thePlayer.movementInput.sneak = false;
      return;
    }
    betaFeature.onMoveInput(event);
    godbridgeFeature.onMoveInput(event);
    boolean applyMoveFix = options.moveFix.getValue() == 1 || options.movementCorrection.getValue();
    if (applyMoveFix
        && RotationState.isActived()
        && RotationState.getPriority() == 3.0F
        && MoveUtil.isForwardPressed()) {
      float strafeRef =
          Float.isNaN(this.lastMoveFixPacketYaw)
              ? RotationState.getSmoothedYaw()
              : this.lastMoveFixPacketYaw;
      MoveUtil.fixStrafe(strafeRef);
    }
    if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
      mc.thePlayer.movementInput.jump = true;
    }
    if (sneakFeature.slow-- > 0) {
      mc.thePlayer.movementInput.moveForward = 0.0F;
      mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!isEnabled()) return;
    if (this.safeStuckTicks > 0) {
      mc.thePlayer.motionX = 0.0;
      mc.thePlayer.motionY = 0.0;
      mc.thePlayer.motionZ = 0.0;
      this.safeStuckTicks--;
    }
    betaFeature.onLivingUpdate(event);

    float speed = betaFeature.isBetaMode() && !betaFeature.isBetaTellyMode() ? 1.0F : getSpeed();
    if (speed != 1.0F) {
      if (mc.thePlayer.movementInput.moveForward != 0.0F
          && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
        mc.thePlayer.movementInput.moveForward *= (1.0F / (float) Math.sqrt(2.0));
        mc.thePlayer.movementInput.moveStrafe *= (1.0F / (float) Math.sqrt(2.0));
      }
      mc.thePlayer.movementInput.moveForward *= speed;
      mc.thePlayer.movementInput.moveStrafe *= speed;
    }
    if (shouldStopSprint()) mc.thePlayer.setSprinting(false);
    else applySprintMode();
    towerFeature.updateSafeStuck();
  }

  @EventTarget
  public void onSafeWalk(SafeWalkEvent event) {
    safeWalkFeature.onSafeWalk(event);
  }

  @EventTarget
  public void onRender(Render2DEvent event) {
    if (mc.thePlayer == null) return;

    long currentFrame = System.currentTimeMillis();
    float delta = (currentFrame - lastFrame) / 1000f;
    lastFrame = currentFrame;

    boolean shouldShow = this.isEnabled() && options.blockCounter.getValue();

    float target = shouldShow ? 1f : 0f;
    animationProgress += (target - animationProgress) * 12f * delta;
    animationProgress = Math.max(0f, Math.min(1f, animationProgress));

    if (animationProgress <= 0.01f) return;

    ItemStack itemStack = null;
    int count = 0;
    ItemStack held = Miau.slotComponent.getItemStack();
    if (held != null && held.getItem() instanceof ItemBlock) {
      itemStack = held;
    }

    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.stackSize > 0) {
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
          Block block = ((ItemBlock) item).getBlock();
          if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
            count += stack.stackSize;
            if (itemStack == null) {
              itemStack = stack;
            }
          }
        }
      }
    }

    if (itemStack == null) return;

    ScaledResolution sr = new ScaledResolution(mc);
    String amount = String.valueOf(count);
    String info = "Blocks: " + amount;

    float textWidth = FontRepository.getHudFont(18).width(info);
    float width = 16f + 8f + textWidth + 8f;
    float height = 22f;
    float x = (sr.getScaledWidth() - width) / 2f;
    float y = sr.getScaledHeight() - 90f;

    GlStateManager.pushMatrix();

    float centerX = x + width / 2f;
    float centerY = y + height / 2f;
    GlStateManager.translate(centerX, centerY, 0);
    GlStateManager.scale(animationProgress, animationProgress, 1f);
    GlStateManager.translate(-centerX, -centerY, 0);

    PostProcessing postProcessing =
        (PostProcessing) Miau.moduleManager.modules.get(PostProcessing.class);
    boolean shaders =
        postProcessing != null
            && postProcessing.isEnabled()
            && (postProcessing.blur.getValue() || postProcessing.bloom.getValue());

    if (shaders) {
      if (postProcessing.bloom.getValue()) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.scale(animationProgress, animationProgress, 1f);
        GlStateManager.translate(-centerX, -centerY, 0);
        RoundedUtils.drawRound(x - 1, y - 1, width + 2, height + 2, 4f, new Color(81, 99, 149, 80));
        GlStateManager.popMatrix();
      }

      if (postProcessing.blur.getValue()) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.scale(animationProgress, animationProgress, 1f);
        GlStateManager.translate(-centerX, -centerY, 0);
        RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, 150));
        GlStateManager.popMatrix();
      }
    }

    int bgAlpha = (int) (150 * animationProgress);
    RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, bgAlpha));

    GlStateManager.pushMatrix();
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (int) x + 4, (int) y + 3);
    RenderHelper.disableStandardItemLighting();
    GlStateManager.popMatrix();

    GlStateManager.enableBlend();
    int textAlpha = (int) (255 * animationProgress);
    float fontY = y + (height / 2f) - (FontRepository.getHudFont(18).height() / 2f);
    float textX = x + 24f;

    FontRepository.getHudFont(18)
        .drawWithShadow(info, textX, fontY, new Color(255, 255, 255, textAlpha).getRGB());

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
      this.lastSlot = Miau.slotComponent.getItemIndex();
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
    this.sneakFeature.sneakingTicks = -1;
    this.sneakFeature.placements = 0;
    this.sneakFeature.pause = 0;
    this.sneakFeature.slow = 0;
    this.sneakFeature.ticksOnAir = 0;
    this.snapRotating = false;
    this.betaFeature.betaAirTicks = 0;
    this.betaFeature.betaGroundTicks = 0;
    this.betaFeature.betaPlaceCooldown = 0;
    this.lastSnapPlaceYaw = Float.NaN;
    this.lastSnapPlacePitch = Float.NaN;
    this.lastPlacedAbsPacketYawDelta = Float.NaN;
    this.duplicatePlaceRotNudgeSign = 1;
    this.betaFeature.lastBetaSentYaw = Float.NaN;
    this.betaFeature.lastBetaSentPitch = Float.NaN;
    this.betaFeature.lastBetaPitchQuotient = 0L;
    this.betaFeature.betaPlaceTicks = 999;
    for (ScaffoldComponent comp : components) comp.onEnable();
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
    this.sneakFeature.sneakingTicks = -1;
    this.sneakFeature.placements = 0;
    this.sneakFeature.pause = 0;
    this.sneakFeature.slow = 0;
    this.sneakFeature.ticksOnAir = 0;
    this.betaFeature.betaAirTicks = 0;
    this.betaFeature.betaGroundTicks = 0;
    this.betaFeature.betaPlaceCooldown = 0;
    this.betaFeature.lastBetaSentYaw = Float.NaN;
    this.betaFeature.lastBetaSentPitch = Float.NaN;
    this.betaFeature.lastBetaPitchQuotient = 0L;
    this.betaFeature.betaPlaceTicks = 999;
    this.lastPlacedAbsPacketYawDelta = Float.NaN;
    this.duplicatePlaceRotNudgeSign = 1;
    for (ScaffoldComponent comp : components) comp.onDisable();

    // Reset sneak key state when disabling
    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    props.addAll(rotationHandler.getProperties());
    props.addAll(options.getProperties());
    for (ScaffoldComponent comp : components) {
      props.addAll(comp.getProperties());
    }
    return props;
  }

  public static class BlockData {
    public final BlockPos blockPos;
    public final EnumFacing facing;

    public BlockData(BlockPos blockPos, EnumFacing facing) {
      this.blockPos = blockPos;
      this.facing = facing;
    }
  }

  private float getGodbridgeYaw(float forward, float strafe, float playerYaw) {
    if (forward == 0 && strafe == 0) {
      float axisMovement = (float) Math.floor(playerYaw / 90.0f) * 90.0f;
      return RotationUtil.quantizeAngle(axisMovement + 45.0f);
    }
    float direction = getMovementDirection(forward, strafe, playerYaw) + 180.0f;
    float movingYaw = Math.round(direction / 45.0f) * 45.0f;
    boolean isMovingStraight = (movingYaw % 90.0f) == 0.0f;
    if (!isMovingStraight) return movingYaw;

    float finalYaw = movingYaw + 45.0f;
    return RotationUtil.quantizeAngle(finalYaw);
  }

  private float getMovementDirection(float forward, float strafe, float yaw) {
    if (forward == 0 && strafe == 0) return yaw;
    boolean reversed = forward < 0.0f;
    float strafingYaw = 90.0f * (forward > 0.0f ? 0.5f : reversed ? -0.5f : 1.0f);
    if (reversed) yaw += 180.0f;
    if (strafe > 0.0f) yaw -= strafingYaw;
    else if (strafe < 0.0f) yaw += strafingYaw;
    return yaw;
  }
}
