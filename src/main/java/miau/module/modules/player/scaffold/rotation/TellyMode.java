package miau.module.modules.player.scaffold.rotation;

import miau.util.math.FastNoiseLite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

/**
 * Improved Telly mode: timing-based rotation with fallback scanning. Keeps scaffold yaw/pitch
 * between placements, only recalculates when off-ground or falling. Includes noise micro-jitter.
 */
public class TellyMode extends RotationMode {

  private final FastNoiseLite noise = new FastNoiseLite();

  public TellyMode() {
    super("Telly");
    noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    boolean falling = !ctx.onGround && mc.thePlayer.fallDistance >= 0.8F;
    boolean jumping = ctx.onGround || mc.thePlayer.fallDistance == 0.0F;

    // Noise jitter
    float yawJitter = noise.GetNoise(ctx.polarTicks * 0.5F, ctx.polarTicks * 0.5F) * 0.5F;
    float pitchJitter = noise.GetNoise(ctx.polarTicks * 0.4F + 50, ctx.polarTicks * 0.6F) * 0.4F;

    if (ctx.enabledTicks <= 2) {
      // Just enabled — use player yaw as base
      return new float[] {ctx.playerYaw + yawJitter, 79.0F + pitchJitter};
    }

    if (jumping && ctx.blockPos != null && ctx.facing != null) {
      // Slight update toward player direction when on ground
      float newYaw = stepAngle(ctx.scaffoldYaw, ctx.playerYaw, 90.0F);
      float pitch =
          IntaveMode.getYawBasedPitch(ctx.blockPos, ctx.facing, newYaw, ctx.lastPitch, 84);
      return new float[] {newYaw + yawJitter, pitch + pitchJitter};
    }

    if (falling && ctx.blockPos != null && ctx.facing != null) {
      // In air — scan progressively
      float pitch =
          IntaveMode.getYawBasedPitch(ctx.blockPos, ctx.facing, ctx.scaffoldYaw, ctx.lastPitch, 84);
      MovingObjectPosition rotRay = ctx.rayCast(ctx.scaffoldYaw, pitch, 4.5);
      if (rotRay == null || !rotRay.getBlockPos().equals(ctx.blockPos)) {
        float[] dirs =
            directionToBlock(
                ctx.blockPos.getX(), ctx.blockPos.getY(), ctx.blockPos.getZ(), ctx.facing);
        int maxTicks =
            (int) Math.abs(MathHelper.wrapAngleTo180_float(ctx.scaffoldYaw - dirs[0]) / 4.0F);
        float newYaw = ctx.scaffoldYaw;
        for (int t = 0; t <= maxTicks; t++) {
          newYaw = stepAngle(newYaw, dirs[0], 5.0F);
          pitch = IntaveMode.getYawBasedPitch(ctx.blockPos, ctx.facing, newYaw, ctx.lastPitch, 84);
          MovingObjectPosition stop = ctx.rayCast(newYaw, pitch, 4.5);
          if (stop != null && stop.getBlockPos().equals(ctx.blockPos) && stop.sideHit == ctx.facing)
            break;
        }
        return new float[] {newYaw + yawJitter, pitch + pitchJitter};
      }
      return new float[] {ctx.scaffoldYaw + yawJitter, pitch + pitchJitter};
    }

    return new float[] {ctx.scaffoldYaw + yawJitter, ctx.scaffoldPitch + pitchJitter};
  }

  private float[] directionToBlock(double x, double y, double z, EnumFacing facing) {
    double px = x + 0.5 + facing.getDirectionVec().getX() * 0.5;
    double py = y + 0.5 + facing.getDirectionVec().getY() * 0.5;
    double pz = z + 0.5 + facing.getDirectionVec().getZ() * 0.5;
    return rotationsTo(px, py, pz);
  }
}
