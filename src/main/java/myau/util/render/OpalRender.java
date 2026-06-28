package myau.util.render;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class OpalRender {

  public static void rect(float x, float y, float width, float height, int color) {
    RenderUtil.drawRect(x, y, x + width, y + height, color);
  }

  public static void rectGradient(
      float x, float y, float width, float height, int color1, int color2, int type) {
    float r1 = (color1 >> 16 & 0xFF) / 255.0F;
    float g1 = (color1 >> 8 & 0xFF) / 255.0F;
    float b1 = (color1 & 0xFF) / 255.0F;
    float a1 = (color1 >> 24 & 0xFF) / 255.0F;

    float r2 = (color2 >> 16 & 0xFF) / 255.0F;
    float g2 = (color2 >> 8 & 0xFF) / 255.0F;
    float b2 = (color2 & 0xFF) / 255.0F;
    float a2 = (color2 >> 24 & 0xFF) / 255.0F;

    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_ALPHA_TEST);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glShadeModel(GL11.GL_SMOOTH);

    GL11.glBegin(GL11.GL_QUADS);
    if (type == 0) { // Horizontal
      GL11.glColor4f(r1, g1, b1, a1);
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x, y + height);
      GL11.glColor4f(r2, g2, b2, a2);
      GL11.glVertex2f(x + width, y + height);
      GL11.glVertex2f(x + width, y);
    } else { // Vertical
      GL11.glColor4f(r1, g1, b1, a1);
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x + width, y);
      GL11.glColor4f(r2, g2, b2, a2);
      GL11.glVertex2f(x + width, y + height);
      GL11.glVertex2f(x, y + height);
    }
    GL11.glEnd();

    GL11.glShadeModel(GL11.GL_FLAT);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_ALPHA_TEST);
    GL11.glEnable(GL11.GL_TEXTURE_2D);

    int err = GL11.glGetError();
    if (err != 0) {
      System.out.println("GL ERROR in OpalRender.rectGradient: " + err);
    }
  }

  public static void roundedRectVarying(
      float x,
      float y,
      float width,
      float height,
      float tl,
      float tr,
      float br,
      float bl,
      int color) {
    // A simple fallback if RoundedUtils doesn't match perfectly, using Miau's RoundedUtils
    // LeftTop, RightTop, RightBottom, LeftBottom
    myau.util.shader.RoundedUtils.drawRoundedRectRise(
        x,
        y,
        width,
        height,
        Math.max(tl, Math.max(tr, Math.max(br, bl))),
        color,
        tl > 0,
        tr > 0,
        br > 0,
        bl > 0);
  }

  public static void roundedRectVaryingGradient(
      float x,
      float y,
      float width,
      float height,
      float tl,
      float tr,
      float br,
      float bl,
      int color1,
      int color2,
      int type) {
    // Miau's RoundedUtils might not support gradient with varying corners directly,
    // so we approximate or use a gradient shader if available.
    // For 1.8.9 pure GL, we'll draw a clipped gradient or just use rectGradient if inside a
    // clip/scissor.
    // In the Opal code, roundedRectVaryingGradient is used, but mostly inside scissors or for
    // backgrounds.
    // We will just draw a standard gradient since RoundedUtils in Miau doesn't support varying
    // corners + gradient.
    myau.util.shader.RoundedUtils.drawGradientRound(
        x,
        y,
        width,
        height,
        Math.max(tl, Math.max(tr, Math.max(br, bl))),
        color1,
        color2,
        color2,
        color1);
  }

  private static final java.nio.IntBuffer SCISSOR_BOX = org.lwjgl.BufferUtils.createIntBuffer(16);

  public static void scissor(float x, float y, float width, float height, Runnable runnable) {
    boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    int[] currentScissor = new int[4];

    if (wasEnabled) {
      SCISSOR_BOX.clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
      currentScissor[0] = SCISSOR_BOX.get(0);
      currentScissor[1] = SCISSOR_BOX.get(1);
      currentScissor[2] = SCISSOR_BOX.get(2);
      currentScissor[3] = SCISSOR_BOX.get(3);
    }

    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
    int scale = sr.getScaleFactor();

    int finalX = (int) (x * scale);
    int finalY = (int) (Minecraft.getMinecraft().displayHeight - ((y + height) * scale));
    int finalWidth = (int) (width * scale);
    int finalHeight = (int) (height * scale);

    if (wasEnabled) {
      // Intersect with current scissor
      int cx = Math.max(finalX, currentScissor[0]);
      int cy = Math.max(finalY, currentScissor[1]);
      int cWidth = Math.min(finalX + finalWidth, currentScissor[0] + currentScissor[2]) - cx;
      int cHeight = Math.min(finalY + finalHeight, currentScissor[1] + currentScissor[3]) - cy;
      GL11.glScissor(cx, cy, Math.max(0, cWidth), Math.max(0, cHeight));
    } else {
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(finalX, finalY, Math.max(0, finalWidth), Math.max(0, finalHeight));
    }

    try {
      runnable.run();
    } finally {
      if (wasEnabled) {
        GL11.glScissor(currentScissor[0], currentScissor[1], currentScissor[2], currentScissor[3]);
      } else {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }
    }

    int err = GL11.glGetError();
    if (err != 0) {
      System.out.println("GL ERROR in OpalRender.scissor: " + err);
    }
  }

  public static int applyOpacity(int color, float opacity) {
    Color c = new Color(color, true);
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getAlpha() * opacity))
        .getRGB();
  }
}
