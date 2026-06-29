package myau.clientanticheat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import myau.util.render.ShapeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Renders a small triangle marker next to marked cheaters in the Tab (player list) overlay. Ported
 * from Rain Anticheat with Miau's RenderUtil.
 */
public class NametagOverlayRenderer {

  private static final int MAX_TAB_PLAYERS = 80;

  @SubscribeEvent
  public void onRenderPlayerList(RenderGameOverlayEvent.Post event) {
    if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
      return;
    }
    if (!AntiCheatAlertStyle.hasMarkedCheaters()) {
      return;
    }

    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null || mc.getNetHandler() == null) {
      return;
    }
    if (!mc.gameSettings.keyBindPlayerList.isKeyDown()) {
      return;
    }

    NetHandlerPlayClient netHandler = mc.getNetHandler();
    Set<NetworkPlayerInfo> markedInfos =
        Collections.newSetFromMap(new IdentityHashMap<NetworkPlayerInfo, Boolean>());
    for (String name : AntiCheatAlertStyle.getMarkedCheaterNames()) {
      NetworkPlayerInfo info = netHandler.getPlayerInfo(name);
      if (info != null) {
        markedInfos.add(info);
      }
    }
    if (markedInfos.isEmpty()) {
      return;
    }

    GuiPlayerTabOverlay tabOverlay = mc.ingameGUI.getTabList();
    List<NetworkPlayerInfo> players = this.sortedPlayers(mc, tabOverlay);
    if (players.size() > MAX_TAB_PLAYERS) {
      players = new ArrayList<>(players.subList(0, MAX_TAB_PLAYERS));
    }

    int markedVisible = 0;
    for (NetworkPlayerInfo info : players) {
      if (markedInfos.contains(info)) {
        ++markedVisible;
      }
    }
    if (markedVisible == 0) {
      return;
    }

    FontRenderer font = mc.fontRendererObj;
    int maxNameWidth = 0;
    for (NetworkPlayerInfo info : players) {
      maxNameWidth = Math.max(maxNameWidth, font.getStringWidth(tabOverlay.getPlayerName(info)));
    }

    Scoreboard scoreboard = mc.theWorld.getScoreboard();
    ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(0);
    int scoreWidth =
        objective != null
                && objective.getRenderType() == IScoreObjectiveCriteria.EnumRenderType.HEARTS
            ? 90
            : 0;

    int playerCount = players.size();
    int rows = playerCount;
    int columns = 1;
    while (rows > 20) {
      ++columns;
      rows = (playerCount + columns - 1) / columns;
    }

    boolean hasSkins = true;
    int screenWidth = event.resolution.getScaledWidth();
    int rowWidth =
        Math.min(columns * ((hasSkins ? 9 : 0) + maxNameWidth + scoreWidth + 13), screenWidth - 50)
            / columns;
    int startX = screenWidth / 2 - (rowWidth * columns + (columns - 1) * 5) / 2;
    int baseY = this.playerListTop(font, tabOverlay, screenWidth);
    int iconColor = AntiCheatAlertStyle.getNametagColor();

    for (int i = 0; i < playerCount; ++i) {
      NetworkPlayerInfo info = players.get(i);
      if (!markedInfos.contains(info)) {
        continue;
      }
      int column = i / rows;
      int row = i % rows;
      int rowX = startX + column * rowWidth + column * 5;
      int rowY = baseY + row * 9;
      String name = tabOverlay.getPlayerName(info);
      int nameX = rowX + (hasSkins ? 9 : 0);
      float markerX = Math.min(nameX + font.getStringWidth(name) + 7.0F, rowX + rowWidth - 4.0F);
      float markerY = rowY + 4.5F;
      ShapeUtil.drawTriangle(markerX, markerY, (float) Math.toRadians(90), 6.0F, iconColor);
    }
  }

  private List<NetworkPlayerInfo> sortedPlayers(Minecraft mc, GuiPlayerTabOverlay tabOverlay) {
    List<NetworkPlayerInfo> vanillaSorted = this.vanillaSortedPlayers(mc);
    if (vanillaSorted != null) {
      return vanillaSorted;
    }

    List<NetworkPlayerInfo> players = new ArrayList<>(mc.getNetHandler().getPlayerInfoMap());
    Collections.sort(
        players,
        new Comparator<NetworkPlayerInfo>() {
          @Override
          public int compare(NetworkPlayerInfo a, NetworkPlayerInfo b) {
            boolean aActive = a.getGameType() != GameType.SPECTATOR;
            boolean bActive = b.getGameType() != GameType.SPECTATOR;
            if (aActive != bActive) {
              return aActive ? -1 : 1;
            }
            int team = teamName(a).compareTo(teamName(b));
            if (team != 0) {
              return team;
            }
            return cleanName(tabOverlay.getPlayerName(a))
                .compareTo(cleanName(tabOverlay.getPlayerName(b)));
          }
        });
    return players;
  }

  @SuppressWarnings("unchecked")
  private List<NetworkPlayerInfo> vanillaSortedPlayers(Minecraft mc) {
    try {
      Field orderingField = GuiPlayerTabOverlay.class.getDeclaredField("field_175252_a");
      orderingField.setAccessible(true);
      Object ordering = orderingField.get(null);
      Method sortedCopy = ordering.getClass().getMethod("sortedCopy", Iterable.class);
      Object result = sortedCopy.invoke(ordering, mc.getNetHandler().getPlayerInfoMap());
      if (!(result instanceof List)) {
        return null;
      }
      List<?> rawPlayers = (List<?>) result;
      List<NetworkPlayerInfo> players = new ArrayList<>(rawPlayers.size());
      for (Object rawPlayer : rawPlayers) {
        if (rawPlayer instanceof NetworkPlayerInfo) {
          players.add((NetworkPlayerInfo) rawPlayer);
        }
      }
      return players;
    } catch (Exception ignored) {
      return null;
    }
  }

  private int playerListTop(FontRenderer font, GuiPlayerTabOverlay tabOverlay, int screenWidth) {
    IChatComponent header = this.readTabComponent(tabOverlay, "field_175256_i");
    if (header == null) {
      return 10;
    }
    List<String> lines =
        font.listFormattedStringToWidth(header.getFormattedText(), screenWidth - 50);
    return 10 + lines.size() * font.FONT_HEIGHT + 1;
  }

  private IChatComponent readTabComponent(GuiPlayerTabOverlay tabOverlay, String fieldName) {
    try {
      Field field = GuiPlayerTabOverlay.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      Object value = field.get(tabOverlay);
      return value instanceof IChatComponent ? (IChatComponent) value : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String teamName(NetworkPlayerInfo info) {
    ScorePlayerTeam team = info.getPlayerTeam();
    return team == null ? "" : team.getRegisteredName();
  }

  private static String cleanName(String name) {
    String clean = EnumChatFormatting.getTextWithoutFormattingCodes(name);
    return clean == null ? "" : clean;
  }
}
