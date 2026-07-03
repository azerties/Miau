package miau.module.modules.player.scaffold.rotation;

import miau.util.math.FastNoiseLite;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Improved Snap mode: conditional snap to player rotation when standing on solid ground, direction
 * to block when over air. Includes noise micro-jitter for anti-pattern detection.
 */
public class SnapMode extends RotationMode {

  private final FastNoiseLite noise = new FastNoiseLite();

  public SnapMode() {
    super("Snap");
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    boolean isAir =
        mc.theWorld.isAirBlock(
            new BlockPos(mc.thePlayer.posX, Math.floor(mc.thePlayer.posY), mc.thePlayer.posZ));

    // Noise jitter
    float jitter = noise.GetNoise(ctx.polarTicks * 0.7F, ctx.polarTicks * 0.5F) * 0.3F;

    if (!isAir) {
      // Standing on solid ground — use player rotation with slight jitter
      return new float[] {ctx.playerYaw + jitter, ctx.playerPitch + jitter};
    }

    // Over air — aim at block with noise jitter
    if (ctx.blockPos != null && ctx.facing != null) {
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
