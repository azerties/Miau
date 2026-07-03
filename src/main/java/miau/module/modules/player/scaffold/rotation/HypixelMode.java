package miau.module.modules.player.scaffold.rotation;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

/** Gothaj "Hypixel" mode: variant of Intave with max pitch 90° and smoother fallback. */
public class HypixelMode extends RotationMode {

  public HypixelMode() {
    super("Hypixel");
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    if (ctx.blockPos == null || ctx.facing == null)
      return new float[] {ctx.scaffoldYaw, ctx.scaffoldPitch};

    float currentYaw = ctx.scaffoldYaw;
    float currentPitch = ctx.scaffoldPitch;

    MovingObjectPosition rotRay = ctx.rayCast(currentYaw, currentPitch, 4.5);
    currentPitch =
        IntaveMode.getYawBasedPitch(ctx.blockPos, ctx.facing, currentYaw, ctx.lastPitch, 90);

    if (rotRay == null || !rotRay.getBlockPos().equals(ctx.blockPos)) {
      float[] dirs =
          directionToBlock(
              ctx.blockPos.getX(), ctx.blockPos.getY(), ctx.blockPos.getZ(), ctx.facing);
      int maxTicks = (int) Math.abs(MathHelper.wrapAngleTo180_float(currentYaw - dirs[0]) / 4.0F);
      for (int t = 0; t <= maxTicks; t++) {
        currentYaw = stepAngle(currentYaw, dirs[0], 5.0F);
        currentPitch =
            IntaveMode.getYawBasedPitch(ctx.blockPos, ctx.facing, currentYaw, ctx.lastPitch, 90);
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

  private float[] directionToBlock(double x, double y, double z, EnumFacing facing) {
    double px = x + 0.5 + facing.getDirectionVec().getX() * 0.5;
    double py = y + 0.5 + facing.getDirectionVec().getY() * 0.5;
    double pz = z + 0.5 + facing.getDirectionVec().getZ() * 0.5;
    return rotationsTo(px, py, pz);
  }
}
