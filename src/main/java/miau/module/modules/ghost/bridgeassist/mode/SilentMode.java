package miau.module.modules.ghost.bridgeassist.mode;

import java.util.ArrayList;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.ghost.BridgeAssist;
import miau.util.player.RotationUtil;
import miau.util.world.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;

public class SilentMode {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final EnumFacing[] SIDES = {
    EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
  };

  private final BridgeAssist parent;

  private float lastTargetYaw = Float.NaN;
  private float lastTargetPitch = Float.NaN;
  private float lastSentYaw = Float.NaN;
  private float lastSentPitch = Float.NaN;
  private boolean wasActive = false;

  public SilentMode(BridgeAssist parent) {
    this.parent = parent;
  }

  public void onDisabled() {
    lastTargetYaw = Float.NaN;
    lastTargetPitch = Float.NaN;
    lastSentYaw = Float.NaN;
    lastSentPitch = Float.NaN;
    wasActive = false;
  }

  public void onUpdate(UpdateEvent e) {
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying)
      return;

    ItemStack held = mc.thePlayer.getHeldItem();
    if (held == null || !(held.getItem() instanceof ItemBlock)) {
      resetState();
      return;
    }

    if (parent.silentSneakingOnly.getValue()) {
      if (!Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
        resetState();
        return;
      }
    }

    if (parent.silentEdgeOnly.getValue() && !isNearEdge()) {
      resetState();
      return;
    }

    if (!mc.thePlayer.onGround) {
      resetState();
      return;
    }

    float basePitch = e.getPitch();
    float baseYaw = e.getYaw();
    double reach = mc.playerController.getBlockReachDistance();

    TargetResult target = findTarget(basePitch, reach);
    if (target == null) {
      resetState();
      return;
    }

    float safePitch = target.pitch + 1.5f;

    // Add continuous sinusoidal noise to mimic human hand jitter
    float noiseYaw = (float) (Math.sin(mc.thePlayer.ticksExisted * 0.15) * 0.4f);
    float noisePitch = (float) (Math.cos(mc.thePlayer.ticksExisted * 0.15) * 0.3f);

    float targetYaw = target.yaw + noiseYaw;
    float targetPitch = MathHelper.clamp_float(safePitch + noisePitch, -90f, 90f);

    lastTargetYaw = targetYaw;
    lastTargetPitch = targetPitch;

    float smoothBaseYaw;
    float smoothBasePitch;
    if (wasActive && !Float.isNaN(lastSentYaw)) {
      smoothBaseYaw = lastSentYaw;
      smoothBasePitch = lastSentPitch;
    } else {
      smoothBaseYaw = baseYaw;
      smoothBasePitch = basePitch;
    }

    int speed = parent.silentRotSpeed.getValue().intValue();

    float[] smoothed =
        RotationUtil.smoothRotation(
            smoothBaseYaw, smoothBasePitch, targetYaw, targetPitch, speed, 25f);

    float[] gcdFixed =
        RotationUtil.applySensitivityPatch(
            smoothed[0], smoothed[1], smoothBaseYaw, smoothBasePitch);

    lastSentYaw = gcdFixed[0];
    lastSentPitch = gcdFixed[1];
    wasActive = true;

    e.setRotation(gcdFixed[0], gcdFixed[1], 100);
  }

  public void onMoveInput(MoveInputEvent event) {
    if (parent.silentSneakingOnly.getValue()
        && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
      mc.thePlayer.movementInput.sneak = false;
      mc.thePlayer.movementInput.moveForward /= 0.3F;
      mc.thePlayer.movementInput.moveStrafe /= 0.3F;
    }

    if (wasActive && !Float.isNaN(lastSentYaw) && parent.silentMoveFix.getValue()) {
      float forward = mc.thePlayer.movementInput.moveForward;
      float strafe = mc.thePlayer.movementInput.moveStrafe;

      if (forward == 0 && strafe == 0) return;

      float diff = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - lastSentYaw);
      float diffRad = (float) Math.toRadians(diff);

      float calcForward =
          Math.round(forward * MathHelper.cos(diffRad) + strafe * MathHelper.sin(diffRad));
      float calcStrafe =
          Math.round(strafe * MathHelper.cos(diffRad) - forward * MathHelper.sin(diffRad));

      mc.thePlayer.movementInput.moveForward = calcForward;
      mc.thePlayer.movementInput.moveStrafe = calcStrafe;
    }
  }

  private void resetState() {
    if (wasActive) {
      wasActive = false;
    }
  }

  private boolean isNearEdge() {
    AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
    int standY = MathHelper.floor_double(bbox.minY) - 1;

    double[][] edgeChecks = {
      {bbox.minX - 0.3, bbox.minZ},
      {bbox.maxX + 0.3, bbox.maxZ},
      {bbox.minX, bbox.minZ - 0.3},
      {bbox.maxX, bbox.maxZ + 0.3}
    };

    for (double[] check : edgeChecks) {
      BlockPos edgeBlock = new BlockPos(check[0], standY, check[1]);
      if (BlockUtil.isReplaceable(edgeBlock)) {
        return true;
      }
    }
    return false;
  }

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

    float minHitPitch = Float.MAX_VALUE;
    float maxHitPitch = Float.MIN_VALUE;
    BlockPos bestSupport = null;
    EnumFacing bestFace = null;

    for (float pitch = 55f; pitch <= 90f; pitch += 0.5f) {
      float samplePitch = Math.min(pitch, 90f);

      MovingObjectPosition mop = RotationUtil.rayCastBlock(reach, yaw, samplePitch);
      if (mop == null) continue;
      EnumFacing hitFace = mop.sideHit;
      if (hitFace == EnumFacing.UP || hitFace == EnumFacing.DOWN) continue;

      BlockPos hitBlock = mop.getBlockPos();
      for (FaceTarget t : targets) {
        if (hitBlock.equals(t.block) && hitFace == t.face) {
          if (samplePitch < minHitPitch) minHitPitch = samplePitch;
          if (samplePitch > maxHitPitch) maxHitPitch = samplePitch;
          bestSupport = t.block;
          bestFace = t.face;
          break;
        }
      }
    }

    if (bestSupport == null || bestFace == null || minHitPitch == Float.MAX_VALUE) return null;

    // Select a legit pitch: 15% down from the top edge of the block face.
    float optimalPitch = minHitPitch + (maxHitPitch - minHitPitch) * 0.15f;

    return new TargetResult(yaw, optimalPitch, bestSupport, bestFace);
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
