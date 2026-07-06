package miau.module.modules.ghost.bridgeassist.mode;

import miau.event.impl.MoveInputEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.ghost.BridgeAssist;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

public class NormalMode {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final BridgeAssist parent;
  private int sneakDelay = 0;

  public NormalMode(BridgeAssist parent) {
    this.parent = parent;
  }

  public void onDisabled() {
    this.sneakDelay = 0;
  }

  private boolean canMoveSafely() {
    double[] offset = MoveUtil.predictMovement();
    return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
  }

  private boolean shouldSneak() {
    if (parent.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
      return false;
    } else if (parent.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
      return false;
    } else if (parent.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
      return false;
    } else if (parent.normalSneakOnly.getValue()
        && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
      return false;
    } else {
      return (!parent.blocksOnly.getValue() || parent.isHoldingBlock()) && mc.thePlayer.onGround;
    }
  }

  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      if (this.sneakDelay > 0) {
        this.sneakDelay--;
      }
      if (this.sneakDelay == 0 && this.canMoveSafely()) {
        this.sneakDelay =
            RandomUtils.nextInt(
                parent.delayMs.getValue().intValue(),
                parent.delayMs.getSecondValue().intValue() + 1);
      }
    }
  }

  public void onMoveInput(MoveInputEvent event) {
    if (parent.normalSneakOnly.getValue()
        && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
        && shouldSneak()) {
      mc.thePlayer.movementInput.sneak = false;
      mc.thePlayer.movementInput.moveForward /= 0.3F;
      mc.thePlayer.movementInput.moveStrafe /= 0.3F;
    }

    if (!mc.thePlayer.movementInput.sneak) {
      if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
        mc.thePlayer.movementInput.sneak = true;
        mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        mc.thePlayer.movementInput.moveForward *= 0.3F;
      }
    }
  }

  public int getSneakDelay() {
    return sneakDelay;
  }
}
