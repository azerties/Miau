package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;

public class BlockSafeFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty blockSafe = new BooleanProperty("block-safe", false);
  public final BooleanProperty airSafe = new BooleanProperty("air-safe", false);

  private float lastSimFallDistance = 0f;

  public BlockSafeFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(blockSafe, airSafe);
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (scaffold.betaFeature.isBetaMode()) return;
    if (Scaffold.mc.thePlayer == null || Scaffold.mc.theWorld == null) return;
    if (!blockSafe.getValue()) return;

    SimulatedPlayer simPlayer =
        SimulatedPlayer.fromClientPlayer(Scaffold.mc.thePlayer.movementInput);
    simPlayer.rotationYaw = Scaffold.mc.thePlayer.rotationYaw;
    simPlayer.tick();

    this.lastSimFallDistance = simPlayer.fallDistance;

    if (simPlayer.fallDistance > Scaffold.mc.thePlayer.fallDistance + 0.05
        && scaffold.rotationTick > 0) {
      scaffold.rotationTick = 1;
    }
  }

  public float getLastSimFallDistance() {
    return lastSimFallDistance;
  }

  @Override
  public void onDisable() {
    this.lastSimFallDistance = 0f;
  }
}
