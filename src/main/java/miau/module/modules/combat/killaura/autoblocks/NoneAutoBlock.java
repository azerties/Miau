package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.player.PlayerUtil;

public class NoneAutoBlock extends AutoBlockMode {
  public NoneAutoBlock(KillAura parent) {
    super("None", parent);
  }

  @Override
  public void processBlock(boolean attack, boolean block) {
    if (PlayerUtil.isUsingItem()) {
      parent.isBlocking = true;
      if (!parent.isPlayerBlocking()
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing) {
        parent.swapFlag = true;
      }
    } else {
      parent.isBlocking = false;
      if (parent.isPlayerBlocking()
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing) {
        parent.stopBlock();
      }
    }
    Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    parent.fakeBlockState = false;
  }
}
