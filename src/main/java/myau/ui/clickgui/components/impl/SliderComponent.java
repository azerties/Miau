package myau.ui.clickgui.components.impl;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import myau.property.Property;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.ui.clickgui.components.Component;
import myau.util.font.Fonts;
import myau.util.render.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;

public class SliderComponent extends Component {
  public Property<?> property;
  private ModuleComponent moduleComponent;
  public float o;
  public float x;
  private float y;
  public boolean heldDown = false;
  private double width;
  public float xOffset;

  private double targetValue;
  private double displayedValue;

  public boolean draggingMin = false;
  public boolean draggingMax = false;
  private double targetSecondValue;
  private double displayedSecondValue;

  private static final double SLIDER_SPEED = 0.6;

  public SliderComponent(Property<?> property, ModuleComponent moduleComponent, float o) {
    this.property = property;
    this.moduleComponent = moduleComponent;
    this.o = o;

    double initial = getValue();
    this.targetValue = initial;
    this.displayedValue = initial;

    if (isDouble()) {
      double second = getSecondValue();
      this.targetSecondValue = second;
      this.displayedSecondValue = second;
    }

    double range = getMax() - getMin();
    this.width =
        range == 0
            ? 0
            : (double) (this.moduleComponent.categoryComponent.getWidth() - 8)
                * (initial - getMin())
                / range;
  }

  public boolean isDouble() {
    return property instanceof FloatProperty && ((FloatProperty) property).isDoubleSlider();
  }

  public double getValue() {
    if (property instanceof FloatProperty) {
      return ((FloatProperty) property).getValue().doubleValue();
    } else if (property instanceof IntProperty) {
      return ((IntProperty) property).getValue().doubleValue();
    } else if (property instanceof PercentProperty) {
      return ((PercentProperty) property).getValue().doubleValue();
    } else if (property instanceof ModeProperty) {
      return ((ModeProperty) property).getValue().doubleValue();
    }
    return 0;
  }

  public double getSecondValue() {
    if (isDouble()) {
      return ((FloatProperty) property).getSecondValue().doubleValue();
    }
    return getValue();
  }

  public double getMin() {
    if (property instanceof FloatProperty) {
      return ((FloatProperty) property).getMinimum().doubleValue();
    } else if (property instanceof IntProperty) {
      return ((IntProperty) property).getMinimum().doubleValue();
    } else if (property instanceof PercentProperty) {
      return ((PercentProperty) property).getMinimum().doubleValue();
    }
    return 0;
  }

  public double getMax() {
    if (property instanceof FloatProperty) {
      return ((FloatProperty) property).getMaximum().doubleValue();
    } else if (property instanceof IntProperty) {
      return ((IntProperty) property).getMaximum().doubleValue();
    } else if (property instanceof PercentProperty) {
      return ((PercentProperty) property).getMaximum().doubleValue();
    } else if (property instanceof ModeProperty) {
      return ((ModeProperty) property).getModes().length - 1;
    }
    return 1;
  }

  public void setValue(double newValue) {
    newValue = Math.max(getMin(), Math.min(getMax(), newValue));
    if (isDouble() && newValue > getSecondValue()) {
      newValue = getSecondValue();
    }
    Object prevValue = property.getValue();
    if (property instanceof FloatProperty) {
      property.setValue((float) newValue);
    } else if (property instanceof IntProperty) {
      property.setValue((int) Math.round(newValue));
    } else if (property instanceof PercentProperty) {
      property.setValue((int) Math.round(newValue));
    } else if (property instanceof ModeProperty) {
      property.setValue((int) Math.round(newValue));
    }
    if (prevValue != null && !prevValue.equals(property.getValue())) {
      this.moduleComponent.reloadSettings();
    }
  }

  public void setSecondValue(double newValue) {
    newValue = Math.max(getMin(), Math.min(getMax(), newValue));
    if (isDouble() && newValue < getValue()) {
      newValue = getValue();
    }
    Object prevValue = getSecondValue();
    if (isDouble()) {
      ((FloatProperty) property).setSecondValue((float) newValue);
    }
    if (prevValue != null && !prevValue.equals(getSecondValue())) {
      this.moduleComponent.reloadSettings();
    }
  }

