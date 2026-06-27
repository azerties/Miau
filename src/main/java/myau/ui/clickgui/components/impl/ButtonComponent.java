package myau.ui.clickgui.components.impl;

import java.awt.*;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.ui.clickgui.components.Component;
import myau.util.font.Font;
import myau.util.font.Fonts;
import net.minecraft.client.renderer.GlStateManager;

public class ButtonComponent extends Component {
  private static final int ENABLED_COLOR = new Color(20, 255, 0).getRGB();

  private Module mod;
  public BooleanProperty property;
  private ModuleComponent moduleComponent;

  public float o;
  public float x;
  private float y;
  public float xOffset;
  private float toggleAnim = -1;

  public ButtonComponent(Module mod, BooleanProperty op, ModuleComponent b, float o) {
    this.mod = mod;
    this.property = op;
    this.moduleComponent = b;
    this.x = b.categoryComponent.getX() + b.categoryComponent.getWidth();
    this.y = b.categoryComponent.getY() + b.yPos;
    this.o = o;
  }

  public void render() {
    Font renderer = Fonts.MINECRAFT.get(18);
    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.moduleComponent.categoryComponent.getY() + this.o;
    float cw = this.moduleComponent.categoryComponent.getWidth();

    float fontScale = myau.ui.clickgui.components.impl.ModuleComponent.getFontScale();
    GlStateManager.color(1f, 1f, 1f, 1f);
    renderer.draw(this.property.getName(), cx + 6 + xOffset / 2, cy + 4 * fontScale, -1, true);

    boolean enabled = this.property.getValue();
    if (toggleAnim == -1) {
      toggleAnim = enabled ? 1f : 0f;
    } else {
      toggleAnim += ((enabled ? 1f : 0f) - toggleAnim) * 0.2f;
    }

    float switchW = 16f;
    float switchH = 8f;
    float switchX = cx + cw - switchW - 6 + (xOffset / 2);
    float switchY = cy + 2f * fontScale;

    Color c1 = new Color(40, 40, 40);
    Color c2 = new Color(ENABLED_COLOR);
    int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * toggleAnim);
    int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * toggleAnim);
    int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * toggleAnim);
    int bgColor = new Color(r, g, b).getRGB();

    myau.util.render.RenderUtil.drawRoundedRectangle(
        switchX, switchY, switchX + switchW, switchY + switchH, switchH / 2f, bgColor);

    float circleR = switchH / 2f - 1f;
    float minCircleX = switchX + circleR + 1f;
    float maxCircleX = switchX + switchW - circleR - 1f;
    float circleX = minCircleX + (maxCircleX - minCircleX) * toggleAnim;
    float circleY = switchY + switchH / 2f;

    myau.util.render.RenderUtil.drawRoundedRectangle(
        circleX - circleR, circleY - circleR, circleX + circleR, circleY + circleR, circleR, -1);
  }

  public void updateHeight(float n) {
    this.o = n;
  }

  @Override
  public float getOffset() {
    return this.o;
  }

  @Override
  public boolean isBaseVisible() {
    return this.property.isVisible();
  }

  public void drawScreen(int x, int y) {
    this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
    this.x = this.moduleComponent.categoryComponent.getX();
  }

  public boolean onClick(int x, int y, int b) {
    if (this.i(x, y)
        && b == 0
        && this.moduleComponent.isOpened
        && this.moduleComponent.isVisible(this)) {
      this.property.setValue(!this.property.getValue());
      this.moduleComponent.reloadSettings();
    }
    return false;
  }

  public boolean i(int x, int y) {
    float fontScale = myau.ui.clickgui.components.impl.ModuleComponent.getFontScale();
    return x > this.x
        && x < this.x + this.moduleComponent.categoryComponent.getWidth()
        && y > this.y
        && y < this.y + 12 * fontScale;
  }
}
