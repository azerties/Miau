package myau.util.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import myau.module.modules.misc.AntiBot;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

public class CombatTargeting {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public enum SortMode {
    DISTANCE,
    HEALTH,
    HURT_TIME,
    CROSSHAIR
  }

  public static List<EntityLivingBase> getTargets(
      boolean targetPlayers,
      boolean targetMobs,
      boolean targetAnimals,
      boolean targetInvis,
      boolean ignoreTeammates,
      boolean ignoreFriends,
      double range,
      SortMode sortMode) {
    if (mc.thePlayer == null || mc.theWorld == null) return new ArrayList<>();

    List<EntityLivingBase> targets =
        mc.theWorld.loadedEntityList.stream()
            .filter(entity -> entity instanceof EntityLivingBase)
            .map(entity -> (EntityLivingBase) entity)
            .filter(
                entity ->
                    isValidTarget(
                        entity,
                        targetPlayers,
                        targetMobs,
                        targetAnimals,
                        targetInvis,
                        ignoreTeammates,
                        ignoreFriends,
                        range))
            .collect(Collectors.toList());

    switch (sortMode) {
      case DISTANCE:
        targets.sort(Comparator.comparingDouble(RotationUtil::distanceSqFromEyeToClosestOnAABB));
        break;
      case HEALTH:
        targets.sort(Comparator.comparingDouble(EntityLivingBase::getHealth));
        break;
      case HURT_TIME:
        targets.sort(Comparator.comparingInt(entity -> entity.hurtTime));
        break;
      case CROSSHAIR:
        targets.sort(Comparator.comparingDouble(RotationUtil::angleToEntity));
        break;
    }

    return targets;
  }

  public static boolean isValidTarget(
      EntityLivingBase entity,
      boolean targetPlayers,
      boolean targetMobs,
      boolean targetAnimals,
      boolean targetInvis,
      boolean ignoreTeammates,
      boolean ignoreFriends,
      double range) {
    if (entity == null
        || entity == mc.thePlayer
        || entity.isDead
        || entity.getHealth() <= 0
        || entity instanceof EntityArmorStand) {
      return false;
    }

    if (AntiBot.isBot(entity)) {
      return false;
    }

    if (RotationUtil.distanceSqFromEyeToClosestOnAABB(entity) > range * range) {
      return false;
    }

    if (entity.isInvisible() && !targetInvis) {
      return false;
    }

    if (entity instanceof EntityPlayer) {
      if (!targetPlayers) return false;
      if (entity instanceof EntityPlayer) {
        if (ignoreTeammates && TeamUtil.isSameTeam((EntityPlayer) entity)) return false;
        if (ignoreFriends && TeamUtil.isFriend((EntityPlayer) entity)) return false;
      }
      return true;
    }

    if (entity instanceof EntityMob || entity instanceof EntitySlime) {
      return targetMobs;
    }

    if (entity instanceof EntityAnimal
        || entity instanceof EntitySquid
        || entity instanceof EntityVillager) {
      return targetAnimals;
    }

    return false;
  }

  public static EntityLivingBase getTarget(
      boolean targetPlayers,
      boolean targetMobs,
      boolean targetAnimals,
      boolean targetInvis,
      boolean ignoreTeammates,
      boolean ignoreFriends,
      double range,
      SortMode sortMode) {
    List<EntityLivingBase> targets =
        getTargets(
            targetPlayers,
            targetMobs,
            targetAnimals,
            targetInvis,
            ignoreTeammates,
            ignoreFriends,
            range,
            sortMode);
    return targets.isEmpty() ? null : targets.get(0);
  }
}
