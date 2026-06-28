package myau.module.modules.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import myau.Myau;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.DragProperty;
import myau.property.properties.IntProperty;
import myau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;

public class Scoreboard extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  /** Drag property — marked as structure=true so DragManager does NOT draw a border over it. */
  public final DragProperty drag = new DragProperty("Position", new Vector2d(0, 0), false, true);

  /** The actual computed card position (updated every frame). */
  public float defaultX = 0;

  public float defaultY = 0;

  public final IntProperty yOffset = new IntProperty("Y Offset", 0, -250, 250);
  public final BooleanProperty customFont = new BooleanProperty("Custom Font", false);
  public final BooleanProperty textShadow = new BooleanProperty("Text Shadow", true);
  public final BooleanProperty redNumbers = new BooleanProperty("Red Numbers", false);
  public final BooleanProperty autofit = new BooleanProperty("Autofit", true);

  public Scoreboard() {
    super("Scoreboard", true, false);
  }

  /**
   * Recalculates card bounds each frame. Autofit shifts the scoreboard down when other right‑side
   * modules occupy the same space.
   */
  public void updateBounds(ScaledResolution scaledRes) {
    net.minecraft.scoreboard.Scoreboard sb = null;
    ScoreObjective objective = null;
    if (mc.theWorld != null) {
      sb = mc.theWorld.getScoreboard();
      if (sb != null) {
        objective = sb.getObjectiveInDisplaySlot(1);
      }
    }

    int size;
    int maxWidth;
    if (objective != null && sb != null) {
      Collection<Score> collection = sb.getSortedScores(objective);
      List<Score> list = new ArrayList<>();
      for (Score score : collection) {
        if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
          list.add(score);
        }
      }
      if (list.size() > 15) {
        list = list.subList(list.size() - 15, list.size());
      }
      size = list.size();
      maxWidth = mc.fontRendererObj.getStringWidth(objective.getDisplayName());
      for (Score score : list) {
        ScorePlayerTeam team = sb.getPlayersTeam(score.getPlayerName());
        String name =
            ScorePlayerTeam.formatPlayerName(team, score.getPlayerName())
                + ": "
                + score.getScorePoints();
        maxWidth = Math.max(maxWidth, mc.fontRendererObj.getStringWidth(name));
      }
    } else {
      size = 5;
      maxWidth = 80;
    }

    // Card dimensions
    int padding = 8;
    int width = maxWidth + padding + 4;
    int height = size * mc.fontRendererObj.FONT_HEIGHT + 14;

    float baseX = scaledRes.getScaledWidth() - width - 2;
    float baseY = scaledRes.getScaledHeight() / 2 - height / 3 + yOffset.getValue();

    // ── Autofit: push down if HUD module list is on the right side ────────
    float autofitOffset = 0;
    if (autofit.getValue()) {
      HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
      if (hud != null && hud.isEnabled() && hud.posX.getValue() == 1) {
        float moduleListHeight = hud.getModuleListHeight();
        if (moduleListHeight > 0) {
          float hudStartY;
          if (hud.posY.getValue() == 0) {
            hudStartY = hud.offsetY.getValue();
            if (hud.showWatermark.getValue()) {
              hudStartY += hud.getFont().getFontHeight() + 6.0F;
            }
          } else {
            hudStartY = scaledRes.getScaledHeight() - hud.offsetY.getValue() - moduleListHeight;
          }
          float hudBottom = hudStartY + moduleListHeight;
          if (hudBottom > baseY) {
            autofitOffset = hudBottom - baseY + 4;
          }
        }
      }
    }

    this.defaultX = baseX;
    this.defaultY = baseY + autofitOffset;

    // Always keep position in sync (no initialisation guard — the Mixin reads defaultX/Y directly)
    this.drag.position.x = baseX;
    this.drag.position.y = baseY + autofitOffset;
    this.drag.targetPosition.x = baseX;
    this.drag.targetPosition.y = baseY + autofitOffset;
    this.drag.scale.x = width;
    this.drag.scale.y = height;
  }
}
