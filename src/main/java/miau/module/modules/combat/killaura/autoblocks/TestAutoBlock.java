package miau.module.modules.combat.killaura.autoblocks;

import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.modules.combat.KillAura;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

/**
 * Port of Opal's BlockModule "auto block" logic.
 *
 * <p>Core mechanic: when KillAura has a valid target + sword held, press right-click (C08) to
 * block, unless looking at an interactable block or currently breaking a block.
 */
public class TestAutoBlock extends AutoBlockMode {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean hasBlocked = false;

  public TestAutoBlock(KillAura parent) {
    super("Test", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;

    if (parent.hasValidTarget()) {
      // Check looking at interactable block or breaking (Opal's check)
      if (mc.objectMouseOver != null
          && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
          && mc.objectMouseOver.getBlockPos() != null) {
        Block blockOver = mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
        IAccessorPlayerControllerMP accessor = (IAccessorPlayerControllerMP) mc.playerController;
        if (isInteractableBlock(blockOver) || accessor.getIsHittingBlock()) {
          // Skip blocking like Opal BlockModule
          if (hasBlocked) {
            release();
          }
          hasBlocked = false;
          parent.isBlocking = false;
          parent.fakeBlockState = false;
          parent.blockTick = 0;
          return false;
        }
      }

      if (!hasBlocked) {
        if (mc.thePlayer.getHeldItem() != null
            && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword) {
          miau.util.network.PacketUtil.sendPacket(
              new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement(
                  mc.thePlayer.getHeldItem()));
          mc.thePlayer.setItemInUse(
              mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
          parent.blockingState = true;
        }
        hasBlocked = true;
      }

      parent.blockTick = 1;
      parent.isBlocking = true;
      parent.fakeBlockState = false;
      swap = true;

    } else {
      if (parent.isPlayerBlocking()) {
        release();
      }
      hasBlocked = false;
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      parent.blockTick = 0;
    }

    return swap;
  }

  private void release() {
    miau.util.network.PacketUtil.sendPacket(
        new net.minecraft.network.play.client.C07PacketPlayerDigging(
            net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
            net.minecraft.util.BlockPos.ORIGIN,
            net.minecraft.util.EnumFacing.DOWN));
    mc.thePlayer.stopUsingItem();
    parent.blockingState = false;
  }

  private boolean isInteractableBlock(Block block) {
    return block instanceof net.minecraft.block.BlockDoor
        || block instanceof net.minecraft.block.BlockChest
        || block instanceof net.minecraft.block.BlockFurnace
        || block instanceof net.minecraft.block.BlockWorkbench
        || block instanceof net.minecraft.block.BlockAnvil
        || block instanceof net.minecraft.block.BlockEnchantmentTable
        || block instanceof net.minecraft.block.BlockBrewingStand
        || block instanceof net.minecraft.block.BlockBeacon
        || block instanceof net.minecraft.block.BlockLever
        || block instanceof net.minecraft.block.BlockButtonWood
        || block instanceof net.minecraft.block.BlockButtonStone
        || block instanceof net.minecraft.block.BlockTrapDoor
        || block instanceof net.minecraft.block.BlockFenceGate
        || block instanceof net.minecraft.block.BlockRedstoneRepeater
        || block instanceof net.minecraft.block.BlockRedstoneComparator
        || block instanceof net.minecraft.block.BlockHopper
        || block instanceof net.minecraft.block.BlockDropper
        || block instanceof net.minecraft.block.BlockDispenser
        || block instanceof net.minecraft.block.BlockEnderChest
        || block == net.minecraft.init.Blocks.anvil
        || block == net.minecraft.init.Blocks.enchanting_table
        || block == net.minecraft.init.Blocks.brewing_stand;
  }
}
