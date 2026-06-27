package myau.module.modules.combat;

import java.util.List;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.player.CombatTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * @author strangerrrrs
 */
public class Piercing extends Module {
  public final ModeProperty sortMode =
      new ModeProperty(
          "Sort-Mode", 0, new String[] {"Distance", "Health", "Hurt-Time", "Crosshair"});
  public final BooleanProperty ignoreBlocks = new BooleanProperty("Ignore-blocks", true);
  public final BooleanProperty ignoreTeammates = new BooleanProperty("Ignore-teammates", true);
  public final BooleanProperty ignoreNonPlayer = new BooleanProperty("Ignore-non-player", true);
  public final BooleanProperty weaponOnly = new BooleanProperty("Weapon-only", false);
  public final BooleanProperty insideHitboxOnly = new BooleanProperty("Inside-hitbox-only", true);

  private static final Minecraft mc = Minecraft.getMinecraft();

  public Piercing() {
    super("Piercing", false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {sortMode.getModeString()};
  }

  public void modifyMouseOver(float partialTicks) {
    if (!isEnabled()) return;
    if (mc.theWorld == null || mc.thePlayer == null) return;
    if (weaponOnly.getValue()
        && mc.thePlayer.getHeldItem() != null
        && !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)
        && !(mc.thePlayer.getHeldItem().getItem() instanceof ItemAxe)) {
      return;
    }

    Entity viewEntity = mc.getRenderViewEntity();
    if (viewEntity == null) return;

    double blockReach = mc.playerController.getBlockReachDistance();
    Vec3 eyePos = viewEntity.getPositionEyes(partialTicks);
    Vec3 lookVec = viewEntity.getLook(partialTicks);
    double range = mc.playerController.extendedReach() ? 6.0 : 3.0;

    Vec3 blockEndVec =
        eyePos.addVector(
            lookVec.xCoord * blockReach, lookVec.yCoord * blockReach, lookVec.zCoord * blockReach);
    Vec3 endVec =
        eyePos.addVector(lookVec.xCoord * range, lookVec.yCoord * range, lookVec.zCoord * range);

    MovingObjectPosition blockHit = null;
    if (!ignoreBlocks.getValue()) {
      blockHit = mc.theWorld.rayTraceBlocks(eyePos, blockEndVec, false, false, true);
    }

    CombatTargeting.SortMode sMode = CombatTargeting.SortMode.values()[sortMode.getValue()];
    boolean tPlayer = true;
    boolean tMob = !ignoreNonPlayer.getValue();
    boolean tAnimal = !ignoreNonPlayer.getValue();

    List<EntityLivingBase> targets =
        CombatTargeting.getTargets(
            tPlayer, tMob, tAnimal, true, ignoreTeammates.getValue(), true, range, sMode);

    Entity pointedEntity = null;
    Vec3 hitVec = null;

    for (EntityLivingBase entity : targets) {
      float borderSize = entity.getCollisionBorderSize();
      AxisAlignedBB aabb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
      MovingObjectPosition intercept = aabb.calculateIntercept(eyePos, endVec);

      if (aabb.isVecInside(eyePos)) {
        pointedEntity = entity;
        hitVec = eyePos;
        break;
      } else if (intercept != null && !insideHitboxOnly.getValue()) {
        pointedEntity = entity;
        hitVec = intercept.hitVec;
        break;
      }
    }

    if (pointedEntity != null) {
      if (blockHit != null && !ignoreBlocks.getValue() && blockHit.hitVec != null) {
        if (eyePos.distanceTo(blockHit.hitVec) < eyePos.distanceTo(hitVec)) {
          return;
        }
      }
      mc.pointedEntity = pointedEntity;
      mc.objectMouseOver = new MovingObjectPosition(pointedEntity, hitVec);
    }
  }
}
