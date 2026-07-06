package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.player.PlayerUtil;

public class FakeAutoBlock extends AutoBlockMode {
  public FakeAutoBlock(KillAura parent) {
    super("Fake", parent);
  }

  @Override
  public void processBlock(boolean attack, boolean block) {
    Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    parent.isBlocking = false;
    parent.fakeBlockState = parent.hasValidTarget();
    if (PlayerUtil.isUsingItem()
        && !parent.isPlayerBlocking()
        && !Miau.playerStateManager.digging
        && !Miau.playerStateManager.placing) {
      parent.swapFlag = true;
    }
  }
}
