package myau.module.modules.render;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import myau.Myau;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public class Capes extends Module {
  public static final List<ResourceLocation> LOADED_CAPES = new ArrayList<>();
  public static String[] CAPES_NAME =
      new String[] {
        "anime", "rvn_aqua", "rvn_green", "rvn_purple", "rvn_red", "rvn_white", "rvn_yellow"
      };

  public final ModeProperty capeMode = new ModeProperty("Cape", 0, CAPES_NAME);
  public final BooleanProperty btnLoadCapes = new BooleanProperty("Load capes", false);
  public final BooleanProperty btnOpenFolder = new BooleanProperty("Open folder", false);

  private static File directory;

  public Capes() {
    super("Capes", false);

    directory =
        new File(Minecraft.getMinecraft().mcDataDir + File.separator + "keystrokes", "customCapes");
    if (!directory.exists()) {
      boolean success = directory.mkdirs();
      if (!success) {
        System.out.println("There was an issue creating customCapes directory.");
      }
    }

    loadCapes();
  }

  @Override
  public void verifyValue(String name) {
    if (name.equals("Load capes") && btnLoadCapes.getValue()) {
      btnLoadCapes.setValue(false);
      loadCapes();
    } else if (name.equals("Open folder") && btnOpenFolder.getValue()) {
      btnOpenFolder.setValue(false);
      try {
        Desktop.getDesktop().open(directory);
      } catch (IOException ex) {
        directory.mkdirs();
        ChatUtil.display("&cError locating folder, recreated.");
      }
    }
  }

  public void loadCapes() {
    final File[] files;
    try {
      files = Objects.requireNonNull(directory.listFiles());
    } catch (NullPointerException e) {
      ChatUtil.display("&cFail to load custom capes.");
      return;
    }

    final String[] builtinCapes =
        new String[] {
          "anime", "rvn_aqua", "rvn_green", "rvn_purple", "rvn_red", "rvn_white", "rvn_yellow"
        };

    CAPES_NAME = new String[files.length + builtinCapes.length];
    LOADED_CAPES.clear();
    System.arraycopy(builtinCapes, 0, CAPES_NAME, 0, builtinCapes.length);

    for (String s : builtinCapes) {
      String name = s.toLowerCase();
      try {
        InputStream stream =
            Myau.class.getResourceAsStream("/assets/keystrokesmod/textures/capes/" + name + ".png");
        if (stream == null) {
          stream =
              Myau.class.getResourceAsStream("/assets/keystrokesmod/textures/capes/" + s + ".png");
        }
        if (stream == null) {
          continue;
        }
        BufferedImage bufferedImage = ImageIO.read(stream);
        LOADED_CAPES.add(
            Minecraft.getMinecraft()
                .renderEngine
                .getDynamicTextureLocation(name, new DynamicTexture(bufferedImage)));
        stream.close();
      } catch (Exception e) {
        ChatUtil.display("&cFailed to load cape '&r" + s + "&c'");
      }
    }

    for (int i = 0, filesLength = files.length; i < filesLength; i++) {
      File file = files[i];
      if (!file.exists() || !file.isFile()) continue;
      if (!file.getName().endsWith(".png")) continue;
      String fileName = file.getName().substring(0, file.getName().length() - 4);

      CAPES_NAME[builtinCapes.length + i] = fileName;

      try {
        BufferedImage bufferedImage = ImageIO.read(file);
        LOADED_CAPES.add(
            Minecraft.getMinecraft()
                .renderEngine
                .getDynamicTextureLocation(fileName, new DynamicTexture(bufferedImage)));
      } catch (IOException e) {
        ChatUtil.display("&cFailed to load cape '&r" + fileName + "&c'");
      }
    }

    capeMode.setModes(CAPES_NAME);
    ChatUtil.display("&aLoaded &r" + CAPES_NAME.length + "&a capes.");
  }

  public ResourceLocation getCape() {
    int index = capeMode.getValue();
    if (index >= 0 && index < LOADED_CAPES.size()) {
      return LOADED_CAPES.get(index);
    }
    return null;
  }
}
