package myau.clientanticheat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class AntiCheatAlertStyle {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static void displayFlag(
      String playerName, String cheatName, String detail, int vl, int flagCount, int maxFlagCount) {
    if (mc.thePlayer == null) return;
    String msg =
        EnumChatFormatting.GRAY
            + "["
            + EnumChatFormatting.RED
            + "Miau"
            + EnumChatFormatting.GRAY
            + "] "
            + EnumChatFormatting.WHITE
            + playerName
            + EnumChatFormatting.GRAY
            + " failed "
            + EnumChatFormatting.YELLOW
            + cheatName
            + EnumChatFormatting.GRAY
            + " (VL: "
            + flagCount
            + "/"
            + maxFlagCount
            + ")";
    mc.thePlayer.addChatMessage(new ChatComponentText(msg));
  }
}
