package miau.module.modules.player.scaffold.features;

import java.util.Collections;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.util.client.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class BetaFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public int betaAirTicks = 0;
  public int betaGroundTicks = 0;
  public int betaPlaceCooldown = 0;
  public float lastBetaSentYaw = Float.NaN;
  public float lastBetaSentPitch = Float.NaN;
  public long lastBetaPitchQuotient = 0L;
  public int betaPlaceTicks = 999;

  public BetaFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Collections.emptyList();
  }

  public boolean isBetaMode() {
    return scaffold.rotationHandler.rotationMode.getValue() == 4;
  }

  public boolean isBetaTellyMode() {
    return isBetaMode()
        && (scaffold.keepYFeature.keepY.getValue() == 2 || scaffold.keepYFeature.keepY.getValue() == 3)
        && mc.gameSettings != null
        && mc.gameSettings.keyBindUseItem.isKeyDown();
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (!isBetaMode() || mc.thePlayer == null) {
      this.betaAirTicks = 0;
      this.betaGroundTicks = 0;
      this.betaPlaceCooldown = 0;
      return;
    }

    if (mc.thePlayer.onGround) {
      this.betaGroundTicks++;
      this.betaAirTicks = 0;
    } else {
      this.betaAirTicks++;
      this.betaGroundTicks = 0;
    }

    if (this.betaPlaceCooldown > 0) {
      this.betaPlaceCooldown--;
    }

    quietBetaMovement();
  }


  @Override
  public void onMoveInput() {
    quietBetaMovement();
  }

  private void quietBetaMovement() {
    if (!isBetaMode() || isBetaTellyMode() || mc.thePlayer == null) {
      return;
    }
    mc.thePlayer.setSprinting(false);
    if (mc.gameSettings != null) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }
  }

  public boolean canBetaPlaceNow() {
    if (!isBetaMode()) {
      return true;
    }
    if (mc.thePlayer == null || scaffold.placedThisTick || this.betaPlaceCooldown > 0) {
      return false;
    }
    if ((!isBetaTellyMode() && mc.thePlayer.isSprinting())
        || mc.thePlayer.isCollidedHorizontally
        || mc.thePlayer.hurtTime > 0) {
      return false;
    }
    if (mc.thePlayer.onGround) {
      return Math.abs(mc.thePlayer.motionY) < 1.0E-4 && this.betaGroundTicks > 0;
    }
    return this.betaAirTicks > 1;
  }

  @Override
  public void onDisable() {
    this.betaAirTicks = 0;
    this.betaGroundTicks = 0;
    this.betaPlaceCooldown = 0;
    this.lastBetaSentYaw = Float.NaN;
    this.lastBetaSentPitch = Float.NaN;
    this.lastBetaPitchQuotient = 0L;
    this.betaPlaceTicks = 999;
  }
}
