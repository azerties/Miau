package myau.module.modules.combat;

import java.util.concurrent.ConcurrentLinkedDeque;
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
  private final ConcurrentLinkedDeque<VelocityEntry> delayedVelocities =
      new ConcurrentLinkedDeque<>();

  private static class VelocityEntry {
    final double motionX, motionY, motionZ;
    final long receiveTimeMs;

    VelocityEntry(double motionX, double motionY, double motionZ, long receiveTimeMs) {
      this.motionX = motionX;
      this.motionY = motionY;
      this.motionZ = motionZ;
      this.receiveTimeMs = receiveTimeMs;
    }
  }

  public KnockbackDelay() {
    super("KnockbackDelay", false);
  }

  @Override
  public void onEnabled() {
    delayedVelocities.clear();
  }

  @Override
  public void onDisabled() {
    flushDelayedVelocities();
  }

  @EventTarget(Priority.HIGHEST)
  public void onReceivePacketHigh(PacketEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.RECEIVE) return;

    if (e.getPacket() instanceof S08PacketPlayerPosLook) {
      flushDelayedVelocities();
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

    e.setCancelled(true);
    delayedVelocities.addLast(
        new VelocityEntry(
            packet.getMotionX() / 8000.0,
            packet.getMotionY() / 8000.0,
            packet.getMotionZ() / 8000.0,
            System.currentTimeMillis()));
  }

  @EventTarget(Priority.LOWEST)
  public void onGameTick(TickEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
      flushDelayedVelocities();
      return;
    }

    if (delayedVelocities.isEmpty()) return;

    if (conditionsFailureReason() != null) {
      flushDelayedVelocities();
      return;
    }

    long nowMs = System.currentTimeMillis();
    long maxDelayMs = maximumDelay.getValue().longValue();

    while (!delayedVelocities.isEmpty()) {
      VelocityEntry entry = delayedVelocities.peekFirst();
      if (entry == null) break;

      long elapsed = nowMs - entry.receiveTimeMs;
      if (elapsed >= maxDelayMs) {
        delayedVelocities.pollFirst();
        applyVelocity(entry);
      } else {
        break;
      }
    }
  }

  private void applyVelocity(VelocityEntry entry) {
    if (mc.thePlayer == null) return;
    mc.thePlayer.motionX = entry.motionX;
    mc.thePlayer.motionY = entry.motionY;
    mc.thePlayer.motionZ = entry.motionZ;
  }

  private void flushDelayedVelocities() {
    if (mc.thePlayer != null) {
      for (VelocityEntry entry : delayedVelocities) {
        applyVelocity(entry);
      }
    }
    delayedVelocities.clear();
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
