package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.math.RandomUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class SneakFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  public int sneakingTicks = -1;
  public int placements = 0;
  public int pause = 0;
  public int slow = 0;
  public int ticksOnAir = 0;

  public final BooleanProperty sneak = new BooleanProperty("sneak", false);
  public final FloatProperty startSneaking =
      new FloatProperty("start-sneaking", 0.0F, 0.0F, 5.0F, () -> this.sneak.getValue());
  public final FloatProperty stopSneaking =
      new FloatProperty("stop-sneaking", 0.0F, 0.0F, 5.0F, () -> this.sneak.getValue());
  public final IntProperty sneakEvery =
      new IntProperty("sneak-every", 1, 1, 10, () -> this.sneak.getValue());
  public final FloatProperty sneakingSpeed =
      new FloatProperty("sneaking-speed", 0.2F, 0.2F, 1.0F, () -> this.sneak.getValue());

  public SneakFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(sneak, startSneaking, stopSneaking, sneakEvery, sneakingSpeed);
  }

  public void calculateSneaking() {
    if (this.ticksOnAir == 0) {
      KeyBinding.setKeyBindState(
          Minecraft.getMinecraft().gameSettings.keyBindSneak.getKeyCode(), false);
    }
    this.sneakingTicks--;
    if (!this.sneak.getValue() && this.pause <= 0) return;

    int ahead = (int) (float) this.startSneaking.getValue();
    int place =
        (int)
            RandomUtil.nextFloat(
                scaffold.options.placeDelay.getValue(),
                scaffold.options.placeDelay.getSecondValue());
    int after = (int) (float) this.stopSneaking.getValue();

    if (this.pause > 0) {
      this.pause--;
      this.sneakingTicks = 0;
      this.placements = 0;
    }

    if (this.sneakingTicks >= 0) {
      KeyBinding.setKeyBindState(
          Minecraft.getMinecraft().gameSettings.keyBindSneak.getKeyCode(), true);
      return;
    }

    if (this.ticksOnAir > 0) this.sneakingTicks = after;

    if (this.ticksOnAir > 0
        || PlayerUtil.blockRelativeToPlayer(
                Minecraft.getMinecraft().thePlayer.motionX * ahead,
                1.0,
                Minecraft.getMinecraft().thePlayer.motionZ * ahead)
            instanceof BlockAir) {
      if (this.placements <= 0) {
        this.sneakingTicks = ahead + place + after;
        this.placements = this.sneakEvery.getValue();
      }
    }
  }

  @Override
  public void onDisable() {
    this.sneakingTicks = -1;
    this.placements = 0;
    this.pause = 0;
    this.slow = 0;
    this.ticksOnAir = 0;
  }
}
