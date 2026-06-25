package myau.ui.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.IOException;
import me.ksyz.accountmanager.gui.GuiAccountManager;
import myau.ClientInfo;
import myau.management.MyauAPI;
import myau.ui.GuiUpdateClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;

public class MiauMainMenu extends GuiScreen {
  private static boolean hasCheckedVersion = false;

  @Override
  public void initGui() {
    super.initGui();
    this.buttonList.clear();

    if (!hasCheckedVersion) {
      hasCheckedVersion = true;
      new Thread(
              () -> {
                try {
                  String responseStr = MyauAPI.getClientVersion();
                  JsonObject response = new JsonParser().parse(responseStr).getAsJsonObject();

                  if (response.has("status")
                      && response.get("status").getAsString().equals("success")) {
                    String latestVersion = response.get("version").getAsString();
                    String updateUrl = response.get("updateUrl").getAsString();

                    if (!ClientInfo.VERSION.equals(latestVersion)) {
                      Minecraft.getMinecraft()
                          .addScheduledTask(
                              () -> {
                                Minecraft.getMinecraft()
                                    .displayGuiScreen(
                                        new GuiUpdateClient(
                                            this, ClientInfo.VERSION, latestVersion, updateUrl));
                              });
                    }
                  }
                } catch (Exception e) {
                }
              })
          .start();
    }

    int buttonWidth = 150;
    int buttonHeight = 20;
    int spacing = 24;

    int startX = this.width / 2 - buttonWidth / 2;
    int startY = this.height / 2 - 20;

    this.buttonList.add(
        new GuiButton(0, startX, startY, buttonWidth, buttonHeight, "Singleplayer"));
    this.buttonList.add(
        new GuiButton(1, startX, startY + spacing, buttonWidth, buttonHeight, "Multiplayer"));
    this.buttonList.add(
        new GuiButton(2, startX, startY + spacing * 2, buttonWidth, buttonHeight, "Alt Manager"));
    this.buttonList.add(
        new GuiButton(3, startX, startY + spacing * 3, buttonWidth, buttonHeight, "Options"));
    this.buttonList.add(
        new GuiButton(4, startX, startY + spacing * 4, buttonWidth, buttonHeight, "Quit"));
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {
    super.actionPerformed(button);
    switch (button.id) {
      case 0:
        this.mc.displayGuiScreen(new GuiSelectWorld(this));
        break;
      case 1:
        this.mc.displayGuiScreen(new GuiMultiplayer(this));
        break;
      case 2:
        this.mc.displayGuiScreen(new GuiAccountManager(this));
        break;
      case 3:
        this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
        break;
      case 4:
        this.mc.shutdown();
        break;
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    Gui.drawRect(0, 0, this.width, this.height, new Color(20, 20, 20, 255).getRGB());
    Gui.drawRect(0, 0, this.width, this.height, new Color(40, 40, 40, 100).getRGB());

    GlStateManager.pushMatrix();
    GlStateManager.scale(3.0f, 3.0f, 1.0f);
    String title = "Miau Client";
    int titleWidth = mc.fontRendererObj.getStringWidth(title);
    mc.fontRendererObj.drawStringWithShadow(
        title, (this.width / 2f) / 3.0f - (titleWidth / 2f), (this.height / 2f - 80) / 3.0f, -1);
    GlStateManager.popMatrix();

    String versionStr = ClientInfo.VERSION;
    mc.fontRendererObj.drawStringWithShadow("Version: " + versionStr, 2, this.height - 10, -1);

    String credits = "Credits: [ksyz, OpenMyau Project, idle]";
    int creditsX = this.width - mc.fontRendererObj.getStringWidth(credits) - 2;
    mc.fontRendererObj.drawStringWithShadow(credits, creditsX, this.height - 10, -1);

    super.drawScreen(mouseX, mouseY, partialTicks);
  }
}
