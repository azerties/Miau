package myau.notification;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.Render2DEvent;
import myau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

public class NotificationManager {

  private static final float PADDING = 8f;
  private static final float ICON_SIZE = 16f;
  private static final float SPACING = 6f;
  private static final float CONTAINER_HEIGHT = 26f;
  private static final float GAP = 3f;

  private final List<Notification> notifications = new ArrayList<>();
  private long lastRenderTime = System.currentTimeMillis();
  private boolean initialized = false;
  public final myau.property.properties.DragProperty drag =
      new myau.property.properties.DragProperty(
          "Notifications", new myau.util.vector.Vector2d(100, 100));

  public NotificationBuilder builder(NotificationType type) {
    return new NotificationBuilder(type, this);
  }

  public void add(Notification notification) {
    synchronized (notifications) {
      notifications.add(notification);
    }
    System.out.println(
        "[Notification] " + notification.getTitle() + " - " + notification.getDescription());
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) return;

    ScaledResolution sr = new ScaledResolution(mc);
    render(sr);
  }

  public void render(ScaledResolution sr) {
    Minecraft mc = Minecraft.getMinecraft();

    long currentTime = System.currentTimeMillis();
    float deltaTime = (currentTime - lastRenderTime) / 1000f;
    lastRenderTime = currentTime;

    if (deltaTime > 0.1f) deltaTime = 0.1f;

    final float animationSpeed = 12.0f;

    if (!initialized) {
      drag.position.x = drag.targetPosition.x = sr.getScaledWidth() - 165;
      drag.position.y = drag.targetPosition.y = sr.getScaledHeight() - 15;
      initialized = true;
    }

    if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat
        || mc.currentScreen instanceof myau.ui.clickgui.ClickGui)) {
      drag.position.x = drag.targetPosition.x;
      drag.position.y = drag.targetPosition.y;
    }

    drag.scale.x = 150;
    drag.scale.y = 40;

    float anchorX = (float) drag.position.x;
    float anchorY = (float) drag.position.y;

    float scaledWidth = sr.getScaledWidth();

    synchronized (notifications) {
      int activeIndex = 0;
      Iterator<Notification> iterator = notifications.iterator();
      while (iterator.hasNext()) {
        Notification notif = iterator.next();

        float containerWidth = getContainerWidth(notif);

        float targetX;
        if (notif.hasExpired()) {
          targetX = scaledWidth + 10;
        } else {
          targetX = (anchorX + 150f) - containerWidth;
        }

        float currentTargetY;
        if (notif.hasExpired()) {
          currentTargetY = notif.targetY;
        } else {
          currentTargetY = anchorY - (activeIndex * (CONTAINER_HEIGHT + GAP)) - CONTAINER_HEIGHT;
          notif.targetY = currentTargetY;
          activeIndex++;
        }

        if (notif.firstFrame) {
          notif.x = scaledWidth + 10;
          notif.y = currentTargetY;
          notif.firstFrame = false;
        }

        notif.x += (targetX - notif.x) * animationSpeed * deltaTime;
        notif.y += (currentTargetY - notif.y) * animationSpeed * deltaTime;

        if (notif.hasExpired() && Math.abs(notif.x - targetX) < 1.0f) {
          iterator.remove();
        }
      }

      for (Notification notif : notifications) {
        renderNotification(notif);
      }
    }
  }

  private float getContainerWidth(Notification notif) {
    myau.util.font.Font boldFont = myau.util.font.Fonts.MAIN.get(14, myau.util.font.Weight.BOLD);
    myau.util.font.Font regFont = myau.util.font.Fonts.MAIN.get(12);
    int titleWidth = boldFont.width(notif.getTitle());
    int descWidth = regFont.width(notif.getDescription());
    float textWidth = Math.max(titleWidth, descWidth);
    return Math.max(150f, PADDING + ICON_SIZE + SPACING + textWidth + PADDING);
  }

  private void renderNotification(Notification notif) {
    float x = notif.x;
    float y = notif.y;
    float containerWidth = getContainerWidth(notif);

    int typeColor = notif.getType().getColor();

    Gui.drawRect(
        (int) x,
        (int) y,
        (int) (x + containerWidth),
        (int) (y + CONTAINER_HEIGHT),
        new Color(0, 0, 0, 200).getRGB());

    Gui.drawRect((int) x, (int) y, (int) (x + 2), (int) (y + CONTAINER_HEIGHT), typeColor);

    float iconCX = x + PADDING + ICON_SIZE / 2f;
    float iconCY = y + CONTAINER_HEIGHT / 2f;
    float iconRadius = 5f;

    RenderUtil.enableRenderState();
    RenderUtil.fillCircle(iconCX, iconCY, iconRadius, 16, typeColor);

    RenderUtil.fillCircle(iconCX, iconCY, iconRadius * 0.45f, 12, 0xFFFFFFFF);
    RenderUtil.disableRenderState();

    myau.util.font.Font boldFont = myau.util.font.Fonts.MAIN.get(14, myau.util.font.Weight.BOLD);
    myau.util.font.Font regFont = myau.util.font.Fonts.MAIN.get(12);
    float textX = x + PADDING + ICON_SIZE + SPACING;
    float totalTextHeight = boldFont.height() + regFont.height() + 2;
    float startTextY = y + (CONTAINER_HEIGHT - totalTextHeight) / 2f;

    boldFont.drawWithShadow(notif.getTitle(), textX, startTextY, -1);
    regFont.drawWithShadow(
        notif.getDescription(), textX, startTextY + boldFont.height() + 2, 0xFFAAAAAA);
  }

  public static class NotificationBuilder {
    private final NotificationType type;
    private final NotificationManager manager;
    private String title = "";
    private String description = "";
    private int duration = 2000;

    public NotificationBuilder(NotificationType type, NotificationManager manager) {
      this.type = type;
      this.manager = manager;
    }

    public NotificationBuilder title(String title) {
      this.title = title;
      return this;
    }

    public NotificationBuilder description(String description) {
      this.description = description;
      return this;
    }

    public NotificationBuilder duration(int duration) {
      this.duration = duration;
      return this;
    }

    public void buildAndPublish() {
      manager.add(new Notification(type, title, description, duration));
    }
  }
}
