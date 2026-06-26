package myau.notification;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import myau.util.animation.Animation;
import myau.util.animation.Easing;
import myau.util.font.Font;
import myau.util.font.Fonts;
import myau.util.font.Weight;
import myau.util.render.ColorUtil;
import myau.util.shader.RoundedUtils;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Opal-style notification renderer for Miau (Forge 1.8.9).
 *
 * <p>Faithful port of Opal's {@code NotificationsElement} render logic:
 *
 * <ul>
 *   <li>Bottom-right stack, newest at bottom, slides in/out with {@link Easing#EASE_OUT_EXPO} (400
 *       ms).
 *   <li>Content-driven width: min 130, icon-offset + max(title width, desc width).
 *   <li>Dark rounded-rect card background ({@code 0x80090909}).
 *   <li>Icon badge with darker icon-color background at 50% opacity.
 *   <li>Material Icons font glyph for the icon (no vector drawing).
 *   <li>Progress bar at bottom with asymmetric rounding (Opal-style).
 *   <li>Removal only after animation reaches screen width (Opal contract).
 * </ul>
 */
public final class NotificationRenderer {

  // ── Singleton ─────────────────────────────────────────────────────────────

  private static final NotificationRenderer INSTANCE = new NotificationRenderer();

  public static NotificationRenderer getInstance() {
    return INSTANCE;
  }

  public void render(ScaledResolution sr) {
    renderAll(sr);
  }

  // ── Animation tracking ────────────────────────────────────────────────────

  private final Map<NotificationManager.Notification, Animation> animations = new HashMap<>();

  // ── Layout constants ──────────────────────────────────────────────────────

  /** Card-to-card gap (GUI units). */
  private static final float PADDING = 3f;

  /** Fixed card height in GUI units. */
  private static final float CARD_HEIGHT = 34f;

  /** Icon badge side length. */
  private static final float ICON_SIZE = 16f;

  /** Horizontal space consumed by the icon plus its gap to the text. */
  private static final float ICON_OFFSET = ICON_SIZE + PADDING;

  /** Corner radius for card background. */
  private static final float RADIUS = 4f;

  /** Minimum card width in GUI units. */
  private static final float MIN_WIDTH = 130f;

  // ── Font providers ────────────────────────────────────────────────────────

  /** Bold title font. */
  private static final Font TITLE_FONT = Fonts.MAIN.get(18, Weight.BOLD);

  /** Medium-weight description font. */
  private static final Font DESC_FONT = Fonts.MAIN.get(15, Weight.MEDIUM);

  /** Material Icons font for the badge glyph. */
  private static final Font ICON_FONT = Fonts.ICONS.get((int) ICON_SIZE);

  // ── Colours (ARGB) ────────────────────────────────────────────────────────

  /** Semi-transparent dark card background (matches Opal's 0x80090909). */
  private static final int COLOR_BG = 0x80090909;

  /** Description text colour. */
  private static final int COLOR_DESC = 0xFFAAAAAA;

  /** Tracks whether the drag anchor has been initialised at least once. */
  private boolean dragInitialized = false;

  // ── Public entry-point ────────────────────────────────────────────────────

  /**
   * Renders all active notifications with Opal-style layout and animation.
   *
   * <p>Must be called from the render thread (inside a {@code Render2DEvent} handler).
   *
   * @param sr current scaled resolution
   */
  public static void renderAll(ScaledResolution sr) {
    INSTANCE.renderInternal(sr);
  }

  // ── Internal render loop ──────────────────────────────────────────────────

  private void renderInternal(ScaledResolution sr) {
    List<NotificationManager.Notification> notifications =
        NotificationManager.getInstance().getNotifications();
    if (notifications.isEmpty()) return;

    float screenW = sr.getScaledWidth();
    float screenH = sr.getScaledHeight();
    NotificationManager mgr = NotificationManager.getInstance();

    // ── Compute default position & drag offset ──────────────────────────────
    // Use the bottom-most (newest) notification to compute default X.
    NotificationManager.Notification lastN = notifications.get(notifications.size() - 1);
    float lastTitleW = TITLE_FONT.getStringWidth(lastN.getTitle());
    float lastDescW = DESC_FONT.getStringWidth(lastN.getDescription());
    float lastWidth = Math.max(MIN_WIDTH, ICON_OFFSET + Math.max(lastTitleW, lastDescW) + 6f);
    float defaultX = screenW - lastWidth - PADDING;
    float defaultY = screenH - (PADDING * 2) - (notifications.size() * (CARD_HEIGHT + PADDING));

    // Initialise drag anchor on first-ever render (persists across empty
    // periods so that the user's drag offset is not lost).
    if (!dragInitialized) {
      mgr.drag.targetPosition.x = defaultX;
      mgr.drag.targetPosition.y = defaultY;
      dragInitialized = true;
    }

    // Convert targetPosition (modified by DragManager during drag) back to
    // a uniform offset from the default position.  All cards in the stack
    // share this same offset.
    double offsetX = mgr.drag.targetPosition.x - defaultX;
    double offsetY = mgr.drag.targetPosition.y - defaultY;

    // ── Iterate oldest-first → newest at bottom (Opal stacking) ────────────
    for (int i = 0; i < notifications.size(); i++) {
      NotificationManager.Notification n = notifications.get(i);
      Animation anim = animations.get(n);
      if (anim == null) {
        anim = new Animation(Easing.EASE_OUT_EXPO, 400);
        anim.setValue(screenW); // start from right edge
        animations.put(n, anim);
      }

      // ── Content-driven width ─────────────────────────────────────────────
      float titleW = TITLE_FONT.getStringWidth(n.getTitle());
      float descW = DESC_FONT.getStringWidth(n.getDescription());
      float width = Math.max(MIN_WIDTH, ICON_OFFSET + Math.max(titleW, descW) + 6f);
      float endX = screenW - width - PADDING + (float) offsetX;

      // Opal-style Y stack: newest at bottom, oldest above
      float y = screenH - (PADDING * 2) - ((i + 1) * (CARD_HEIGHT + PADDING)) + (float) offsetY;

      // ── Opal 100% animation logic ────────────────────────────────────────
      //
      // Every frame while the notification is still alive we force the
      // animation's current value to `screenW` (the right edge).  This acts
      // as Opal's `setStartValue(scaledWidth)` so that the ease-out-expo
      // curve always starts from the right edge when computing the current
      // slide-in position.  Once expired we let the animation continue
      // naturally from wherever it was toward screenW (slide out).
      if (!n.hasExpired()) {
        anim.setValue(screenW);
      }
      anim.run(n.hasExpired() ? screenW + PADDING : endX);
      float x = anim.getValue();

      // ── Opal-style removal ───────────────────────────────────────────────
      // Only remove when the notification has expired AND the exit animation
      // has reached the right edge (value >= screenW).
      if (n.hasExpired() && anim.isFinished() && anim.getValue() >= screenW) {
        notifications.remove(i);
        animations.remove(n);
        i--;
        continue;
      }

      // ── Update drag property bounds (for DragManager hit-testing) ────────
      if (i == notifications.size() - 1) {
        // Only track the bottom-most notification as the drag anchor
        mgr.drag.position.x = x;
        mgr.drag.position.y = y;
        mgr.drag.scale.x = width;
        mgr.drag.scale.y = CARD_HEIGHT;
      }

      // ── Render the card ──────────────────────────────────────────────────
      renderCard(n, x, y, width);
    }
  }

