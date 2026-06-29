package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;

public interface ClientAntiCheatContext {
  void receiveSignal(String playerName, String cheatName);

  default void receiveSignal(String playerName, String cheatName, String detail, int vl) {
    receiveSignal(playerName, cheatName);
  }

  PlayerCheckData getPlayerData(EntityPlayer player);
}
