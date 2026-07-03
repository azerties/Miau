package miau.module.modules.player.scaffold.rotation;

import miau.util.math.FastNoiseLite;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Improved Keep mode: uses direction-to-block rotations when standing over air, else uses player
 * rotation. Includes noise micro-jitter for anti-pattern detection.
 */
public class KeepMode extends RotationMode {

  private final FastNoiseLite noise = new FastNoiseLite();

  public KeepMode() {
    super("Keep");
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    if (ctx.blockPos == null || ctx.facing == null)
      return new float[] {ctx.scaffoldYaw, ctx.scaffoldPitch};

    // Noise jitter
    float jitter = noise.GetNoise(ctx.polarTicks * 0.6F, ctx.polarTicks * 0.4F) * 0.4F;

    // Check if block at player feet level is air
    boolean airBelow =
        mc.theWorld.isAirBlock(
            new BlockPos(
                mc.thePlayer.posX,
                (double) ctx.enabledTicks > 0
                    ? Math.floor(mc.thePlayer.posY) - 1
                    : Math.floor(mc.thePlayer.posY),
                mc.thePlayer.posZ));

    if (airBelow && ctx.blockPos != null && ctx.facing != null) {
      float[] dirs =
          directionToBlock(
              ctx.blockPos.getX(), ctx.blockPos.getY(), ctx.blockPos.getZ(), ctx.facing);
      return new float[] {dirs[0] + jitter, dirs[1] + jitter};
    }
    return new float[] {ctx.scaffoldYaw + jitter, ctx.scaffoldPitch + jitter};
  }

  private float[] directionToBlock(double x, double y, double z, EnumFacing facing) {
    double px = x + 0.5 + facing.getDirectionVec().getX() * 0.5;
    double py = y + 0.5 + facing.getDirectionVec().getY() * 0.5;
    double pz = z + 0.5 + facing.getDirectionVec().getZ() * 0.5;
    return rotationsTo(px, py, pz);
  }
}