  public boolean isString() {
    return property instanceof ModeProperty;
  }

  public String[] getOptions() {
    if (property instanceof ModeProperty) {
      return ((ModeProperty) property).getModes();
    }
    return null;
  }

  public String getSuffix() {
    if (property instanceof PercentProperty) {
      return "%";
    }
    return "";
  }

  @Override
  public void render() {
    float cx = this.moduleComponent.categoryComponent.getX();
    float cy = this.moduleComponent.categoryComponent.getY() + this.o;
    float cw = this.moduleComponent.categoryComponent.getWidth();

    double input = getValue();
    String suffix = getSuffix();
    String valueText;

    if (isString()) {
      int idx = (int) Math.round(input);
      String[] opts = getOptions();
      if (opts != null && opts.length > 0) {
        idx = Math.max(0, Math.min(idx, opts.length - 1));
        valueText = opts[idx];
      } else {
        valueText = "";
      }
    } else {
      if (property instanceof IntProperty || property instanceof PercentProperty) {
        valueText = String.valueOf((int) Math.round(input));
      } else {
        if (isDouble()) {
          valueText = String.format("%.1f - %.1f", input, getSecondValue());
        } else {
          valueText = String.format("%.2f", input);
        }
      }
    }

    float fontScale = myau.ui.clickgui.components.impl.ModuleComponent.getFontScale();
    GlStateManager.color(1f, 1f, 1f, 1f);
    float labelX = cx + 6 + xOffset / 2;
    float labelY = cy + 4 * fontScale;
    Fonts.MINECRAFT
        .get(18)
        .draw(this.property.getName() + ": " + valueText + suffix, labelX, labelY, -1, true);

    float trackLeft = cx + 6 + (xOffset / 2);
    float trackRight = cx + cw - 6 + (xOffset / 2);
    float trackY = cy + 13 * fontScale;
    float trackHeight = 2.5f;

    RenderUtil.drawRoundedRectangle(
        trackLeft,
        trackY,
        trackRight,
        trackY + trackHeight,
        trackHeight / 2f,
        new Color(40, 40, 40).getRGB());

    double range = getMax() - getMin();
    double fraction = range == 0 ? 0 : (this.displayedValue - getMin()) / range;
    float actualFillRight = trackLeft + (float) ((trackRight - trackLeft) * fraction);

    int accentColor =
        Color.getHSBColor((float) (System.currentTimeMillis() % 11000L) / 11000.0F, 0.75F, 0.9F)
            .getRGB();

    if (isDouble()) {
      double fraction2 = range == 0 ? 0 : (this.displayedSecondValue - getMin()) / range;
      float actualFillRight2 = trackLeft + (float) ((trackRight - trackLeft) * fraction2);

      RenderUtil.drawRoundedRectangle(
          actualFillRight,
          trackY,
          actualFillRight2,
          trackY + trackHeight,
          trackHeight / 2f,
          accentColor);

      float thumbRadius = 2.5f;
      RenderUtil.drawRoundedRectangle(
          actualFillRight - thumbRadius,
          trackY + trackHeight / 2f - thumbRadius,
          actualFillRight + thumbRadius,
          trackY + trackHeight / 2f + thumbRadius,
          thumbRadius,
          -1);
      RenderUtil.drawRoundedRectangle(
          actualFillRight2 - thumbRadius,
          trackY + trackHeight / 2f - thumbRadius,
          actualFillRight2 + thumbRadius,
          trackY + trackHeight / 2f + thumbRadius,
          thumbRadius,
          -1);
    } else {
      RenderUtil.drawRoundedRectangle(
          trackLeft, trackY, actualFillRight, trackY + trackHeight, trackHeight / 2f, accentColor);

      float thumbRadius = 2.5f;
      RenderUtil.drawRoundedRectangle(
          actualFillRight - thumbRadius,
          trackY + trackHeight / 2f - thumbRadius,
          actualFillRight + thumbRadius,
          trackY + trackHeight / 2f + thumbRadius,
          thumbRadius,
          -1);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
    this.x = this.moduleComponent.categoryComponent.getX();

    if (this.heldDown || this.draggingMin || this.draggingMax) {
      float trackLeft = this.x + 6 + (xOffset / 2);
      float trackRight =
          this.x + this.moduleComponent.categoryComponent.getWidth() - 6 + (xOffset / 2);
      float trackWidth = trackRight - trackLeft;

      double d = Math.min(trackWidth, Math.max(0, mouseX - trackLeft));
      double range = getMax() - getMin();
      double n = getMin() + (d / trackWidth) * range;

      if (isDouble()) {
        if (this.draggingMin) {
          this.targetValue = roundToInterval(n, 4);
          this.displayedValue = displayedValue + (targetValue - displayedValue) * SLIDER_SPEED;
          setValue(this.targetValue);
        } else if (this.draggingMax) {
          this.targetSecondValue = roundToInterval(n, 4);
          this.displayedSecondValue =
              displayedSecondValue + (targetSecondValue - displayedSecondValue) * SLIDER_SPEED;
          setSecondValue(this.targetSecondValue);
        }
      } else {
        this.targetValue = roundToInterval(n, 4);
        this.displayedValue = displayedValue + (targetValue - displayedValue) * SLIDER_SPEED;
        setValue(this.targetValue);

        if (range == 0) {
          this.width = 0;
        } else {
          double fraction = (this.displayedValue - getMin()) / range;
          this.width = (this.moduleComponent.categoryComponent.getWidth() - 12) * fraction;
        }
      }
    }
  }

  public void onSliderChange() {
    double initial = getValue();
    this.targetValue = initial;
    this.displayedValue = initial;

    if (isDouble()) {
      double second = getSecondValue();
      this.targetSecondValue = second;
      this.displayedSecondValue = second;
    }

    double range = getMax() - getMin();
    this.width =
        range == 0
            ? 0
            : (double) (this.moduleComponent.categoryComponent.getWidth() - 8)
                * (initial - getMin())
                / range;
  }

  private static double roundToInterval(double value, int places) {
    if (places < 0) {
      return 0.0D;
    }

    BigDecimal bd = new BigDecimal(value);
    bd = bd.setScale(places, RoundingMode.HALF_UP);
    return bd.doubleValue();
  }

  @Override
  public boolean onClick(int mouseX, int mouseY, int button) {
    if ((u(mouseX, mouseY) || i(mouseX, mouseY))
        && button == 0
        && this.moduleComponent.isOpened
        && this.moduleComponent.isVisible(this)) {
      if (isDouble()) {
        float cx = this.moduleComponent.categoryComponent.getX();
        float cw = this.moduleComponent.categoryComponent.getWidth();
        float trackLeft = cx + 6 + (xOffset / 2);
        float trackRight = cx + cw - 6 + (xOffset / 2);

        double range = getMax() - getMin();
        double fraction1 = range == 0 ? 0 : (getValue() - getMin()) / range;
        double fraction2 = range == 0 ? 0 : (getSecondValue() - getMin()) / range;

        float thumb1X = trackLeft + (float) ((trackRight - trackLeft) * fraction1);
        float thumb2X = trackLeft + (float) ((trackRight - trackLeft) * fraction2);

        if (Math.abs(mouseX - thumb1X) < Math.abs(mouseX - thumb2X)) {
          this.draggingMin = true;
        } else {
          this.draggingMax = true;
        }
      } else {
        this.heldDown = true;
      }
    }
    return false;
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int button) {
    this.heldDown = false;
    this.draggingMin = false;
    this.draggingMax = false;
  }

  public boolean u(int mouseX, int mouseY) {
    float fontScale = myau.ui.clickgui.components.impl.ModuleComponent.getFontScale();
    return mouseX > this.x
        && mouseX < this.x + this.moduleComponent.categoryComponent.getWidth() / 2 + 1
        && mouseY > this.y
        && mouseY < this.y + 16 * fontScale;
  }

  public boolean i(int mouseX, int mouseY) {
    float fontScale = myau.ui.clickgui.components.impl.ModuleComponent.getFontScale();
    return mouseX > this.x + this.moduleComponent.categoryComponent.getWidth() / 2
        && mouseX < this.x + this.moduleComponent.categoryComponent.getWidth()
        && mouseY > this.y
        && mouseY < this.y + 16 * fontScale;
  }

  @Override
  public void onGuiClosed() {
    this.heldDown = false;
    this.draggingMin = false;
    this.draggingMax = false;
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
}
