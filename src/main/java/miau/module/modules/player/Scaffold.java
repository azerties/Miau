package miau.module.modules.player;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import miau.Miau;
import miau.component.BadPacketsComponent;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorKeyBinding;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.module.modules.movement.LongJump;
import miau.module.modules.render.HUD;
import miau.property.properties.*;
import miau.util.font.FontRepository;
import miau.util.math.RandomUtil;
import miau.util.network.PacketUtil;
import miau.util.player.*;
import miau.util.shader.BlurUtils;
import miau.util.shader.RoundedUtils;
import miau.util.time.TimerUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class Scaffold extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private int rotationTick = 0;
  private int lastSlot = -1;
  private int blockCount = -1;
  private float yaw = -180.0F;
  private float pitch = 0.0F;
  private boolean canRotate = false;
  private int towerTick = 0;
  private int towerDelay = 0;
  private int stage = 0;
  private int startY = 256;
  private boolean shouldKeepY = false;
  private boolean towering = false;
  private EnumFacing targetFacing = null;
  private float animationProgress = 0f;
  private long lastFrame = System.currentTimeMillis();
  private net.minecraft.util.MovingObjectPosition lastBlockRenderRaytrace = null;
  private final java.util.Map<net.minecraft.util.BlockPos, miau.util.time.TimerUtil>
      blockRenderHighlights = new java.util.HashMap<>();

  private float targetYaw, targetPitch;
  private float yawDrift, pitchDrift;
  private int directionalChange;
  private int sneakingTicks;
  private int pause;
  private int ticksOnAir;
  private int slow;
  private float forward, strafe;
  private EnumFacingOffset enumFacing;
  private BlockPos blockFace;
  private Vec3 targetBlock;
  private int placements;
  private int recursions;
  private int offGroundTicks;
  private int onGroundTicks;

  // Watchdog Sprint state fields
  private boolean watchdogJump = false;
  private boolean watchdogJumpHandled = false;
  private int watchdogPreviousTick = -1;
  private int watchdogPreviousBlockValue = -1;
  private int watchdogHasC08Packet = 0;
  private boolean watchdogStart2 = false;
  private boolean watchdogStart3 = false;
  private boolean watchdogStart4 = false;
  private int watchdogStartTriggerCount = 0;
  private int watchdogBlock = 0;
  private int watchdogTime = 0;
  private double watchdogSpeed = 0;
  private boolean watchdogEnable = false;
  private int watchdogTicks = 0;
  private int watchdogOngroundticks = 0;

  private int cloudTicks = 0;
  private int cloudPlacementDelay = 0;
  private boolean cloudTriggered = false;

  private final double[] placeOffsets = new double[] {0.0, 0.5, 1.0};

  public final ModeProperty rotationMode =
      new ModeProperty(
          "rotations",
          2,
          new String[] {
            "NONE", "STATIC_GOD", "POLAR", "INTAVE", "HYPIXEL", "DIRECT", "KEEP", "SNAP", "TELLY"
          });

  public final miau.module.modules.player.scaffold.rotation.RotationMode[] rotationStrategies;
  public final miau.module.modules.player.scaffold.rotation.RotationContext rotationCtx =
      new miau.module.modules.player.scaffold.rotation.RotationContext();

  public final ModeProperty sprintMode =
      new ModeProperty(
          "sprint",
          0,
          new String[] {
            "NONE",
            "VANILLA",
            "LEGIT",
            "WATCHDOG_SLOW",
            "WATCHDOG_FAST",
            "WATCHDOG_JUMP",
            "MOTION",
            "NO_PACKET",
            "PACKET_LEGIT",
            "OLD_INTAVE"
          });
  public final BooleanProperty jumpSprint =
      new BooleanProperty("jump-sprint", true, () -> this.sprintMode.getValue() == 1);
  public final BooleanProperty diaSprint =
      new BooleanProperty("dia-sprint", true, () -> this.sprintMode.getValue() == 1);

  public final ModeProperty tower =
      new ModeProperty("tower", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY", "NORMAL"});

  public final ModeProperty keepY =
      new ModeProperty("keep-y", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY"});
  public final BooleanProperty keepYonPress =
      new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
  public final BooleanProperty disableWhileJumpActive =
      new BooleanProperty("no-keep-y-on-jump-potion", false, () -> this.keepY.getValue() != 0);

  public final ModeProperty moveFix =
      new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT", "NORMAL"});
  public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
  public final ModeProperty cloudBypass =
      new ModeProperty("cloud-bypass", 0, new String[] {"OFF", "NORMAL", "STRICT"});
  public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
  public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
  public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
  public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
  public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);

  public final ModeProperty yawOffsetProp =
      new ModeProperty("yaw-offset", 0, new String[] {"0", "45", "-45"});

  public final ModeProperty rayCast =
      new ModeProperty("ray-cast", 0, new String[] {"OFF", "NORMAL", "STRICT"});
  public final BooleanProperty sneak = new BooleanProperty("sneak", false);
  public final IntProperty startSneaking =
      new IntProperty("start-sneaking", 0, 0, 5, () -> this.sneak.getValue());
  public final IntProperty stopSneaking =
      new IntProperty("stop-sneaking", 0, 0, 5, () -> this.sneak.getValue());
  public final IntProperty sneakEvery =
      new IntProperty("sneak-every", 1, 1, 10, () -> this.sneak.getValue());
  public final FloatProperty sneakingSpeed =
      new FloatProperty("sneaking-speed", 0.2F, 0.2F, 1.0F, () -> this.sneak.getValue());
  public final BooleanProperty ignoreSpeed = new BooleanProperty("ignore-speed", false);

  public final BooleanProperty swing = new BooleanProperty("swing", true);

  public final BooleanProperty blockRender = new BooleanProperty("block-render", false);
  public final ModeProperty blockRenderColorMode =
      new ModeProperty(
          "block-render-color",
          0,
          new String[] {"HUD", "CUSTOM"},
          () -> this.blockRender.getValue());
  public final ColorProperty blockRenderColor =
      new ColorProperty(
          "block-render-custom-color",
          0xFF55AAFF,
          () -> this.blockRender.getValue() && this.blockRenderColorMode.getValue() == 1);
  public final BooleanProperty blockRenderRaytrace =
      new BooleanProperty("block-render-raytrace", true, () -> this.blockRender.getValue());
  public final IntProperty blockRenderAlpha =
      new IntProperty(
          "block-render-alpha",
          200,
          0,
          255,
          () -> this.blockRender.getValue() && this.blockRenderRaytrace.getValue());
  public final BooleanProperty blockRenderOutline =
      new BooleanProperty("block-render-outline", true, () -> this.blockRender.getValue());
  public final BooleanProperty blockRenderShade =
      new BooleanProperty("block-render-shade", false, () -> this.blockRender.getValue());

  public Scaffold() {
    super("Scaffold", false);
    this.rotationStrategies =
        new miau.module.modules.player.scaffold.rotation.RotationMode[] {
          null, // NONE (index 0)
          new miau.module.modules.player.scaffold.rotation.StaticGodMode(), // STATIC_GOD (1)
          new miau.module.modules.player.scaffold.rotation.PolarMode(), // POLAR (2)
          new miau.module.modules.player.scaffold.rotation.IntaveMode(), // INTAVE (3)
          new miau.module.modules.player.scaffold.rotation.HypixelMode(), // HYPIXEL (4)
          new miau.module.modules.player.scaffold.rotation.DirectMode(), // DIRECT (5)
          new miau.module.modules.player.scaffold.rotation.KeepMode(), // KEEP (6)
          new miau.module.modules.player.scaffold.rotation.SnapMode(), // SNAP (7)
          new miau.module.modules.player.scaffold.rotation.TellyMode(), // TELLY (8)
        };
  }

  public int getSlot() {
    return Miau.slotComponent.getItemIndex();
  }

  private boolean shouldStopSprint() {
    if (this.isTowering()) return false;
    boolean keepYActive = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
    if (keepYActive && this.stage > 0) return false;

    int sprint = this.sprintMode.getValue();
    switch (sprint) {
      case 0:
        return true;
      case 1:
        return mc.thePlayer.onGround
            ? !this.jumpSprint.getValue()
            : !(this.diaSprint.getValue() && this.isDiagonal(this.getCurrentYaw()));
      case 2:
      case 3: // WATCHDOG_SLOW
      case 4: // WATCHDOG_FAST
      case 5: // WATCHDOG_JUMP
      case 6: // MOTION
      case 7: // NO_PACKET
      case 8: // PACKET_LEGIT
      case 9: // OLD_INTAVE
        return false;
      default:
        return false;
    }
  }

  private boolean isCloudReady() {
    int bypass = this.cloudBypass.getValue();
    if (bypass == 0) return true; // OFF

    if (mc.thePlayer.onGround || !PlayerUtil.isAirBelow()) return true;

    // In air — apply cloud delay
    int requiredDelay;
    double skipChance;
    if (bypass == 1) { // NORMAL
      requiredDelay = 2 + RandomUtil.nextInt(0, 3); // 2-4 ticks
      skipChance = 0.2;
    } else { // STRICT
      requiredDelay = 5 + RandomUtil.nextInt(0, 5); // 5-9 ticks
      skipChance = 0.4;
    }

    // Initialize delay on first cloud tick
    if (!this.cloudTriggered) {
      this.cloudTriggered = true;
      this.cloudPlacementDelay = requiredDelay;
    }

    // Random skip to avoid pattern
    if (Math.random() < skipChance) {
      return false;
    }

    return this.cloudTicks >= this.cloudPlacementDelay;
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) miau.Miau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
    LongJump longJump = (LongJump) miau.Miau.moduleManager.modules.get(LongJump.class);
    return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
  }

  private BlockData getBlockData() {
    List<BlockData> blocks = findBlocks(0, 0);
    if (blocks == null || blocks.isEmpty()) return null;
    for (BlockData data : blocks) {
      if (this.stage == 0 || this.shouldKeepY || data.blockPos().getY() < this.startY) {
        return data;
      }
    }
    return null;
  }

  private List<BlockData> findBlocks(int yOffset, int xOffset) {
    int sy = MathHelper.floor_double(mc.thePlayer.posY);
    int baseY = (this.stage != 0 && !this.shouldKeepY ? Math.min(sy, this.startY) : sy) + yOffset;
    int x = MathHelper.floor_double(mc.thePlayer.posX + xOffset);
    int z = MathHelper.floor_double(mc.thePlayer.posZ);

    BlockPos base = new BlockPos(x, baseY - 1, z);
    if (!BlockUtil.isReplaceable(base)) return null;

    EnumFacing[] allFacings = getFacingsSorted();
    List<EnumFacing> validFacings = new ArrayList<>(5);
    for (EnumFacing facing : allFacings) {
      if (facing != EnumFacing.UP && placeConditions(facing, yOffset, xOffset)) {
        validFacings.add(facing);
      }
    }

    int maxYLayer = 2;
    List<BlockData> possibleBlocks = new ArrayList<>();

    for (int dy = 1; dy <= maxYLayer; dy++) {
      BlockPos layerBase = new BlockPos(x, baseY - dy, z);
      if (dy == 1) {
        for (EnumFacing facing : validFacings) {
          BlockPos neighbor = layerBase.offset(facing);
          if (!BlockUtil.isReplaceable(neighbor)
              && !BlockUtil.isInteractable(BlockUtil.getBlock(neighbor))) {
            possibleBlocks.add(new BlockData(neighbor, facing.getOpposite()));
          }
        }
      }
      for (EnumFacing facing : validFacings) {
        BlockPos adjacent = layerBase.offset(facing);
        if (BlockUtil.isReplaceable(adjacent)) {
          for (EnumFacing nestedFacing : validFacings) {
            BlockPos nestedNeighbor = adjacent.offset(nestedFacing);
            if (!BlockUtil.isReplaceable(nestedNeighbor)
                && !BlockUtil.isInteractable(BlockUtil.getBlock(nestedNeighbor))) {
              possibleBlocks.add(new BlockData(nestedNeighbor, nestedFacing.getOpposite()));
            }
          }
        }
      }
      for (EnumFacing facing : validFacings) {
        BlockPos adjacent = layerBase.offset(facing);
        if (BlockUtil.isReplaceable(adjacent)) {
          for (EnumFacing nestedFacing : validFacings) {
            BlockPos nestedNeighbor = adjacent.offset(nestedFacing);
            if (BlockUtil.isReplaceable(nestedNeighbor)) {
              for (EnumFacing thirdFacing : validFacings) {
                BlockPos thirdNeighbor = nestedNeighbor.offset(thirdFacing);
                if (!BlockUtil.isReplaceable(thirdNeighbor)
                    && !BlockUtil.isInteractable(BlockUtil.getBlock(thirdNeighbor))) {
                  possibleBlocks.add(new BlockData(thirdNeighbor, thirdFacing.getOpposite()));
                }
              }
            }
          }
        }
      }
    }

    return possibleBlocks.isEmpty() ? null : possibleBlocks;
  }

  private EnumFacing[] getFacingsSorted() {
    float currentYaw = this.getCurrentYaw();
    EnumFacing lastFacing =
        EnumFacing.getHorizontal(MathHelper.floor_double((currentYaw * 4.0F / 360.0F) + 0.5D) & 3);

    EnumFacing perpClockwise = lastFacing.rotateY();
    EnumFacing perpCounterClockwise = lastFacing.rotateYCCW();
    EnumFacing opposite = lastFacing.getOpposite();

    float yaw = currentYaw % 360;
    if (yaw > 180) yaw -= 360;
    else if (yaw < -180) yaw += 360;

    float diffClockwise =
        Math.abs(MathHelper.wrapAngleTo180_float(yaw - getFacingAngle(perpClockwise)));
    float diffCounterClockwise =
        Math.abs(MathHelper.wrapAngleTo180_float(yaw - getFacingAngle(perpCounterClockwise)));

    EnumFacing firstPerp, secondPerp;
    if (diffClockwise <= diffCounterClockwise) {
      firstPerp = perpClockwise;
      secondPerp = perpCounterClockwise;
    } else {
      firstPerp = perpCounterClockwise;
      secondPerp = perpClockwise;
    }

    return new EnumFacing[] {
      EnumFacing.UP, EnumFacing.DOWN, lastFacing, firstPerp, secondPerp, opposite
    };
  }

  private float getFacingAngle(EnumFacing facing) {
    switch (facing) {
      case WEST:
        return 90;
      case NORTH:
        return 180;
      case EAST:
        return -90;
      default:
        return 0;
    }
  }

  private boolean placeConditions(EnumFacing enumFacing, int yCondition, int xCondition) {
    if (xCondition == -1) return enumFacing == EnumFacing.EAST;
    if (yCondition == 1) return enumFacing == EnumFacing.DOWN;
    return true;
  }

  private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
    ItemStack activeItem = Miau.slotComponent.getItemStack();
    if (activeItem != null && ItemUtil.isBlock(activeItem) && this.blockCount > 0) {
      if (mc.playerController.onPlayerRightClick(
          mc.thePlayer, mc.theWorld, activeItem, blockPos, enumFacing, vec3)) {
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
        if (this.swing.getValue()) {
          mc.thePlayer.swingItem();
        } else {
          PacketUtil.sendPacket(new C0APacketAnimation());
        }
        if (this.blockRender.getValue()) {
          TimerUtil timer = new TimerUtil();
          timer.reset();
          this.blockRenderHighlights.put(blockPos.offset(enumFacing), timer);
          this.lastBlockRenderRaytrace = new MovingObjectPosition(vec3, enumFacing, blockPos);
        }
      }
    }
  }

  private EnumFacing yawToFacing(float yaw) {
    if (yaw < -135.0F || yaw > 135.0F) return EnumFacing.NORTH;
    if (yaw < -45.0F) return EnumFacing.EAST;
    return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
  }

  private double distanceToEdge(EnumFacing enumFacing) {
    switch (enumFacing) {
      case NORTH:
        return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
      case EAST:
        return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
      case SOUTH:
        return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
      default:
        return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    }
  }

  private float getSpeed() {
    if (!mc.thePlayer.onGround) return (float) this.airMotion.getValue() / 100.0F;
    return MoveUtil.getSpeedLevel() > 0
        ? (float) this.speedMotion.getValue() / 100.0F
        : (float) this.groundMotion.getValue() / 100.0F;
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

  private boolean isDiagonal(float yaw) {
    float absYaw = Math.abs(yaw % 90.0F);
    return absYaw > 20.0F && absYaw < 70.0F;
  }

  private boolean isTowering() {
    // When Watchdog modes are active, they handle their own Y-control - skip tower/keepY checks
    int sprint = this.sprintMode.getValue();
    if (sprint >= 3) return false;

    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepYTelly = this.keepY.getValue() == 3;
      boolean towerTelly = this.tower.getValue() == 3;
      return (keepYTelly && this.stage > 0)
          || (towerTelly && mc.gameSettings.keyBindJump.isKeyDown());
    }
    return false;
  }

  private int findBlock() {

    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.stackSize > 0 && ItemUtil.isBlock(stack)) {
        return i;
      }
    }
    return -1;
  }

  private void calculateSneaking() {
    if (ticksOnAir == 0) ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(false);
    this.sneakingTicks--;

    if (!this.sneak.getValue() && pause <= 0) return;

    int ahead = this.startSneaking.getValue();
    int place = 0;
    int after = this.stopSneaking.getValue();

    if (pause > 0) {
      pause--;
      sneakingTicks = 0;
      placements = 0;
    }

    if (this.sneakingTicks >= 0) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(true);
      return;
    }

    if (ticksOnAir > 0) {
      this.sneakingTicks = after;
    }

    if (ticksOnAir > 0 || PlayerUtil.isAirBelow()) {
      if (placements <= 0) {
        this.sneakingTicks = ahead + place + after;
        placements = this.sneakEvery.getValue();
      }
    }
  }

  /** Rise's getRotations - finds valid rotations by scanning at 45-degree offsets. */
  private void getRotations(final int yawOffset) {
    double difference =
        mc.thePlayer.posY
            + mc.thePlayer.getEyeHeight()
            - targetBlock.yCoord
            - 0.5
            - (Math.random() - 0.5) * 0.1;

    for (int offset = -180 + yawOffset; offset <= 180; offset += 45) {
      mc.thePlayer.setPosition(
          mc.thePlayer.posX, mc.thePlayer.posY - difference, mc.thePlayer.posZ);
      MovingObjectPosition mop =
          RayCastUtil.rayCast(mc.thePlayer.rotationYaw + (offset * 3), 0, 4.5F);
      mc.thePlayer.setPosition(
          mc.thePlayer.posX, mc.thePlayer.posY + difference, mc.thePlayer.posZ);

      if (mop == null || mop.hitVec == null) return;

      float[] rot = RotationUtil.calculate(mop.hitVec);
      MovingObjectPosition check = RayCastUtil.rayCast(rot[0], rot[1], 4.5F);
      if (check != null
          && check.getBlockPos() != null
          && check.getBlockPos().equals(blockFace)
          && check.sideHit == enumFacing.getEnumFacing()) {
        targetYaw = rot[0];
        targetPitch = rot[1];
        return;
      }
    }

    float[] backup =
        RotationUtil.calculate(new Vec3(blockFace.getX(), blockFace.getY(), blockFace.getZ()));
    MovingObjectPosition checkBackup = RayCastUtil.rayCast(targetYaw, targetPitch, 4.5F);
    if (checkBackup == null
        || checkBackup.getBlockPos() == null
        || !checkBackup.getBlockPos().equals(blockFace)) {
      targetYaw = backup[0];
      targetPitch = backup[1];
    }
  }

  private boolean doesNotContainBlock(int down) {
    return mc.theWorld
        .getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - down, mc.thePlayer.posZ))
        .getBlock()
        .isReplaceable(
            mc.thePlayer.worldObj,
            new BlockPos(
                mc.thePlayer.posX, Math.floor(mc.thePlayer.posY) + down, mc.thePlayer.posZ));
  }

  @EventTarget
  public void onRender(Render2DEvent event) {
    if (mc.thePlayer == null) return;

    long currentFrame = System.currentTimeMillis();
    float delta = (currentFrame - lastFrame) / 1000f;
    lastFrame = currentFrame;

    boolean shouldShow = this.isEnabled() && this.blockCounter.getValue();

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

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    boolean shaders = hud != null && hud.shaders.getValue();

    if (shaders) {

      BlurUtils.prepareBlur();
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, centerY, 0);
      GlStateManager.scale(animationProgress, animationProgress, 1f);
      GlStateManager.translate(-centerX, -centerY, 0);
      RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, 150));
      GlStateManager.popMatrix();
      BlurUtils.blurEnd(2, 3);

      BlurUtils.prepareBloom();
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, centerY, 0);
      GlStateManager.scale(animationProgress, animationProgress, 1f);
      GlStateManager.translate(-centerX, -centerY, 0);
      RoundedUtils.drawRound(x - 1, y - 1, width + 2, height + 2, 4f, new Color(81, 99, 149, 80));
      GlStateManager.popMatrix();
      BlurUtils.bloomEnd(2, 3);
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

  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled()
        || !this.blockRender.getValue()
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    Color renderColor = this.getBlockRenderColor();
    if (this.blockRenderRaytrace.getValue()) {
      java.util.Iterator<java.util.Map.Entry<net.minecraft.util.BlockPos, TimerUtil>> iterator =
          this.blockRenderHighlights.entrySet().iterator();
      while (iterator.hasNext()) {
        java.util.Map.Entry<net.minecraft.util.BlockPos, TimerUtil> entry = iterator.next();
        long elapsed = entry.getValue().getElapsedTime();
        int alpha = 210 - (int) (210.0F * elapsed / 750.0F);
        if (alpha <= 0) {
          iterator.remove();
          continue;
        }
        this.renderScaffoldBlock(entry.getKey(), this.mergeAlpha(renderColor, alpha));
      }
      return;
    }
    MovingObjectPosition hitResult = mc.objectMouseOver;
    if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
      hitResult = this.lastBlockRenderRaytrace;
    } else if (hitResult != null) {
      this.lastBlockRenderRaytrace = hitResult;
    }
    if (hitResult == null) hitResult = this.lastBlockRenderRaytrace;
    if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
      this.renderScaffoldBlock(
          hitResult.getBlockPos(), this.mergeAlpha(renderColor, this.blockRenderAlpha.getValue()));
    }
  }

  private Color getBlockRenderColor() {
    if (this.blockRenderColorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud != null ? hud.getColor(System.currentTimeMillis()) : Color.WHITE;
    }
    return new Color(this.blockRenderColor.getValue());
  }

  private int mergeAlpha(Color color, int alpha) {
    int clampedAlpha = Math.max(0, Math.min(255, alpha));
    return (clampedAlpha << 24)
        | (color.getRed() << 16)
        | (color.getGreen() << 8)
        | color.getBlue();
  }

  private void renderScaffoldBlock(BlockPos blockPos, int color) {
    if (blockPos == null) return;
    this.renderScaffoldBox(
        blockPos.getX(),
        blockPos.getY(),
        blockPos.getZ(),
        1.0D,
        1.0D,
        1.0D,
        color,
        this.blockRenderOutline.getValue(),
        this.blockRenderShade.getValue());
  }

  private void renderScaffoldBox(
      int x,
      int y,
      int z,
      double x2,
      double y2,
      double z2,
      int color,
      boolean outline,
      boolean shade) {
    double xPos = x - mc.getRenderManager().viewerPosX;
    double yPos = y - mc.getRenderManager().viewerPosY;
    double zPos = z - mc.getRenderManager().viewerPosZ;
    GL11.glPushMatrix();
    GL11.glBlendFunc(770, 771);
    GL11.glEnable(3042);
    GL11.glLineWidth(2.0F);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    float alpha = (color >> 24 & 0xFF) / 255.0F;
    float red = (color >> 16 & 0xFF) / 255.0F;
    float green = (color >> 8 & 0xFF) / 255.0F;
    float blue = (color & 0xFF) / 255.0F;
    GL11.glColor4f(red, green, blue, alpha);
    AxisAlignedBB bb = new AxisAlignedBB(xPos, yPos, zPos, xPos + x2, yPos + y2, zPos + z2);
    if (outline) {
      RenderGlobal.drawSelectionBoundingBox(bb);
    }
    if (shade) {
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      tessellator.draw();
    }
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    GL11.glPopMatrix();
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    if (this.rotationTick > 0) this.rotationTick--;

    if (mc.thePlayer.onGround) {
      this.onGroundTicks++;
      this.offGroundTicks = 0;
    } else {
      this.offGroundTicks++;
      this.onGroundTicks = 0;
    }

    // Cloud bypass tick tracking
    if (this.cloudBypass.getValue() != 0) {
      if (!mc.thePlayer.onGround && PlayerUtil.isAirBelow()) {
        // In cloud state — increment counter
        this.cloudTicks++;
      } else {
        this.cloudTicks = 0;
        this.cloudTriggered = false;
        this.cloudPlacementDelay = 0;
      }
    }

    if (mc.thePlayer.onGround) {
      if (this.stage > 0) this.stage--;
      if (this.stage < 0) this.stage++;
      if (this.stage == 0
          && this.keepY.getValue() != 0
          && (!this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
          && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
          && !mc.gameSettings.keyBindJump.isKeyDown()
          // When Watchdog modes are active, they handle their own Y-control - skip keepY stage
          && this.sprintMode.getValue() < 3) {
        this.stage = 1;
      }
      this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
      this.shouldKeepY = false;
      this.towering = false;
    }

    if (!this.canPlace()) return;

    int sprint = this.sprintMode.getValue();

    // Watchdog Jump - ground speed multipliers + jump handling (equivalent to Rise PreMotionEvent)
    if (sprint == 5) {
      this.recursions = 1;

      // onPreMotionEvent logic
      if (event.getType() == EventType.PRE) {
        if (mc.thePlayer.onGround) {
          watchdogOngroundticks++;
        } else {
          watchdogOngroundticks = 0;
        }

        // Speed multipliers when on ground with no jump
        if (this.onGroundTicks > 2 && !mc.gameSettings.keyBindJump.isKeyDown()) {
          MoveUtil.strafe();
          if (!mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            mc.thePlayer.motionZ *= 1.129;
            mc.thePlayer.motionX *= 1.129;
          } else if (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1 >= 2) {
            mc.thePlayer.motionZ *= 1.143;
            mc.thePlayer.motionX *= 1.143;
          } else {
            mc.thePlayer.motionZ *= 1.131;
            mc.thePlayer.motionX *= 1.131;
          }
        }

        // C08 ice packet spoofing
        if (watchdogStart3 && MoveUtil.isMoving() && Math.random() > 0.5) {
          java.util.Random random = new java.util.Random();
          float hitX = random.nextFloat();
          float hitZ = random.nextFloat();
          PacketUtil.sendPacket(
              new C08PacketPlayerBlockPlacement(
                  new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                  EnumFacing.UP.getIndex(),
                  new ItemStack(Blocks.ice),
                  hitX,
                  1.0F,
                  hitZ));
          watchdogStart3 = false;
        }

        // Previous tick tracking
        if (watchdogPreviousTick != -1 && mc.thePlayer.ticksExisted - watchdogPreviousTick >= 4) {
          watchdogPreviousBlockValue = watchdogBlock;
        } else if (watchdogPreviousTick == -1) {
          watchdogPreviousTick = mc.thePlayer.ticksExisted;
          watchdogPreviousBlockValue = watchdogBlock;
        }

        // Ground strafe
        if (mc.thePlayer.onGround) {
          MoveUtil.strafe();
        }

        // PosY offset
        if (mc.thePlayer.onGround) {
          event.setRotation(event.getNewYaw(), event.getNewPitch(), 3);
        }

        // Jump release handling
        if (watchdogJump && !mc.gameSettings.keyBindJump.isKeyDown()) {
          MoveUtil.stop();
          watchdogJump = false;
          // Set rotations to 90 pitch for scaffold placement
          float newYaw = mc.thePlayer.rotationYaw + (float) ((Math.random() - 0.5) * 3);
          event.setRotation(newYaw, 90.0F, 3);
          if (!(PlayerUtil.block(mc.thePlayer.posX, mc.thePlayer.prevPosY, mc.thePlayer.posY)
              instanceof BlockAir)) {
            this.startY = (int) mc.thePlayer.posY - 1;
          }
        }

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
          watchdogJump = true;
        }
      }
    }

    // Watchdog Fast - enable state tracking
    if (sprint == 4) {
      if (mc.thePlayer.onGround && !watchdogEnable && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.stop();
        watchdogEnable = true;
      }
      if (mc.thePlayer.onGround) {
        event.setRotation(event.getNewYaw(), event.getNewPitch(), 3);
      }
    }

    // Watchdog Jump - PreUpdate logic: auto-jump, block finding, safe walk
    if (sprint == 5) {
      boolean start = mc.thePlayer.posY <= this.startY + 1;

      // Block finding when above startY and about to land
      if (Miau.slotComponent.getItemStack() != null
          && mc.thePlayer.posY > this.startY
          && mc.thePlayer.posY + MoveUtil.predictedMotion(mc.thePlayer.motionY, 2)
              < this.startY + 1) {
        int blockSlot2 = SlotUtil.findBlock();
        if (blockSlot2 != -1) Miau.slotComponent.setSlot(blockSlot2);
      }

      // Jump trigger when on ground
      if (!start && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.strafe(MoveUtil.getbaseMoveSpeed() * 0.9);
        mc.thePlayer.jump();
      }

      // Auto-jump handling (sameY equivalent)
      if (this.offGroundTicks == 1 && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.strafe();
      }

      // Safe walk control
      // Safe walk control handled via SafeWalkEvent

      // Jump key pressed - initial jump trigger
      if (mc.gameSettings.keyBindJump.isPressed() && mc.thePlayer.onGround && watchdogStart2) {
        watchdogStart2 = false;
        MoveUtil.strafe(MoveUtil.getbaseMoveSpeed() * 0.9);
      }

      // Slow down when falling
      if (start && !mc.gameSettings.keyBindJump.isKeyDown() && !mc.thePlayer.onGround) {
        MoveUtil.stop();
      }

      // Start trigger tracking
      if (start && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
        watchdogStartTriggerCount++;
      }
      if (!start) {
        watchdogStartTriggerCount = 0;
      }
    }

    int blockSlot = this.findBlock();
    if (blockSlot != -1) Miau.slotComponent.setSlot(blockSlot);
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

    float currentYaw = this.getCurrentYaw();
    float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
    float diagonalYaw =
        this.isDiagonal(currentYaw)
            ? yawDiffTo180
            : RotationUtil.wrapAngleDiff(
                currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                event.getYaw());

    targetYaw = this.yaw;
    targetPitch = this.pitch;
    float snappedYaw = 0;

    int mode = this.rotationMode.getValue();

    // ====== ROTATION COMPUTATION (Strategy) ======
    if (mode >= 1) {
      rotationCtx.blockPos = blockFace;
      rotationCtx.facing = enumFacing != null ? enumFacing.getEnumFacing() : null;
      rotationCtx.scaffoldYaw = this.yaw;
      rotationCtx.scaffoldPitch = this.pitch;
      rotationCtx.lastYaw = this.yaw;
      rotationCtx.lastPitch = this.pitch;
      rotationCtx.moving = MoveUtil.isMoving();
      rotationCtx.onGround = mc.thePlayer.onGround;
      rotationCtx.enabledTicks = this.offGroundTicks;
      rotationCtx.polarTicks += 0.05F;
      rotationCtx.playerYaw = mc.thePlayer.rotationYaw;
      rotationCtx.playerPitch = mc.thePlayer.rotationPitch;

      miau.module.modules.player.scaffold.rotation.RotationMode strat =
          this.rotationStrategies[mode];
      if (strat != null) {
        float[] rots = strat.getRotations(rotationCtx);
        if (rots != null) {
          targetYaw = rots[0];
          targetPitch = rots[1];
          this.yaw = targetYaw;
          this.pitch = targetPitch;
        }
      }
    }

    // ====== BASIC MODE PLACEMENT PATH (mode 1-4) ======
    if (mode >= 1 && mode < 5) {

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
            break;
        }

        float bestYaw = -180.0F;
        float bestPitch = 0.0F;
        float bestDiff = 0.0F;
        for (double dx : x) {
          for (double dy : y) {
            for (double dz : z) {
              double relX = blockData.blockPos().getX() + dx - mc.thePlayer.posX;
              double relY =
                  blockData.blockPos().getY()
                      + dy
                      - mc.thePlayer.posY
                      - mc.thePlayer.getEyeHeight();
              double relZ = blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
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
                  && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
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

      if (mode != 0) {
        if (this.towering
            && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
          float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
          float tolerance =
              this.rotationTick >= 2
                  ? RandomUtil.nextFloat(90.0F, 95.0F)
                  : RandomUtil.nextFloat(30.0F, 35.0F);
          if (Math.abs(yawDiff) > tolerance) {
            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
            this.yaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
            this.rotationTick = Math.max(this.rotationTick, 1);
          }
        }
        if (this.isTowering()) {
          float yawDelta =
              MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
          this.yaw =
              RotationUtil.quantizeAngle(
                  event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
          this.pitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
          this.rotationTick = 3;
          this.towering = true;
        }
        event.setRotation(this.yaw, this.pitch, 3);
        RotationUtil.serverYaw = this.yaw;
        RotationUtil.serverPitch = this.pitch;
        RotationUtil.customRots = true;
        if (this.moveFix.getValue() == 1 || this.moveFix.getValue() == 2)
          event.setPervRotation(this.yaw, 3);
      }

      // Cloud bypass delay check
      boolean cloudReady = isCloudReady();

      if (blockData != null && hitVec != null && this.rotationTick <= 0 && cloudReady) {
        this.place(blockData.blockPos(), blockData.facing(), hitVec);
        if (this.multiplace.getValue()) {
          for (int i = 0; i < 3; i++) {
            blockData = this.getBlockData();
            if (blockData == null) break;
            MovingObjectPosition mop =
                RotationUtil.rayTrace(
                    this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
            if (mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockData.blockPos())
                && mop.sideHit == blockData.facing()) {
              this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
            } else {
              hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
              double dx = hitVec.xCoord - mc.thePlayer.posX;
              double dy = hitVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
              double dz = hitVec.zCoord - mc.thePlayer.posZ;
              float[] rotations =
                  RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
              if (!(Math.abs(rotations[0] - this.yaw) < 120.0F)
                  || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) break;
              mop =
                  RotationUtil.rayTrace(
                      rotations[0],
                      rotations[1],
                      mc.playerController.getBlockReachDistance(),
                      1.0F);
              if (mop == null
                  || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                  || !mop.getBlockPos().equals(blockData.blockPos())
                  || mop.sideHit != blockData.facing()) break;
              this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
            }
          }
        }
      }

      if (this.targetFacing != null) {
        if (this.rotationTick <= 0) {
          int pX = MathHelper.floor_double(mc.thePlayer.posX);
          int pY = MathHelper.floor_double(mc.thePlayer.posY);
          int pZ = MathHelper.floor_double(mc.thePlayer.posZ);
          BlockPos below = new BlockPos(pX, pY - 1, pZ);
          Vec3 hv = BlockUtil.getHitVec(below, this.targetFacing, this.yaw, this.pitch);
          this.place(below, this.targetFacing, hv);
        }
        this.targetFacing = null;
      } else if (this.keepY.getValue() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
        int nextY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
        if (nextY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
          this.shouldKeepY = true;
          blockData = this.getBlockData();
          if (blockData != null && this.rotationTick <= 0) {
            hitVec =
                BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
            this.place(blockData.blockPos(), blockData.facing(), hitVec);
          }
        }
      }
    }

    // ====== ADVANCED MODE PLACEMENT PATH (mode 5-8) ======
    if (mode >= 5 && mode < 9) {

      event.setRotation(this.yaw, this.pitch, 3);
      RotationUtil.serverYaw = this.yaw;
      RotationUtil.serverPitch = this.pitch;
      RotationUtil.customRots = true;
      if (this.moveFix.getValue() == 1 || this.moveFix.getValue() == 2)
        event.setPervRotation(this.yaw, 3);

      BlockData bd = this.getBlockData();

      if (bd != null) {
        this.targetBlock =
            new Vec3(bd.blockPos().getX(), bd.blockPos().getY(), bd.blockPos().getZ());
        this.enumFacing =
            new EnumFacingOffset(
                bd.facing(),
                new Vec3(
                    bd.facing().getDirectionVec().getX(),
                    bd.facing().getDirectionVec().getY(),
                    bd.facing().getDirectionVec().getZ()));
        this.blockFace =
            new BlockPos(bd.blockPos())
                .add(
                    bd.facing().getDirectionVec().getX(),
                    bd.facing().getDirectionVec().getY(),
                    bd.facing().getDirectionVec().getZ());

        boolean badPackets = BadPacketsComponent.bad(false, true, false, false, true);

        if (!mc.gameSettings.keyBindJump.isKeyDown() || MoveUtil.isMoving()) {
          if (doesNotContainBlock(1)) {
            ticksOnAir++;
          } else {
            ticksOnAir = 0;
          }
        }

        boolean canPlaceNow = !badPackets && ticksOnAir > 0 && isCloudReady();

        // Hybrid rotation validation: use yaw-based pitch scanning to ensure rotation hits block
        if (canPlaceNow) {
          float optPitch =
              miau.module.modules.player.scaffold.rotation.IntaveMode.getYawBasedPitch(
                  bd.blockPos(), bd.facing(), this.yaw, this.pitch, 84);
          MovingObjectPosition checkPitch = RotationUtil.rayTrace(this.yaw, optPitch, 4.5, 1.0F);
          if (checkPitch == null
              || !checkPitch.getBlockPos().equals(bd.blockPos())
              || checkPitch.sideHit != bd.facing()) {
            // Pitch scan failed — try scanning yaw slightly
            float bestYaw = this.yaw;
            float bestPitch = optPitch;
            for (int yawOff = -8; yawOff <= 8; yawOff += 2) {
              float testYaw = this.yaw + yawOff;
              float testPitch =
                  miau.module.modules.player.scaffold.rotation.IntaveMode.getYawBasedPitch(
                      bd.blockPos(), bd.facing(), testYaw, this.pitch, 84);
              MovingObjectPosition check = RotationUtil.rayTrace(testYaw, testPitch, 4.5, 1.0F);
              if (check != null
                  && check.getBlockPos().equals(bd.blockPos())
                  && check.sideHit == bd.facing()) {
                bestYaw = testYaw;
                bestPitch = testPitch;
                break;
              }
            }
            this.yaw = bestYaw;
            this.pitch = bestPitch;
            event.setRotation(this.yaw, this.pitch, 3);
          } else {
            this.pitch = optPitch;
            event.setRotation(this.yaw, this.pitch, 3);
          }
        }

        if (canPlaceNow
            && (rayCast.getValue() == 0
                || overBlockCheck(bd.facing(), blockFace, rayCast.getValue() == 2))) {
          this.place(bd.blockPos(), bd.facing(), BlockUtil.getClickVec(bd.blockPos(), bd.facing()));
          ticksOnAir = 0;

          ItemStack item = Miau.slotComponent.getItemStack();
          if (item != null && item.stackSize == 0) {
            mc.thePlayer.inventory.mainInventory[Miau.slotComponent.getItemIndex()] = null;
          }
        } else if (Math.random() > 0.3
            && mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit != null
            && mc.objectMouseOver.getBlockPos() != null
            && mc.objectMouseOver.getBlockPos().equals(blockFace)
            && blockFace != null
            && mc.objectMouseOver.sideHit == EnumFacing.UP
            && rayCast.getValue() == 2
            && !(mc.theWorld
                    .getBlockState(
                        new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ))
                    .getBlock()
                instanceof BlockAir)) {
          ((IAccessorMinecraft) mc).callRightClickMouse();
        }

        if (mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.posY % 1 > 0.5) {
          startY = MathHelper.floor_double(mc.thePlayer.posY);
        }
        if ((mc.thePlayer.posY < startY || mc.thePlayer.onGround) && !MoveUtil.isMoving()) {
          startY = MathHelper.floor_double(mc.thePlayer.posY);
        }
      }
    }
  }

  /** Raycast check - returns true if the current rotation can see the target block face. */
  private boolean overBlockCheck(EnumFacing enumFacing, BlockPos pos, boolean strict) {
    MovingObjectPosition mop = RayCastUtil.rayCast(this.yaw, this.pitch, 4.5F);
    if (mop == null || mop.hitVec == null || mop.getBlockPos() == null) return false;
    return mop.getBlockPos().equals(pos) && (!strict || mop.sideHit == enumFacing);
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (!this.isEnabled()) return;

    int sprint = this.sprintMode.getValue();

    // Watchdog Slow - speed limiting
    if (sprint == 3) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
      mc.thePlayer.setSprinting(true);
      double limit = mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 0.118 : 0.083;
      if (mc.thePlayer.onGround) MoveUtil.strafe(limit - (Math.random() * 0.0001));
      if (MoveUtil.speed() >= limit && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.moveFlying((MoveUtil.speed() - limit) * -1);
      }
      return;
    }

    // Watchdog Fast - jump motion on enable
    if (sprint == 4) {
      if (mc.thePlayer.onGround
          && watchdogHasC08Packet > 0
          && !mc.gameSettings.keyBindJump.isKeyDown()) {
        mc.thePlayer.motionY = 0.42;
        watchdogHasC08Packet = 0;
      }
    }

    // MOTION mode - modify sprint motion for anti-cheat bypass
    if (sprint == 6) {
      if (mc.thePlayer.onGround && MoveUtil.isMoving()) {
        // Slightly modify motion to look less predictable
        if (mc.thePlayer.ticksExisted % 4 == 0) {
          mc.thePlayer.motionX *= 0.99;
          mc.thePlayer.motionZ *= 0.99;
        }
        if (mc.thePlayer.ticksExisted % 7 == 0) {
          mc.thePlayer.motionX *= 1.01;
          mc.thePlayer.motionZ *= 1.01;
        }
      }
    }

    // OLD_INTAVE mode - bypass old Intave sprint checks
    if (sprint == 9) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
      mc.thePlayer.setSprinting(true);
    }

    // Unified move-fix: Gothaj style silentMoveFix for both SILENT and NORMAL modes
    if (this.moveFix.getValue() != 0 && RotationUtil.customRots && !mc.isSingleplayer()) {
      MoveUtil.silentMoveFix(event);
      event.setStrafe(0);
      event.setForward(0);
      event.setFriction(0);
    }

    if (!this.yawOffsetProp.getModeString().equals("0") && this.moveFix.getValue() == 0) {}

    if (!mc.thePlayer.isCollidedHorizontally
        && mc.thePlayer.hurtTime <= 5
        && !mc.thePlayer.isPotionActive(Potion.jump)
        && mc.gameSettings.keyBindJump.isKeyDown()
        && Miau.slotComponent.isHoldingBlock()) {
      int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);

      switch (this.tower.getValue()) {
        case 1:
          switch (this.towerTick) {
            case 0:
              if (mc.thePlayer.onGround) {
                this.towerTick = 1;
                mc.thePlayer.motionY = -0.0784000015258789;
              }
              return;
            case 1:
              if (yState == 0 && PlayerUtil.isAirBelow()) {
                this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                this.towerTick = 2;
                mc.thePlayer.motionY = 0.42F;
                if (MoveUtil.isForwardPressed()) {
                  MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                } else {
                  MoveUtil.setSpeed(0.0);
                  event.setForward(0.0F);
                  event.setStrafe(0.0F);
                }
                return;
              } else {
                this.towerTick = 0;
                return;
              }
            case 2:
              this.towerTick = 3;
              mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
              return;
            case 3:
              this.towerTick = 1;
              mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
              return;
            default:
              this.towerTick = 0;
              return;
          }
        case 2:
          switch (this.towerTick) {
            case 0:
              if (mc.thePlayer.onGround) {
                this.towerTick = 1;
                mc.thePlayer.motionY = -0.0784000015258789;
              }
              return;
            case 1:
              if (yState == 0 && PlayerUtil.isAirBelow()) {
                this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                if (!MoveUtil.isForwardPressed()) {
                  this.towerDelay = 2;
                  MoveUtil.setSpeed(0.0);
                  event.setForward(0.0F);
                  event.setStrafe(0.0F);
                  EnumFacing facing =
                      this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                  double distance = this.distanceToEdge(facing);
                  if (distance > 0.1) {
                    if (mc.thePlayer.onGround) {
                      Vec3i dirVec = facing.getDirectionVec();
                      double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                      double jitter = RandomUtil.nextDouble(0.02, 0.03);
                      AxisAlignedBB nextBox =
                          mc.thePlayer
                              .getEntityBoundingBox()
                              .offset(
                                  dirVec.getX() * (offset - jitter),
                                  0.0,
                                  dirVec.getZ() * (offset - jitter));
                      if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                        mc.thePlayer.motionY = -0.0784000015258789;
                        mc.thePlayer.setPosition(
                            nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0,
                            nextBox.minY,
                            nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                      }
                      return;
                    }
                  } else {
                    this.towerTick = 2;
                    this.targetFacing = facing;
                    mc.thePlayer.motionY = 0.42F;
                  }
                  return;
                } else {
                  this.towerTick = 2;
                  this.towerDelay++;
                  mc.thePlayer.motionY = 0.42F;
                  MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                  return;
                }
              } else {
                this.towerTick = 0;
                this.towerDelay = 0;
                return;
              }
            case 2:
              this.towerTick = 3;
              mc.thePlayer.motionY -= RandomUtil.nextDouble(0.00101, 0.00109);
              return;
            case 3:
              if (this.towerDelay >= 4) {
                this.towerTick = 4;
                this.towerDelay = 0;
              } else {
                this.towerTick = 1;
                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
              }
              return;
            case 4:
              this.towerTick = 5;
              return;
            case 5:
              if (!PlayerUtil.isAirBelow()) this.towerTick = 0;
              else {
                this.towerTick = 1;
                mc.thePlayer.motionY -= 0.08;
                mc.thePlayer.motionY *= 0.98F;
                mc.thePlayer.motionY -= 0.08;
                mc.thePlayer.motionY *= 0.98F;
              }
              return;
            default:
              this.towerTick = 0;
              this.towerDelay = 0;
              return;
          }
        case 4:
          if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42F;
          }
          return;
        default:
          this.towerTick = 0;
          this.towerDelay = 0;
      }
    } else {
      this.towerTick = 0;
      this.towerDelay = 0;
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled()) return;

    // NOTE: MoveFix is now handled in onStrafe via silentMoveFix (Gothaj style)
    // No longer need fixStrafe here

    // Telly rotation mode auto-jump (mode index 8)
    if (this.rotationMode.getValue() == 8 && mc.thePlayer.onGround && MoveUtil.isMoving()) {
      mc.thePlayer.movementInput.jump = true;
    }

    if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
      mc.thePlayer.movementInput.jump = true;
    }

    if (this.sneak.getValue()) {
      float speed = this.sneakingSpeed.getValue();
      if (speed > 0.2F && mc.thePlayer.movementInput.sneak) {
        mc.thePlayer.movementInput.moveForward *= 0.3F / 0.2F * speed;
        mc.thePlayer.movementInput.moveStrafe *= 0.3F / 0.2F * speed;
      }
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled()) return;

    int sprint = this.sprintMode.getValue();

    // NO_PACKET mode - sprint client-side but don't send C0B packets
    if (sprint == 7) {
      mc.thePlayer.setSprinting(true);
      // C0B packet suppression is handled in onPacketSend
    }

    // PACKET_LEGIT mode - send sprint packets with delays like legit client
    if (sprint == 8) {
      if (mc.thePlayer.ticksExisted % 20 == 0) {
        mc.thePlayer.setSprinting(true);
      }
    }

    float speed = this.getSpeed();
    if (speed != 1.0F) {
      if (mc.thePlayer.movementInput.moveForward != 0.0F
          && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
        mc.thePlayer.movementInput.moveForward *= (1.0F / (float) Math.sqrt(2.0));
        mc.thePlayer.movementInput.moveStrafe *= (1.0F / (float) Math.sqrt(2.0));
      }
      mc.thePlayer.movementInput.moveForward *= speed;
      mc.thePlayer.movementInput.moveStrafe *= speed;
    }

    if (this.shouldStopSprint()) {
      mc.thePlayer.setSprinting(false);
    }

    // Watchdog Slow - force sprint always
    if (sprint == 3) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
      mc.thePlayer.setSprinting(true);
    }

    // Watchdog Fast - diagonal prevention
    if (sprint == 4 && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0029f;
      MoveUtil.preventDiagonalSpeed();
      mc.thePlayer.motionZ *= .998;
      mc.thePlayer.motionX *= .998;
    }

    if (slow > 0) {
      slow--;
      mc.thePlayer.movementInput.moveForward = 0;
      mc.thePlayer.movementInput.moveStrafe = 0;
    }

    this.calculateSneaking();
  }

  @EventTarget
  public void onSafeWalk(SafeWalkEvent event) {
    if (this.isEnabled() && this.safeWalk.getValue()) {
      if (mc.thePlayer.onGround
          && mc.thePlayer.motionY <= 0.0
          && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
        event.setSafeWalk(true);
      }
    }
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      this.lastSlot = event.setSlot(this.lastSlot);
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onPacketSend(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;
    Packet<?> packet = event.getPacket();

    // NO_PACKET mode - cancel C0B sprint packets
    if (this.sprintMode.getValue() == 7) {
      if (packet instanceof C0BPacketEntityAction) {
        C0BPacketEntityAction action = (C0BPacketEntityAction) packet;
        if (action.getAction() == C0BPacketEntityAction.Action.START_SPRINTING
            || action.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
          event.setCancelled(true);
          return;
        }
      }
    }

    if (packet instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
      if (!p.getPosition().equals(new BlockPos(-1, -1, -1))) {
        placements--;
      }
      // Watchdog Jump: track C08 for ice packet assist
      if (this.sprintMode.getValue() == 5) {
        watchdogBlock++;
        watchdogStart3 = true;
      }
      // Watchdog Fast: track C08 for jump motion trigger
      if (this.sprintMode.getValue() == 4) {
        watchdogHasC08Packet++;
      }
    }
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer == null) return;

    this.lastSlot = Miau.slotComponent.getItemIndex();
    this.blockCount = -1;
    this.rotationTick = 3;
    this.yaw = -180.0F;
    this.pitch = 0.0F;
    this.canRotate = false;
    this.towerTick = 0;
    this.towerDelay = 0;
    this.towering = false;

    this.targetYaw = mc.thePlayer.rotationYaw - 180;
    this.targetPitch = 90;
    this.pitchDrift = (float) ((Math.random() - 0.5) * (Math.random() - 0.5) * 10);
    this.yawDrift = (float) ((Math.random() - 0.5) * (Math.random() - 0.5) * 10);
    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
    this.sneakingTicks = -1;
    this.ticksOnAir = 0;
    this.directionalChange = 0;
    this.pause = 0;
    this.slow = 0;
    this.placements = 0;
    this.recursions = 0;
    this.offGroundTicks = 0;
    this.onGroundTicks = 0;

    // Watchdog mode reset
    this.watchdogJump = false;
    this.watchdogJumpHandled = false;
    this.watchdogPreviousTick = -1;
    this.watchdogPreviousBlockValue = -1;
    this.watchdogHasC08Packet = 0;
    this.watchdogStart2 = true;
    this.watchdogStart3 = false;
    this.watchdogStart4 = false;
    this.watchdogStartTriggerCount = 0;
    this.watchdogBlock = 0;
    this.watchdogTime = 0;
    this.watchdogSpeed = 0;
    this.watchdogEnable = true;
    this.watchdogTicks = 0;
    this.watchdogOngroundticks = 0;

    // Cloud bypass state reset
    this.cloudTicks = 0;
    this.cloudPlacementDelay = 0;
    this.cloudTriggered = false;

    // Gothaj: init serverYaw/serverPitch if not already set
    if (!RotationUtil.customRots) {
      RotationUtil.serverYaw = mc.thePlayer.rotationYaw;
      RotationUtil.serverPitch = mc.thePlayer.rotationPitch;
    }

    BadPacketsComponent.reset();
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null && this.lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.lastSlot;
    }

    ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak)
        .setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));

    // Watchdog mode cleanup
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
    mc.thePlayer.setSprinting(false);
    // mc.thePlayer.safeWalk = false;

    // Reset Watchdog Jump state
    if (this.sprintMode.getValue() == 5) {
      watchdogStart3 = false;
      watchdogStart2 = false;
      watchdogHasC08Packet = 0;
      if (watchdogJump) {
        MoveUtil.stop();
      }
    }

    RotationUtil.customRots = false;
    RotationUtil.serverYaw = mc.thePlayer.rotationYaw;
    RotationUtil.serverPitch = mc.thePlayer.rotationPitch;
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
