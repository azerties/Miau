package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;

public class MultiPlaceFeature implements ScaffoldComponent {
  public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);

  public MultiPlaceFeature(Scaffold scaffold) {}

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(multiplace);
  }
}
