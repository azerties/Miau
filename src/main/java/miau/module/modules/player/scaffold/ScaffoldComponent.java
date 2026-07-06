package miau.module.modules.player.scaffold;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.SafeWalkEvent;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.property.Property;

public interface ScaffoldComponent {
  default List<Property<?>> getProperties() {
    return new ArrayList<>();
  }

  default void onEnable() {}

  default void onDisable() {}

  default void onUpdate(UpdateEvent event) {}

  default void onStrafe(StrafeEvent event) {}

  default void onSafeWalk(SafeWalkEvent event) {}

  default void onMoveInput() {}

  default void onBlockPlaced() {}
}
