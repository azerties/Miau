package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class GodbridgeFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty godBridge = new BooleanProperty("godbridge", false);
  public final ModeProperty godBridgeLedgeMode =
      new ModeProperty(
          "godbridge-ledge",
          0,
          new String[] {"JUMP", "SNEAK", "STOP", "BACKWARDS"},
          () -> this.godBridge.getValue());
  public final IntProperty godBridgeForceSneakBelowCount =
      new IntProperty(
          "godbridge-force-sneak",
          3,
          0,
          10,
          () -> this.godBridge.getValue() && this.godBridgeLedgeMode.getValue() == 1);
  public final FloatProperty godBridgeEdgeDistance =
      new FloatProperty(
          "godbridge-edge-dist",
          0.13F,
          0.0F,
          0.5F,
          () -> this.godBridge.getValue() && this.godBridgeLedgeMode.getValue() == 1);
  public final IntProperty godBridgeSneakDelay =
      new IntProperty(
          "godbridge-sneak-ticks",
          1,
          1,
          10,
          () -> this.godBridge.getValue() && this.godBridgeLedgeMode.getValue() == 1);

  public final BooleanProperty jumpAutomatically =
      new BooleanProperty(
          "jump-automatically",
          true,
          () -> this.godBridge.getValue() && this.godBridgeLedgeMode.getValue() == 0);
  public final IntProperty jumpPerBlockMin =
      new IntProperty(
          "jump-per-block-min",
          6,
          1,
          10,
          () ->
              this.godBridge.getValue()
                  && this.godBridgeLedgeMode.getValue() == 0
                  && !this.jumpAutomatically.getValue());
  public final IntProperty jumpPerBlockMax =
      new IntProperty(
          "jump-per-block-max",
          8,
          1,
          10,
          () ->
              this.godBridge.getValue()
                  && this.godBridgeLedgeMode.getValue() == 0
                  && !this.jumpAutomatically.getValue());

  private boolean sneaking = false;
  private int sneakTicks = 0;
  private int forceSneak = 0;

  private int blocksPlaced = 0;
  private int targetJumpBlocks = 0;

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(
        godBridge,
        godBridgeLedgeMode,
        godBridgeForceSneakBelowCount,
        godBridgeEdgeDistance,
        godBridgeSneakDelay,
        jumpAutomatically,
        jumpPerBlockMin,
        jumpPerBlockMax);
  }

  public void onBlockPlaced() {
    if (this.godBridge.getValue()
        && this.godBridgeLedgeMode.getValue() == 0
        && !this.jumpAutomatically.getValue()) {
      blocksPlaced++;
    }
  }

  public GodbridgeFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (!this.godBridge.getValue()) {
      this.sneaking = false;
      this.sneakTicks = 0;
      this.forceSneak = 0;
      return;
    }

    if (this.sneakTicks > 0) {
      this.sneakTicks--;
      if (this.sneakTicks == 0) {
        this.sneaking = false;
      }
    }
  }

  public void onMoveInput() {
    if (!scaffold.isEnabled() || !this.godBridge.getValue()) return;

    if (forceSneak > 0) {
      mc.thePlayer.movementInput.sneak = true;
      forceSneak--;
    }

    if (isNearEdge()) {
      int blockCount = scaffold.getBlockCount();
      int currentMode =
          blockCount < godBridgeForceSneakBelowCount.getValue() ? 1 : godBridgeLedgeMode.getValue();

      switch (currentMode) {
        case 0: // JUMP
          if (this.jumpAutomatically.getValue()) {
            mc.thePlayer.movementInput.jump = true;
          } else {
            if (this.blocksPlaced >= this.targetJumpBlocks) {
              mc.thePlayer.movementInput.jump = true;
              this.blocksPlaced = 0;
              this.targetJumpBlocks =
                  miau.util.math.RandomUtil.nextInt(
                      this.jumpPerBlockMin.getValue(), this.jumpPerBlockMax.getValue());
            }
          }
          break;
        case 1: // SNEAK
          int delay = godBridgeSneakDelay.getValue();
          if (delay > forceSneak) {
            mc.thePlayer.movementInput.sneak = true;
            forceSneak = delay;
          }
          break;
        case 2: // STOP
          mc.thePlayer.movementInput.moveForward = 0.0f;
          mc.thePlayer.movementInput.moveStrafe = 0.0f;
          break;
        case 3: // BACKWARDS
          mc.thePlayer.movementInput.moveForward = -1.0f;
          break;
      }
    }
  }

  @Override
  public void onDisable() {
    this.sneaking = false;
    this.sneakTicks = 0;
    this.forceSneak = 0;
    this.blocksPlaced = 0;
    this.targetJumpBlocks = 0;
  }

  private boolean isNearEdge() {
    if (!mc.thePlayer.onGround) {
      return false;
    }
    double fracX = mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    double fracZ = mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
    double threshold = this.godBridgeEdgeDistance.getValue();
    double minDist = Math.min(Math.min(fracX, 1.0 - fracX), Math.min(fracZ, 1.0 - fracZ));
    return minDist <= threshold;
  }
}
