package miau.module.modules.player.scaffold.rotation;

import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

/** Shared context passed to RotationMode.getRotations(). Carries target block and player state. */
public class RotationContext {

  public final Minecraft mc;

  /** The block we want to place on (the support block). */
  public BlockPos blockPos = null;

  /** Which face of blockPos to place against. */
  public EnumFacing facing = null;

  /** Current scaffold tracking yaw/pitch. */
  public float scaffoldYaw;

  public float scaffoldPitch;

  /** Last frame yaw/pitch for GCD fix. */
  public float lastYaw;

  public float lastPitch;

  /** Whether the player is moving. */
  public boolean moving;

  /** Whether the player is on ground. */
  public boolean onGround;

  /** Ticks since the scaffold was enabled. */
  public int enabledTicks;

  /** Scaffold internal tick counter for noise animations. */
  public float polarTicks;

  /** Player's original rotation. */
  public float playerYaw;

  public float playerPitch;

  public RotationContext() {
    this.mc = Minecraft.getMinecraft();
    this.polarTicks = 0;
  }

  /** Raycast using our util with hitbox expansion. */
  public MovingObjectPosition rayCast(float yaw, float pitch, double range) {
    return RotationUtil.rayCastBlock(range, yaw, pitch);
  }
}
