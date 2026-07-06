package miau.module.modules.render;

import java.awt.Color;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.ColorProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.ModeProperty;
import miau.util.font.FontRepository;
<<<<<<< HEAD
import miau.util.render.RenderUtil;
=======
import miau.util.shader.BlurUtils;
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
import miau.util.shader.RoundedUtils;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.StringUtils;

public class Statistics extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public static int wins, killCount;
  public static long startTime = System.currentTimeMillis();
  public static final String[] KILL_TRIGGERS = {"by *", "para *", "fue destrozado a manos de *"};

  public final DragProperty dragging = new DragProperty("SessionStats", new Vector2d(5, 150));

  public final ModeProperty colorMode =
      new ModeProperty("Color Mode", 0, new String[] {"HUD", "Custom"});
  public final ColorProperty customColor =
      new ColorProperty(
          "Custom Color", new Color(255, 105, 180).getRGB(), () -> this.colorMode.getValue() == 1);

  private float width, height;
  private String timeString = "0 seconds";

  public Statistics() {
    super("Statistics", false, true);
  }

  private Color getAccentColor() {
    if (colorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud.getColor(System.currentTimeMillis(), 0);
    }
    return new Color(customColor.getValue());
  }

  @EventTarget
  public void onShaderEvent(miau.event.impl.ShaderEvent event) {
    if (!this.isEnabled()) return;
    miau.module.Module postProc =
        Miau.moduleManager.getModule(miau.module.modules.render.PostProcessing.class);
    if (postProc == null || !postProc.isEnabled()) return;

    float x = (float) this.dragging.position.x;
    float y = (float) this.dragging.position.y;
    boolean seperated = motionGraph.getValue() && separateMotionGraph.getValue();
    float localWidth = 145; // using local temp to not desync
    miau.util.font.Font font18 = FontRepository.getHudFont(18);
    boolean moreHeight = motionGraph.getValue() && !separateMotionGraph.getValue();
    float orginalHeight = statistics.size() * (font18.getFontHeight() + 6) + 26;
    float localHeight = orginalHeight + (moreHeight ? 75 : 0);

    Color c1 = applyOpacity(getColor1(), .85f);
    Color c2 = applyOpacity(getColor2(), .85f);
    Color c3 = applyOpacity(getColor3(), .85f);
    Color c4 = applyOpacity(getColor4(), .85f);

    if (event.isBloom()) {
      RoundedUtils.drawGradientRound(x, y, localWidth, localHeight, 6, c2, c1, c4, c3);
      if (seperated) {
        RoundedUtils.drawGradientRound(
            (float) motionDragging.position.x,
            (float) motionDragging.position.y,
            (float) motionDragging.scale.x,
            (float) motionDragging.scale.y,
            6,
            c2,
            c1,
            c4,
            c3);
      }
    } else {
      RoundedUtils.drawGradientRound(
          x, y, localWidth, localHeight, 6, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK);
      if (seperated) {
        RoundedUtils.drawGradientRound(
            (float) motionDragging.position.x,
            (float) motionDragging.position.y,
            (float) motionDragging.scale.x,
            (float) motionDragging.scale.y,
            6,
            Color.BLACK,
            Color.BLACK,
            Color.BLACK,
            Color.BLACK);
      }
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent e) {
    if (!this.isEnabled()) return;

    if (mc.thePlayer != null && mc.thePlayer.ticksExisted % 20 == 0) {
      long elapsed = System.currentTimeMillis() - startTime;
      long hours = TimeUnit.MILLISECONDS.toHours(elapsed);
      long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;
      long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;

      String base = "";
      if (hours > 0)
        base += hours + " " + (hours == 1 ? "hour" : "hours") + ((minutes == 0 ? "" : " "));
      if (minutes > 0)
        base +=
            minutes
                + " "
                + (minutes == 1 ? "minute" : "minutes")
                + (seconds == 0 || hours > 0 ? "" : " ");
      if (seconds > 0 && hours == 0) base += seconds + " " + (seconds == 1 ? "second" : "seconds");
      if (base.isEmpty()) base = "0 seconds";

      this.timeString = base;
    }

    float x = (float) this.dragging.position.x;
    float y = (float) this.dragging.position.y;
    width = 130;
    height = 55;

    this.dragging.scale.x = width;
    this.dragging.scale.y = height;

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
<<<<<<< HEAD
    // Shaders are now handled in onShaderEvent
=======
    boolean shaders = hud != null && hud.shaders.getValue();

    if (shaders) {
      BlurUtils.prepareBlur();
      RoundedUtils.drawRound(x, y, width, height, 6, Color.BLACK);
      BlurUtils.blurEnd(2, 3);
    }
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b

    Color c1 = applyOpacity(getAccentColor(), 0.8f);
    RoundedUtils.drawRoundOutline(x, y, width, height, 6, 1.0f, new Color(0, 0, 0, 100), c1);

    miau.util.font.Font font22 = FontRepository.getHudFont(22);
    miau.util.font.Font font18 = FontRepository.getHudFont(18);

    double padding = 8;

    // Title
    String title = "Session Stats";
    font22.draw(
        title,
        x + width / 2f - font22.getStringWidth(title) / 2f,
        (float) (y + padding),
        getAccentColor().getRGB());

    // Time
    font18.draw(
        timeString,
        x + width / 2f - font18.getStringWidth(timeString) / 2f,
        (float) (y + padding + 19),
        new Color(255, 255, 255, 200).getRGB());

    // Kills & Wins
    String killsText = "kills " + killCount;
    String winsText = "wins " + wins;
    font18.draw(
        killsText, x + 25, (float) (y + padding + 32), new Color(255, 255, 255, 200).getRGB());
    font18.draw(
        winsText,
        x + width - 25 - font18.getStringWidth(winsText),
        (float) (y + padding + 32),
        new Color(255, 255, 255, 200).getRGB());
  }

  private Color applyOpacity(Color color, float alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255));
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE) {
      if (event.getPacket() instanceof S02PacketChat) {
        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (mc.thePlayer == null) return;
        String message = packet.getChatComponent().getUnformattedText();
        String strippedMessage = StringUtils.stripControlCodes(message);

        if (!strippedMessage.contains(":")
            && Arrays.stream(KILL_TRIGGERS)
                .anyMatch(strippedMessage.replace(mc.thePlayer.getName(), "*")::contains)) {
          killCount++;
        }
      } else if (event.getPacket() instanceof S45PacketTitle) {
        S45PacketTitle packet = (S45PacketTitle) event.getPacket();
        if (packet.getMessage() != null) {
          String text = StringUtils.stripControlCodes(packet.getMessage().getUnformattedText());
          if (text.equals("VICTORY!")) {
            wins++;
          }
        }
      }
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (mc.currentScreen instanceof GuiMainMenu
        || mc.currentScreen instanceof GuiMultiplayer
        || mc.currentScreen instanceof GuiDisconnected) {
      resetStats();
    }
  }

  public static void resetStats() {
    startTime = System.currentTimeMillis();
    wins = 0;
    killCount = 0;
  }
}
