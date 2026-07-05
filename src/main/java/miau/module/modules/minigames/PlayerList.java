package miau.module.modules.minigames;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.event.impl.TickEvent;
import miau.module.Module;
import miau.module.modules.render.HUD;
import miau.property.properties.DragProperty;
import miau.property.properties.FloatProperty;
import miau.util.font.FontRepository;
import miau.util.math.MathUtil;
import miau.util.player.TeamUtil;
import miau.util.render.ShapeUtil;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

public class PlayerList extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final FloatProperty scale = new FloatProperty("Scale", 1.0f, 0.5f, 2.0f);
  public final DragProperty pos = new DragProperty("playerList", new Vector2d(4, 30));
  private final List<EntityPlayer> cachedPlayers = new ArrayList<>();

  public PlayerList() {
    super("PlayerList", false, false);
  }

  private String getTeamName(EntityPlayer player) {
    ScorePlayerTeam team = (ScorePlayerTeam) player.getTeam();
    if (team != null) {
      String prefix = FontRenderer.getFormatFromString(team.getColorPrefix());
      if (prefix.length() >= 2) {
        char colorChar = prefix.charAt(1);
        switch (colorChar) {
          case '7':
            return "Gray";
          case '9':
            return "Blue";
          case 'a':
            return "Green";
          case 'b':
            return "Aqua";
          case 'c':
            return "Red";
          case 'd':
            return "Pink";
          case 'e':
            return "Yellow";
          case 'f':
            return "White";
        }
      }
    }
    return "None";
  }

  @EventTarget
  public void onTickEvent(TickEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    cachedPlayers.clear();
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (p != null && !p.isDead) {
        cachedPlayers.add(p);
      }
    }

    // Sort players by team name
    cachedPlayers.sort(Comparator.comparing(this::getTeamName));
  }

  @EventTarget
  public void onRender2DEvent(Render2DEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    miau.util.font.Font font16 = FontRepository.getHudFont(16);
    miau.util.font.Font font18 = FontRepository.getHudFont(18);

    float rowHeight = font16.getFontHeight() + 3;
    float headerHeight = 14;
    float height = headerHeight + cachedPlayers.size() * rowHeight;
    float width = 220;

    float scaleValue = scale.getValue();
    pos.scale.x = width * scaleValue;
    pos.scale.y = height * scaleValue;

    float x = (float) pos.position.x / scaleValue;
    float y = (float) pos.position.y / scaleValue;

    int ix = (int) x;
    int iy = (int) y;

    GlStateManager.pushMatrix();
    GlStateManager.scale(scaleValue, scaleValue, 1f);
    GlStateManager.color(1, 1, 1, 1);

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    Color clientColor = hud.getColor(0);

    ShapeUtil.drawRect(ix, iy, ix + width, iy + height, new Color(0, 0, 0, 100).getRGB());
    ShapeUtil.drawRect(ix, iy, ix + width, iy + 1, clientColor.getRGB());
    ShapeUtil.drawRect(
        ix + 4, iy + headerHeight, ix + width - 4, iy + headerHeight + 0.5f, 0x50AAAAAA);

    font18.draw("Players \u00A77" + cachedPlayers.size(), ix + 16, iy + 3, -1, true);
    font18.draw("Dist.", ix + 105, iy + 3, -1, true);
    font18.draw("HP", ix + 140, iy + 3, -1, true);
    font18.draw("Team", ix + 175, iy + 3, -1, true);

    float currentY = iy + headerHeight;

    for (int i = 0; i < cachedPlayers.size(); i++) {
      EntityPlayer player = cachedPlayers.get(i);
      renderPlayer(player, ix, (int) currentY, font16, font18, clientColor);
      currentY += rowHeight;
    }

    GlStateManager.popMatrix();
  }

  private void renderPlayer(
      EntityPlayer player,
      int x,
      int y,
      miau.util.font.Font font16,
      miau.util.font.Font font18,
      Color clientColor) {
    float rowHeight = font16.getFontHeight() + 3;

    int headWH = 10;
    int headX = x + 3;
    int headY = y + (int) (rowHeight / 2f - headWH / 2f);

    GlStateManager.pushMatrix();
    GlStateManager.color(1, 1, 1, 1);
    mc.getTextureManager().bindTexture(((AbstractClientPlayer) player).getLocationSkin());
    Gui.drawScaledCustomSizeModalRect(headX, headY, 8.0F, 8.0F, 8, 8, headWH, headWH, 64.0F, 64.0F);
    GlStateManager.popMatrix();

    int textY = y + (int) ((rowHeight - font16.getFontHeight()) / 2.0f);

    Color nameColor = TeamUtil.getTeamColor(player, 1.0f);
    font16.drawWithShadow(player.getName(), x + 16, textY, nameColor.getRGB());

    int dist = (int) MathUtil.round(mc.thePlayer.getDistanceToEntity(player), 0);
    font16.drawWithShadow(dist + "m", x + 105, textY, 0xFFAAAAAA);

    float healthPercent = Math.min(player.getHealth() / player.getMaxHealth(), 1.0f);
    int hp = (int) MathUtil.round(healthPercent * 100, 0);

    Color healthColor =
        healthPercent > .75
            ? new Color(66, 246, 123)
            : healthPercent > .5
                ? new Color(228, 255, 105)
                : healthPercent > .35 ? new Color(236, 100, 64) : new Color(255, 65, 68);

    font16.drawWithShadow(hp + "%", x + 140, textY, healthColor.getRGB());

    String teamName = getTeamName(player);
    font16.drawWithShadow(teamName, x + 175, textY, nameColor.getRGB());
  }
}
