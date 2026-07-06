package miau.module.modules.combat.killaura.autoblocks;

import java.util.Random;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class HypixelCustomAutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public HypixelCustomAutoBlock(KillAura parent) {
    super("Custom", parent);
  }

  @Override
  public void processBlock(boolean attack, boolean block) {
    if (parent.hasValidTarget()) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
        if (parent.blockTick + 1 == parent.startBlinkTick.getValue()) {
          parent.blockedFlag = true;
        }
        if (parent.blockTick + 1 != parent.attackTick.getValue()) {
          parent.attackFlag = false;
        }
        if (parent.blockTick + 1 == parent.startBlockTick.getValue()) {
          if (!parent.isPlayerBlocking()) {
            parent.swapFlag = true;
            if (parent.postStartBlock.getValue()) parent.postBlock = true;
          }
        }
        if (parent.blockTick + 1 == parent.stopBlinkTick.getValue()) {
          Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        }
        if (parent.blockTick + 1 == parent.swapTick.getValue()) {
          int randomSlot = new Random().nextInt(9);
          while (randomSlot == mc.thePlayer.inventory.currentItem) {
            randomSlot = new Random().nextInt(9);
          }
          PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
          parent.swapped = true;
        }
        if (parent.blockTick + 1 == parent.switchBackTick.getValue()) {
          if (parent.swapped) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            parent.swapped = false;
          }
        }
        if (parent.blockTick + 1 == parent.stopBlockTick.getValue()) {
          if (parent.isPlayerBlocking()) {
            parent.stopBlock();
          }
        }
        parent.blockTick++;
        if (parent.blockTick >= parent.maxTick.getValue() - 1) {
          parent.blockTick = 0;
        }
      }
      parent.isBlocking = true;
      parent.fakeBlockState = true;
    } else {
      if (parent.swapped) {
        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        parent.swapped = false;
      }
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
  }
}
