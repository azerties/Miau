package myau.module.modules.movement.noslow;

import myau.Myau;
import myau.component.BadPacketsComponent;
import myau.event.impl.PacketEvent;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.module.modules.combat.KillAura;
import myau.module.modules.movement.NoSlow;
import myau.util.network.PacketUtil;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class OMIntaveNoSlow extends NoSlowMode {
  private int disable;

  public OMIntaveNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      this.disable++;
      if (this.getParent().isAnyActive()) {
        this.performBypass();
      }
    }

    KillAura aura = (KillAura) Myau.moduleManager.getModule(KillAura.class);
    if (aura != null && aura.getTarget() != null) {
      return;
    }

    if (this.getParent().isAnyActive()) {
      float multiplier = this.getParent().getMotionMultiplier();
      mc.thePlayer.movementInput.moveForward *= multiplier;
      mc.thePlayer.movementInput.moveStrafe *= multiplier;
      if (!this.getParent().canSprint()) {
        mc.thePlayer.setSprinting(false);
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND
        && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
      if (this.getParent().isSwordActive()
          && !BadPacketsComponent.bad(false, true, true, false, false)) {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));
      }
    }
  }
}
