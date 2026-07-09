package miau.module.modules.render;

import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.ui.clickgui.ClickGui;
import miau.ui.clickgui.demise.PanelGui;
import miau.ui.clickgui.faiths.FaithsClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class ClickGUI extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private ClickGui clickGui;
  private FaithsClickGui faithsClickGui;

  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"Miau", "Faiths", "Demise"});

  public final ModeProperty theme =
      new ModeProperty(
          "Theme",
          0,
          new String[] {"Default", "Dark", "Blue", "Red", "Green", "Purple", "Orange", "Cyan"});

  public final ModeProperty character =
      new ModeProperty(
          "Character", 0, miau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());

  public final BooleanProperty blur = new BooleanProperty("Blur", true);
  public final BooleanProperty showCharacter = new BooleanProperty("show-character", true);

  public ClickGUI() {
    super("ClickGUI", false);
    setKey(Keyboard.KEY_RSHIFT);
  }

  @Override
  public void onEnabled() {
    setEnabled(false);
    character.setModes(miau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());
    switch (mode.getValue()) {
      case 0:
        if (clickGui == null) {
          clickGui = new ClickGui();
        }
        mc.displayGuiScreen(clickGui);
        break;
      case 1:
        if (faithsClickGui == null) {
          faithsClickGui = new FaithsClickGui();
        }
        mc.displayGuiScreen(faithsClickGui);
        break;
      case 2:
        mc.displayGuiScreen(new PanelGui());
        break;
    }
  }

  public void checkModeSwitch() {
    if (mc.currentScreen == null) return;
    int currentMode = mode.getValue();
    if (currentMode == 0 && !(mc.currentScreen instanceof ClickGui)) {
      if (clickGui == null) {
        clickGui = new ClickGui();
      }
      mc.displayGuiScreen(clickGui);
    } else if (currentMode == 1 && !(mc.currentScreen instanceof FaithsClickGui)) {
      if (faithsClickGui == null) {
        faithsClickGui = new FaithsClickGui();
      }
      mc.displayGuiScreen(faithsClickGui);
    } else if (currentMode == 2 && !(mc.currentScreen instanceof PanelGui)) {
      mc.displayGuiScreen(new PanelGui());
    }
  }
}
