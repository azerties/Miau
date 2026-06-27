package myau.module.modules.misc.disabler;

import java.util.Random;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.module.modules.misc.Disabler;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/**
 * Vulcan disabler mode.
 *
 * @author TrimoneWasTaken (original LiquidBounce)
 */
public class VulcanTestDisabler extends DisablerMode {

  private final Random random = new Random();

  public VulcanTestDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (event.getType() != EventType.PRE) return;

    if (mc.thePlayer.isInWater()
        || mc.thePlayer.isInLava()
        || mc.thePlayer.isDead
        || mc.thePlayer.capabilities.isFlying) {
      return;
    }

    sendPacketNoEvent(
        new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
    sendPacketNoEvent(
        new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));

    if (mc.thePlayer.ticksExisted % 9 == 0 && mc.thePlayer.onGround && random.nextFloat() <= 0.7f) {
      sendPacketNoEvent(
          new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
    }
  }

  private void sendPacketNoEvent(net.minecraft.network.Packet<?> packet) {
    if (mc.getNetHandler() != null) {
      mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }
  }
}
