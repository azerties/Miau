package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class EagleFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty eagle = new BooleanProperty("eagle", false);
  public final FloatProperty edgeDistance =
      new FloatProperty("edge-distance", 0.13F, 0.0F, 0.5F, () -> this.eagle.getValue());
  public final IntProperty sneakDelay =
      new IntProperty("sneak-delay", 80, 0, 500, () -> this.eagle.getValue());
  public final IntProperty blocksPerSneak =
      new IntProperty("blocks-per-sneak", 1, 1, 5, () -> this.eagle.getValue());

  private boolean sneaking = false;
  private int sneakTicks = 0;
  private long lastSneakTime = 0L;
  private int blocksPlaced = 0;

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(eagle, edgeDistance, sneakDelay, blocksPerSneak);
  }

  public EagleFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (!this.eagle.getValue()) {
      this.sneaking = false;
      this.sneakTicks = 0;
      return;
    }
    if (this.sneakTicks > 0) {
      this.sneakTicks--;
      if (this.sneakTicks == 0) {
        this.sneaking = false;
      }
      return;
    }
    if (this.shouldSneak()) {
      this.sneaking = true;
      this.sneakTicks = 2;
      this.lastSneakTime = System.currentTimeMillis();
      this.blocksPlaced = 0;
    }
  }

  public void onMoveInput() {
    if (scaffold.isEnabled() && this.sneaking && !mc.thePlayer.movementInput.sneak) {
      mc.thePlayer.movementInput.sneak = true;
      mc.thePlayer.movementInput.moveForward *= 0.3F;
      mc.thePlayer.movementInput.moveStrafe *= 0.3F;
    }
  }

  public void onBlockPlaced() {
    this.blocksPlaced++;
  }

  @Override
  public void onDisable() {
    this.sneaking = false;
    this.sneakTicks = 0;
    this.blocksPlaced = 0;
    this.lastSneakTime = 0L;
  }

  private boolean shouldSneak() {
    if (!this.eagle.getValue() || !mc.thePlayer.onGround) {
      return false;
    }
    if (this.blocksPlaced < this.blocksPerSneak.getValue()) {
      return false;
    }
    if (System.currentTimeMillis() - this.lastSneakTime
        < (long) this.sneakDelay.getValue().intValue()) {
      return false;
    }
    return this.isNearEdge();
  }

  private boolean isNearEdge() {
    if (!mc.thePlayer.onGround) {
      return false;
    }
    double fracX = mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    double fracZ = mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
    double threshold = this.edgeDistance.getValue();
    double minDist = Math.min(Math.min(fracX, 1.0 - fracX), Math.min(fracZ, 1.0 - fracZ));
    return minDist <= threshold;
  }
}
