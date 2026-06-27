package myau.module.modules.movement.speeds;

import myau.event.impl.LivingUpdateEvent;
import myau.event.impl.PacketEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.modules.movement.Speed;
import myau.util.player.MoveUtil;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Vulcan 2.8.8 BHop speed mode.
 *
 * @author CCBlueX (original LiquidBounce)
 */
public class VulcanSpeed extends SpeedMode {

  private boolean jumped;
  private int jumpTicks;

  public VulcanSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    jumped = false;
    jumpTicks = 0;
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!parent.canBoost()) return;

    if (mc.thePlayer.onGround) {
      if (MoveUtil.isMoving()) {
        mc.thePlayer.jump();
        jumped = true;
        jumpTicks = 0;
      }
      mc.thePlayer.movementInput.jump = false;
      return;
    }

    if (!jumped) return;

    jumpTicks++;
    boolean hasSpeed = MoveUtil.getSpeedLevel() > 0;

    switch (jumpTicks) {
      case 1:
        MoveUtil.setSpeed(hasSpeed ? 0.771 : 0.5, MoveUtil.getMoveYaw());
        break;
      case 2:
        MoveUtil.setSpeed(hasSpeed ? 0.605 : 0.31, MoveUtil.getMoveYaw());
        break;
      case 3:
        MoveUtil.setSpeed(hasSpeed ? 0.57 : 0.29, MoveUtil.getMoveYaw());
        mc.thePlayer.motionY = hasSpeed ? -0.5 : -0.37;
        break;
      case 4:
        MoveUtil.setSpeed(hasSpeed ? 0.595 : 0.27, MoveUtil.getMoveYaw());
        break;
      case 5:
        MoveUtil.setSpeed(hasSpeed ? 0.595 : 0.28, MoveUtil.getMoveYaw());
        jumped = false;
        break;
    }

    if (!mc.thePlayer.onGround && hasSpeed && mc.thePlayer.fallDistance > 0) {
      double motionX = mc.thePlayer.motionX * 1.055;
      double motionZ = mc.thePlayer.motionZ * 1.055;
      mc.thePlayer.motionX = motionX;
      mc.thePlayer.motionZ = motionZ;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND
        && event.getPacket() instanceof C03PacketPlayer
        && mc.thePlayer.motionY < 0) {
      ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(true);
    }
  }
}
