package miau.module.modules.player.scaffold.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

/**
 * Base class for Scaffold rotation modes. Each mode computes custom yaw/pitch aiming at a target
 * block face.
 */
public abstract class RotationMode {

  protected static final Minecraft mc = Minecraft.getMinecraft();

  /** Display name shown in GUI. */
  protected final String name;

  protected RotationMode(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  /**
   * Compute target yaw/pitch for the scaffold to aim at the given block face.
   *
   * @param ctx Context with current block target and player state.
   * @return float[]{yaw, pitch} or null to skip rotation override.
   */
  public abstract float[] getRotations(RotationContext ctx);

  // ========== Shared utilities for subclasses ==========

  /** Minimal angular difference between two angles. */
  protected static float angleDiff(float a, float b) {
    float diff = Math.abs(a - b);
    return diff > 180.0F ? 360.0F - diff : diff;
  }

  /** Smoothly step current toward intended with max delta. */
  protected static float stepAngle(float current, float intended, float maxStep) {
    float delta = MathHelper.wrapAngleTo180_float(intended - current);
    if (delta > maxStep) delta = maxStep;
    if (delta < -maxStep) delta = -maxStep;
    return current + delta;
  }

  /** Atan2-based rotation from player eyes to a target point. */
  protected static float[] rotationsTo(double x, double y, double z) {
    double dx = x - mc.thePlayer.posX;
    double dy = y - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
    double dz = z - mc.thePlayer.posZ;
    double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
    float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
    float pitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
    return new float[] {yaw, pitch};
  }

  /** Apply GCD fix to rotations. */
  protected static float[] applyGCD(float[] rots, float[] lastRots) {
    float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
    float gcd = sens * sens * sens * 8.0F;
    float dy = rots[0] - lastRots[0];
    float dp = rots[1] - lastRots[1];
    return new float[] {lastRots[0] + dy - dy % gcd, lastRots[1] + dp - dp % gcd};
  }

  /** Clamp pitch to valid range. */
  protected static float clampPitch(float pitch) {
    return MathHelper.clamp_float(pitch, -90.0F, 90.0F);
  }
}
