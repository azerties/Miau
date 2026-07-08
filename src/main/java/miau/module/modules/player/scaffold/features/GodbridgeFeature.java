package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;

public class GodbridgeFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty godBridge = new BooleanProperty("godbridge", false);

  public final ModeProperty godBridgeLedgeMode =
      new ModeProperty("godbridge-ledge", 0, new String[] {"JUMP", "SNEAK", "STOP", "BACKWARDS"});
  public final IntProperty godBridgeForceSneakBelowCount =
      new IntProperty("godbridge-force-sneak", 3, 0, 10);
  public final FloatProperty godBridgeEdgeDistance =
      new FloatProperty("godbridge-edge-dist", 0.13F, 0.0F, 0.5F);
  public final IntProperty godBridgeSneakDelay = new IntProperty("godbridge-sneak-ticks", 1, 1, 10);

  // ─── Jump settings ────
  public final BooleanProperty jumpAutomatically = new BooleanProperty("jump-automatically", true);
  public final IntProperty jumpPerBlockMin = new IntProperty("jump-per-block-min", 6, 1, 10);
  public final IntProperty jumpPerBlockMax = new IntProperty("jump-per-block-max", 8, 1, 10);

  // ─── LiquidBounce-ported GodBridge settings ────
  public final BooleanProperty waitForRotations = new BooleanProperty("wait-for-rotations", false);
  public final BooleanProperty useOptimizedPitch =
      new BooleanProperty("use-optimized-pitch", false);
  public final FloatProperty godbridgePitch =
      new FloatProperty("godbridge-pitch", 73.5F, 0.0F, 90.0F);
  public final BooleanProperty jumpAutoSimulate = new BooleanProperty("jump-auto-simulate", true);

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
        jumpPerBlockMax,
        waitForRotations,
        useOptimizedPitch,
        godbridgePitch,
        jumpAutoSimulate);
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
    // Set visibility checkers in constructor so they can check rotationMode too
    BooleanSupplier godbridgeOrRotationMode =
        () -> this.godBridge.getValue() || isRotationGodbridge();
    BooleanSupplier ledgeMode1 =
        () ->
            (this.godBridge.getValue() || isRotationGodbridge())
                && this.godBridgeLedgeMode.getValue() == 1;
    BooleanSupplier ledgeMode0 =
        () ->
            (this.godBridge.getValue() || isRotationGodbridge())
                && this.godBridgeLedgeMode.getValue() == 0;
    BooleanSupplier ledgeMode0NoAuto =
        () ->
            (this.godBridge.getValue() || isRotationGodbridge())
                && this.godBridgeLedgeMode.getValue() == 0
                && !this.jumpAutomatically.getValue();

    godBridgeLedgeMode.setVisibleChecker(godbridgeOrRotationMode);
    godBridgeForceSneakBelowCount.setVisibleChecker(ledgeMode1);
    godBridgeEdgeDistance.setVisibleChecker(ledgeMode1);
    godBridgeSneakDelay.setVisibleChecker(ledgeMode1);
    jumpAutomatically.setVisibleChecker(ledgeMode0);
    jumpPerBlockMin.setVisibleChecker(ledgeMode0NoAuto);
    jumpPerBlockMax.setVisibleChecker(ledgeMode0NoAuto);
    waitForRotations.setVisibleChecker(godbridgeOrRotationMode);
    useOptimizedPitch.setVisibleChecker(godbridgeOrRotationMode);
    godbridgePitch.setVisibleChecker(
        () ->
            (this.godBridge.getValue() || isRotationGodbridge())
                && !this.useOptimizedPitch.getValue());
    jumpAutoSimulate.setVisibleChecker(
        () ->
            (this.godBridge.getValue() || isRotationGodbridge())
                && this.godBridgeLedgeMode.getValue() == 0);
  }

  private boolean isRotationGodbridge() {
    return scaffold.rotationHandler.rotationMode.getValue() == 4;
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

  /**
   * Called from Scaffold.onMoveInput. Handles: - WaitForRotations: hold sneak until rotation
   * difference is acceptable - SimulatedPlayer jump prediction: if sim player would fall off edge,
   * force jump - Ledge modes: JUMP, SNEAK, STOP, BACKWARDS
   */
  public void onMoveInput() {
    if (!scaffold.isEnabled() || !this.godBridge.getValue()) return;

    // ─── WaitForRotations: hold sneak while rotation is still changing ────
    if (waitForRotations.getValue() && Scaffold.mc.thePlayer != null) {
      float yawDiff =
          Math.abs(
              MathHelper.wrapAngleTo180_float(scaffold.yaw - Scaffold.mc.thePlayer.rotationYaw));
      float pitchDiff = Math.abs(scaffold.pitch - Scaffold.mc.thePlayer.rotationPitch);
      float rotationDiff = (float) Math.hypot(yawDiff, pitchDiff);
      float fixedDelta = getFixedAngleDelta();
      if (rotationDiff > fixedDelta) {
        mc.thePlayer.movementInput.sneak = true;
      }
    }

    // ─── SimulatedPlayer jump prediction ────
    if (jumpAutoSimulate.getValue()
        && godBridgeLedgeMode.getValue() == 0
        && Scaffold.mc.thePlayer != null
        && Scaffold.mc.thePlayer.onGround) {
      SimulatedPlayer simPlayer =
          SimulatedPlayer.fromClientPlayer(Scaffold.mc.thePlayer.movementInput);
      simPlayer.tick();
      if (!simPlayer.onGround) {
        mc.thePlayer.movementInput.jump = true;
      }
    }

    // ─── Force sneak ────
    if (forceSneak > 0) {
      mc.thePlayer.movementInput.sneak = true;
      forceSneak--;
    }

    // ─── Ledge handling ────
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
    if (Scaffold.mc.thePlayer == null || !Scaffold.mc.thePlayer.onGround) {
      return false;
    }
    // LiquidBounce-style edge detection using fractional position
    double fracX = Scaffold.mc.thePlayer.posX - Math.floor(Scaffold.mc.thePlayer.posX);
    double fracZ = Scaffold.mc.thePlayer.posZ - Math.floor(Scaffold.mc.thePlayer.posZ);
    double threshold = this.godBridgeEdgeDistance.getValue();
    double minDist = Math.min(Math.min(fracX, 1.0 - fracX), Math.min(fracZ, 1.0 - fracZ));
    return minDist <= threshold;
  }

  /** Computes the smallest angle possible with the current mouse sensitivity (GCD fix). */
  private static float getFixedAngleDelta() {
    float sensitivity = Minecraft.getMinecraft().gameSettings.mouseSensitivity;
    return (float) (Math.pow(sensitivity * 0.6f + 0.2f, 3) * 1.2f);
  }

  /** Minecraft's MathHelper.wrapAngleTo180_float (duplicated here to avoid import). */
  private static class MathHelper {
    static float wrapAngleTo180_float(float value) {
      value %= 360.0f;
      if (value >= 180.0f) value -= 360.0f;
      if (value < -180.0f) value += 360.0f;
      return value;
    }
  }
}
