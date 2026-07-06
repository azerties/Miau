package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class GodbridgeRotation implements IRotationLogic {
  private boolean isOnRightSide = false;

  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    if (Scaffold.mc.thePlayer == null || Scaffold.mc.theWorld == null) return;

    float forward = Scaffold.mc.thePlayer.movementInput.moveForward;
    float strafe = Scaffold.mc.thePlayer.movementInput.moveStrafe;

    if (forward == 0 && strafe == 0) {
      float axisMovement = (float) Math.floor(Scaffold.mc.thePlayer.rotationYaw / 90.0f) * 90.0f;
      scaffold.yaw = RotationUtil.quantizeAngle(axisMovement + 45.0f);
      scaffold.pitch = RotationUtil.quantizeAngle(75.0f);
      return;
    }

    float direction =
        getMovementDirection(forward, strafe, Scaffold.mc.thePlayer.rotationYaw) + 180.0f;
    float movingYaw = Math.round(direction / 45.0f) * 45.0f;
    boolean isMovingStraight = (movingYaw % 90.0f) == 0.0f;

    if (isMovingStraight) {
      if (Scaffold.mc.thePlayer.onGround) {
        isOnRightSide =
            Math.floor(Scaffold.mc.thePlayer.posX + Math.cos(Math.toRadians(movingYaw)) * 0.5)
                    != Math.floor(Scaffold.mc.thePlayer.posX)
                || Math.floor(
                        Scaffold.mc.thePlayer.posZ + Math.sin(Math.toRadians(movingYaw)) * 0.5)
                    != Math.floor(Scaffold.mc.thePlayer.posZ);

        EnumFacing facing = EnumFacing.fromAngle(movingYaw);
        BlockPos posInDirection =
            new BlockPos(
                Scaffold.mc.thePlayer.posX + facing.getFrontOffsetX() * 0.6,
                Scaffold.mc.thePlayer.posY,
                Scaffold.mc.thePlayer.posZ + facing.getFrontOffsetZ() * 0.6);

        BlockPos currentPos =
            new BlockPos(
                Scaffold.mc.thePlayer.posX, Scaffold.mc.thePlayer.posY, Scaffold.mc.thePlayer.posZ);
        boolean isLeaningOffBlock = Scaffold.mc.theWorld.isAirBlock(currentPos.down());
        boolean nextBlockIsAir = Scaffold.mc.theWorld.isAirBlock(posInDirection.down());

        if (isLeaningOffBlock && nextBlockIsAir) {
          isOnRightSide = !isOnRightSide;
        }
      }

      float finalYaw = movingYaw + (isOnRightSide ? 45.0f : -45.0f);
      scaffold.yaw = RotationUtil.quantizeAngle(finalYaw);
      scaffold.pitch = RotationUtil.quantizeAngle(75.7f);
    } else {
      scaffold.yaw = RotationUtil.quantizeAngle(movingYaw);
      scaffold.pitch = RotationUtil.quantizeAngle(75.6f);
    }
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
