package miau.module.modules.misc;

import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.module.Module;
import miau.module.modules.misc.disabler.DelayedTransaction;
import miau.module.modules.misc.disabler.DisablerMode;
import miau.module.modules.misc.disabler.VulcanTestDisabler;
import miau.property.Property;
import miau.property.properties.ModeProperty;

public class Disabler extends Module {

  public final List<DisablerMode> modes = new ArrayList<>();

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            register(new VulcanTestDisabler("VulcanTest", this)),
            register(new DelayedTransaction("DelayedTransaction", this)),
          });

  private String register(DisablerMode m) {
    this.modes.add(m);
    return m.getName();
  }

  public Disabler() {
    super("Disabler", false);
  }

  public DisablerMode getActiveMode() {
    return modes.stream()
        .filter(m -> m.getName().equals(mode.getModeString()))
        .findFirst()
        .orElse(modes.get(0));
  }

  @Override
  public void onEnabled() {
    getActiveMode().onEnable();
  }

  @Override
  public void onDisabled() {
    getActiveMode().onDisable();
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (DisablerMode m : modes) {
      for (java.lang.reflect.Field field : m.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        try {
          Object obj = field.get(m);
          if (obj instanceof Property<?>) {
            Property<?> prop = (Property<?>) obj;
            java.util.function.BooleanSupplier original = prop.getVisibleChecker();
            prop.setVisibleChecker(
                () -> this.getActiveMode() == m && (original == null || original.getAsBoolean()));
            props.add(prop);
          }
        } catch (Exception e) {
        }
      }
    }
    return props;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onTick(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onPacket(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }
}
