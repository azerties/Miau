package myau.module.modules.player;

import java.util.ArrayList;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.SlotUpdateEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.interfaces.IMixinItemRenderer;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class AutoTool extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final FloatProperty activationTime = new FloatProperty("Activation time", 0f, 0f, 1000f);
  public final FloatProperty hoverDelay = new FloatProperty("Hover delay", 0f, 0f, 1000f);

  public final BooleanProperty onlyWhileCrouching =
      new BooleanProperty("Only while crouching", false);
  public final BooleanProperty requireLeftMouse = new BooleanProperty("Require Left mouse", true);

  public final BooleanProperty switchBackWhenDone =
      new BooleanProperty("Switch back when done", true);
  public final BooleanProperty overrideSwapBack = new BooleanProperty("Override swap back", true);
  public final BooleanProperty spoofItem = new BooleanProperty("Spoof item", false);

  public final BooleanProperty ignoredHeldItemsToggle =
      new BooleanProperty("Held item blacklist", false);

  public final BooleanProperty blockWhitelistToggle = new BooleanProperty("Block whitelist", false);
  public final BooleanProperty blockBlacklistToggle = new BooleanProperty("Block blacklist", false);

  private boolean hasSwapped;
  public int previousSlot = -1;
  private int tickCounter;
  private int leftMouseDownSinceTick = -1;
  private int hoverStartTick = -1;

  public AutoTool() {
    super("AutoTool", false);
  }

  @EventTarget(Priority.HIGH)
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!this.isEnabled()) {
      resetState(true);
      return;
    }
    if (!nullCheck()) {
      resetState(true);
      return;
    }

    int currentTick = ++tickCounter;
    boolean leftMouseDown = Mouse.isButtonDown(0);
    updateLeftMouseState(leftMouseDown, currentTick);

    if (!mc.inGameHasFocus
        || mc.currentScreen != null
        || mc.thePlayer.isDead
        || !mc.thePlayer.capabilities.allowEdit) {
      resetState(true);
      return;
    }

    MovingObjectPosition hoverResult = mc.objectMouseOver;
    BlockPos hoverPos =
        hoverResult != null && hoverResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            ? hoverResult.getBlockPos()
            : null;
    updateHoverState(hoverPos, currentTick);

    if (hoverPos == null) {
      resetSlot();
      return;
    }

    if (onlyWhileCrouching.getValue() && !mc.thePlayer.isSneaking()) {
      resetSlot();
      return;
    }

    if (requireLeftMouse.getValue()) {
      if (!leftMouseDown) {
        resetSlot();
        return;
      }
      if (!hasElapsed(leftMouseDownSinceTick, activationTime.getValue(), currentTick)) {
        resetSlot();
        return;
      }
    }

    if (!hasElapsed(hoverStartTick, hoverDelay.getValue(), currentTick)) {
      resetSlot();
      return;
    }

    if (isUseBlocked()) {
      resetSlot();
      return;
    }

    if (isBlockedBlock(hoverPos)) {
      resetSlot();
      return;
    }

    if (blockWhitelistToggle.getValue() && !isWhitelistedBlock(hoverPos)) {
      resetSlot();
      return;
    }

    MovingObjectPosition swapResult = mc.objectMouseOver;
    BlockPos swapPos =
        swapResult != null && swapResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            ? swapResult.getBlockPos()
            : null;
    if (swapPos == null) {
      resetSlot();
      return;
    }

    int slot = getTool(BlockUtil.getBlock(swapPos));
    if (slot == -1) {
      return;
    }

    if (previousSlot == -1 && slot != mc.thePlayer.inventory.currentItem) {
      previousSlot = mc.thePlayer.inventory.currentItem;
    }

    if (!hasSwapped) {
      setSlot(slot);
      return;
    }

    if (slot != mc.thePlayer.inventory.currentItem) {
      setSlot(slot);
    }
  }

  @EventTarget
  public void onSlotUpdate(SlotUpdateEvent e) {
    if (!hasSwapped) return;
    if (overrideSwapBack.getValue()) {
      previousSlot = e.slot;
    }
    e.setCancelled(true);
  }

  private void updateLeftMouseState(boolean leftMouseDown, int currentTick) {
    if (leftMouseDown) {
      if (leftMouseDownSinceTick == -1) {
        leftMouseDownSinceTick = currentTick;
      }
    } else {
      leftMouseDownSinceTick = -1;
    }
  }

  private void updateHoverState(BlockPos hoverPos, int currentTick) {
    if (hoverPos == null) {
      hoverStartTick = -1;
      return;
    }
    if (hoverStartTick == -1) {
      hoverStartTick = currentTick;
    }
  }

  private boolean isUseBlocked() {
    boolean useActive = isBindDown(mc.gameSettings.keyBindUseItem) || mc.thePlayer.isUsingItem();
    if (ignoredHeldItemsToggle.getValue() && mc.thePlayer.getHeldItem() != null) {
      return true;
    }
    return useActive;
  }

  private boolean isBlockedBlock(BlockPos blockPos) {
    if (!blockBlacklistToggle.getValue()) {
      return false;
    }
    return matchesBlockList(blockPos, BLACKLIST_BLOCKS);
  }

  private boolean isWhitelistedBlock(BlockPos blockPos) {
    if (!blockWhitelistToggle.getValue()) {
      return false;
    }
    return matchesBlockList(blockPos, WHITELIST_BLOCKS);
  }

  private boolean matchesBlockList(BlockPos blockPos, List<String> blockList) {
    IBlockState state = BlockUtil.getBlockState(blockPos);
    Block hoveredBlock = state.getBlock();
    if (hoveredBlock == null || Block.blockRegistry.getNameForObject(hoveredBlock) == null) {
      return false;
    }
    String registryId = Block.blockRegistry.getNameForObject(hoveredBlock).toString();
    int meta = hoveredBlock.getMetaFromState(state);
    String storageId = meta != 0 ? registryId + ":" + meta : registryId;
    return blockList.contains(storageId) || blockList.contains(registryId);
  }

  private boolean hasElapsed(int startTick, float requiredMs, int currentTick) {
    int requiredTicks = getRequiredTicks(requiredMs);
    if (requiredTicks <= 0) {
      return true;
    }
    return startTick != -1 && currentTick - startTick >= requiredTicks;
  }

  private int getRequiredTicks(float requiredMs) {
    if (requiredMs <= 0.0f) {
      return 0;
    }
    return (int) Math.ceil(requiredMs / 50.0);
  }

  private void resetState(boolean resetTimers) {
    if (resetTimers) {
      tickCounter = 0;
      leftMouseDownSinceTick = -1;
      hoverStartTick = -1;
    }
    resetSlot();
  }

  private void resetSlot() {
    if (previousSlot != -1 && switchBackWhenDone.getValue()) {
      setSlot(previousSlot);
    }
    previousSlot = -1;
    hasSwapped = false;
  }

  private void setSlot(int currentItem) {
    if (currentItem == -1 || currentItem == mc.thePlayer.inventory.currentItem) {
      return;
    }

    if (spoofItem.getValue()) {
      ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
      ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
    }
    mc.thePlayer.inventory.currentItem = currentItem;
    hasSwapped = true;
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
  }

  public void preInteractSpoof() {
    if (spoofItem.getValue()
        && previousSlot != mc.thePlayer.inventory.currentItem
        && previousSlot != -1) {
      ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
      ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
    }
  }

  public static boolean nullCheck() {
    return mc.thePlayer != null && mc.theWorld != null;
  }

  public static boolean isBindDown(net.minecraft.client.settings.KeyBinding keyBinding) {
    int keyCode = keyBinding.getKeyCode();
    if (keyCode < 0) {
      return Mouse.isButtonDown(keyCode + 100);
    }
    return Keyboard.isKeyDown(keyCode);
  }

  public static int getTool(Block block) {
    double bestScore = 1.0D;
    int bestSlot = -1;
    for (int i = 0; i < net.minecraft.entity.player.InventoryPlayer.getHotbarSize(); ++i) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null) {
        double score = getBlockBreakingScore(stack, block);
        if (score > bestScore) {
          bestScore = score;
          bestSlot = i;
        }
      }
    }
    return bestSlot;
  }

  public static double getBlockBreakingScore(ItemStack stack, Block block) {
    float speed = stack.getStrVsBlock(block);
    if (speed > 1.0F) {
      int efficiency =
          net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
              net.minecraft.enchantment.Enchantment.efficiency.effectId, stack);
      if (efficiency > 0) {
        speed += efficiency * efficiency + 1;
      }
    }
    return speed;
  }

  private static final List<String> BLACKLIST_BLOCKS = new ArrayList<>();
  private static final List<String> WHITELIST_BLOCKS = new ArrayList<>();

  static {
    BLACKLIST_BLOCKS.add("minecraft:bed");
    BLACKLIST_BLOCKS.add("minecraft:crafting_table");
    BLACKLIST_BLOCKS.add("minecraft:anvil");
  }
}
