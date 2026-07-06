package miau.module.modules.combat.killaura.autoblocks;

import java.util.Random;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class HypixelTestAutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public HypixelTestAutoBlock(KillAura parent) {
    super("Test", parent);
  }

  @Override
  public void processBlock(boolean attack, boolean block) {
    if (parent.hasValidTarget()) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
        switch (parent.blockTick) {
          case 0:
            parent.blockedFlag = true;
            if (!parent.isPlayerBlocking()) {
              parent.swapFlag = true;
            }
            parent.blockTick = 1;
            break;
          case 1:
            if (parent.isPlayerBlocking()) {
              int randomSlot = new Random().nextInt(9);
              while (randomSlot == mc.thePlayer.inventory.currentItem) {
                randomSlot = new Random().nextInt(9);
              }
              PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
              PacketUtil.sendPacket(
                  new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            }
            parent.attackFlag = false;
            parent.blockTick = 2;
            break;
          case 2:
            parent.attackFlag = false;
            parent.stopBlock();
            if (parent.attackDelayMS <= 50L) {
              parent.blockTick = 0;
            }
            break;
          default:
            parent.blockTick = 0;
        }
      }
      parent.isBlocking = true;
      parent.fakeBlockState = true;
    } else {
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      int randomSlot = new Random().nextInt(9);
      while (randomSlot == mc.thePlayer.inventory.currentItem) {
        randomSlot = new Random().nextInt(9);
      }
      PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
      PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
  }
}
