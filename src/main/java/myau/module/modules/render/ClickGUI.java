package myau.module.modules.render;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.ui.clickgui.ClickGui;
import myau.ui.clickgui.faiths.FaithsClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class ClickGUI extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private ClickGui clickGui;
  private FaithsClickGui faithsClickGui;

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Miau", "Faiths"});

  public final ModeProperty theme =
      new ModeProperty(
          "Theme",
          0,
          new String[] {"Default", "Dark", "Blue", "Red", "Green", "Purple", "Orange", "Cyan"});

  public final ModeProperty character =
      new ModeProperty(
          "Character", 0, myau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());

  public final BooleanProperty blur = new BooleanProperty("Blur", true);

  public ClickGUI() {
    super("ClickGUI", false);
    setKey(Keyboard.KEY_RSHIFT);
  }

  @Override
  public void onEnabled() {
    setEnabled(false);
    character.setModes(myau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());
    if (mode.getValue() == 0) {
      if (clickGui == null) {
        clickGui = new ClickGui();
      }
      mc.displayGuiScreen(clickGui);
    } else {
      if (faithsClickGui == null) {
        faithsClickGui = new FaithsClickGui();
      }
      mc.displayGuiScreen(faithsClickGui);
    }
  }

  public void checkModeSwitch() {
    if (mc.currentScreen == null) return;
    if (mode.getValue() == 0 && mc.currentScreen instanceof FaithsClickGui) {
      if (clickGui == null) {
        clickGui = new ClickGui();
      }
      mc.displayGuiScreen(clickGui);
    } else if (mode.getValue() == 1 && mc.currentScreen instanceof ClickGui) {
      if (faithsClickGui == null) {
        faithsClickGui = new FaithsClickGui();
      }
      mc.displayGuiScreen(faithsClickGui);
    }
  }
}
