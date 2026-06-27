package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class KillAuraToolSwitchCheck {
  private final Map<String, CheckBuffer> switchBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> combatSwitchBuffers = new HashMap<>();

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    CheckBuffer switchBuffer = this.switchBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer combatSwitchBuffer =
        this.combatSwitchBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (data.heldItemChangeTicks > 4) {
      switchBuffer.flag(1.0D, 999.0D);
    } else {
      switchBuffer.decay(0.25D);
    }

    boolean inCombat =
        data.startedSwinging() && data.nearestTarget != null && data.nearestTargetDistance < 5.0D;

    if (inCombat && data.heldItemSlot != data.lastHeldItemSlot) {
      ItemStack currentItem = player.getHeldItem();
      boolean holdsSword = currentItem != null && currentItem.getItem() instanceof ItemSword;

      if (holdsSword && data.heldItemChangeTicks > 0) {
        combatSwitchBuffer.flag(1.0D, 999.0D);
      } else {
        combatSwitchBuffer.decay(0.3D);
      }
    } else {
      combatSwitchBuffer.decay(0.3D);
    }

    if (switchBuffer.get() > 6.0D) {
      context.receiveSignal(name, "KillAura (Rapid Switch)");
      switchBuffer.reset();
    }
    if (combatSwitchBuffer.get() > 6.0D) {
      context.receiveSignal(name, "KillAura (Combat Switch)");
      combatSwitchBuffer.reset();
    }
  }

  public void reset() {
    this.switchBuffers.clear();
    this.combatSwitchBuffers.clear();
  }
}
