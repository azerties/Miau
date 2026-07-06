package miau.module.modules.render;

import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.ui.clickgui.ClickGui;
import miau.ui.clickgui.demise.PanelGui;
import miau.ui.clickgui.faiths.FaithsClickGui;
import miau.ui.clickgui.opal.OpalClickGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class ClickGUI extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private ClickGui clickGui;
  private FaithsClickGui faithsClickGui;
  private OpalClickGui opalClickGui;

  public final ModeProperty mode =
<<<<<<< HEAD
      new ModeProperty("Mode", 0, new String[] {"Miau", "Faiths", "Opal"});
=======
      new ModeProperty("Mode", 0, new String[] {"Miau", "Faiths", "Demise"});
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b

  public final ModeProperty theme =
      new ModeProperty(
          "Theme",
          0,
          new String[] {"Default", "Dark", "Blue", "Red", "Green", "Purple", "Orange", "Cyan"});

  public final ModeProperty character =
      new ModeProperty(
          "Character", 0, miau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());

  public final BooleanProperty blur = new BooleanProperty("Blur", true);

  public ClickGUI() {
    super("ClickGUI", false);
    setKey(Keyboard.KEY_RSHIFT);
  }

  @Override
  public void onEnabled() {
    setEnabled(false);
    character.setModes(miau.ui.clickgui.faiths.FaithsCharacterRenderer.getCharacterArray());
<<<<<<< HEAD
    if (mode.getValue() == 0) {
      if (clickGui == null) {
        clickGui = new ClickGui();
      }
      mc.displayGuiScreen(clickGui);
    } else if (mode.getValue() == 1) {
      if (faithsClickGui == null) {
        faithsClickGui = new FaithsClickGui();
      }
      mc.displayGuiScreen(faithsClickGui);
    } else if (mode.getValue() == 2) {
      if (opalClickGui == null) {
        opalClickGui = new OpalClickGui();
      }
      mc.displayGuiScreen(opalClickGui);
=======
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
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
    }
  }

  public void checkModeSwitch() {
    if (mc.currentScreen == null) return;
<<<<<<< HEAD
    if (mode.getValue() == 0 && !(mc.currentScreen instanceof ClickGui)) {
=======
    int currentMode = mode.getValue();
    if (currentMode == 0 && !(mc.currentScreen instanceof ClickGui)) {
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
      if (clickGui == null) {
        clickGui = new ClickGui();
      }
      mc.displayGuiScreen(clickGui);
<<<<<<< HEAD
    } else if (mode.getValue() == 1 && !(mc.currentScreen instanceof FaithsClickGui)) {
=======
    } else if (currentMode == 1 && !(mc.currentScreen instanceof FaithsClickGui)) {
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
      if (faithsClickGui == null) {
        faithsClickGui = new FaithsClickGui();
      }
      mc.displayGuiScreen(faithsClickGui);
<<<<<<< HEAD
    } else if (mode.getValue() == 2 && !(mc.currentScreen instanceof OpalClickGui)) {
      if (opalClickGui == null) {
        opalClickGui = new OpalClickGui();
      }
      mc.displayGuiScreen(opalClickGui);
=======
    } else if (currentMode == 2 && !(mc.currentScreen instanceof PanelGui)) {
      mc.displayGuiScreen(new PanelGui());
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
    }
  }
}
