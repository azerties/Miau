package myau.util.font;

import java.io.InputStream;
import myau.util.font.impl.rise.FontRenderer;

public class ClientFontManager {

  public static final String[] FACES = {
    "Minecraft",
    "Rise",
    "Rise Bold",
    "Rise Medium",
    "Rise Semibold",
    "Comfortaa Regular",
    "Comfortaa Medium",
    "Comfortaa Bold",
    "Comfortaa Semibold",
    "Geist Sans Regular",
    "Geist Sans Bold",
    "Geist Sans Semibold",
    "Greycliff CF Regular",
    "Greycliff CF Medium",
    "Greycliff CF Bold",
    "Greycliff CF Semibold"
  };

  private static final String[] FILES = {
    "",
    "Rise.ttf",
    "Rise Bold.ttf",
    "Rise Medium.ttf",
    "Rise Semibold.ttf",
    "comfortaa-regular.ttf",
    "comfortaa-medium.ttf",
    "comfortaa-bold.ttf",
    "comfortaa-semibold.ttf",
    "geistsans-regular.ttf",
    "geistsans-bold.ttf",
    "geistsans-semibold.ttf",
    "greycliffcf-regular.ttf",
    "greycliffcf-medium.ttf",
    "greycliffcf-bold.ttf",
    "greycliffcf-semibold.ttf"
  };

  private static int currentFace = 1;

  private static Font small;
  private static Font regular;
  private static Font medium;
  private static Font large;
  private static Font title;

  public static void setFace(int faceIndex) {
    if (faceIndex < 0 || faceIndex >= FILES.length) return;
    if (faceIndex == currentFace && small != null) return;
    currentFace = faceIndex;
    load();
  }

  public static boolean isMinecraftSelected() {
    return currentFace == 0;
  }

  public static String getSelectedFontFileName() {
    return FILES[currentFace];
  }

  public static int getFace() {
    return currentFace;
  }

  private static void load() {
    if (isMinecraftSelected()) {
      small = Fonts.MINECRAFT.get();
      regular = Fonts.MINECRAFT.get();
      medium = Fonts.MINECRAFT.get();
      large = Fonts.MINECRAFT.get();
      title = Fonts.MINECRAFT.get();
    } else {
      small = build(FILES[currentFace], 10f);
      regular = build(FILES[currentFace], 13f);
      medium = build(FILES[currentFace], 16f);
      large = build(FILES[currentFace], 22f);
      title = build(FILES[currentFace], 48f);
    }
  }

  private static Font build(String file, float size) {
    try {
      InputStream is =
          ClientFontManager.class
              .getClassLoader()
              .getResourceAsStream("assets/keystrokesmod/fonts/" + file);
      if (is == null) return Fonts.MINECRAFT.get();
      java.awt.Font awt =
          java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is).deriveFont(size);
      return new FontRenderer(awt, true, true, false);
    } catch (Exception e) {
      return Fonts.MINECRAFT.get();
    }
  }

  public static Font getSmall() {
    if (small == null) load();
    return small;
  }

  public static Font getRegular() {
    if (regular == null) load();
    return regular;
  }

  public static Font getMedium() {
    if (medium == null) load();
    return medium;
  }

  public static Font getLarge() {
    if (large == null) load();
    return large;
  }

  public static Font getTitle() {
    if (title == null) load();
    return title;
  }

  public static Font get(float size) {
    if (isMinecraftSelected()) {
      return Fonts.MINECRAFT.get();
    }
    return build(FILES[currentFace], size);
  }
}
