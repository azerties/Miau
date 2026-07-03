package miau.module.modules.player.scaffold.rotation;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

/**
 * Gothaj "Intave" mode: uses getYawBasedPitch with raycast validation. Progressive scanning toward
 * the target block face.
 */
public class IntaveMode extends RotationMode {

  public IntaveMode() {
    super("Intave");
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    if (ctx.blockPos == null || ctx.facing == null)
      return new float[] {ctx.scaffoldYaw, ctx.scaffoldPitch};

    float currentYaw = ctx.scaffoldYaw;
    float currentPitch = ctx.scaffoldPitch;

    // Check if current rotation already sees the target
    MovingObjectPosition rotRay = ctx.rayCast(currentYaw, currentPitch, 4.5);
    currentPitch = getYawBasedPitch(ctx.blockPos, ctx.facing, currentYaw, ctx.lastPitch, 84);

    if (rotRay == null || !rotRay.getBlockPos().equals(ctx.blockPos)) {
      // Need to rotate toward the block
      float[] dirs =
          directionToBlock(
              ctx.blockPos.getX(), ctx.blockPos.getY(), ctx.blockPos.getZ(), ctx.facing);
      int maxTicks = (int) Math.abs(MathHelper.wrapAngleTo180_float(currentYaw - dirs[0]) / 4.0F);
      for (int t = 0; t <= maxTicks; t++) {
        currentYaw = stepAngle(currentYaw, dirs[0], 5.0F);
        currentPitch = getYawBasedPitch(ctx.blockPos, ctx.facing, currentYaw, ctx.lastPitch, 84);
        MovingObjectPosition stopRay = ctx.rayCast(currentYaw, currentPitch, 4.5);
        if (stopRay != null
            && stopRay.getBlockPos().equals(ctx.blockPos)
            && stopRay.sideHit == ctx.facing) {
          break;
        }
      }
    }

    return new float[] {currentYaw, currentPitch};
  }

  /** Gothaj's getYawBasedPitch: find max pitch that can see the target block. */
  public static float getYawBasedPitch(
      BlockPos blockPos, EnumFacing facing, float yaw, float lastPitch, int maxPitch) {
    float inc = (float) (Math.random() / 20.0 + 0.05);
    for (float pitch = (float) maxPitch; pitch > 45.0F; pitch -= inc) {
      MovingObjectPosition ray =
          RotationMode.mc.theWorld.rayTraceBlocks(
              RotationMode.mc.thePlayer.getPositionEyes(1.0F),
              RotationMode.mc
                  .thePlayer
                  .getPositionEyes(1.0F)
                  .addVector(
                      -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 4.5,
                      -Math.sin(Math.toRadians(pitch)) * 4.5,
                      Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 4.5),
              false,
              false,
              false);
      if (ray != null
          && ray.getBlockPos() != null
          && ray.getBlockPos().equals(blockPos)
          && ray.sideHit == facing) {
        return pitch;
      }
    }
    return lastPitch;
  }

  private float[] directionToBlock(double x, double y, double z, EnumFacing facing) {
    double px = x + 0.5 + facing.getDirectionVec().getX() * 0.5;
    double py = y + 0.5 + facing.getDirectionVec().getY() * 0.5;
    double pz = z + 0.5 + facing.getDirectionVec().getZ() * 0.5;
    return rotationsTo(px, py, pz);
  }
}
