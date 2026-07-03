package miau.module.modules.player.scaffold.rotation;

import miau.util.math.FastNoiseLite;
import net.minecraft.util.MovingObjectPosition;

/**
 * Gothaj "Polar" mode: noise-based yaw/pitch jitter with raycast validation. Uses FastNoiseLite for
 * smooth, non-repeating micro-adjustments.
 */
public class PolarMode extends RotationMode {

  private final FastNoiseLite noise = new FastNoiseLite();

  public PolarMode() {
    super("Polar");
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    if (ctx.blockPos == null || ctx.facing == null) {
      return new float[] {
        ctx.scaffoldYaw, clampPitch(ctx.scaffoldPitch + getNoiseAdd(ctx.polarTicks, 4))
      };
    }

    float currentYaw = ctx.scaffoldYaw;
    float currentPitch = ctx.scaffoldPitch;
    float yawAdd = getNoiseAdd(ctx.polarTicks, 1);
    float pitchAdd = getNoiseAdd(ctx.polarTicks + 50, 4);
    float basePitch = 78.0F;

    if (ctx.moving) {
      MovingObjectPosition check = ctx.rayCast(currentYaw, basePitch, 4.5);
      boolean needsAdjust =
          check == null
              || !check.getBlockPos().equals(ctx.blockPos)
              || (check.sideHit != ctx.facing);

      if (needsAdjust) {
        // Progressive scan: widen yaw offset until we hit the block
        for (int mult = 1; mult <= 360; mult++) {
          float testYaw = currentYaw + yawAdd * mult;
          float testPitch = basePitch + pitchAdd;
          MovingObjectPosition ray = ctx.rayCast(testYaw, testPitch, 4.5);
          if (ray != null && ray.getBlockPos().equals(ctx.blockPos) && ray.sideHit == ctx.facing) {
            return new float[] {testYaw, clampPitch(testPitch)};
          }
          ctx.polarTicks++;
        }
      } else {
        // Already aimed correctly, just apply micro-jitter
        for (int t = 0; t < 100; t++) {
          float testYaw = currentYaw + yawAdd;
          float testPitch = basePitch + pitchAdd;
          MovingObjectPosition ray = ctx.rayCast(testYaw, testPitch, 4.5);
          if (ray != null && ray.getBlockPos().equals(ctx.blockPos) && ray.sideHit == ctx.facing) {
            return new float[] {testYaw, clampPitch(testPitch)};
          }
          ctx.polarTicks++;
        }
      }
    }

    float yaw = currentYaw + yawAdd;
    float pitch = clampPitch(basePitch + pitchAdd);
    return new float[] {yaw, pitch};
  }

  private float getNoiseAdd(float tick, float multiplier) {
    return noise.GetNoise(tick, tick) * multiplier;
  }
}
