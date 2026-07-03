package miau.module.modules.player.scaffold.rotation;

import miau.util.math.FastNoiseLite;
import net.minecraft.util.MovingObjectPosition;

/**
 * Improved Direct mode: finds the lowest pitch that can still see the target block, with
 * noise-based micro-jitter for anti-pattern detection.
 */
public class DirectMode extends RotationMode {

  private final FastNoiseLite noise = new FastNoiseLite();

  public DirectMode() {
    super("Direct");
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    if (ctx.blockPos == null || ctx.facing == null) return null;

    // Noise jitter for anti-pattern
    float noiseYaw = noise.GetNoise(ctx.polarTicks * 0.5F, ctx.polarTicks * 0.3F) * 0.8F;
    float noisePitch = noise.GetNoise(ctx.polarTicks * 0.4F + 100, ctx.polarTicks * 0.6F) * 0.6F;

    float baseYaw = ctx.playerYaw + 180.0F + noiseYaw;
    float pitch = ctx.lastPitch;

    // Scan pitch from 90 down to 30 with noise-like step variation
    float step = 0.5F + (float) (Math.random() * 0.5F);
    for (float p = 90.0F; p > 30.0F; p -= step) {
      MovingObjectPosition mop = ctx.rayCast(baseYaw, p, 4.5);
      if (mop != null
          && mop.getBlockPos() != null
          && mop.getBlockPos().equals(ctx.blockPos)
          && mop.sideHit == ctx.facing) {
        pitch = p + noisePitch;
        break;
      }
    }
    return new float[] {baseYaw, clampPitch(pitch)};
  }
}
