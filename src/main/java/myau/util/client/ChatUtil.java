package myau.util.client;

import java.util.IllegalFormatException;
import java.util.MissingFormatArgumentException;
import myau.Myau;
import myau.enums.ChatColors;
import myau.util.animation.*;
import myau.util.animation.impl.DecelerateAnimation;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.player.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

public class ChatUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public static Animation openingAnimation = new DecelerateAnimation(175, 1, Direction.BACKWARDS);

  public static void display(String message, Object... objects) {
    if (mc.thePlayer != null) {
      String text = formatMessage(message, objects);
      if (!hasClientPrefix(text)) {
        text = Myau.clientName + text;
      }

      try {
        String dynamicPrefix = myau.enums.ChatColors.getDynamicPrefix();
        String formattedStatic = ChatColors.formatColor(Myau.clientName);
        if (text.startsWith(Myau.clientName)) {
          text = dynamicPrefix + text.substring(Myau.clientName.length());
        } else if (text.startsWith(formattedStatic)) {
          text = dynamicPrefix + text.substring(formattedStatic.length());
        }
      } catch (Exception e) {
      }

      String finalMessage = ChatColors.formatColor(text);
      mc.thePlayer.addChatMessage(new ChatComponentText(finalMessage));
    }
  }

  private static String formatMessage(String message, Object[] objects) {
    try {
      return String.format(message, objects);
    } catch (MissingFormatArgumentException e) {
      if (message.startsWith("%s")) {
        try {
          return String.format(message, prependClientName(objects));
        } catch (IllegalFormatException ignored) {
        }
      }
      return message;
    } catch (IllegalFormatException e) {
      return message;
    }
  }

  private static boolean hasClientPrefix(String message) {
    String formattedPrefix = ChatColors.formatColor(Myau.clientName);
    return message.startsWith(Myau.clientName)
        || message.startsWith(formattedPrefix)
        || message.startsWith(ChatColors.PREFIX_CLEAN);
  }

  private static Object[] prependClientName(Object[] objects) {
    Object[] result = new Object[objects.length + 1];
    result[0] = Myau.clientName;
    System.arraycopy(objects, 0, result, 1, objects.length);
    return result;
  }

  public static void displayNoPrefix(String message, Object... objects) {
    if (mc.thePlayer != null) {
      String text = String.format(message, objects);
      mc.thePlayer.addChatMessage(new ChatComponentText(ChatColors.formatColor(text)));
    }
  }

  public static void sendRaw(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(ChatColors.formatColor(message)));
    }
  }

  public static void sendMessage(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }
  }

  public static void send(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }
  }

  public static void send(IChatComponent component) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(component);
    }
  }

  public static void sendFormatted(String message) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(new ChatComponentText(ChatColors.formatColor(message)));
    }
  }

  public static void sendFormatted(IChatComponent component) {
    if (mc.thePlayer != null) {
      mc.thePlayer.addChatMessage(component);
    }
  }
}
