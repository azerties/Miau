package myau.notification;

/**
 * Convenience type alias.
 *
 * <p>The notification value object has been moved into {@link NotificationManager.Notification} as
 * a static inner class. This subclass re-exports it under the top-level name so that any existing
 * {@code import myau.notification.Notification} continues to compile without modification.
 */
public class Notification extends NotificationManager.Notification {

  public Notification(NotificationType type, String title, String description, int duration) {
    super(type, title, description, duration);
  }
}
