package myau.ui.menu;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import myau.util.font.Font;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class WelcomeScreen extends GuiScreen {

  private static final long BG_FADE_DUR = 800L;
  private static final long M_START = 1100L;
  private static final long M_DUR = 900L;
  private static final long M_PAUSE = 700L;
  private static final long SLIDE_DUR = 500L;
  private static final long IAU_PAUSE = 300L;
  private static final long IAU_DUR = 500L;
  private static final long HOLD_DUR = 1300L;
  private static final long FADE_OUT_DUR = 700L;

  private static final long SLIDE_START = M_START + M_DUR + M_PAUSE;
  private static final long IAU_START = SLIDE_START + SLIDE_DUR + IAU_PAUSE;
  private static final long FADE_OUT_START = IAU_START + IAU_DUR + HOLD_DUR;
  private static final long TOTAL = FADE_OUT_START + FADE_OUT_DUR;

  private long startTime = -1L;
  private boolean done = false;

  private Font mFont;
  private Font iauFont;

  static Font loadFont(String filename, float size) {
    try {
      InputStream is =
          WelcomeScreen.class
              .getClassLoader()
              .getResourceAsStream("assets/keystrokesmod/fonts/" + filename);
      if (is == null) return null;
      java.awt.Font awt =
          java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is).deriveFont(size);
      return new myau.util.font.impl.rise.FontRenderer(awt, true, true, false);
    } catch (Exception e) {
      return null;
    }
  }

  private void loadFonts() {
    if (mFont == null) {
      mFont = loadFont("Rise Bold.ttf", 120f);
      if (mFont == null) mFont = loadFont("Rise.ttf", 120f);
    }
    if (iauFont == null) {
      iauFont = loadFont("Rise Bold.ttf", 72f);
      if (iauFont == null) iauFont = loadFont("Rise.ttf", 72f);
    }
  }

  @Override
  public void initGui() {
    super.initGui();
    loadFonts();
    if (startTime < 0) startTime = System.currentTimeMillis();
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {}

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {}

  @Override
  protected void actionPerformed(net.minecraft.client.gui.GuiButton b) throws IOException {}

  @Override
  public void drawScreen(int mx, int my, float pt) {
    loadFonts();
    if (startTime < 0) startTime = System.currentTimeMillis();
    GlStateManager.color(1f, 1f, 1f, 1f);

    long el = System.currentTimeMillis() - startTime;

    if (el >= TOTAL && !done) {
      done = true;
      mc.displayGuiScreen(new MiauMainMenu());
      return;
    }

    float bgA;
    if (el <= BG_FADE_DUR) {
      bgA = 0.75f * easeOut((float) el / BG_FADE_DUR);
    } else if (el <= FADE_OUT_START) {
      bgA = 0.75f;
    } else {
      bgA = 0.75f * (1f - easeIn((float) (el - FADE_OUT_START) / FADE_OUT_DUR));
    }
    drawBg(bgA);

    if (mFont == null || iauFont == null) {
      GlStateManager.color(1f, 1f, 1f, 1f);
      return;
    }

    float gf = el > FADE_OUT_START ? 1f - easeIn((float) (el - FADE_OUT_START) / FADE_OUT_DUR) : 1f;

    float mScale = 1f, mAlpha = 0f;
    if (el >= M_START) {
      long s = el - M_START;
      if (s <= M_DUR) {
        float p = easeOut((float) s / M_DUR);
        mScale = lerp(2f, 1f, p);
        mAlpha = p;
      } else {
        mAlpha = 1f;
      }
    }

    float slide = 0f;
    if (el > SLIDE_START) {
      slide = el <= SLIDE_START + SLIDE_DUR ? easeOut((float) (el - SLIDE_START) / SLIDE_DUR) : 1f;
    }

    float iauAlpha = 0f, iauOffY = 14f;
    if (el > IAU_START) {
      long s = el - IAU_START;
      if (s <= IAU_DUR) {
        float p = easeOut((float) s / IAU_DUR);
        iauAlpha = p;
        iauOffY = (1f - p) * 14f;
      } else {
        iauAlpha = 1f;
        iauOffY = 0f;
      }
    }

    float cx = width / 2f;
    float cy = height / 2f;

    float mNatW = mFont.width("M");
    float mNatH = mFont.height();
    float mScW = mNatW * mScale;

    float iauW = iauFont.width("iau");
    float iauH = iauFont.height();

    float spacing = 0f;
    float totalW = mScW + spacing + iauW;

    float visibleHeightM = 120f * 0.36f;
    float mRY = cy - (visibleHeightM * mScale) / 2f;
    float mCenterX = cx - mScW / 2f;
    float mSlideX = cx - totalW / 2f;
    float mRX = lerp(mCenterX, mSlideX, slide);

    float iauRY = mRY + (120f - 72f) * 0.5f;

    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

    if (mAlpha > 0f) {
      float originX = mRX + (mScW - mNatW) / 2f;
      float originY = mRY + (mNatH * mScale - mNatH) / 2f;

      GL11.glPushMatrix();
      GL11.glTranslatef(originX, originY, 0f);
      GL11.glScalef(mScale, mScale, 1f);
      mFont.drawWithShadow("M", 0, -2, rgba(255, 255, 255, clamp(mAlpha * gf)));
      GL11.glPopMatrix();
    }

    if (iauAlpha > 0f) {
      float iauX = mRX + mScW + spacing;
      float iauY = iauRY + iauOffY;
      iauFont.drawWithShadow("iau", iauX, iauY - 2, rgba(255, 255, 255, clamp(iauAlpha * gf)));
    }

    GlStateManager.disableBlend();
    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  private void drawBg(float alpha) {
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(
        GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    for (int i = 0; i < 3; i++) {
      GL11.glColor4f(0f, 0f, 0f, alpha * 0.33f);
      GL11.glBegin(GL11.GL_QUADS);
      GL11.glVertex2f(0, 0);
      GL11.glVertex2f(width, 0);
      GL11.glVertex2f(width, height);
      GL11.glVertex2f(0, height);
      GL11.glEnd();
    }
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
  }

  private static float clamp(float v) {
    return v < 0f ? 0f : (v > 1f ? 1f : v);
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  private static float easeOut(float t) {
    return 1f - (float) Math.pow(1f - clamp(t), 3.0);
  }

  private static float easeIn(float t) {
    t = clamp(t);
    return t * t * t;
  }

  private static int rgba(int r, int g, int b, float a) {
    return new Color(r, g, b, (int) (clamp(a) * 255f)).getRGB();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
