package miau.module.modules.player.scaffold.rotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.property.Property;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.player.RotationUtil;
import net.minecraft.util.MathHelper;

public class RotationHandler {
  private final Scaffold scaffold;
  private final Map<Integer, IRotationLogic> rotationLogics = new HashMap<>();

  public final ModeProperty rotationMode =
      new ModeProperty("rotations", 2, new String[] {"NONE", "DEFAULT", "BACKWARDS", "GODBIRGDE", "BETA"});

  public List<Property<?>> getProperties() {
    return Arrays.asList(rotationMode);
  }

  public RotationHandler(Scaffold scaffold) {
    this.scaffold = scaffold;

    rotationLogics.put(1, new DefaultRotation());
    rotationLogics.put(2, new BackwardsRotation());
    rotationLogics.put(3, new GodbridgeRotation());
    rotationLogics.put(4, new BetaRotation());
  }

  public void handleInitialRotation(
      UpdateEvent event, float currentYaw, float yawDiffTo180, float diagonalYaw) {
    IRotationLogic logic = rotationLogics.get(rotationMode.getValue());
    if (logic != null) {
      logic.handleInitialRotation(scaffold, event, currentYaw, yawDiffTo180, diagonalYaw);
    }
  }

  public void handleUpdateRotation(
      UpdateEvent event,
      float yawDiffTo180,
      float diagonalYaw,
      boolean snapMode,
      boolean towerRotating) {
    float placeYaw = scaffold.yaw;
    float placePitch = scaffold.pitch;
    if (rotationMode.getValue() != 0 && (!snapMode || scaffold.snapRotating || towerRotating)) {
      float targetYaw = scaffold.yaw;
      float targetPitch = scaffold.pitch;
      if (scaffold.towering
          && (Scaffold.mc.thePlayer.motionY > 0.0
              || Scaffold.mc.thePlayer.posY > (double) (scaffold.startY + 1))) {
        float yawDiff = MathHelper.wrapAngleTo180_float(scaffold.yaw - event.getYaw());
        float tolerance =
            scaffold.rotationTick >= 2
                ? RandomUtil.nextFloat(90.0F, 95.0F)
                : RandomUtil.nextFloat(30.0F, 35.0F);
        if (Math.abs(yawDiff) > tolerance) {
          float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
          targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
          scaffold.rotationTick = Math.max(scaffold.rotationTick, 1);
        }
      }
      if (towerRotating && scaffold.isTowering()) {
        float yawDelta =
            MathHelper.wrapAngleTo180_float(Scaffold.mc.thePlayer.rotationYaw - event.getYaw());
        targetYaw =
            RotationUtil.quantizeAngle(
                event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
        scaffold.rotationTick = 3;
        scaffold.towering = true;
      }
      if (rotationMode.getValue() == 4) {
        if (!Float.isNaN(scaffold.betaFeature.lastBetaSentYaw)) {
          boolean clampE = scaffold.betaFeature.betaPlaceTicks < 3;
          float[] corrected =
              RotationUtil.antiDetectionRotation(
                  targetYaw,
                  targetPitch,
                  scaffold.betaFeature.lastBetaSentYaw,
                  scaffold.betaFeature.lastBetaSentPitch,
                  scaffold.betaFeature.lastBetaPitchQuotient,
                  clampE);
          targetYaw = corrected[0];
          targetPitch = corrected[1];
          placeYaw = corrected[0];
          placePitch = corrected[1];

          float mcpSens =
              (float)
                  (Scaffold.mc.gameSettings.mouseSensitivity
                          * (1.0 + Math.random() / 10000000.0)
                          * 0.6F
                      + 0.2F);
          double m = mcpSens * mcpSens * mcpSens * 8.0F * 0.15D;
          scaffold.betaFeature.lastBetaPitchQuotient =
              Math.round((corrected[1] - scaffold.betaFeature.lastBetaSentPitch) / m);
          if (scaffold.betaFeature.lastBetaPitchQuotient == 0L) {
            scaffold.betaFeature.lastBetaPitchQuotient =
                corrected[1] > scaffold.betaFeature.lastBetaSentPitch ? 1L : -1L;
          }
        }
        scaffold.betaFeature.lastBetaSentYaw = targetYaw;
        scaffold.betaFeature.lastBetaSentPitch = targetPitch;
        scaffold.betaFeature.betaPlaceTicks++;
      }
      
      event.setRotation(targetYaw, targetPitch, 3);
      if (scaffold.options.moveFix.getValue() == 1) {
        event.setPervRotation(targetYaw, 3);
      }
    }
  }
}
