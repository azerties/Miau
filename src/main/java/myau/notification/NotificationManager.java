package myau.notification;

import java.util.ArrayList;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.Render2DEvent;
import myau.property.properties.DragProperty;
import myau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;

/**
 * Singleton notification manager for Miau client – Opal-style.
 *
 * <p>Maintains a simple {@link ArrayList} of {@link Notification} entries, ordered oldest-first
 * (newest appended at the end). Rendering and animation state are managed entirely by {@link
 * NotificationRenderer} via a {@code Map<Notification, Animation>}.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // Simple push
 * NotificationManager.getInstance().push("Title", "Message", NotificationType.INFO);
 *
 * // Builder API (backward-compatible)
 * NotificationManager.getInstance()
 *         .builder(NotificationType.ENABLED)
 *         .title("KillAura")
 *         .description("Enabled")
 *         .buildAndPublish();
 * }</pre>
 */
public class NotificationManager {

  // ── Singleton ────────────────────────────────────────────────────────────

  private static volatile NotificationManager instance;

  /** Returns the process-wide singleton instance. Creates it lazily if necessary. */
  public static NotificationManager getInstance() {
    if (instance == null) {
      synchronized (NotificationManager.class) {
        if (instance == null) {
          instance = new NotificationManager();
        }
      }
    }
    return instance;
  }

  // ── Inner class: Notification (Opal-style data model) ──────────────────

  /** Immutable notification value object with {@link TimerUtil}-based timing. */
  public static class Notification {

    private final TimerUtil timer = new TimerUtil();
    private final NotificationType type;
    private final String title, description;
    private final int duration;

    Notification(NotificationType type, String title, String description, int duration) {
      this.type = type;
      this.title = title;
      this.description = description;
      this.duration = duration;
      timer.reset();
    }

    public NotificationType getType() {
      return type;
    }

    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public String getMessage() {
      return description;
    }

    public int getDuration() {
      return duration;
    }

    /**
     * @return {@code true} once the notification has lived past its duration.
     */
    public boolean hasExpired() {
      return timer.hasTimeElapsed(duration, false);
    }

    /** Milliseconds elapsed since creation. */
    public long getTime() {
      return timer.getTime();
    }

    /**
     * @deprecated Not used in Opal-style rendering – kept for API compatibility.
     */
    @Deprecated
    public long getStartTime() {
      return System.currentTimeMillis() - timer.getTime();
    }
  }

  public static int DEFAULT_DURATION = 4000;

  private final List<Notification> notifications = new ArrayList<>();

  @Deprecated public final DragProperty drag;

  public NotificationManager() {
    this.drag = new DragProperty("Notifications", new myau.util.vector.Vector2d(0, 0));
    this.drag.setScale(new myau.util.vector.Vector2d(0, 0));
    this.drag.structure = false;
    if (instance == null) {
      instance = this;
    }
  }

  public void push(String title, String message, NotificationType type) {
    push(title, message, type, DEFAULT_DURATION);
  }

  public void push(String title, String message, NotificationType type, int duration) {
    notifications.add(new Notification(type, title, message, duration));
    System.out.println("[Notification] " + title + " - " + message);

    // Subtle audio feedback — only when a player is in-world
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.thePlayer != null) {
        mc.thePlayer.playSound("random.click", 0.3f, 1.8f);
      }
    } catch (Exception ignored) {
      /* no-op outside game world */
    }
  }

  /**
   * Returns the full notification list (oldest first, newest last). Renderer iterates bottom-up for
   * the Opal stack layout.
   */
  public List<Notification> getNotifications() {
    return notifications;
  }

  /**
   * Removes a specific notification from the list. Called by the renderer after the exit animation
   * completes.
   */
  public void remove(Notification notification) {
    notifications.remove(notification);
  }

  // ── Static convenience helper ────────────────────────────────────────────

  /**
   * Push a notification from anywhere without holding a reference to the manager.
   *
   * <pre>{@code
   * NotificationManager.notify("KillAura", "Enabled", NotificationType.ENABLED);
   * }</pre>
   *
   * @param title module or feature name
   * @param msg short description
   * @param type visual style
   */
  public static void notify(String title, String msg, NotificationType type) {
    getInstance().push(title, msg, type);
  }

  // ── Legacy / Builder API ─────────────────────────────────────────────────

  /**
   * Fluent builder entry-point kept for backward compatibility.
   *
   * @param type notification type
   * @return a new {@link NotificationBuilder}
   */
  public NotificationBuilder builder(NotificationType type) {
    return new NotificationBuilder(type, this);
  }

  /** Low-level add used by {@link NotificationBuilder#buildAndPublish()}. */
  public void add(Notification notification) {
    push(notification.title, notification.description, notification.type, notification.duration);
  }

  // ── Event handler ─────────────────────────────────────────────────────────

  /** Fired by the custom event bus from {@code MixinGuiIngameForge}. */
  @EventTarget
  public void onRender2D(Render2DEvent event) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.theWorld == null || mc.thePlayer == null) return;

    // Skip all non-chat overlay screens
    if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))
      return;

    // Hand off rendering to NotificationRenderer
    NotificationRenderer.renderAll(new net.minecraft.client.gui.ScaledResolution(mc));
  }

  // ── Builder (backward-compat) ────────────────────────────────────────────

  public static class NotificationBuilder {

    private final NotificationType type;
    private final NotificationManager manager;
    private String title = "";
    private String description = "";
    private int duration = DEFAULT_DURATION;

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

    /** Builds the {@link Notification} and submits it to the manager. */
    public void buildAndPublish() {
      manager.push(title, description, type, duration);
    }
  }
}
