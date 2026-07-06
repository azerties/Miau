package miau.module.modules.player.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;

public class ScaffoldUtils {
  public static double distanceToEdge(Minecraft mc, EnumFacing enumFacing) {
    switch (enumFacing) {
      case NORTH:
        return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
      case EAST:
        return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
      case SOUTH:
        return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
      case WEST:
      default:
        return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    }
  }

  public static EnumFacing yawToFacing(float yaw) {
    if (yaw < -135.0F || yaw > 135.0F) {
      return EnumFacing.NORTH;
    } else if (yaw < -45.0F) {
      return EnumFacing.EAST;
    } else {
      return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
    }
  }

  public static double getRandomOffset() {
    return 0.2155 - miau.util.math.RandomUtil.nextDouble(1.0E-4, 9.0E-4);
  }
}
