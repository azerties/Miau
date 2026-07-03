package miau.module.modules.player.scaffold.rotation;

import net.minecraft.util.MathHelper;

/** Gothaj "Static god" mode: quantized yaw snapped to nearest 45°, fixed pitch at 75.7°. */
public class StaticGodMode extends RotationMode {

  public StaticGodMode() {
    super("Static god");
  }

  @Override
  public float[] getRotations(RotationContext ctx) {
    float quantized = (float) MathHelper.roundUp((int) (ctx.playerYaw + 180.0F), 45);
    return new float[] {quantized, 75.7F};
  }
}
