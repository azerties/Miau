package myau.module.modules.combat;

import myau.Myau;
import myau.enums.DelayModules;
import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ItemListProperty;
import myau.util.player.CombatTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjgl.input.Mouse;

/**
 * @author strangerrrrs
 */
public class KnockbackDelay extends Module {
  public final FloatProperty distanceToTarget =
      new FloatProperty("Distance-to-target", 6.0f, 3.0f, 12.0f);
  public final FloatProperty chance = new FloatProperty("Chance", 100.0f, 0.0f, 100.0f);
  public final FloatProperty maximumDelay =
      new FloatProperty("Maximum-delay", 200.0f, 50.0f, 1000.0f);
  public final BooleanProperty inAir = new BooleanProperty("In-air", true);
  public final BooleanProperty lookingAtPlayer = new BooleanProperty("Looking-at-player", false);
  public final BooleanProperty requireLeftMouse = new BooleanProperty("Require-Left-mouse", false);
  public final BooleanProperty onlyWhitelistedItem =
      new BooleanProperty("Restrict-held-item", false);
  public final ItemListProperty whitelistedItems = new ItemListProperty("Whitelisted-items", "");

  private static final Minecraft mc = Minecraft.getMinecraft();
  private long startDelayMs = 0;

  public KnockbackDelay() {
    super("KnockbackDelay", false);
  }

  @Override
  public void onEnabled() {
    startDelayMs = 0;
  }

  @Override
  public void onDisabled() {
    flushInboundLagAndClear();
  }

  @EventTarget(Priority.HIGHEST)
  public void onReceivePacketHigh(PacketEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.RECEIVE) return;

    if (e.getPacket() instanceof S08PacketPlayerPosLook) {
      flushInboundLagAndClear();
      return;
    }

    if (!(e.getPacket() instanceof S12PacketEntityVelocity)) {
      return;
    }

    if (mc.thePlayer == null || mc.theWorld == null) {
      return;
    }

    S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
    if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
      return;
    }

    if (conditionsFailureReason() != null) {
      return;
    }

    if (chance.getValue() < 100.0 && Math.random() * 100.0 >= chance.getValue()) {
      return;
    }

    if (isInboundSessionActive()) {
      return;
    }

    startDelayMs = System.currentTimeMillis();
    Myau.delayManager.setDelayState(true, DelayModules.KNOCKBACK_DELAY);
  }

  @EventTarget(Priority.LOWEST)
  public void onGameTick(TickEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
      flushInboundLagAndClear();
      return;
    }

    if (!isInboundSessionActive()) {
      return;
    }

    if (conditionsFailureReason() != null) {
      flushInboundLagAndClear();
      return;
    }

    if (System.currentTimeMillis() - startDelayMs >= maximumDelay.getValue()) {
      flushInboundLagAndClear();
    }
  }

  private boolean isInboundSessionActive() {
    return Myau.delayManager.getDelayModule() == DelayModules.KNOCKBACK_DELAY;
  }

  private void flushInboundLagAndClear() {
    if (Myau.delayManager.getDelayModule() == DelayModules.KNOCKBACK_DELAY) {
      Myau.delayManager.setDelayState(false, DelayModules.KNOCKBACK_DELAY);
    }
  }

  private String conditionsFailureReason() {
    double maxRange = distanceToTarget.getValue();
    if (CombatTargeting.getTarget(
            true, false, false, true, false, true, maxRange, CombatTargeting.SortMode.DISTANCE)
        == null) {
      return "no target in range";
    }

    if (inAir.getValue() && mc.thePlayer.onGround) {
      return "not in air";
    }

    if (lookingAtPlayer.getValue()
        && CombatTargeting.getTarget(
                true, false, false, true, false, true, maxRange, CombatTargeting.SortMode.CROSSHAIR)
            == null) {
      return "not looking at player";
    }

    if (requireLeftMouse.getValue() && !Mouse.isButtonDown(0)) {
      return "LMB not held";
    }

    if (onlyWhitelistedItem.getValue()) {
      ItemStack held = mc.thePlayer.getHeldItem();
      if (held == null || !whitelistedItems.matches(held)) {
        return "held item not whitelisted";
      }
    }

    return null;
  }
}
