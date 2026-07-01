package miau.module.modules.minigames;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S02PacketChat;

public class ThePit3F extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty autoFlashQuestion = new BooleanProperty("autoflashquestion", true);

  public ThePit3F() {
    super("ThePit3F", false);
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    Packet<?> packet = event.getPacket();

    if (packet instanceof S02PacketChat) {
      S02PacketChat chatPacket = (S02PacketChat) packet;
      String unformattedText = chatPacket.getChatComponent().getUnformattedText();

      if (autoFlashQuestion.getValue() && unformattedText.contains("Nhanh như chớp! Giải: ")) {
        String equation = unformattedText.substring(unformattedText.indexOf("Giải: ") + 6).trim();
        try {
          double result = eval(equation);
          if (result == (long) result) {
            mc.thePlayer.sendChatMessage(String.valueOf((long) result));
          } else {
            mc.thePlayer.sendChatMessage(String.valueOf(result));
          }
        } catch (Exception e) {
          ChatUtil.display("&c[ThePit3F] Failed to parse equation: " + equation);
        }
      }
    }
  }

  private static double eval(final String str) {
    return new Object() {
      int pos = -1, ch;

      void nextChar() {
        ch = (++pos < str.length()) ? str.charAt(pos) : -1;
      }

      boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
          nextChar();
          return true;
        }
        return false;
      }

      double parse() {
        nextChar();
        double x = parseExpression();
        if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
        return x;
      }

      double parseExpression() {
        double x = parseTerm();
        for (;;) {
          if (eat('+')) x += parseTerm(); 
          else if (eat('-')) x -= parseTerm(); 
          else return x;
        }
      }

      double parseTerm() {
        double x = parseFactor();
        for (;;) {
          if (eat('*') || eat('x') || eat('X')) x *= parseFactor(); 
          else if (eat('/')) x /= parseFactor();
          else return x;
        }
      }

      double parseFactor() {
        if (eat('+')) return parseFactor(); 
        if (eat('-')) return -parseFactor(); 

        double x;
        int startPos = this.pos;
        if (eat('(')) { 
          x = parseExpression();
          eat(')');
        } else if ((ch >= '0' && ch <= '9') || ch == '.') { 
          while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
          x = Double.parseDouble(str.substring(startPos, this.pos));
        } else {
          throw new RuntimeException("Unexpected: " + (char) ch);
        }
        return x;
      }
    }.parse();
  }
}
