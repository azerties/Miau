package myau.notification;

public enum NotificationType {
  SUCCESS("\ue86c", 0xFF40FD3E),
  ERROR("\ue5c9", 0xFFFD3F3F),
  WARN("\ue002", 0xFFFEFE3E),
  INFO("\ue88e", 0xFF3D40FF);

  private final String icon;
  private final int color;

  NotificationType(String icon, int color) {
    this.icon = icon;
    this.color = color;
  }

  public String getIcon() {
    return icon;
  }

  public int getColor() {
    return color;
  }
}
