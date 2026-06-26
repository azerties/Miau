package myau.notification;

public enum NotificationType {
  // ── Primary types (Opal-style Material Icons) ──────────────────────────
  INFO("\ue88f", 0xFF7097CF),
  ENABLED("\ue5ca", 0xFF2ECC71),
  DISABLED("\ue5cd", 0xFFE74C3C),
  WARNING("\ue002", 0xFFFDD235),

  // ── Legacy aliases – kept so existing call-sites compile unchanged ──────
  /**
   * @deprecated Use {@link #ENABLED}
   */
  @Deprecated
  SUCCESS("\ue5ca", 0xFF2ECC71),
  /**
   * @deprecated Use {@link #DISABLED}
   */
  @Deprecated
  ERROR("\ue5cd", 0xFFE74C3C),
  /**
   * @deprecated Use {@link #WARNING}
   */
  @Deprecated
  WARN("\ue002", 0xFFFDD235);

  // ────────────────────────────────────────────────────────────────────────

  private final String icon;
  private final int iconColor;

  NotificationType(String icon, int iconColor) {
    this.icon = icon;
    this.iconColor = iconColor;
  }

  /** Material Icons glyph rendered inside the notification badge. */
  public String getIcon() {
    return icon;
  }

  /** Main accent colour (ARGB). */
  public int getIconColor() {
    return iconColor;
  }

  /**
   * @deprecated Use {@link #getIconColor()}
   */
  @Deprecated
  public int getPrimaryColor() {
    return iconColor;
  }

  /**
   * Convenience alias kept for backward compatibility with code that calls {@code type.getColor()}.
   */
  @Deprecated
  public int getColor() {
    return iconColor;
  }

  /**
   * @deprecated No longer used – icon background is derived from {@link #getIconColor()}.
   */
  @Deprecated
  public int getIconBgColor() {
    return 0;
  }
}
