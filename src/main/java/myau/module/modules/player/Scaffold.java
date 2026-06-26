package myau.module.modules.player;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import myau.Myau;
import myau.component.BadPacketsComponent;
import myau.event.EventTarget;
import myau.event.impl.*;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.management.RotationState;
import myau.mixin.IAccessorKeyBinding;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.module.modules.movement.LongJump;
import myau.module.modules.render.HUD;
import myau.property.properties.*;
import myau.util.font.Fonts;
import myau.util.math.RandomUtil;
import myau.util.network.PacketUtil;
import myau.util.player.*;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedUtils;
import myau.util.time.TimerUtil;
import myau.util.world.BlockUtil;
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

  private static final double[] placeOffsets =
      new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
        0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
        0.78125, 0.84375, 0.90625, 0.96875
      };

  // ===== EXISTING MIAU FIELDS =====
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
  private final java.util.Map<net.minecraft.util.BlockPos, myau.util.time.TimerUtil>
      blockRenderHighlights = new java.util.HashMap<>();

  // ===== RISE NEW FIELDS =====
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

  public final ModeProperty rotationMode =
      new ModeProperty(
          "rotations",
          2,
          new String[] {
            "NONE",
            "DEFAULT",
            "BACKWARDS",
            "SIDEWAYS",
            "GRIM_TEST",
            "GODBRIDGE",
            "EAGLE",
            "BREESILY",
            "SNAP"
          });

  public final ModeProperty sprintMode =
      new ModeProperty("sprint", 0, new String[] {"NONE", "VANILLA", "LEGIT"});
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
      new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT"});
  public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
  public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
  public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
  public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
  public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
  public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);

  public final ModeProperty yawOffsetProp =
      new ModeProperty("yaw-offset", 0, new String[] {"0", "45", "-45"});
  public final IntProperty expand = new IntProperty("expand", 0, 0, 4);
  public final FloatProperty timerProp = new FloatProperty("timer", 1.0F, 0.1F, 10.0F);
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
  public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
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
      new BooleanProperty("block-render-raytrace", false, () -> this.blockRender.getValue());
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
  }

  // ===== EXISTING MIAU HELPERS =====

  public int getSlot() {
    return Myau.slotComponent.getItemIndex();
  }

  private boolean shouldStopSprint() {
    if (this.isTowering()) return false;
    boolean keepYActive = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
    if (keepYActive && this.stage > 0) return false;

    int sprint = this.sprintMode.getValue();
    switch (sprint) {
      case 0:
        return true; // NONE
      case 1: // VANILLA
        return mc.thePlayer.onGround
            ? !this.jumpSprint.getValue()
            : !(this.diaSprint.getValue() && this.isDiagonal(this.getCurrentYaw()));
      case 2:
        return false; // LEGIT — handled separately in onLivingUpdate
      default:
        return false;
    }
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) myau.Myau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
    LongJump longJump = (LongJump) myau.Myau.moduleManager.modules.get(LongJump.class);
    return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
  }

  private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
    double offset = 0.0;
    EnumFacing enumFacing = null;
    for (EnumFacing facing : EnumFacing.VALUES) {
      if (facing == EnumFacing.DOWN) continue;
      BlockPos pos = blockPos1.offset(facing);
      if (pos.getY() <= blockPos3.getY()) {
        double distance =
            pos.distanceSqToCenter(
                blockPos3.getX() + 0.5, blockPos3.getY() + 0.5, blockPos3.getZ() + 0.5);
        if (enumFacing == null
            || distance < offset
            || (distance == offset && facing == EnumFacing.UP)) {
          offset = distance;
          enumFacing = facing;
        }
      }
    }
    return enumFacing;
  }

  private BlockData getBlockData() {
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
              && mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                  <= mc.playerController.getBlockReachDistance()
              && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
            for (EnumFacing facing : EnumFacing.VALUES) {
              if (facing != EnumFacing.DOWN) {
                BlockPos bp = pos.offset(facing);
                if (BlockUtil.isReplaceable(bp)) {
                  positions.add(pos);
                }
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
                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5)));
    BlockPos blockPos = positions.get(0);
    EnumFacing facing = this.getBestFacing(blockPos, targetPos);
    return facing == null ? null : new BlockData(blockPos, facing);
  }

  private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
    ItemStack activeItem = Myau.slotComponent.getItemStack();
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
    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepYTelly = this.keepY.getValue() == 3;
      boolean towerTelly = this.tower.getValue() == 3;
      return (keepYTelly && this.stage > 0)
          || (towerTelly && mc.gameSettings.keyBindJump.isKeyDown());
    }
    return false;
  }

  private int findBlock() {
    // Simple hotbar search matching old working Miau-main logic
    // SlotUtil.findBlock() is too restrictive (stackSize > 5 threshold)
    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.stackSize > 0 && ItemUtil.isBlock(stack)) {
        return i;
      }
    }
    return -1;
  }

  // ===== RISE HELPER METHODS =====

  private void calculateSneaking() {
    if (ticksOnAir == 0) ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(false);
    this.sneakingTicks--;

    if (!this.sneak.getValue() && pause <= 0) return;

    int ahead = this.startSneaking.getValue();
    int place = 0; // placeDelay not ported as bounds number, simplified
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

    // Backup rotations
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

  // ===== EVENT HANDLERS =====

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
    ItemStack held = Myau.slotComponent.getItemStack();
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

    float textWidth = Fonts.MAIN.get(18).width(info);
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

    HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
    boolean shaders = hud != null && hud.shaders.getValue();

    if (shaders) {
      // Blur pass — frosted-glass background
      BlurUtils.prepareBlur();
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, centerY, 0);
      GlStateManager.scale(animationProgress, animationProgress, 1f);
      GlStateManager.translate(-centerX, -centerY, 0);
      RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, 150));
      GlStateManager.popMatrix();
      BlurUtils.blurEnd(2, 3);

      // Bloom pass — soft glow outline
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
    float fontY = y + (height / 2f) - (Fonts.MAIN.get(18).height() / 2f);
    float textX = x + 24f;

    GlStateManager.popMatrix();
  }

  @EventTarget
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
      HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
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

    // ===== KEEP-Y HANDLING (Miau's system, fully preserved) =====
    // ===== OFF-GROUND / ON-GROUND TICK TRACKING =====
    if (mc.thePlayer.onGround) {
      this.onGroundTicks++;
      this.offGroundTicks = 0;
    } else {
      this.offGroundTicks++;
      this.onGroundTicks = 0;
    }

    if (mc.thePlayer.onGround) {
      if (this.stage > 0) this.stage--;
      if (this.stage < 0) this.stage++;
      if (this.stage == 0
          && this.keepY.getValue() != 0
          && (!this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
          && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
          && !mc.gameSettings.keyBindJump.isKeyDown()) {
        this.stage = 1;
      }
      this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
      this.shouldKeepY = false;
      this.towering = false;
    }

    if (!this.canPlace()) return;

    // ===== BLOCK SLOT MANAGEMENT (spoofItem style via SlotComponent) =====
    int blockSlot = this.findBlock();
    if (blockSlot != -1) Myau.slotComponent.setSlot(blockSlot);
    ItemStack stack = Myau.slotComponent.getItemStack();
    int count = (stack != null && stack.getItem() instanceof ItemBlock) ? stack.stackSize : 0;
    this.blockCount = Math.min(this.blockCount, count);
    if (this.blockCount <= 0) {
      int slot = Myau.slotComponent.getItemIndex();
      if (this.blockCount == 0) slot--;
      for (int i = slot; i > slot - 9; i--) {
        int hotbarSlot = (i % 9 + 9) % 9;
        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
        if (candidate != null && candidate.getItem() instanceof ItemBlock) {
          Myau.slotComponent.setSlot(hotbarSlot);
          this.blockCount = candidate.stackSize;
          break;
        }
      }
    }

    // ===== YAW / ROTATION SETUP =====
    float currentYaw = this.getCurrentYaw();
    float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
    float diagonalYaw =
        this.isDiagonal(currentYaw)
            ? yawDiffTo180
            : RotationUtil.wrapAngleDiff(
                currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                event.getYaw());

    // ---- Rotation modes ----
    // Initialize target yaw/pitch
    targetYaw = this.yaw;
    targetPitch = this.pitch;
    float snappedYaw = 0;

    // Timer (Rise)
    if (timerProp.getValue() != 1.0F)
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = timerProp.getValue();

    // ---- Calculate rotations based on mode ----
    int mode = this.rotationMode.getValue();

    if (mode >= 5) {
      // ===== RISE MODES: GODBRIDGE / EAGLE / BREESILY / SNAP =====
      this.canRotate = true;

      switch (mode) {
        case 5:
          { // GODBRIDGE
            // Auto right-click (like Rise)
            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
            if (held != null && held.getItem() instanceof ItemBlock && ticksOnAir > 0) {
              ((IAccessorMinecraft) mc).callRightClickMouse();
            }
            // Godbridge yaw: quantize to 90 degrees
            targetYaw =
                (mc.thePlayer.rotationYaw - mc.thePlayer.rotationYaw % 90)
                    - 180
                    + 45 * (mc.thePlayer.rotationYaw > 0 ? 1 : -1);
            targetPitch = 76.4F;

            // Drift randomization
            directionalChange++;
            if (Math.abs(MathHelper.wrapAngleTo180_double(targetYaw - (mc.thePlayer.rotationYaw)))
                > 10) {
              directionalChange = (int) (Math.random() * 4);
              yawDrift = (float) (Math.random() - 0.5) / 10f;
              pitchDrift = (float) (Math.random() - 0.5) / 10f;
            }
            if (Math.random() > 0.99) {
              yawDrift = (float) (Math.random() - 0.5) / 10f;
              pitchDrift = (float) (Math.random() - 0.5) / 10f;
            }
            targetYaw += yawDrift;
            targetPitch += pitchDrift;
            break;
          }
        case 6:
          { // EAGLE
            float yawWrapped = (mc.thePlayer.rotationYaw + 10000000) % 360;
            float staticYaw = (yawWrapped - 180) - (yawWrapped % 90) + 45;
            float staticPitch = 78;

            boolean straight =
                (Math.min(Math.abs(yawWrapped % 90), Math.abs(90 - yawWrapped) % 90)
                    < Math.min(
                        Math.abs(yawWrapped + 45) % 90, Math.abs(90 - (yawWrapped + 45)) % 90));

            if (straight) {
              MovingObjectPosition check90 = RayCastUtil.rayCast(staticYaw + 90, staticPitch, 3);
              MovingObjectPosition checkMain = RayCastUtil.rayCast(staticYaw, staticPitch, 3);
              if (check90 != null
                  && check90.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                  && (checkMain == null
                      || checkMain.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)) {
                staticYaw += 90;
              }
            }
            if (!straight) staticYaw += 90;

            targetYaw = staticYaw + yawDrift / 2;
            targetPitch = staticPitch + pitchDrift / 2;

            // Auto right-click
            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
            if (Math.random() > (mc.thePlayer.onGround ? 0.5 : 0.2)
                && held != null
                && held.getItem() instanceof ItemBlock) {
              ((IAccessorMinecraft) mc).callRightClickMouse();
            }
            // Auto sneak on edge
            mc.thePlayer.movementInput.sneak = mc.theWorld != null;
            if (this.offGroundTicks >= 4 && MoveUtil.isMoving()) {
              ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(true);
            }
            if (this.onGroundTicks == 1)
              ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(false);
            break;
          }
        case 7:
          { // BREESILY
            if (enumFacing != null) {
              if (enumFacing.getEnumFacing() == EnumFacing.UP) {
                targetPitch = 90;
              } else {
                double staticYaw =
                    (Math.toDegrees(
                                Math.atan2(
                                    enumFacing.getOffset().zCoord, enumFacing.getOffset().xCoord))
                            % 360)
                        - 90;
                double staticPitch = 80;

                targetYaw = (float) staticYaw + yawDrift;
                targetPitch = (float) staticPitch + pitchDrift;
              }
            }
            if (Math.random() > 0.99 || targetPitch % 90 == 0) {
              yawDrift = (float) (Math.random() - 0.5);
              pitchDrift = (float) (Math.random() - 0.5);
            }
            break;
          }
        case 8:
          { // SNAP
            if (enumFacing != null
                && blockFace != null
                && !(ticksOnAir > 0
                    && !overBlockCheck(enumFacing.getEnumFacing(), blockFace, true))) {
              snappedYaw = targetYaw;
              // Then calculate normal rotation
              this.getRotations(Integer.parseInt(this.yawOffsetProp.getModeString()));
              // After normal calc, snap back to movement yaw if valid
              float movementYaw =
                  (float)
                          (Math.toDegrees(
                              MoveUtil.direction(mc.thePlayer.rotationYaw, forward, strafe)))
                      - Integer.parseInt(this.yawOffsetProp.getModeString());
              targetYaw = movementYaw;
            } else {
              this.getRotations(Integer.parseInt(this.yawOffsetProp.getModeString()));
            }
            break;
          }
      }
      this.yaw = targetYaw;
      this.pitch = targetPitch;

    } else {
      // ===== MIAU MODES: NONE / DEFAULT / BACKWARDS / SIDEWAYS / GRIM_TEST =====
      if (!this.canRotate) {
        switch (mode) {
          case 1: // DEFAULT
            if (this.yaw == -180.0F && this.pitch == 0.0F) {
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
              this.pitch = RotationUtil.quantizeAngle(85.0F);
            } else {
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
            }
            break;
          case 2: // BACKWARDS
            if (this.yaw == -180.0F && this.pitch == 0.0F) {
              this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
              this.pitch = RotationUtil.quantizeAngle(85.0F);
            } else {
              this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
            }
            break;
          case 3:
          case 4: // SIDEWAYS / GRIM_TEST
            if (this.yaw == -180.0F && this.pitch == 0.0F) {
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
              this.pitch = RotationUtil.quantizeAngle(85.0F);
            } else {
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
            }
            break;
        }
      }

      // ---- Block data / rotation finding loop (Miau's system) ----
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

      if (this.canRotate
          && MoveUtil.isForwardPressed()
          && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
        switch (mode) {
          case 2:
            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
            break;
          case 3:
          case 4:
            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
            break;
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
        if (this.moveFix.getValue() == 1) event.setPervRotation(this.yaw, 3);
      }

      // ---- Place block (Miau modes) ----
      if (blockData != null && hitVec != null && this.rotationTick <= 0) {
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

      // ---- KeepY extra placement ----
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

    // ===== RISE MODES placement (5-8) =====
    if (mode >= 5) {
      // Apply rotation to event for Godbridge/Eagle/Breesily/Snap
      event.setRotation(this.yaw, this.pitch, 3);
      if (this.moveFix.getValue() == 1) event.setPervRotation(this.yaw, 3);

      // Expand
      if (this.expand.getValue() > 0) {
        double dir =
            MoveUtil.direction(
                mc.thePlayer.rotationYaw,
                mc.gameSettings.keyBindForward.isKeyDown()
                    ? 1
                    : mc.gameSettings.keyBindBack.isKeyDown() ? -1 : 0,
                mc.gameSettings.keyBindRight.isKeyDown()
                    ? -1
                    : mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0);
        for (double r = 0; r <= this.expand.getValue(); r++) {
          if (mc.theWorld
                  .getBlockState(
                      new BlockPos(
                          mc.thePlayer.posX + (-Math.sin(dir) * (r + 1)),
                          mc.thePlayer.posY - 0.5,
                          mc.thePlayer.posZ + (Math.cos(dir) * (r + 1))))
                  .getBlock()
              instanceof BlockAir) {
            // offset applied via expand - block finding handles this
            break;
          }
        }
      }

      // ---- Get block data for placement (Rise modes) ----
      BlockData bd = this.getBlockData();

      // Rise-style target block/enum facing for rotation calculation
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

        // BadPackets check
        boolean badPackets = BadPacketsComponent.bad(false, true, false, false, true);

        // Ticks on air
        if (!mc.gameSettings.keyBindJump.isKeyDown() || MoveUtil.isMoving()) {
          if (doesNotContainBlock(1)) {
            ticksOnAir++;
          } else {
            ticksOnAir = 0;
          }
        }

        // CanPlace flag
        boolean canPlaceNow = !badPackets && ticksOnAir > 0; // simplified placeDelay

        // Raycast check
        if (canPlaceNow
            && (rayCast.getValue() == 0
                || overBlockCheck(bd.facing(), blockFace, rayCast.getValue() == 2))) {
          this.place(bd.blockPos(), bd.facing(), BlockUtil.getClickVec(bd.blockPos(), bd.facing()));
          ticksOnAir = 0;

          ItemStack item = Myau.slotComponent.getItemStack();
          if (item != null && item.stackSize == 0) {
            mc.thePlayer.inventory.mainInventory[Myau.slotComponent.getItemIndex()] = null;
          }
        } else if (Math.random() > 0.3
            && mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit != null
            && mc.objectMouseOver.getBlockPos() != null
            && mc.objectMouseOver.getBlockPos().equals(blockFace)
            && blockFace != null
            && mc.objectMouseOver.sideHit == EnumFacing.UP
            && rayCast.getValue() == 2 // Strict
            && !(mc.theWorld
                    .getBlockState(
                        new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ))
                    .getBlock()
                instanceof BlockAir)) {
          ((IAccessorMinecraft) mc).callRightClickMouse();
        }

        // Same Y tracking (for keepY)
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

    // Diagonal speed for non-zero yawOffset (Rise)
    // Note: MoveUtil doesn't have useDiagonalSpeed, handled via motion directly
    if (!this.yawOffsetProp.getModeString().equals("0") && this.moveFix.getValue() == 0) {
      // Diagonal movement handled naturally by moveFix
    }

    // Tower modes (Miau's system, extended with NORMAL)
    if (!mc.thePlayer.isCollidedHorizontally
        && mc.thePlayer.hurtTime <= 5
        && !mc.thePlayer.isPotionActive(Potion.jump)
        && mc.gameSettings.keyBindJump.isKeyDown()
        && Myau.slotComponent.isHoldingBlock()) {
      int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);

      switch (this.tower.getValue()) {
        case 1: // VANILLA
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
        case 2: // EXTRA
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
        case 4: // NORMAL (Rise's NormalTower)
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

    // Move fix (existing Miau)
    if (this.moveFix.getValue() == 1
        && RotationState.isActived()
        && RotationState.getPriority() == 3.0F
        && MoveUtil.isForwardPressed()) {
      MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }

    // KeepY auto-jump (existing Miau)
    if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
      mc.thePlayer.movementInput.jump = true;
    }

    // Sneak slowdown (Rise's calculateSneaking in MoveInputEvent)
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

    // Speed control (existing Miau)
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

    // Sprint control (existing + new modes)
    if (this.shouldStopSprint()) {
      mc.thePlayer.setSprinting(false);
    }

    // LEGIT sprint: stop if yaw diff > 90 (Rise)
    if (this.sprintMode.getValue() == 3) { // LEGIT
      float diff = Math.abs(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - this.yaw));
      if (diff > 90) {
        mc.thePlayer.setSprinting(false);
      }
    }

    // Slow handling from calculateSneaking (Rise)
    if (slow > 0) {
      slow--;
      mc.thePlayer.movementInput.moveForward = 0;
      mc.thePlayer.movementInput.moveStrafe = 0;
    }

    // Sneak calculate (Rise)
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

    // Track placements for sneak system (Rise)
    if (packet instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
      if (!p.getPosition().equals(new BlockPos(-1, -1, -1))) {
        placements--;
      }
    }
  }

  // ===== LIFECYCLE =====

  @Override
  public void onEnabled() {
    if (mc.thePlayer == null) return;

    this.lastSlot = Myau.slotComponent.getItemIndex();
    this.blockCount = -1;
    this.rotationTick = 3;
    this.yaw = -180.0F;
    this.pitch = 0.0F;
    this.canRotate = false;
    this.towerTick = 0;
    this.towerDelay = 0;
    this.towering = false;

    // Rise field init
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

    // Reset bad packets
    BadPacketsComponent.reset();
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null && this.lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.lastSlot;
    }

    // Reset timer
    if (timerProp.getValue() != 1.0F) {
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
    }

    // Reset sneak key
    ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak)
        .setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
  }

  // ===== INNER CLASS =====

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
