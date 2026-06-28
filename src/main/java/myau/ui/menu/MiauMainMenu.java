package myau.ui.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import me.ksyz.accountmanager.gui.GuiAccountManager;
import myau.ClientInfo;
import myau.management.MyauAPI;
import myau.ui.GuiUpdateClient;
import myau.util.font.Font;
import myau.util.font.FontRepository;
import myau.util.render.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class MiauMainMenu extends GuiScreen {

  private static boolean hasCheckedVersion = false;

  private static final int PARTICLE_COUNT = 55;
  private final float[] px = new float[PARTICLE_COUNT];
  private final float[] py = new float[PARTICLE_COUNT];
  private final float[] pSpeed = new float[PARTICLE_COUNT];
  private final float[] pSize = new float[PARTICLE_COUNT];
  private final float[] pAlpha = new float[PARTICLE_COUNT];
  private boolean particlesInit = false;
  private final Random rng = new Random();

  private float animProgress = 0.0f;

  private final float[] hoverAnim = new float[6];

  private long lastFrame = System.currentTimeMillis();

  private Font fontLogo;
  private Font fontSubtitle;
  private Font fontBtn;
  private Font fontMeta;

  static Font loadRiseFont(String filename, float size) {
    try {
      InputStream is =
          MiauMainMenu.class
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
    fontLogo = FontRepository.getHudFont(56);
    fontSubtitle = FontRepository.getHudFont(18);
    fontBtn = FontRepository.getHudFont(18);
    fontMeta = FontRepository.getHudFont(14);
  }

  @Override
  public void initGui() {
    super.initGui();
    this.buttonList.clear();
    loadFonts();
    this.animProgress = 0.0f;

    if (!particlesInit) {
      for (int i = 0; i < PARTICLE_COUNT; i++) respawnParticle(i, true);
      particlesInit = true;
    }

    if (!hasCheckedVersion) {
      hasCheckedVersion = true;
      new Thread(
              () -> {
                try {
                  String resp = MyauAPI.getClientVersion();
                  JsonObject json = new JsonParser().parse(resp).getAsJsonObject();
                  if (json.has("status") && json.get("status").getAsString().equals("success")) {
                    String latest = json.get("version").getAsString();
                    String url = json.get("updateUrl").getAsString();
                    if (!ClientInfo.VERSION.equals(latest)) {
                      Minecraft.getMinecraft()
                          .addScheduledTask(
                              () ->
                                  Minecraft.getMinecraft()
                                      .displayGuiScreen(
                                          new GuiUpdateClient(
                                              this, ClientInfo.VERSION, latest, url)));
                    }
                  }
                } catch (Exception ignored) {
                }
              })
          .start();
    }
  }

  private void respawnParticle(int i, boolean anywhere) {
    px[i] = rng.nextFloat() * Math.max(width, 854);
    py[i] = anywhere ? rng.nextFloat() * Math.max(height, 480) : Math.max(height, 480) + 5;
    pSpeed[i] = 0.15f + rng.nextFloat() * 0.45f;
    pSize[i] = 1.0f + rng.nextFloat() * 2.2f;
    pAlpha[i] = 0.2f + rng.nextFloat() * 0.45f;
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {}

  @Override
  public void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
    super.mouseClicked(mouseX, mouseY, button);
    if (button != 0) return;

    int centerX = width / 2;
    int centerY = height / 2;

    int btnWidth = 160;
    int btnHeight = 22;
    int spacing = 5;
    int startY = centerY - 25;

    for (int i = 0; i < 3; i++) {
      int by = startY + i * (btnHeight + spacing);
      int bx = centerX - btnWidth / 2;
      if (mouseX >= bx && mouseX <= bx + btnWidth && mouseY >= by && mouseY <= by + btnHeight) {
        onButtonClick(i);
        return;
      }
    }

    int bottomY = startY + (btnHeight + spacing) * 3;
    int smallW = (btnWidth - (spacing * 2)) / 3;
    int startSmallX = centerX - btnWidth / 2;

    for (int i = 0; i < 3; i++) {
      int bx = startSmallX + i * (smallW + spacing);
      if (mouseX >= bx
          && mouseX <= bx + smallW
          && mouseY >= bottomY
          && mouseY <= bottomY + btnHeight) {
        onButtonClick(3 + i);
        return;
      }
    }
  }

  private void onButtonClick(int id) {
    myau.module.modules.render.HUD hud = null;
    try {
      if (myau.Myau.moduleManager != null) {
        hud =
            (myau.module.modules.render.HUD)
                myau.Myau.moduleManager.getModule(myau.module.modules.render.HUD.class);
      }
    } catch (Exception ignored) {
    }

    switch (id) {
      case 0:
        mc.displayGuiScreen(new GuiSelectWorld(this));
        break;
      case 1:
        mc.displayGuiScreen(new GuiMultiplayer(this));
        break;
      case 2:
        mc.displayGuiScreen(new GuiAccountManager(this));
        break;
      case 3:
        mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
        break;
      case 4:
        if (hud != null) {
          int current = hud.menuBackground.getValue();
          int next = (current + 1) % myau.util.render.MenuBackground.NAMES.length;
          hud.menuBackground.setValue(next);
        }
        break;
      case 5:
        mc.shutdown();
        break;
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    loadFonts();

    long now = System.currentTimeMillis();
    float dt = Math.min((now - lastFrame) / 1000f, 0.05f);
    lastFrame = now;

    animProgress = myau.util.math.MathUtil.lerp(animProgress, 1.0f, 0.08f * (dt * 60f));

    drawBackground(now);
    drawParticles(dt, now);
    drawHeaderBlock(now);
    drawButtonBlock(mouseX, mouseY, dt, now);
    drawFooter();

    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  private void drawBackground(long now) {
    int shaderIndex = 0;
    try {
      if (myau.Myau.moduleManager != null) {
        myau.module.modules.render.HUD hud =
            (myau.module.modules.render.HUD)
                myau.Myau.moduleManager.getModule(myau.module.modules.render.HUD.class);
        if (hud != null) {
          shaderIndex = hud.menuBackground.getValue();
        }
      }
    } catch (Exception ignored) {
    }

    myau.util.render.MenuBackground.draw(width, height, shaderIndex);
  }

  private void drawParticles(float dt, long now) {
    Themes theme = Themes.getCurrentTheme();
    Color c1 = theme.getFirstColor();
    Color c2 = theme.getSecondColor();

    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableTexture2D();
    GL11.glEnable(GL11.GL_POINT_SMOOTH);
    GL11.glBegin(GL11.GL_POINTS);

    for (int i = 0; i < PARTICLE_COUNT; i++) {
      py[i] -= pSpeed[i] * 18f * dt;
      px[i] += (float) Math.sin(now / 1800.0 + i * 1.7f) * 0.1f;
      if (py[i] < -4) respawnParticle(i, false);

      Color c = blendC(c1, c2, (float) i / PARTICLE_COUNT);
      GL11.glPointSize(pSize[i]);
      GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, pAlpha[i]);
      GL11.glVertex2f(px[i], py[i]);
    }

    GL11.glEnd();
    GL11.glDisable(GL11.GL_POINT_SMOOTH);
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.resetColor();
  }

  private void drawHeaderBlock(long now) {
    if (fontLogo == null || fontSubtitle == null) return;

    Themes theme = Themes.getCurrentTheme();
    Color accent = theme.getFirstColor();

    float centerX = width / 2.0f;
    float centerY = height / 2.0f;

    float titleY = centerY - 85;
    float s = 1.0f + (1.0f - animProgress) * 0.2f;
    int alphaVal = (int) (255 * animProgress);
    int color = new Color(255, 255, 255, alphaVal).getRGB();
    int subColor = new Color(180, 180, 180, Math.max(0, alphaVal - 80)).getRGB();

    if (fontLogo instanceof myau.util.font.impl.minecraft.MinecraftFontRenderer) {
      GlStateManager.pushMatrix();
      float titleScale = s * 3.0f;
      GlStateManager.translate(centerX, titleY, 0);
      GlStateManager.scale(titleScale, titleScale, 1.0f);
      GlStateManager.translate(-centerX, -titleY, 0);
      fontLogo.drawCentered("Miau Client", centerX, titleY, color);
      GlStateManager.popMatrix();

      GlStateManager.pushMatrix();
      float subScale = s * 1.2f;
      float subY = titleY + 9.0f * 3.0f + 4.0f;
      GlStateManager.translate(centerX, subY, 0);
      GlStateManager.scale(subScale, subScale, 1.0f);
      GlStateManager.translate(-centerX, -subY, 0);
      String ver = "v" + ClientInfo.VERSION;
      fontSubtitle.drawCentered(ver, centerX, subY, subColor);
      GlStateManager.popMatrix();
    } else {
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, titleY, 0);
      GlStateManager.scale(s, s, 1.0f);
      GlStateManager.translate(-centerX, -titleY, 0);
      fontLogo.drawCentered("Miau Client", centerX, titleY, color);
      String ver = "v" + ClientInfo.VERSION;
      fontSubtitle.drawCentered(ver, centerX, titleY + fontLogo.height() + 4, subColor);
      GlStateManager.popMatrix();
    }
  }

  private void drawButtonBlock(int mouseX, int mouseY, float dt, long now) {
    if (fontBtn == null) return;

    int centerX = width / 2;
    int centerY = height / 2;

    int btnWidth = 160;
    int btnHeight = 22;
    int spacing = 5;
    int startY = centerY - 25;

    drawButton(
        0,
        centerX - btnWidth / 2,
        startY,
        btnWidth,
        btnHeight,
        "Singleplayer",
        mouseX,
        mouseY,
        dt,
        0);

    drawButton(
        1,
        centerX - btnWidth / 2,
        startY + btnHeight + spacing,
        btnWidth,
        btnHeight,
        "Multiplayer",
        mouseX,
        mouseY,
        dt,
        1);

    drawButton(
        2,
        centerX - btnWidth / 2,
        startY + (btnHeight + spacing) * 2,
        btnWidth,
        btnHeight,
        "Alt Manager",
        mouseX,
        mouseY,
        dt,
        2);

    int bottomY = startY + (btnHeight + spacing) * 3;
    int smallW = (btnWidth - (spacing * 2)) / 3;
    int startSmallX = centerX - btnWidth / 2;

    drawButton(3, startSmallX, bottomY, smallW, btnHeight, "Settings", mouseX, mouseY, dt, 3);
    drawButton(
        4,
        startSmallX + smallW + spacing,
        bottomY,
        smallW,
        btnHeight,
        "Theme",
        mouseX,
        mouseY,
        dt,
        4);
    drawButton(
        5,
        startSmallX + (smallW + spacing) * 2,
        bottomY,
        smallW,
        btnHeight,
        "Exit",
        mouseX,
        mouseY,
        dt,
        5);
  }

  private void drawButton(
      int id,
      int bx,
      int by,
      int bw,
      int bh,
      String label,
      int mouseX,
      int mouseY,
      float dt,
      int count) {
    float btnAnim = Math.max(0.0f, Math.min(1.0f, (animProgress - (count * 0.03f)) * 2.5f));
    if (btnAnim <= 0.01f) return;

    boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
    float target = hov ? 1.0f : 0.0f;
    hoverAnim[id] = myau.util.math.MathUtil.lerp(hoverAnim[id], target, 0.25f * (dt * 60f));
    float h = hoverAnim[id];

    GlStateManager.pushMatrix();

    float cx = bx + bw / 2.0f;
    float cy = by + bh / 2.0f;

    GlStateManager.translate(cx, cy, 0);
    GlStateManager.scale(btnAnim, btnAnim, 1.0f);
    GlStateManager.translate(-cx, -cy, 0);
    GlStateManager.translate(0, (1.0f - btnAnim) * 5.0f, 0);

    Color colorBgNormal = new Color(20, 20, 20, 120);
    Color colorBgHover = new Color(40, 40, 45, 200);
    Color colorOutlineNormal = new Color(255, 255, 255, 60);
    Color colorOutlineHover = new Color(255, 255, 255, 180);

    Color bg = blendC(colorBgNormal, colorBgHover, h);
    Color outline = blendC(colorOutlineNormal, colorOutlineHover, h);
    Color textCol = blendC(new Color(200, 200, 200), Color.WHITE, h);

    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

    myau.util.render.RenderUtil.drawRoundedRectangle(bx, by, bx + bw, by + bh, 5.0f, bg.getRGB());
    myau.util.render.RenderUtil.drawRoundedRectangleOutline(
        bx, by, bx + bw, by + bh, 5.0f, 1.0f, outline.getRGB());

    float fontHeight = fontBtn.height();
    if (fontBtn instanceof myau.util.font.impl.minecraft.MinecraftFontRenderer) {
      fontHeight = 8.0f;
    }
    float fontY = by + (bh - fontHeight) / 2.0f;
    fontBtn.drawCentered(label, cx, fontY, textCol.getRGB());

    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    GlStateManager.popMatrix();
  }

  private void drawFooter() {
    if (fontMeta == null) return;
    Gui.drawRect(0, height - 1, width, height, new Color(255, 255, 255, 10).getRGB());

    String left = "Miau Client v" + ClientInfo.VERSION;
    float footerY = height - fontMeta.height() - 5;
    fontMeta.draw(left, 5, footerY, 0xD0FFFFFF, false);

    String right = "Credits: [ksyz, OpenMyau Project, idle]";
    int rw = fontMeta.width(right);
    fontMeta.draw(right, width - rw - 5, footerY, 0xD0FFFFFF, false);
  }

  private void drawGlow(Font font, String text, int x, int y, Color color, long now) {
    double pulse = Math.sin(now / 1400.0) * 0.5 + 0.5;
    int baseAlpha = (int) (18 + pulse * 18);
    for (int off = 4; off >= 1; off--) {
      int a = Math.max(1, baseAlpha / off);
      int gc = alpha(color, a).getRGB();
      font.draw(text, x - off, y, gc, false);
      font.draw(text, x + off, y, gc, false);
      font.draw(text, x, y - off, gc, false);
      font.draw(text, x, y + off, gc, false);
    }
  }

  private void drawBlob(float cx, float cy, float radius, Color color, int alpha) {
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableTexture2D();
    int steps = 20;
    for (int s = steps; s >= 0; s--) {
      float r = radius * ((float) s / steps);
      float a = (alpha / 255f) * (1f - (float) s / steps);
      GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, a);
      GL11.glBegin(GL11.GL_TRIANGLE_FAN);
      GL11.glVertex2f(cx, cy);
      for (int seg = 0; seg <= 52; seg++) {
        double ang = seg * (Math.PI * 2.0 / 52);
        GL11.glVertex2f((float) (cx + Math.cos(ang) * r), (float) (cy + Math.sin(ang) * r));
      }
      GL11.glEnd();
    }
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    GlStateManager.resetColor();
  }

  private static Color blendC(Color a, Color b, float t) {
    t = Math.max(0f, Math.min(1f, t));
    return new Color(
        (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
        (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
        (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
        (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
  }

  private static Color alpha(Color c, int a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a));
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
