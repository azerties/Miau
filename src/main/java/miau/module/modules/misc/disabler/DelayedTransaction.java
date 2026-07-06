package miau.module.modules.misc.disabler;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class DelayedTransaction extends DisablerMode {
  private boolean delay = false;
  private final List<Packet<INetHandlerPlayClient>> packets = new ArrayList<>();

  public DelayedTransaction(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onDisable() {
    ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;

    if (mc.thePlayer != null) {
      for (Packet<INetHandlerPlayClient> packet : packets) {
        PacketUtil.handlePacket(packet);
      }
    }

    packets.clear();
    delay = false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE) {
      if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 20) {
        packets.clear();
        return;
      }

      Packet<?> rawPacket = event.getPacket();

      if (mc.thePlayer.capabilities.isFlying || mc.thePlayer.capabilities.allowFlying) {
        if (!delay && rawPacket instanceof S08PacketPlayerPosLook) {
          delay = true;
        }
      }

      if (delay && rawPacket instanceof S32PacketConfirmTransaction) {
        packets.add((Packet<INetHandlerPlayClient>) rawPacket);
        event.setCancelled(true);
      }
    }
  }
}
