package myau.module.modules.ghost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import myau.event.EventTarget;
import myau.event.impl.MoveInputEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.client.KeyBindUtil;
import myau.util.player.RotationUtil;
import myau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;

public class BridgeAssist extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final EnumFacing[] SIDES = {
    EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
  };

  public final FloatProperty edgeOffset = new FloatProperty("edge-offset", 0.0F, 0.0F, 0.3F);
  public final IntProperty unsneakDelay = new IntProperty("unsneak-delay", 50, 50, 300);
  public final IntProperty sneakOnJump = new IntProperty("sneak-on-jump", 0, 0, 500);

  public final BooleanProperty sneakKeyPressed = new BooleanProperty("sneak-key-pressed", false);
  public final BooleanProperty holdingBlocks = new BooleanProperty("holding-blocks", false);
  public final BooleanProperty lookingDown = new BooleanProperty("looking-down", false);
  public final BooleanProperty notMovingForward = new BooleanProperty("not-moving-forward", false);

  public final BooleanProperty prePlace = new BooleanProperty("pre-place", false);

  private boolean sneakingFromModule;
  private boolean placed;
  private boolean forceRelease;
  private int sneakJumpDelayTicks = -1;
  private int sneakJumpStartTick = -1;
  private int unsneakDelayTicks = -1;
  private int unsneakStartTick = -1;

  private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();

  public BridgeAssist() {
    super("Bridge Assist", false);
    BLOCK_SCORE.put("obsidian", 0);
    BLOCK_SCORE.put("end_stone", 1);
    BLOCK_SCORE.put("planks", 2);
    BLOCK_SCORE.put("log", 2);
    BLOCK_SCORE.put("glass", 3);
    BLOCK_SCORE.put("stained_glass", 3);
    BLOCK_SCORE.put("hardened_clay", 4);
    BLOCK_SCORE.put("stained_hardened_clay", 4);
    BLOCK_SCORE.put("cloth", 5);
  }

  @Override
  public String[] getSuffix() {
    float offset = edgeOffset.getValue();
    if (offset == Math.rint(offset)) {
      return new String[] {Integer.toString((int) offset)};
    }
    return new String[] {String.format("%.2f", offset)};
  }

  @Override
  public void onDisabled() {
    sneakingFromModule = false;
    resetUnsneak();
  }

  @EventTarget(Priority.LOWEST)
  public void onMoveInput(MoveInputEvent e) {
    if (!this.isEnabled()) return;
    if (mc.thePlayer == null || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying)
      return;

    boolean manualSneak = isManualSneak();
    boolean requireSneak = sneakKeyPressed.getValue();

    if (manualSneak && !requireSneak) {
      resetUnsneak();
      return;
    }

    if (requireSneak
        && (!manualSneak
            || (mc.thePlayer.movementInput.moveForward == 0
                && mc.thePlayer.movementInput.moveStrafe == 0))) {
      if (!manualSneak) resetUnsneak();
      repressSneak();
      return;
    }

    if (notMovingForward.getValue() && mc.thePlayer.movementInput.moveForward > 0) {
      clearSneak();
      return;
    }
    if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) {
      clearSneak();
      return;
    }
    if (holdingBlocks.getValue()) {
      ItemStack held = mc.thePlayer.getHeldItem();
      if (held == null || !(held.getItem() instanceof ItemBlock)) {
        clearSneak();
        return;
      }
    }

    if (mc.thePlayer.onGround
        && mc.gameSettings.keyBindJump.isKeyDown()
        && (mc.thePlayer.movementInput.moveForward != 0
            || mc.thePlayer.movementInput.moveStrafe != 0)
        && sneakOnJump.getValue() > 0) {
      if (!requireSneak || forceRelease) {
        sneakJumpStartTick = mc.thePlayer.ticksExisted;
        double raw = sneakOnJump.getValue() / 50.0;
        int base = (int) raw;
        sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
        pressSneak(true);
        return;
      }
    }

    // Predict bounding box after one tick without sneak
    double[] motion = getPredictedMotion();
    AxisAlignedBB simBox =
        mc.thePlayer.getEntityBoundingBox().offset(motion[0], motion[1], motion[2]);

    double offset = computeEdgeOffset(simBox);

    if (Double.isNaN(offset)) {
      if (mc.gameSettings.keyBindJump.isKeyDown()
          && (sneakOnJump.getValue() <= 0
              || (mc.thePlayer.movementInput.moveForward == 0
                  && mc.thePlayer.movementInput.moveStrafe == 0))) {
        if (sneakingFromModule) tryReleaseSneak(true);
      } else if (mc.thePlayer.onGround) {
        pressSneak(true);
      } else if (sneakingFromModule) {
        tryReleaseSneak(true);
      }
      return;
    }

    if (offset > edgeOffset.getValue()) {
      pressSneak(true);
    } else if (sneakingFromModule) {
      tryReleaseSneak(true);
    }
  }

  @EventTarget
  public void onSendPacket(PacketEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.SEND) return;
    if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) e.getPacket();
      if (c08.getPlacedBlockDirection() != 255
          && sneakingFromModule
          && sneakKeyPressed.getValue()) {
        placed = true;
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent e) {
    if (!this.isEnabled() || e.getType() != EventType.PRE) return;
    if (!prePlace.getValue()) return;
    if (mc.thePlayer == null || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying)
      return;

    ItemStack held = mc.thePlayer.getHeldItem();
    if (held == null || !(held.getItem() instanceof ItemBlock)) return;
    if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70f) return;
    if (notMovingForward.getValue() && mc.thePlayer.movementInput.moveForward > 0f) return;

    // Prefer strongest block if holding multiple block types
    int bestSlot = findBestBlockSlot();
    if (bestSlot != -1 && bestSlot != mc.thePlayer.inventory.currentItem) {
      mc.thePlayer.inventory.currentItem = bestSlot;
    }

    float basePitch = mc.thePlayer.rotationPitch;
    double reach = mc.playerController.getBlockReachDistance();

    TargetResult target = findTarget(basePitch, reach);
    if (target == null) return;

    float baseYaw = mc.thePlayer.rotationYaw;
    float[] sm = RotationUtil.smoothRotation(baseYaw, basePitch, target.yaw, target.pitch, 15, 20f);

    e.setRotation(sm[0], sm[1], 100);
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Sneak helpers
  // ══════════════════════════════════════════════════════════════════════════

  private void pressSneak(boolean resetDelay) {
    mc.thePlayer.movementInput.sneak = true;
    sneakingFromModule = true;
    if (resetDelay) unsneakStartTick = -1;
    repressSneak();
  }

  private void tryReleaseSneak(boolean resetDelay) {
    int existed = mc.thePlayer.ticksExisted;
    if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
      unsneakStartTick = existed;
      double raw = (unsneakDelay.getValue() - 50) / 50.0;
      int base = (int) raw;
      unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
    }

    if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
      pressSneak(false);
      return;
    }
    if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
      pressSneak(false);
      return;
    }

    releaseSneak(resetDelay);
  }

  private void releaseSneak(boolean resetDelay) {
    if (!sneakKeyPressed.getValue()) {
      mc.thePlayer.movementInput.sneak = false;
    } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
      mc.thePlayer.movementInput.sneak = false;
      forceRelease = true;
    } else if (forceRelease) {
      mc.thePlayer.movementInput.sneak = false;
    }

    sneakingFromModule = false;
    placed = false;
    if (resetDelay) resetUnsneak();
  }

  private void repressSneak() {
    if (forceRelease && isManualSneak()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
      mc.thePlayer.movementInput.sneak = true;
    }
    forceRelease = false;
  }

  private void clearSneak() {
    sneakingFromModule = false;
    resetUnsneak();
    if (sneakKeyPressed.getValue()) repressSneak();
  }

  private void resetUnsneak() {
    unsneakStartTick = -1;
    sneakJumpStartTick = -1;
    sneakJumpDelayTicks = -1;
    unsneakDelayTicks = -1;
  }

  private boolean isManualSneak() {
    return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Edge detection (SimulatedPlayer equivalent)
  // ══════════════════════════════════════════════════════════════════════════

  private double[] getPredictedMotion() {
    // Horizontal motion without sneak
    double[] hMotion = myau.util.player.MoveUtil.predictMovement();
    // Vertical: if not on ground, apply gravity
    double vMotion = mc.thePlayer.onGround ? 0.0 : mc.thePlayer.motionY;
    return new double[] {hMotion[0], vMotion, hMotion[1]};
  }

  private double computeEdgeOffset(AxisAlignedBB simBox) {
    AxisAlignedBB groundCheck =
        new AxisAlignedBB(
            simBox.minX, simBox.minY - 0.01, simBox.minZ, simBox.maxX, simBox.minY, simBox.maxZ);

    List<AxisAlignedBB> groundBoxes =
        mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
    if (groundBoxes.isEmpty()) return Double.NaN;

    double feetX = (simBox.minX + simBox.maxX) / 2.0;
    double feetZ = (simBox.minZ + simBox.maxZ) / 2.0;

    double minDist = Double.MAX_VALUE;
    for (AxisAlignedBB box : groundBoxes) {
      double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
      double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
      double dx = Math.abs(feetX - closestX);
      double dz = Math.abs(feetZ - closestZ);
      double dist = Math.max(dx, dz);
      minDist = Math.min(minDist, dist);
    }

    return minDist;
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Pre-place rotation
  // ══════════════════════════════════════════════════════════════════════════

  private TargetResult findTarget(float currentPitch, double reach) {
    float yaw = mc.thePlayer.rotationYaw;

    AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
    int standY = MathHelper.floor_double(bbox.minY) - 1;
    int minX = MathHelper.floor_double(bbox.minX);
    int maxX = MathHelper.floor_double(bbox.maxX);
    int minZ = MathHelper.floor_double(bbox.minZ);
    int maxZ = MathHelper.floor_double(bbox.maxZ);

    ArrayList<FaceTarget> targets = new ArrayList<>();
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        BlockPos standBlock = new BlockPos(x, standY, z);
        if (BlockUtil.isReplaceable(standBlock)) continue;
        for (EnumFacing face : SIDES) {
          BlockPos placed = standBlock.offset(face);
          if (!BlockUtil.isReplaceable(placed)) continue;
          targets.add(new FaceTarget(standBlock, face));
        }
      }
    }
    if (targets.isEmpty()) return null;

    float bestDelta = Float.MAX_VALUE;
    float bestPitch = Float.NaN;
    BlockPos bestSupport = null;
    EnumFacing bestFace = null;
    float randScale = 0.2f;

    for (float pitch = 60f; pitch <= 90f; ) {
      float step = 1.0f + (float) (Math.random() * 2 - 1) * (0.3f + randScale * 0.4f);
      if (step < 0.4f) step = 0.4f;
      if (step > 1.8f) step = 1.8f;
      pitch += step;
      float samplePitch = Math.min(pitch, 90f);

      MovingObjectPosition mop = RotationUtil.rayCastBlock(reach, yaw, samplePitch);
      if (mop == null) continue;
      EnumFacing hitFace = mop.sideHit;
      if (hitFace == EnumFacing.UP || hitFace == EnumFacing.DOWN) continue;

      BlockPos hitBlock = mop.getBlockPos();
      for (FaceTarget t : targets) {
        if (hitBlock.equals(t.block) && hitFace == t.face) {
          float delta = Math.abs(samplePitch - currentPitch);
          if (delta < bestDelta) {
            bestDelta = delta;
            bestPitch = samplePitch;
            bestSupport = t.block;
            bestFace = t.face;
          }
          break;
        }
      }
      if (pitch >= 90f) break;
    }

    if (bestSupport == null || bestFace == null || Float.isNaN(bestPitch)) return null;
    return new TargetResult(yaw, bestPitch, bestSupport, bestFace);
  }

  private static class FaceTarget {
    final BlockPos block;
    final EnumFacing face;

    FaceTarget(BlockPos block, EnumFacing face) {
      this.block = block;
      this.face = face;
    }
  }

  private static class TargetResult {
    final float yaw, pitch;
    final BlockPos support;
    final EnumFacing face;

    TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
      this.yaw = yaw;
      this.pitch = pitch;
      this.support = support;
      this.face = face;
    }
  }
}
