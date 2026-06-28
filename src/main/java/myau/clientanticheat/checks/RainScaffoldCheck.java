package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.PlayerEligibility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;

/**
 * Rain Anticheat port — LegitScaffold detection. Detects robotic crouch-bridge rhythm (quick crouch
 * + swing timing).
 */
public class RainScaffoldCheck {
  private final Map<UUID, Long> lastCrouchStart = new HashMap<>();
  private final Map<UUID, Long> lastCrouchEnd = new HashMap<>();
  private final Map<UUID, Boolean> wasSneaking = new HashMap<>();
  private final Map<UUID, Long> lastSwingTick = new HashMap<>();
  private final Map<UUID, List<Integer>> crouchDurations = new HashMap<>();
  private final Map<UUID, Long> lastFlagTick = new HashMap<>();
  private final Map<UUID, Integer> violationLevels = new HashMap<>();
  private final long cooldownTicks = 60L;

  public void check(EntityPlayer player) {
    if (player == null) return;
    UUID uuid = player.getUniqueID();
    if (uuid == null) return;
    if (!PlayerEligibility.shouldCheckPlayer(player)) {
      forgetPlayer(uuid);
      return;
    }

    long tick = player.ticksExisted;
    boolean currSneak = player.isSneaking();
    boolean prevSneak = wasSneaking.getOrDefault(uuid, false);

    if (currSneak && !prevSneak) {
      lastCrouchStart.put(uuid, tick);
    } else if (!currSneak && prevSneak) {
      lastCrouchEnd.put(uuid, tick);
      long start = lastCrouchStart.getOrDefault(uuid, tick - 1L);
      int duration = (int) (tick - start);
      List<Integer> durations = crouchDurations.computeIfAbsent(uuid, k -> new ArrayList<>());
      durations.add(0, duration);
      if (durations.size() > 5) durations.remove(5);
    }

    wasSneaking.put(uuid, currSneak);

    if (player.isSwingInProgress && player.swingProgressInt != player.prevSwingProgress) {
      lastSwingTick.put(uuid, tick);
    }

    if (player.rotationPitch >= 60.0F
        && player.getHeldItem() != null
        && player.getHeldItem().getItem() instanceof ItemBlock
        && player.onGround) {
      long end = lastCrouchEnd.getOrDefault(uuid, 0L);
      long swing = lastSwingTick.getOrDefault(uuid, Long.MIN_VALUE);
      int crouchDuration = (int) (end - lastCrouchStart.getOrDefault(uuid, end - 1L));
      boolean quickCrouch = crouchDuration >= 1 && crouchDuration <= 2;
      boolean swingTiming = swing >= end && swing <= end + 1L;
      List<Integer> durations = crouchDurations.getOrDefault(uuid, Collections.emptyList());
      boolean consistent =
          durations.size() >= 3
              && durations.get(0) <= 2
              && durations.get(1) <= 2
              && durations.get(2) <= 2;

      if (quickCrouch && swingTiming && consistent) {
        long lastFlag = lastFlagTick.getOrDefault(uuid, 0L);
        if (tick - lastFlag >= cooldownTicks) {
          lastFlagTick.put(uuid, tick);
          int vl = violationLevels.getOrDefault(uuid, 0) + 1;
          violationLevels.put(uuid, vl);
          AlertManager.flag(player.getName(), "Scaffold", "crouch-bridge", vl);
        }
      }
    }
  }

  public void forgetPlayer(UUID uuid) {
    if (uuid == null) return;
    lastCrouchStart.remove(uuid);
    lastCrouchEnd.remove(uuid);
    wasSneaking.remove(uuid);
    lastSwingTick.remove(uuid);
    crouchDurations.remove(uuid);
    lastFlagTick.remove(uuid);
    violationLevels.remove(uuid);
  }

  public void reset() {
    lastCrouchStart.clear();
    lastCrouchEnd.clear();
    wasSneaking.clear();
    lastSwingTick.clear();
    crouchDurations.clear();
    lastFlagTick.clear();
    violationLevels.clear();
  }
}
