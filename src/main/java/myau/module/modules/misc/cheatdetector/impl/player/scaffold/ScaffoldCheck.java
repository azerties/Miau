package myau.module.modules.misc.cheatdetector.impl.player.scaffold;

import java.util.*;
import myau.event.impl.PacketEvent;
import myau.module.modules.misc.cheatdetector.Check;
import myau.module.modules.misc.cheatdetector.impl.player.scaffold.subchecks.ScaffoldA;
import myau.module.modules.misc.cheatdetector.impl.player.scaffold.subchecks.ScaffoldB;
import myau.module.modules.misc.cheatdetector.impl.player.scaffold.subchecks.ScaffoldC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;

public class ScaffoldCheck extends Check {
  @Override
  public String getName() {
    return "Scaffold";
  }

  private final List<Check> checks =
      Arrays.asList(new ScaffoldA(), new ScaffoldB(), new ScaffoldC());
  public static final Map<UUID, Integer> blocksPlacedMap = new HashMap<>();
  private final Map<UUID, Boolean> bridgingMap = new HashMap<>();

  @Override
  public void onUpdate(EntityPlayer player) {
    checks.forEach(check -> check.onUpdate(player));

    UUID uuid = player.getUniqueID();
    int blocksPlaced = blocksPlacedMap.getOrDefault(uuid, 0);
    boolean bridgingCheck = bridgingMap.getOrDefault(uuid, false);

    if (bridgingCheck && player.swingProgressInt == 0 && player.isSwingInProgress) {
      blocksPlaced++;
    }

    bridgingCheck =
        player.rotationPitch > 70
            && player.getHeldItem() != null
            && player.getHeldItem().getItem() instanceof ItemBlock;

    if (Math.hypot(player.motionX, player.motionZ) < 0.05
        || player.isSneaking()
        || !player.onGround
        || Math.hypot(player.motionX, player.motionZ) < 0.1) blocksPlaced = 0;

    if (blocksPlaced > 7) {
      blocksPlaced = 0;
    }

    bridgingMap.put(uuid, bridgingCheck);
    blocksPlacedMap.put(uuid, blocksPlaced);
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    if (e.getPacket() instanceof S25PacketBlockBreakAnim) {
      S25PacketBlockBreakAnim s25 = (S25PacketBlockBreakAnim) e.getPacket();
      if (s25.getBreakerId() == player.getEntityId()) {
        if (bridgingMap.getOrDefault(player.getUniqueID(), false)) {
          bridgingMap.put(player.getUniqueID(), false);
        }
      }
    }
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    blocksPlacedMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    checks.forEach(check -> check.cleanup(onlineUUIDs));
  }
}
