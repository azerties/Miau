package myau.module.modules.misc.cheatdetector;

import java.util.Set;
import java.util.UUID;
import myau.Myau;
import myau.event.impl.PacketEvent;
import myau.module.modules.misc.CheatDetector;
import myau.notification.NotificationType;
import myau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public abstract class Check {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  public TimerUtil flagTimer = new TimerUtil();

  public abstract String getName();

  public void onUpdate(EntityPlayer player) {}

  public void onPacket(PacketEvent event, EntityPlayer player) {}

  public void cleanup(Set<UUID> onlineUUIDs) {}

  public void flag(EntityPlayer player, String verbose) {
    if (flagTimer.hasTimeElapsed(
        (long)
            ((CheatDetector) Myau.moduleManager.getModule(CheatDetector.class))
                .alertCoolDown
                .getValue()
                .floatValue())) {
      Myau.notificationManager.pop(
          "CheatDetector",
          player.getName()
              + net.minecraft.util.EnumChatFormatting.WHITE
              + " has failed "
              + net.minecraft.util.EnumChatFormatting.GRAY
              + getName()
              + net.minecraft.util.EnumChatFormatting.WHITE
              + " "
              + verbose,
          NotificationType.INFO);
      ((CheatDetector) Myau.moduleManager.getModule(CheatDetector.class)).mark(player);
      flagTimer.reset();
    }
  }
}
