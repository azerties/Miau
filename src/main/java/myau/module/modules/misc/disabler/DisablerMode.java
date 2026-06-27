package myau.module.modules.misc.disabler;

import myau.event.impl.PacketEvent;
import myau.event.impl.TickEvent;
import myau.module.modules.misc.Disabler;
import net.minecraft.client.Minecraft;

public abstract class DisablerMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final Disabler parent;

  public DisablerMode(String name, Disabler parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onTick(TickEvent event) {}

  public void onPacket(PacketEvent event) {}
}
