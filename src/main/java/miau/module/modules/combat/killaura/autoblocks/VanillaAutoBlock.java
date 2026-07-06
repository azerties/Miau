package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;

public class VanillaAutoBlock extends AutoBlockMode {
  public VanillaAutoBlock(KillAura parent) {
    super("Vanilla", parent);
  }

  @Override
  public void processBlock(boolean attack, boolean block) {
    if (parent.hasValidTarget()) {
      if (!parent.isPlayerBlocking()
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing) {
        parent.swapFlag = true;
      }
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
  }
}
