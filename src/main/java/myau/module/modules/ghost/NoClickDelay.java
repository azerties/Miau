package myau.module.modules.ghost;

import myau.event.EventTarget;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import net.minecraft.client.Minecraft;

public class NoClickDelay extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public NoClickDelay() {
    super("NoClickDelay", true, true);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      if (mc.thePlayer != null && mc.theWorld != null) {
        ((IAccessorMinecraft) mc).setLeftClickCounter(0);
      }
    }
  }
}
