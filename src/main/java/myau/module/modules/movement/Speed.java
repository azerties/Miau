package myau.module.modules.movement;

import java.util.concurrent.ThreadLocalRandom;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.JumpEvent;
import myau.event.impl.LivingUpdateEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.StrafeEvent;
import myau.event.types.Priority;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.module.modules.movement.speeds.VulcanSpeed;
import myau.module.modules.player.Scaffold;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

public class Speed extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode =
      new ModeProperty("mode", 0, new String[] {"DEFAULT", "LEGIT", "LowHop", "VULCAN"});
  public final FloatProperty multiplier =
      new FloatProperty("multiplier", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 0);
  public final FloatProperty friction =
      new FloatProperty("friction", 1.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 0);
  public final PercentProperty strafe =
      new PercentProperty("strafe", 0, () -> this.mode.getValue() == 0);
  public final FloatProperty sevenTickSpeed =
      new FloatProperty("lowhop-speed", 2.0F, 0.8F, 2.5F, () -> this.mode.getValue() == 2);
  public final BooleanProperty liquidDisable =
      new BooleanProperty("disable-in-liquid", true, () -> this.mode.getValue() == 2);
  public final BooleanProperty sneakDisable =
      new BooleanProperty("disable-while-sneaking", true, () -> this.mode.getValue() == 2);
  public final BooleanProperty jumpMoving =
      new BooleanProperty("only-jump-when-moving", true, () -> this.mode.getValue() == 2);

  private boolean hopping;
  private int lastMode = -1;

  public final VulcanSpeed vulcan = new VulcanSpeed("VULCAN", this);

  public boolean canBoost() {
    Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
    return !scaffold.isEnabled()
        && MoveUtil.isForwardPressed()
        && mc.thePlayer.getFoodStats().getFoodLevel() > 6
        && !mc.thePlayer.isSneaking()
        && !mc.thePlayer.isInWater()
        && !mc.thePlayer.isInLava()
        && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
  }

  private boolean canSevenTick() {
    if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.capabilities.isFlying) {
      return false;
    }
    if (this.liquidDisable.getValue() && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) {
      return false;
    }
    return !this.sneakDisable.getValue() || !mc.thePlayer.isSneaking();
  }

  private double randomizeDouble(double min, double max) {
    return min + (max - min) * ThreadLocalRandom.current().nextDouble();
  }

  public Speed() {
    super("Speed", false);
  }

  @EventTarget(Priority.LOW)
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled() && this.mode.getValue() == 0 && this.canBoost()) {
      if (mc.thePlayer.onGround) {
        mc.thePlayer.motionY = 0.42F;
        MoveUtil.setSpeed(
            MoveUtil.getJumpMotion() * (double) this.multiplier.getValue().floatValue(),
            MoveUtil.getMoveYaw());
      } else {
        if (this.friction.getValue() != 1.0F) {
          event.setFriction(event.getFriction() * this.friction.getValue());
        }
        if (this.strafe.getValue() > 0) {
          double speed = MoveUtil.getSpeed();
          MoveUtil.setSpeed(
              speed * (double) ((float) (100 - this.strafe.getValue()) / 100.0F),
              MoveUtil.getDirectionYaw());
          MoveUtil.addSpeed(
              speed * (double) ((float) this.strafe.getValue().intValue() / 100.0F),
              MoveUtil.getMoveYaw());
          MoveUtil.setSpeed(speed);
        }
      }
    }
  }

  @EventTarget(Priority.LOW)
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled()) {
      return;
    }

    if (this.mode.getValue() != lastMode) {
      ((myau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
      lastMode = this.mode.getValue();
    }

    if (this.mode.getValue() == 2) {
      if (!this.canSevenTick()) {
        return;
      }

      if (mc.thePlayer.onGround && (!this.jumpMoving.getValue() || MoveUtil.isMoving())) {
        mc.thePlayer.jump();

        double speed = this.sevenTickSpeed.getValue() - 0.52D;
        int speedAmplifier = MoveUtil.getSpeedLevel();
        if (speedAmplifier == 1) {
          speed += 0.02D;
        } else if (speedAmplifier == 2) {
          speed += 0.04D;
        } else if (speedAmplifier >= 3) {
          speed += 0.1D;
        }

        if (MoveUtil.isMoving()) {
          MoveUtil.setSpeed(speed - this.randomizeDouble(0.0001D, 0.0003D), MoveUtil.getMoveYaw());
        }
        this.hopping = true;
      }

      if (!mc.thePlayer.onGround) {
        this.hopping = false;
      }
      mc.thePlayer.movementInput.jump = false;
      return;
    }

    if (this.mode.getValue() == 3) {
      this.vulcan.onLivingUpdate(event);
      return;
    }

    if (this.canBoost()) {
      if (this.mode.getValue() == 1) {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed()) {
          mc.thePlayer.jump();
        }
        return;
      }
      mc.thePlayer.movementInput.jump = false;
    }
  }

  @EventTarget(Priority.LOW)
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled()) return;
    if (this.mode.getValue() == 3) {
      this.vulcan.onPacket(event);
    }
  }

  @Override
  public void onDisabled() {
    this.hopping = false;
    ((myau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
  }

  @EventTarget(Priority.LOW)
  public void onJump(JumpEvent event) {
    if (!this.isEnabled()) return;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }
}