  // ── Per-card render ───────────────────────────────────────────────────────

  private static void renderCard(NotificationManager.Notification n, float x, float y, float w) {
    int iconColor = n.getType().getIconColor();
    float progress = Math.min(1f, (float) n.getTime() / Math.max(1, n.getDuration()));

    // ── 1. Dark rounded-rect background ────────────────────────────────────
    RoundedUtils.drawRound(x, y, w, CARD_HEIGHT, RADIUS, COLOR_BG);

    // ── 2. Progress bar at bottom (Opal-style asymmetric rounding) ────────
    //
    // Opal uses `roundedRectVaryingGradient` where:
    //   - top-left  = 0 (sharp)
    //   - top-right = 0 (sharp)
    //   - bottom-right = 4 when progress > 0.95F, else 0
    //   - bottom-left  = 4 (rounded)
    // We replicate this with `drawRoundedRectRise` + per-corner flags.
    float barH = 4f;
    float barY = y + CARD_HEIGHT - barH;
    float barW = Math.max(0f, (w - 0.5f) * progress);
    int barColor = applyOpacity(iconColor, 0.25f);
    if (barW > 0.5f) {
      RoundedUtils.drawRoundedRectRise(
          x + 0.5f,
          barY,
          barW,
          barH,
          4f,
          barColor,
          false, // leftTop
          false, // rightTop
          progress > 0.95f, // rightBottom (rounded when near full)
          true); // leftBottom (always rounded)
    }

    // ── 3. Icon badge background (darker icon colour at 50 % opacity) ──────
    float badgeSize = ICON_OFFSET;
    float badgeX = x + PADDING - 0.5f;
    float badgeY = y + (CARD_HEIGHT - badgeSize) / 2f;

    // Opal: ColorUtility.applyOpacity(ColorUtility.darker(iconColor, 0.6F), 0.5F)
    // Miau's ColorUtil.darker(Color, factor) scales RGB by factor.
    // Opal's darker(_, 0.6F) uses (1 - 0.6) = 0.4 multiplier, so we pass 0.4F.
    int darkerArgb = ColorUtil.darker(new Color(iconColor, true), 0.4f).getRGB();
    int badgeColor = applyOpacity(darkerArgb, 0.5f);
    RoundedUtils.drawRound(badgeX, badgeY, badgeSize, badgeSize, 2.75f, badgeColor);

    // ── 4. Material Icons glyph (REPLACES old vector drawing entirely) ─────
    String icon = n.getType().getIcon();
    float iconX = badgeX + (badgeSize - ICON_FONT.width(icon)) / 2f;
    float iconY = badgeY + (badgeSize - ICON_FONT.height()) / 2f;
    ICON_FONT.draw(icon, iconX, iconY, iconColor);

    // ── 5. Title (bold, white) ─────────────────────────────────────────────
    float textX = x + (PADDING * 2) + ICON_OFFSET;
    float titleY = y + 5f;
    TITLE_FONT.draw(n.getTitle(), textX, titleY, 0xFFFFFFFF);

    // ── 6. Description (medium, muted) ─────────────────────────────────────
    float descY = titleY + TITLE_FONT.height() + 2f;
    DESC_FONT.draw(n.getDescription(), textX, descY, COLOR_DESC);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** Applies an opacity factor (0-1) to an ARGB int color. */
  private static int applyOpacity(int color, float opacity) {
    int alpha = Math.min(255, Math.max(0, (int) (opacity * 255f)));
    return (alpha << 24) | (color & 0x00FFFFFF);
  }

  private NotificationRenderer() {}
}
