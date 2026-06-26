package myau.module.modules.combat;

import com.google.common.base.CaseFormat;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.*;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.impl.*;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.module.modules.misc.AntiBot;
import myau.module.modules.movement.NoSlow;
import myau.module.modules.player.AutoBlockIn;
import myau.module.modules.player.AutoHeal;
import myau.module.modules.player.BedNuker;
import myau.module.modules.player.Scaffold;
import myau.module.modules.render.HUD;
import myau.property.properties.*;
import myau.util.client.*;
import myau.util.math.*;
import myau.util.network.*;
import myau.util.player.*;
import myau.util.render.*;
import myau.util.time.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

public class KillAura extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final DecimalFormat df =
      new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
  private final TimerUtil timer = new TimerUtil();
  private AttackData target = null;
  private int switchTick = 0;
  private boolean hitRegistered = false;
  private boolean blockingState = false;
  private boolean isBlocking = false;
  private boolean fakeBlockState = false;
  private boolean blinkReset = false;
  private boolean rightHoldActive = false;
  private long attackDelayMS = 0L;
  private int blockTick = 0;
  private int lastTickProcessed;
  private int ticksSinceVelocity = 0;
  private double expandRange = 0.0;
  public final ModeProperty mode;
  public final IntProperty switchDelay;
  public final ModeProperty autoBlock;
  public final BooleanProperty autoBlockRequirePress;
  public final ModeProperty smartUnblockMode;
  public final BooleanProperty smartReleaseAutoBlock;
  public final BooleanProperty smartForceBlockRender;
  public final BooleanProperty smartIgnoreTickRule;
  public final IntProperty smartBlockRate;
  public final BooleanProperty smartUpdatedNCPAutoBlock;
  public final BooleanProperty smartSwitchStartBlock;
  public final BooleanProperty smartInteractAutoBlock;
  public final BooleanProperty smartBlinkAutoBlock;
  public final IntProperty smartBlinkBlockTicks;
  public final BooleanProperty smartAutoBlockCheck;
  public final BooleanProperty smartForceBlockWhenStill;
  public final BooleanProperty smartCheckEnemyWeapon;
  public final FloatProperty smartBlockRange;
  public final IntProperty smartMaxOwnHurtTime;
  public final FloatProperty smartMaxDirectionDiff;
  public final IntProperty smartMaxSwingProgress;
  public final BooleanProperty preventServersideBlocking;
  public final ModeProperty sort;
  public final ModeProperty clickMode;
  public final ModeProperty hitSelectPreference;
  public final IntProperty hitSelectDelay;
  public final IntProperty hitSelectChance;
  public final FloatProperty attackRange;
  public final FloatProperty swingRange;
  public final FloatProperty cps;
  public final ModeProperty rotations;
  public final PercentProperty smoothing;
  public final IntProperty angleStep;
  public final ModeProperty moveFix;
  public final BooleanProperty rayCast;
  public final BooleanProperty throughWalls;
  public final BooleanProperty whileScaffold;
  public final BooleanProperty badPacketsCheck;
  public final IntProperty fov;
  public final BooleanProperty requirePress;
  public final BooleanProperty allowMining;
  public final BooleanProperty weaponsOnly;
  public final BooleanProperty allowTools;
  public final BooleanProperty inventoryCheck;
  public final ModeProperty showTarget;
  public final ModeProperty debugLog;
  public final BooleanProperty tickLookahead;
  public final BooleanProperty smartKill;
  public final BooleanProperty tacticalKD;
  public final FloatProperty kdOffset;
  private int ticks = 255;

  public final BooleanProperty targetPlayers = new BooleanProperty("target-players", true);
  public final BooleanProperty targetInvisibles =
      new BooleanProperty("target-invisibles", false, this.targetPlayers::getValue);
  public final BooleanProperty targetBosses = new BooleanProperty("target-bosses", false);
  public final BooleanProperty targetMobs = new BooleanProperty("target-mobs", false);
  public final BooleanProperty targetAnimals = new BooleanProperty("target-animals", false);
  public final BooleanProperty targetGolems = new BooleanProperty("target-golems", false);
  public final BooleanProperty targetSilverfish = new BooleanProperty("target-silverfish", false);
  public final BooleanProperty targetTeams = new BooleanProperty("target-teams", true);

  private static final int KD_DIRECTIONS = 24;
  private static final int KD_VOID_RINGS = 7;
  private static final int KD_VOID_DEPTH = 8;
  private static final double KD_RING_STEP = 0.5;
  private static final double KD_WALL_BEHIND_DIST = 1.2;
  private static final double KD_WALL_SIDE_STEP = 0.6;

  private static final double[] KD_COS = new double[KD_DIRECTIONS];
  private static final double[] KD_SIN = new double[KD_DIRECTIONS];

  static {
    for (int i = 0; i < KD_DIRECTIONS; i++) {
      double theta = Math.PI * 2.0 * i / KD_DIRECTIONS;
      KD_COS[i] = Math.cos(theta);
      KD_SIN[i] = Math.sin(theta);
    }
  }

  private int kdHitCounter = 0;

  // HitSelect state
  private boolean hsSprintState = false;
  private boolean hsSet = false;
  private boolean hsKeepSprintWasEnabled = false;
  private int hsSavedSlowdown = 0;
  private long hsAttackTime = -1L;
  private boolean hsCurrentShouldAttack = false;

  private long getAttackDelay() {
    if (this.clickMode.getValue() == 6) {
      double speed = 4;
      if (mc.thePlayer.getHeldItem() != null) {
        net.minecraft.item.Item item = mc.thePlayer.getHeldItem().getItem();
        if (item instanceof net.minecraft.item.ItemSword) {
          speed = 1.6;
        } else if (item instanceof net.minecraft.item.ItemSpade) {
          speed = 1.0;
        } else if (item instanceof net.minecraft.item.ItemPickaxe) {
          speed = 1.2;
        } else if (item instanceof net.minecraft.item.ItemAxe) {
          String mat = ((net.minecraft.item.ItemAxe) item).getToolMaterialName();
          if (mat.equals("WOOD") || mat.equals("STONE")) {
            speed = 0.8;
          } else if (mat.equals("IRON")) {
            speed = 0.9;
          } else {
            speed = 1.0;
          }
        } else if (item instanceof net.minecraft.item.ItemHoe) {
          String mat = ((net.minecraft.item.ItemHoe) item).getMaterialName();
          if (mat.equals("WOOD") || mat.equals("GOLD")) {
            speed = 1.0;
          } else if (mat.equals("STONE")) {
            speed = 2.0;
          } else if (mat.equals("IRON")) {
            speed = 3.0;
          } else {
            speed = 1.0;
          }
        }
      }
      return (long) ((1 / speed * 20 - 1) * 50);
    }
    return 1000L
        / RandomUtil.nextLong(this.cps.getValue().intValue(), this.cps.getSecondValue().intValue());
  }

  private boolean performAttack(float yaw, float pitch) {
    if (this.badPacketsCheck.getValue()
        && myau.component.BadPacketsComponent.bad(false, false, false, true, true)) {
      return false;
    }
    if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
      if (this.isPlayerBlocking()
          && this.autoBlock.getValue() != 1
          && this.autoBlock.getValue() != 10) {
        return false;
      } else if (this.shouldDelayHit()) {
        return false;
      } else if (this.attackDelayMS > 0L) {
        return false;
      } else {
        this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
        mc.thePlayer.swingItem();
        this.hsAttackTime = System.currentTimeMillis();

        net.minecraft.util.MovingObjectPosition rayCastPos = null;
        boolean rayCastHit = false;

        boolean useRaycast = this.rayCast.getValue() || this.tickLookahead.getValue();

        if (useRaycast) {
          if (this.throughWalls.getValue()) {
            // Rise getEntityIntercept: ignores blocks, uses proper collision border size
            rayCastPos =
                myau.util.player.RayCastUtil.getEntityIntercept(
                    this.target.getEntity(), yaw, pitch, this.attackRange.getValue());
          } else {
            // Rise full raycast: checks blocks then entities
            rayCastPos =
                myau.util.player.RayCastUtil.rayCast(yaw, pitch, this.attackRange.getValue());
          }

          if (rayCastPos != null && rayCastPos.entityHit == this.target.getEntity()) {
            rayCastHit = true;
          } else if (rayCastPos != null
              && rayCastPos.typeOfHit
                  == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY) {
            if (!(rayCastPos.entityHit instanceof net.minecraft.entity.projectile.EntityFireball
                || rayCastPos.entityHit instanceof net.minecraft.entity.item.EntityItemFrame)) {
              this.target =
                  new AttackData((net.minecraft.entity.EntityLivingBase) rayCastPos.entityHit);
              rayCastHit = true;
            }
          }
        } else if (mc.thePlayer.getDistanceToEntity(this.target.getEntity())
            <= this.attackRange.getValue()) {
          rayCastHit = true;
        }

        if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
            && !rayCastHit) {
          return false;
        } else {
          AttackEvent event = new AttackEvent(this.target.getEntity());
          EventManager.call(event);
          ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
          PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
          if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(this.target.getEntity());
          }
          LastAttackData lastAttack = this.targetMap.get(this.target.getEntity().getEntityId());
          if (lastAttack == null) {
            this.targetMap.put(
                this.target.getEntity().getEntityId(),
                new LastAttackData(this.getDamage(this.target.getEntity())));
          } else {
            lastAttack.reset(true, this.getDamage(this.target.getEntity()));
          }
          this.hitRegistered = true;
          return true;
        }
      }
    } else {
      return false;
    }
  }

  private boolean shouldDelayHit() {
    if (this.target == null || this.target.getEntity() == null) return false;
    EntityLivingBase living = this.target.getEntity();

    if (this.smartKill.getValue() && living.getHealth() <= this.getDamage(living)) {
      return false;
    }

    switch (this.clickMode.getValue()) {
      case 1: // ACTIVE
        return living.hurtTime > (this.getPing() / 50 - 1) && this.ticksSinceVelocity > 11;
      case 2: // SECOND
        return !this.hsPrioritizeSecondHit(mc.thePlayer, living);
      case 3: // CRITICALS
        return !this.hsPrioritizeCriticalHits(mc.thePlayer);
      case 4: // W_TAP
        return !this.hsPrioritizeWTapHits(mc.thePlayer, this.hsSprintState);
      case 5: // PAUSE
        return !this.hsPrioritizePauseHits();
    }
    return false;
  }

  private boolean hsPrioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
    if (target.hurtTime != 0) return true;
    if (player.hurtTime <= player.maxHurtTime - 1) return true;
    double dist = player.getDistanceToEntity(target);
    if (dist < 2.5) return true;
    if (!this.hsIsMovingTowards(target, player, 60.0)) return true;
    if (!this.hsIsMovingTowards(player, target, 60.0)) return true;
    this.hsFixMotion();
    return false;
  }

  private boolean hsPrioritizeCriticalHits(EntityLivingBase player) {
    if (player.onGround) return true;
    if (player.hurtTime != 0) return true;
    if (player.fallDistance > 0.0f) return true;
    this.hsFixMotion();
    return false;
  }

  private boolean hsPrioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
    if (player.isCollidedHorizontally) return true;
    if (!mc.gameSettings.keyBindForward.isKeyDown()) return true;
    if (sprinting) return true;
    this.hsFixMotion();
    return false;
  }

  private boolean hsPrioritizePauseHits() {
    if (this.hsCurrentShouldAttack) return true;
    this.hsFixMotion();
    return false;
  }

  private boolean hsIsMovingTowards(
      EntityLivingBase source, EntityLivingBase target, double maxAngle) {
    net.minecraft.util.Vec3 currentPos = source.getPositionVector();
    net.minecraft.util.Vec3 lastPos =
        new net.minecraft.util.Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
    net.minecraft.util.Vec3 targetPos = target.getPositionVector();
    double mx = currentPos.xCoord - lastPos.xCoord;
    double mz = currentPos.zCoord - lastPos.zCoord;
    double movementLength = Math.sqrt(mx * mx + mz * mz);
    if (movementLength == 0.0) return false;
    mx /= movementLength;
    mz /= movementLength;
    double tx = targetPos.xCoord - currentPos.xCoord;
    double tz = targetPos.zCoord - currentPos.zCoord;
    double targetLength = Math.sqrt(tx * tx + tz * tz);
    if (targetLength == 0.0) return false;
    tx /= targetLength;
    tz /= targetLength;
    double dotProduct = mx * tx + mz * tz;
    return dotProduct >= Math.cos(Math.toRadians(maxAngle));
  }

  private boolean hsIsMoving(EntityLivingBase entity) {
    return Math.abs(entity.motionX) > 0.005D || Math.abs(entity.motionZ) > 0.005D;
  }

  private void hsFixMotion() {
    if (this.hsSet) return;
    myau.module.modules.movement.KeepSprint keepSprint =
        (myau.module.modules.movement.KeepSprint)
            Myau.moduleManager.modules.get(myau.module.modules.movement.KeepSprint.class);
    if (keepSprint == null) return;
    try {
      this.hsSavedSlowdown = keepSprint.slowdown.getValue();
      this.hsKeepSprintWasEnabled = keepSprint.isEnabled();
      if (!this.hsKeepSprintWasEnabled) {
        keepSprint.setEnabled(true);
      }
      keepSprint.slowdown.setValue(0);
      this.hsSet = true;
    } catch (Exception e) {
    }
  }

  private void hsResetMotion() {
    if (!this.hsSet) return;
    myau.module.modules.movement.KeepSprint keepSprint =
        (myau.module.modules.movement.KeepSprint)
            Myau.moduleManager.modules.get(myau.module.modules.movement.KeepSprint.class);
    if (keepSprint != null) {
      try {
        keepSprint.slowdown.setValue(this.hsSavedSlowdown);
        if (!this.hsKeepSprintWasEnabled && keepSprint.isEnabled()) {
          keepSprint.setEnabled(false);
        }
      } catch (Exception e) {
      }
    }
    this.hsSet = false;
    this.hsKeepSprintWasEnabled = false;
    this.hsSavedSlowdown = 0;
  }

  private void sendUseItem() {
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    this.startBlock(mc.thePlayer.getHeldItem());
  }

  private void startBlock(ItemStack itemStack) {
    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
    mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
    this.blockingState = true;
  }

  private void stopBlock() {
    PacketUtil.sendPacket(
        new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    mc.thePlayer.stopUsingItem();
    this.blockingState = false;
  }

  private void setRightHold(boolean pressed) {
    int useKey = mc.gameSettings.keyBindUseItem.getKeyCode();
    if (pressed) {
      KeyBindUtil.setKeyBindState(useKey, true);
      this.rightHoldActive = true;
    } else if (this.rightHoldActive) {
      KeyBindUtil.updateKeyState(useKey);
      this.rightHoldActive = false;
    }
  }

  private boolean shouldRightHoldBlock() {
    return this.target != null
        && this.canAutoBlock()
        && RotationUtil.distanceToBox(this.target.getBox()) < (double) this.attackRange.getValue();
  }

  private void updateRightHoldBlock() {
    if (this.autoBlock.getValue() == 10) {
      this.setRightHold(this.shouldRightHoldBlock());
    } else {
      this.setRightHold(false);
    }
  }

  private void interactAttack(float yaw, float pitch) {
    this.interactAttack(yaw, pitch, true);
  }

  private void interactAttack(float yaw, float pitch, boolean sendInteractAt) {
    if (this.target != null) {
      net.minecraft.util.Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
      net.minecraft.util.Vec3 lookVec =
          myau.util.player.RayCastUtil.getVectorForRotation(pitch, yaw);
      net.minecraft.util.Vec3 targetPos =
          eyePos.addVector(lookVec.xCoord * 8.0, lookVec.yCoord * 8.0, lookVec.zCoord * 8.0);
      net.minecraft.util.MovingObjectPosition mop =
          this.target.getBox().calculateIntercept(eyePos, targetPos);
      if (mop != null) {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        if (sendInteractAt) {
          PacketUtil.sendPacket(
              new C02PacketUseEntity(
                  this.target.getEntity(),
                  new Vec3(
                      mop.hitVec.xCoord - this.target.getX(),
                      mop.hitVec.yCoord - this.target.getY(),
                      mop.hitVec.zCoord - this.target.getZ())));
        }
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        mc.thePlayer.setItemInUse(
            mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        this.blockingState = true;
      }
    }
  }

  private void stopCustomBlock(boolean forceStop) {
    if (forceStop || this.smartUnblockMode.getValue() == 0) {
      this.stopBlock();
    } else if (this.smartUnblockMode.getValue() == 1) {
      int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
      PacketUtil.sendPacket(new C09PacketHeldItemChange((item + 1) % 9));
      PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
      mc.thePlayer.stopUsingItem();
      this.blockingState = false;
    } else {
      int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
      int slot = this.findEmptySlot(item);
      if (slot != item) {
        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
      } else {
        this.stopBlock();
      }
    }
  }

  private boolean shouldCustomSmartBlock() {
    if (!this.smartAutoBlockCheck.getValue() || this.target == null) {
      return true;
    }
    EntityLivingBase entity = this.target.getEntity();
    if (RotationUtil.distanceToEntity(entity) > (double) this.smartBlockRange.getValue()) {
      return false;
    }
    if (mc.thePlayer.hurtTime > this.smartMaxOwnHurtTime.getValue()) {
      return false;
    }
    if (this.smartCheckEnemyWeapon.getValue()) {
      ItemStack heldItem = entity.getHeldItem();
      if (heldItem == null || !(heldItem.getItem() instanceof ItemSword)) {
        return false;
      }
    }
    if (entity.swingProgressInt > this.smartMaxSwingProgress.getValue()) {
      return false;
    }
    float yawToPlayer =
        (float)
                (Math.atan2(mc.thePlayer.posZ - entity.posZ, mc.thePlayer.posX - entity.posX)
                    * 180.0D
                    / Math.PI)
            - 90.0F;
    return this.smartForceBlockWhenStill.getValue()
            && mc.thePlayer.motionX == 0.0D
            && mc.thePlayer.motionZ == 0.0D
        || Math.abs(MathHelper.wrapAngleTo180_float(entity.rotationYaw - yawToPlayer))
            <= this.smartMaxDirectionDiff.getValue();
  }

  private boolean canAttack() {
    if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
      return false;
    } else if (!(Boolean) this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
      if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
        return false;
      } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
        return false;
      } else {
        AutoHeal autoHeal = (AutoHeal) Myau.moduleManager.modules.get(AutoHeal.class);
        if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
          return false;
        } else {
          BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
          AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
          if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
          } else if (!this.whileScaffold.getValue()
              && Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
            return false;
          } else if (autoBlockIn.isEnabled()) {
            return false;
          } else if (this.requirePress.getValue()) {
            return PlayerUtil.isAttacking();
          } else {
            return !this.allowMining.getValue()
                || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK)
                || !PlayerUtil.isAttacking();
          }
        }
      }
    } else {
      return false;
    }
  }

  private boolean canAutoBlock() {
    if (!ItemUtil.isHoldingSword()) {
      return false;
    } else {
      return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
    }
  }

  private boolean hasValidTarget() {
    return mc.theWorld.loadedEntityList.stream()
        .anyMatch(
            entity ->
                entity instanceof EntityLivingBase
                    && this.isValidTarget((EntityLivingBase) entity)
                    && this.isInRange((EntityLivingBase) entity));
  }

  private boolean isValidTarget(EntityLivingBase entityLivingBase) {
    return this.isValid(entityLivingBase)
        && RotationUtil.angleToEntity(entityLivingBase) <= this.fov.getValue().floatValue()
        && (this.throughWalls.getValue() || RotationUtil.rayTrace(entityLivingBase) == null);
  }

  private boolean isInRange(EntityLivingBase entityLivingBase) {
    double maxRange = Math.max(this.swingRange.getValue(), this.attackRange.getValue());
    maxRange += this.expandRange;
    return RotationUtil.distanceToEntity(entityLivingBase) <= maxRange;
  }

  private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
  }

  private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
    return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
  }

  private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
  }

  private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
    return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
  }

  private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
    return entityLivingBase instanceof EntityPlayer
        && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
  }

  // ─── Target Validation (inlined from Targets.java) ─────────────

  private boolean isValid(EntityLivingBase entityLivingBase) {
    if (entityLivingBase == null || mc.theWorld == null || mc.thePlayer == null) {
      return false;
    }
    if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
      return false;
    }
    if (entityLivingBase == mc.thePlayer || entityLivingBase == mc.thePlayer.ridingEntity) {
      return false;
    }
    if (entityLivingBase == mc.getRenderViewEntity()
        || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
      return false;
    }
    if (entityLivingBase.deathTime > 0) {
      return false;
    }
    if (entityLivingBase instanceof EntityOtherPlayerMP) {
      return this.isValidPlayer((EntityPlayer) entityLivingBase);
    }
    if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
      return this.targetBosses.getValue();
    }
    if (entityLivingBase instanceof EntityMob || entityLivingBase instanceof EntitySlime) {
      if (entityLivingBase instanceof EntitySilverfish) {
        return this.targetSilverfish.getValue() && this.allowTeamColor(entityLivingBase);
      }
      return this.targetMobs.getValue();
    }
    if (entityLivingBase instanceof EntityAnimal
        || entityLivingBase instanceof EntityBat
        || entityLivingBase instanceof EntitySquid
        || entityLivingBase instanceof EntityVillager) {
      return this.targetAnimals.getValue();
    }
    if (entityLivingBase instanceof EntityIronGolem) {
      return this.targetGolems.getValue() && this.allowTeamColor(entityLivingBase);
    }
    return false;
  }

  private boolean isValidPlayer(EntityPlayer player) {
    if (!this.targetPlayers.getValue()) {
      return false;
    }
    boolean isInvisible = player.isInvisible();
    if (isInvisible && !this.targetInvisibles.getValue()) {
      return false;
    }
    if (TeamUtil.isFriend(player)) {
      return false;
    }
    return this.allowSameTeam(player) && (isInvisible || !AntiBot.isBot(player));
  }

  private boolean allowTeamColor(EntityLivingBase entityLivingBase) {
    return this.targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase);
  }

  private boolean allowSameTeam(EntityPlayer player) {
    return this.targetTeams.getValue() || !TeamUtil.isSameTeam(player);
  }

  private int findEmptySlot(int currentSlot) {
    for (int i = 0; i < 9; i++) {
      if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) {
        return i;
      }
    }
    for (int i = 0; i < 9; i++) {
      if (i != currentSlot) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
        if (stack != null && !stack.hasDisplayName()) {
          return i;
        }
      }
    }
    return Math.floorMod(currentSlot - 1, 9);
  }

  private int findSwordSlot(int currentSlot) {
    for (int i = 0; i < 9; i++) {
      if (i != currentSlot) {
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
        if (item != null && item.getItem() instanceof ItemSword) {
          return i;
        }
      }
    }
    return -1;
  }

  public KillAura() {
    super("KillAura", false);
    this.lastTickProcessed = 0;
    this.mode = new ModeProperty("mode", 0, new String[] {"SINGLE", "SWITCH"});
    this.switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
    this.autoBlock =
        new ModeProperty(
            "auto-block",
            2,
            new String[] {
              "NONE",
              "VANILLA",
              "SPOOF",
              "HYPIXEL",
              "BLINK",
              "INTERACT",
              "SWAP",
              "LEGIT",
              "FAKE",
              "SMART",
              "RIGHT_HOLD",
              "GRIM",
              "Test"
            });
    this.autoBlockRequirePress = new BooleanProperty("auto-block-require-press", false);
    this.smartUnblockMode =
        new ModeProperty(
            "unblock-mode",
            0,
            new String[] {"STOP", "SWITCH", "EMPTY"},
            () -> this.autoBlock.getValue() == 9);
    this.smartReleaseAutoBlock =
        new BooleanProperty("release-auto-block", true, () -> this.autoBlock.getValue() == 9);
    this.smartForceBlockRender =
        new BooleanProperty(
            "force-block-render",
            true,
            () -> this.autoBlock.getValue() == 9 && this.smartReleaseAutoBlock.getValue());
    this.smartIgnoreTickRule =
        new BooleanProperty(
            "ignore-tick-rule",
            false,
            () -> this.autoBlock.getValue() == 9 && this.smartReleaseAutoBlock.getValue());
    this.smartBlockRate =
        new IntProperty(
            "block-rate",
            100,
            1,
            100,
            () -> this.autoBlock.getValue() == 9 && this.smartReleaseAutoBlock.getValue());
    this.smartUpdatedNCPAutoBlock =
        new BooleanProperty(
            "updated-ncp-auto-block",
            false,
            () -> this.autoBlock.getValue() == 9 && !this.smartReleaseAutoBlock.getValue());
    this.smartSwitchStartBlock =
        new BooleanProperty("switch-start-block", false, () -> this.autoBlock.getValue() == 9);
    this.smartInteractAutoBlock =
        new BooleanProperty("interact-auto-block", true, () -> this.autoBlock.getValue() == 9);
    this.smartBlinkAutoBlock =
        new BooleanProperty("blink-auto-block", false, () -> this.autoBlock.getValue() == 9);
    this.smartBlinkBlockTicks =
        new IntProperty(
            "blink-block-ticks",
            3,
            2,
            5,
            () -> this.autoBlock.getValue() == 9 && this.smartBlinkAutoBlock.getValue());
    this.smartAutoBlockCheck =
        new BooleanProperty("smart-auto-block", false, () -> this.autoBlock.getValue() == 9);
    this.smartForceBlockWhenStill =
        new BooleanProperty(
            "force-block-when-still",
            true,
            () -> this.autoBlock.getValue() == 9 && this.smartAutoBlockCheck.getValue());
    this.smartCheckEnemyWeapon =
        new BooleanProperty(
            "check-enemy-weapon",
            true,
            () -> this.autoBlock.getValue() == 9 && this.smartAutoBlockCheck.getValue());
    this.smartBlockRange =
        new FloatProperty(
            "block-range",
            3.0F,
            1.0F,
            8.0F,
            () -> this.autoBlock.getValue() == 9 && this.smartAutoBlockCheck.getValue());
    this.smartMaxOwnHurtTime =
        new IntProperty(
            "max-own-hurt-time",
            3,
            0,
            10,
            () -> this.autoBlock.getValue() == 9 && this.smartAutoBlockCheck.getValue());
    this.smartMaxDirectionDiff =
        new FloatProperty(
            "max-opponent-direction-diff",
            60.0F,
            30.0F,
            180.0F,
            () -> this.autoBlock.getValue() == 9 && this.smartAutoBlockCheck.getValue());
    this.smartMaxSwingProgress =
        new IntProperty(
            "max-opponent-swing-progress",
            1,
            0,
            5,
            () -> this.autoBlock.getValue() == 9 && this.smartAutoBlockCheck.getValue());
    this.preventServersideBlocking = new BooleanProperty("prevent-serverside-blocking", false);
    this.sort =
        new ModeProperty("sort", 0, new String[] {"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
    this.clickMode =
        new ModeProperty(
            "click-mode",
            0,
            new String[] {"NORMAL", "ACTIVE", "SECOND", "CRITICALS", "W_TAP", "PAUSE", "1.9+"});
    this.hitSelectPreference =
        new ModeProperty(
            "hs-preference",
            0,
            new String[] {"MOVE_SPEED", "KB_REDUCTION", "CRITICAL_HITS"},
            () -> this.clickMode.getValue() == 4 || this.clickMode.getValue() == 5);
    this.hitSelectDelay =
        new IntProperty(
            "hs-delay",
            420,
            300,
            500,
            () -> this.clickMode.getValue() == 4 || this.clickMode.getValue() == 5);
    this.hitSelectChance =
        new IntProperty(
            "hs-chance",
            80,
            0,
            100,
            () -> this.clickMode.getValue() == 4 || this.clickMode.getValue() == 5);
    this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
    this.swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
    this.cps = new FloatProperty("aps", 14.0F, 14.0F, 1.0F, 20.0F);
    this.rotations =
        new ModeProperty(
            "rotations", 1, new String[] {"NONE", "LEGIT/NORMAL", "SNAP", "NCP", "AUTISTIC"});
    this.smoothing = new PercentProperty("smoothing", 0);
    this.angleStep = new IntProperty("angle-step", 90, 30, 180);
    this.moveFix =
        new ModeProperty(
            "move-fix", 0, new String[] {"OFF", "MIAU", "TRADITIONAL", "BACKWARDS_SPRINT"});
    this.rayCast = new BooleanProperty("ray-cast", false);
    this.throughWalls = new BooleanProperty("through-walls", true);
    this.whileScaffold = new BooleanProperty("while-scaffold", false);
    this.badPacketsCheck = new BooleanProperty("bad-packets-check", true);
    this.fov = new IntProperty("fov", 360, 30, 360);
    this.requirePress = new BooleanProperty("require-press", false);
    this.allowMining = new BooleanProperty("allow-mining", true);
    this.weaponsOnly = new BooleanProperty("weapons-only", true);
    this.allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    this.inventoryCheck = new BooleanProperty("inventory-check", true);
    this.showTarget =
        new ModeProperty(
            "show-target", 0, new String[] {"NONE", "SIGMA_RING", "ABOVE_BOX", "FULL_BOX"});
    this.debugLog = new ModeProperty("debug-log", 0, new String[] {"NONE", "HEALTH"});
    this.tickLookahead = new BooleanProperty("tick-lookahead", false);
    this.smartKill = new BooleanProperty("smart-kill", true);
    this.tacticalKD = new BooleanProperty("tactical-kd", false);
    this.kdOffset = new FloatProperty("kd-offset", 45.0F, 15.0F, 90.0F);
  }

  public EntityLivingBase getTarget() {
    return this.target != null ? this.target.getEntity() : null;
  }

  public boolean isAttackAllowed() {
    Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
    if (!this.whileScaffold.getValue() && scaffold.isEnabled()) {
      return false;
    } else if (!this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
      return !this.requirePress.getValue()
          || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    } else {
      return false;
    }
  }

  public boolean shouldAutoBlock() {
    if (this.isPlayerBlocking() && this.isBlocking) {
      return !mc.thePlayer.isInWater()
          && !mc.thePlayer.isInLava()
          && (this.autoBlock.getValue() == 3
              || this.autoBlock.getValue() == 4
              || this.autoBlock.getValue() == 5
              || this.autoBlock.getValue() == 6
              || this.autoBlock.getValue() == 7
              || this.autoBlock.getValue() == 9
              || this.autoBlock.getValue() == 10
              || this.autoBlock.getValue() == 11);
    } else {
      return false;
    }
  }

  public boolean isBlocking() {
    return this.fakeBlockState && ItemUtil.isHoldingSword();
  }

  public boolean isPlayerBlocking() {
    return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
  }

  private boolean isNoSlowAntiSwitchActive() {
    return Myau.moduleManager != null
        && Myau.moduleManager.modules.get(NoSlow.class) instanceof NoSlow
        && ((NoSlow) Myau.moduleManager.modules.get(NoSlow.class)).isAntiSwitchActive();
  }

  @EventTarget(Priority.LOW)
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.POST && this.blinkReset) {
      this.blinkReset = false;
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
    }
    if (event.getType() == EventType.POST) {
      this.hsResetMotion();
    }
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (this.clickMode.getValue() == 5) {
        if (this.target == null) {
          this.hsCurrentShouldAttack = false;
        } else {
          this.hsCurrentShouldAttack = false;
          if (Math.random() * 100.0D > this.hitSelectChance.getValue()) {
            this.hsCurrentShouldAttack = true;
          } else {
            switch (this.hitSelectPreference.getValue()) {
              case 1:
                this.hsCurrentShouldAttack = !mc.thePlayer.onGround && mc.thePlayer.motionY < 0.0D;
                break;
              case 2:
                this.hsCurrentShouldAttack =
                    mc.thePlayer.hurtTime > 0
                        && !mc.thePlayer.onGround
                        && this.hsIsMoving(mc.thePlayer);
                break;
            }
            if (!this.hsCurrentShouldAttack) {
              this.hsCurrentShouldAttack =
                  System.currentTimeMillis() - this.hsAttackTime >= this.hitSelectDelay.getValue();
            }
          }
        }
      }
      this.ticksSinceVelocity++;
      if (mc.thePlayer.ticksExisted % 20 == 0) {
        this.expandRange = 3.0 + Math.random() * 0.5;
      }
      if (this.moveFix.getValue() == 3 && RotationState.isActived() && this.target != null) {
        float moveYaw =
            MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw,
                mc.thePlayer.movementInput.moveForward,
                mc.thePlayer.movementInput.moveStrafe);
        float serverYaw = RotationState.getRotationYawHead();
        if (Math.abs(MathHelper.wrapAngleTo180_float(serverYaw - moveYaw)) > 45.0F) {
          KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
          mc.thePlayer.setSprinting(false);
        }
      }
      this.updateRightHoldBlock();
      if (this.attackDelayMS > 0L) {
        this.attackDelayMS -= 50L;
      }
      boolean attack = this.target != null && this.canAttack();
      boolean block = attack && this.canAutoBlock();
      if (!block) {
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.blockTick = 0;
      }
      if (attack) {
        boolean swap = false;
        boolean blocked = false;
        if (block) {
          switch (this.autoBlock.getValue()) {
            case 0:
              if (PlayerUtil.isUsingItem()) {
                this.isBlocking = true;
                if (!this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  swap = true;
                }
              } else {
                this.isBlocking = false;
                if (this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  this.stopBlock();
                }
              }
              Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
              this.fakeBlockState = false;
              break;
            case 1:
              if (this.hasValidTarget()) {
                if (!this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  swap = true;
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = true;
                this.fakeBlockState = false;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 2:
              if (this.hasValidTarget()) {
                int item =
                    ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (Myau.playerStateManager.digging
                    || Myau.playerStateManager.placing
                    || mc.thePlayer.inventory.currentItem != item
                    || this.isPlayerBlocking() && this.blockTick != 0
                    || this.attackDelayMS > 0L && this.attackDelayMS <= 50L) {
                  this.blockTick = 0;
                } else {
                  int slot = this.findEmptySlot(item);
                  PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                  PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
                  swap = true;
                  this.blockTick = 1;
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = true;
                this.fakeBlockState = false;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 3:
              if (this.hasValidTarget()) {
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                  switch (this.blockTick) {
                    case 0:
                      if (!this.isPlayerBlocking()) {
                        swap = true;
                      }
                      blocked = true;
                      this.blockTick = 1;
                      break;
                    case 1:
                      if (this.isPlayerBlocking()) {
                        if (Myau.moduleManager.modules.get(NoSlow.class).isEnabled()) {
                          int randomSlot = new Random().nextInt(9);
                          while (randomSlot == mc.thePlayer.inventory.currentItem) {
                            randomSlot = new Random().nextInt(9);
                          }
                          PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                          PacketUtil.sendPacket(
                              new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                        }
                        this.stopBlock();
                        attack = false;
                      }
                      if (this.attackDelayMS <= 50L) {
                        this.blockTick = 0;
                      }
                      break;
                    default:
                      this.blockTick = 0;
                  }
                }
                this.isBlocking = true;
                this.fakeBlockState = true;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 4:
              if (this.hasValidTarget()) {
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                  switch (this.blockTick) {
                    case 0:
                      if (!this.isPlayerBlocking()) {
                        swap = true;
                      }
                      this.blinkReset = true;
                      this.blockTick = 1;
                      break;
                    case 1:
                      if (this.isPlayerBlocking()) {
                        this.stopBlock();
                        attack = false;
                      }
                      if (this.attackDelayMS <= 50L) {
                        this.blockTick = 0;
                      }
                      break;
                    default:
                      this.blockTick = 0;
                  }
                }
                this.isBlocking = true;
                this.fakeBlockState = true;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 5:
              if (this.hasValidTarget()) {
                int item =
                    ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == item
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  switch (this.blockTick) {
                    case 0:
                      if (!this.isPlayerBlocking()) {
                        swap = true;
                      }
                      this.blinkReset = true;
                      this.blockTick = 1;
                      break;
                    case 1:
                      if (this.isPlayerBlocking()) {
                        int slot = this.findEmptySlot(item);
                        if (!this.isNoSlowAntiSwitchActive()) {
                          PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                          ((IAccessorPlayerControllerMP) mc.playerController)
                              .setCurrentPlayerItem(slot);
                        }
                        attack = false;
                      }
                      if (this.attackDelayMS <= 50L) {
                        this.blockTick = 0;
                      }
                      break;
                    default:
                      this.blockTick = 0;
                  }
                }
                this.isBlocking = true;
                this.fakeBlockState = true;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 6:
              if (this.hasValidTarget()) {
                int item =
                    ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == item
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  switch (this.blockTick) {
                    case 0:
                      int slot = this.findSwordSlot(item);
                      if (slot != -1) {
                        if (!this.isPlayerBlocking()) {
                          swap = true;
                        }
                        this.blockTick = 1;
                      }
                      break;
                    case 1:
                      int swordsSlot = this.findSwordSlot(item);
                      if (swordsSlot == -1) {
                        this.blockTick = 0;
                      } else if (!this.isPlayerBlocking()) {
                        swap = true;
                      } else if (this.attackDelayMS <= 50L) {
                        if (!this.isNoSlowAntiSwitchActive()) {
                          PacketUtil.sendPacket(new C09PacketHeldItemChange(swordsSlot));
                          ((IAccessorPlayerControllerMP) mc.playerController)
                              .setCurrentPlayerItem(swordsSlot);
                          this.startBlock(mc.thePlayer.inventory.getStackInSlot(swordsSlot));
                        }
                        attack = false;
                        this.blockTick = 0;
                      }
                      break;
                    default:
                      this.blockTick = 0;
                  }
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = true;
                  this.fakeBlockState = true;
                  break;
                }
              }
              Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
              this.isBlocking = false;
              this.fakeBlockState = false;
              break;
            case 7:
              if (this.hasValidTarget()) {
                if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                  switch (this.blockTick) {
                    case 0:
                      if (!this.isPlayerBlocking()) {
                        swap = true;
                      }
                      this.blockTick = 1;
                      break;
                    case 1:
                      if (this.isPlayerBlocking()) {
                        this.stopBlock();
                        attack = false;
                      }
                      if (this.attackDelayMS <= 50L) {
                        this.blockTick = 0;
                      }
                      break;
                    default:
                      this.blockTick = 0;
                  }
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = true;
                this.fakeBlockState = false;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 8:
              Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
              this.isBlocking = false;
              this.fakeBlockState = this.hasValidTarget();
              if (PlayerUtil.isUsingItem()
                  && !this.isPlayerBlocking()
                  && !Myau.playerStateManager.digging
                  && !Myau.playerStateManager.placing) {
                swap = true;
              }
              break;
            case 9:
              if (this.hasValidTarget() && this.shouldCustomSmartBlock()) {
                int item =
                    ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == item
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  if (this.isPlayerBlocking()
                      && this.smartReleaseAutoBlock.getValue()
                      && !this.smartIgnoreTickRule.getValue()) {
                    this.stopCustomBlock(false);
                    attack = false;
                  } else if (!this.isPlayerBlocking() || this.smartUpdatedNCPAutoBlock.getValue()) {
                    if (this.smartBlockRate.getValue() >= 100
                        || new Random().nextInt(100) < this.smartBlockRate.getValue()) {
                      if (this.smartSwitchStartBlock.getValue()) {
                        PacketUtil.sendPacket(new C09PacketHeldItemChange((item + 1) % 9));
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
                      }
                      swap = true;
                    }
                  }
                  if (this.smartBlinkAutoBlock.getValue()) {
                    int blinkCycle = this.smartBlinkBlockTicks.getValue() + 1;
                    int blinkTick = Math.floorMod(mc.thePlayer.ticksExisted, blinkCycle);
                    if (blinkTick == 1 && this.isPlayerBlocking()) {
                      this.stopCustomBlock(false);
                      attack = false;
                    } else if (blinkTick == this.smartBlinkBlockTicks.getValue()
                        && !this.isPlayerBlocking()) {
                      swap = true;
                      this.blinkReset = true;
                    }
                  }
                }
                this.isBlocking = true;
                this.fakeBlockState =
                    this.smartForceBlockRender.getValue() || this.smartBlinkAutoBlock.getValue();
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                if (this.isPlayerBlocking()
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  this.stopCustomBlock(true);
                }
                this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
              }
              break;
            case 10:
              Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
              this.isBlocking = this.shouldRightHoldBlock();
              this.fakeBlockState = false;
              break;
            case 11:
              if (this.hasValidTarget()) {
                int item =
                    ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == item
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  PacketUtil.sendPacket(new C09PacketHeldItemChange(item % 8 + 1));
                  PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
                  swap = true;
                }
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = true;
                this.fakeBlockState = false;
              } else {
                Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
              }
              break;
            case 12:
              if (this.hasValidTarget()) {
                int item =
                    ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
                if (mc.thePlayer.inventory.currentItem == item
                    && !Myau.playerStateManager.digging
                    && !Myau.playerStateManager.placing) {
                  PacketUtil.sendPacket(
                      new C02PacketUseEntity(target.getEntity(), C02PacketUseEntity.Action.ATTACK));
                  mc.thePlayer.swingItem();
                  if (this.autoBlock.getValue() == 12) {
                    PacketUtil.sendPacket(
                        new C08PacketPlayerBlockPlacement(
                            new BlockPos(-1, -1, -1),
                            255,
                            mc.thePlayer.inventory.getCurrentItem(),
                            0,
                            0,
                            0));
                    this.isBlocking = true;
                    this.fakeBlockState = true;
                  }
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = true;
                  this.fakeBlockState = false;
                } else {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false;
                  this.fakeBlockState = false;
                }
              }
              break;
          }
        }
        boolean attacked = false;
        if (this.isBoxInSwingRange(this.target.getBox())) {
          if (this.rotations.getValue() != 0) {
            float[] targetRots =
                RotationUtil.calculate(this.target.getEntity(), true, this.attackRange.getValue());
            float[] lastRots = new float[] {event.getYaw(), event.getPitch()};

            double rotSpeed = (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
            float[] rotations = lastRots;

            switch (this.rotations.getValue()) {
              case 1: // LEGIT/NORMAL
                rotations =
                    RotationUtil.smooth(
                        lastRots,
                        targetRots,
                        rotSpeed,
                        this.target.getEntity(),
                        this.attackRange.getValue());
                break;
              case 2: // SNAP
                if (rotSpeed != 0 && this.attackDelayMS <= 50L) {
                  rotations =
                      RotationUtil.smooth(
                          lastRots,
                          targetRots,
                          rotSpeed,
                          this.target.getEntity(),
                          this.attackRange.getValue());
                } else {
                  rotations = new float[] {mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
                }
                break;
              case 3: // NCP
                if (rotSpeed != 0) {
                  if (Math.random() > 0.1) {
                    rotations = targetRots;
                  } else {
                    rotations =
                        new float[] {
                          targetRots[0] + (float) ((Math.random() - 0.5) * 10),
                          targetRots[1] + (float) ((Math.random() - 0.5) * 3)
                        };
                  }
                }
                break;
              case 4: // AUTISTIC
                rotations = new float[] {(float) (lastRots[0] + rotSpeed * 10), 0};
                break;
            }

            event.setRotation(rotations[0], rotations[1], 1);
            if (this.moveFix.getValue() != 0) {
              event.setPervRotation(rotations[0], 1);
            }
          }
          // ════════════════════════════════════════
          //  KNOCKBACK DISPLACEMENT INTERCEPT
          // ════════════════════════════════════════
          if (attack
              && this.tacticalKD.getValue()
              && this.target.getEntity() instanceof EntityPlayer) {

            EntityPlayer kdTarget = (EntityPlayer) this.target.getEntity();
            float kdYaw = this.getTacticalKDYaw(kdTarget, event.getNewYaw());
            float kdPitch = this.getTacticalKDPitch(kdTarget, event.getNewPitch());

            // Raycast verification — revert to normal if KD yaw would miss
            if (RayCastUtil.getEntityIntercept(
                    kdTarget, kdYaw, kdPitch, this.attackRange.getValue())
                != null) {
              // Apply sensitivity patch for more natural rotation
              float[] patched =
                  RotationUtil.applySensitivityPatch(
                      kdYaw, kdPitch, mc.thePlayer.prevRotationYaw, mc.thePlayer.prevRotationPitch);
              event.setRotation(patched[0], patched[1], 1);
            } else {
              event.setRotation(kdYaw, event.getNewPitch(), 1);
            }
            this.kdHitCounter++;
          }
          if (attack) {
            attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
          }
        }
        if (swap) {
          if (attacked) {
            this.interactAttack(event.getNewYaw(), event.getNewPitch());
          } else {
            this.sendUseItem();
          }
        }
        if (blocked) {
          Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
          Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
      }
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      switch (event.getType()) {
        case PRE:
          if (this.target == null
              || !this.isValidTarget(this.target.getEntity())
              || !this.isBoxInAttackRange(this.target.getBox())
              || !this.isBoxInSwingRange(this.target.getBox())
              || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
            this.timer.reset();
            ArrayList<EntityLivingBase> targets = new ArrayList<>();
            for (Entity entity : mc.theWorld.loadedEntityList) {
              if (entity instanceof EntityLivingBase
                  && this.isValidTarget((EntityLivingBase) entity)
                  && this.isInRange((EntityLivingBase) entity)) {
                targets.add((EntityLivingBase) entity);
              }
            }
            if (targets.isEmpty()) {
              this.target = null;
            } else {
              if (targets.stream().anyMatch(this::isInSwingRange)) {
                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
              }
              if (targets.stream().anyMatch(this::isInAttackRange)) {
                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
              }
              if (targets.stream().anyMatch(this::isPlayerTarget)) {
                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
              }
              targets.sort(
                  (entityLivingBase1, entityLivingBase2) -> {
                    int sortBase = 0;
                    switch (this.sort.getValue()) {
                      case 1:
                        sortBase =
                            Float.compare(
                                TeamUtil.getHealthScore(entityLivingBase1),
                                TeamUtil.getHealthScore(entityLivingBase2));
                        break;
                      case 2:
                        sortBase =
                            Integer.compare(
                                entityLivingBase1.hurtResistantTime,
                                entityLivingBase2.hurtResistantTime);
                        break;
                      case 3:
                        sortBase =
                            Float.compare(
                                RotationUtil.angleToEntity(entityLivingBase1),
                                RotationUtil.angleToEntity(entityLivingBase2));
                    }
                    return sortBase != 0
                        ? sortBase
                        : Double.compare(
                            RotationUtil.distanceToEntity(entityLivingBase1),
                            RotationUtil.distanceToEntity(entityLivingBase2));
                  });
              if (this.mode.getValue() == 1 && targets.size() > 1) {
                targets.sort(
                    (e1, e2) -> {
                      LastAttackData data1 = KillAura.this.targetMap.get(e1.getEntityId());
                      LastAttackData data2 = KillAura.this.targetMap.get(e2.getEntityId());
                      double score1 =
                          -((e1.getHealth() * 25.0D) + (data1 == null ? 0 : data1.getTime()));
                      double score2 =
                          -((e2.getHealth() * 25.0D) + (data2 == null ? 0 : data2.getTime()));
                      return Double.compare(score1, score2);
                    });
              }
              if (this.mode.getValue() == 1 && this.hitRegistered) {
                this.hitRegistered = false;
                this.switchTick = 0;
              }
              if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                this.switchTick = 0;
              }
              this.target = new AttackData(targets.get(this.switchTick));
            }
          }
          if (this.target != null) {
            this.target = new AttackData(this.target.getEntity());
          }
          break;
        case POST:
          if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
            mc.thePlayer.setItemInUse(
                mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
          }
          break;
        default:
          break;
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof net.minecraft.network.play.client.C0BPacketEntityAction) {
        net.minecraft.network.play.client.C0BPacketEntityAction packet =
            (net.minecraft.network.play.client.C0BPacketEntityAction) event.getPacket();
        switch (packet.getAction()) {
          case START_SPRINTING:
            this.hsSprintState = true;
            break;
          case STOP_SPRINTING:
            this.hsSprintState = false;
            break;
          default:
            break;
        }
      }
      if (this.preventServersideBlocking.getValue() && this.isPlayerBlocking()) {
        if (event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
          C08PacketPlayerBlockPlacement wrapper = (C08PacketPlayerBlockPlacement) event.getPacket();
          if (wrapper.getStack() != null && wrapper.getStack().getItem() instanceof ItemSword) {
            event.setCancelled(true);
          }
        } else if (event.getPacket() instanceof C07PacketPlayerDigging) {
          C07PacketPlayerDigging wrapper = (C07PacketPlayerDigging) event.getPacket();
          if (wrapper.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            event.setCancelled(true);
          }
        }
      }
    }
    if (this.isEnabled() && !event.isCancelled() && mc.thePlayer != null && mc.theWorld != null) {
      if (event.getPacket() instanceof C07PacketPlayerDigging) {
        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
        if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
          this.blockingState = false;
        }
      }
      if (event.getPacket() instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
        net.minecraft.network.play.server.S12PacketEntityVelocity packet =
            (net.minecraft.network.play.server.S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
          this.ticksSinceVelocity = 0;
        }
      }
      if (event.getPacket() instanceof C09PacketHeldItemChange) {
        this.blockingState = false;
        if (this.isBlocking) {
          mc.thePlayer.stopUsingItem();
        }
      }
      if (this.debugLog.getValue() == 1 && this.isAttackAllowed()) {
        if (event.getPacket() instanceof S06PacketUpdateHealth) {
          float packet =
              ((S06PacketUpdateHealth) event.getPacket()).getHealth() - mc.thePlayer.getHealth();
          if (packet != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) {
            this.lastTickProcessed = mc.thePlayer.ticksExisted;
            ChatUtil.sendFormatted(
                String.format(
                    "%sHealth: %s&l%s&r (&otick: %d&r)&r",
                    Myau.clientName,
                    packet > 0.0F ? "&a" : "&c",
                    df.format(packet),
                    mc.thePlayer.ticksExisted));
          }
        }
        if (event.getPacket() instanceof S1CPacketEntityMetadata) {
          S1CPacketEntityMetadata packet = (S1CPacketEntityMetadata) event.getPacket();
          if (packet.getEntityId() == mc.thePlayer.getEntityId()) {
            for (WatchableObject watchableObject : packet.func_149376_c()) {
              if (watchableObject.getDataValueId() == 6) {
                float diff = (Float) watchableObject.getObject() - mc.thePlayer.getHealth();
                if (diff != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) {
                  this.lastTickProcessed = mc.thePlayer.ticksExisted;
                  ChatUtil.sendFormatted(
                      String.format(
                          "%sHealth: %s&l%s&r (&otick: %d&r)&r",
                          Myau.clientName,
                          diff > 0.0F ? "&a" : "&c",
                          df.format(diff),
                          mc.thePlayer.ticksExisted));
                }
              }
            }
          }
        }
      }
    }
  }

  @EventTarget
  public void onMove(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.moveFix.getValue() == 1 && RotationState.isActived()) {
        MoveUtil.fixMovement(RotationState.getRotationYawHead());
      }
      if (this.shouldAutoBlock()) {
        mc.thePlayer.movementInput.jump = false;
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled() && RotationState.isActived() && this.target != null) {
      if (this.moveFix.getValue() == 1 || this.moveFix.getValue() == 2) {
        event.setYaw(RotationState.getRotationYawHead());
      }
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (this.isEnabled() && RotationState.isActived() && this.target != null) {
      if (this.moveFix.getValue() == 1
          || this.moveFix.getValue() == 2
          || this.moveFix.getValue() == 3) {
        event.setYaw(RotationState.getRotationYawHead());
      }
    }
  }

  @EventTarget
  public void onRender(Render3DEvent event) {
    if (this.isEnabled() && target != null) {
      if (this.showTarget.getValue() != 0
          && TeamUtil.isEntityLoaded(this.target.getEntity())
          && this.isAttackAllowed()) {
        final float partialTicks = event.getPartialTicks();
        EntityLivingBase player = this.target.getEntity();

        if (mc.getRenderManager() == null || player == null) return;

        final double x =
            player.prevPosX
                + (player.posX - player.prevPosX) * partialTicks
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        final double y =
            player.prevPosY
                + (player.posY - player.prevPosY) * partialTicks
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        final double z =
            player.prevPosZ
                + (player.posZ - player.prevPosZ) * partialTicks
                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        if (this.showTarget.getValue() == 1) {
          final Color color =
              ((HUD) Myau.moduleManager.modules.get(HUD.class))
                  .getColor(System.currentTimeMillis());
          final double ringY = y + Math.sin(System.currentTimeMillis() / 2E+2) + 1;
          GL11.glPushMatrix();
          GL11.glDisable(3553);
          GL11.glEnable(2848);
          GL11.glEnable(2832);
          GL11.glEnable(3042);
          GL11.glBlendFunc(770, 771);
          GL11.glHint(3154, 4354);
          GL11.glHint(3155, 4354);
          GL11.glHint(3153, 4354);
          GL11.glDepthMask(false);
          GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
          GL11.glShadeModel(GL11.GL_SMOOTH);
          GlStateManager.disableCull();
          GL11.glBegin(GL11.GL_TRIANGLE_STRIP);

          for (float i = 0;
              i <= Math.PI * 2 + ((Math.PI * 2) / 25);
              i += (float) ((Math.PI * 2) / 25)) {
            double vecX = x + 0.67 * Math.cos(i);
            double vecZ = z + 0.67 * Math.sin(i);

            ColorUtil.glColor(ColorUtil.withAlpha(color, (int) (255 * 0.25)));
            GL11.glVertex3d(vecX, ringY, vecZ);
          }

          for (float i = 0; i <= Math.PI * 2 + (Math.PI * 2) / 25; i += (Math.PI * 2) / 25) {
            double vecX = x + 0.67 * Math.cos(i);
            double vecZ = z + 0.67 * Math.sin(i);

            ColorUtil.glColor(ColorUtil.withAlpha(color, (int) (255 * 0.25)));
            GL11.glVertex3d(vecX, ringY, vecZ);

            ColorUtil.glColor(ColorUtil.withAlpha(color, 0));
            GL11.glVertex3d(vecX, ringY - Math.cos(System.currentTimeMillis() / 2E+2) / 2.0F, vecZ);
          }

          GL11.glEnd();
          GL11.glShadeModel(GL11.GL_FLAT);
          GL11.glDepthMask(true);
          GL11.glEnable(2929);
          GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
          GlStateManager.enableCull();
          GL11.glDisable(2848);
          GL11.glDisable(2848);
          GL11.glEnable(2832);
          GL11.glEnable(3553);
          GL11.glPopMatrix();
          GlStateManager.resetColor();
        } else if (this.showTarget.getValue() == 2) {
          final Color color =
              player.hurtTime > 0
                  ? Color.red
                  : ((HUD) Myau.moduleManager.modules.get(HUD.class))
                      .getColor(System.currentTimeMillis());
          GL11.glPushMatrix();
          GL11.glEnable(3042);
          GL11.glLineWidth(1.8F);
          GL11.glBlendFunc(770, 771);
          GL11.glEnable(2848);
          GlStateManager.depthMask(true);

          GL11.glEnable(GL11.GL_BLEND);
          GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          GL11.glDisable(GL11.GL_TEXTURE_2D);
          GL11.glEnable(GL11.GL_LINE_SMOOTH);
          GL11.glDisable(GL11.GL_DEPTH_TEST);
          GL11.glDepthMask(false);

          double renderY = y + player.getEyeHeight() * 1.2;
          float width = player.width;
          AxisAlignedBB aabb =
              new AxisAlignedBB(
                  x - width / 1.75,
                  renderY,
                  z - width / 1.75,
                  x + width / 1.75,
                  renderY + 0.1,
                  z + width / 1.75);

          RenderUtil.drawBoundingBox(
              aabb, color.getRed(), color.getGreen(), color.getBlue(), 40, 1.8F);

          GL11.glDisable(GL11.GL_LINE_SMOOTH);
          GL11.glEnable(GL11.GL_TEXTURE_2D);
          GL11.glEnable(GL11.GL_DEPTH_TEST);
          GL11.glDepthMask(true);
          GL11.glDisable(GL11.GL_BLEND);

          GL11.glDisable(3042);
          GL11.glDisable(2848);
          GL11.glPopMatrix();
          GlStateManager.resetColor();
        } else if (this.showTarget.getValue() == 3) {
          boolean wasHurtRecently = false;
          if (player.hurtTime > 0) {
            wasHurtRecently = true;
            this.ticks = 0;
          }
          if (this.ticks <= 23) {
            wasHurtRecently = true;
          }
          this.ticks++;

          Color color =
              wasHurtRecently
                  ? Color.red
                  : ((HUD) Myau.moduleManager.modules.get(HUD.class))
                      .getColor(System.currentTimeMillis());
          GL11.glPushMatrix();
          GL11.glEnable(3042);
          GL11.glLineWidth(1.8F);
          GL11.glBlendFunc(770, 771);
          GL11.glEnable(2848);
          GlStateManager.depthMask(true);

          GL11.glEnable(GL11.GL_BLEND);
          GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          GL11.glDisable(GL11.GL_TEXTURE_2D);
          GL11.glEnable(GL11.GL_LINE_SMOOTH);
          GL11.glDisable(GL11.GL_DEPTH_TEST);
          GL11.glDepthMask(false);

          float width = player.width / 1.15F;
          float height = player.height + (player.isSneaking() ? -0.2F : 0.1F);
          AxisAlignedBB aabb =
              new AxisAlignedBB(
                  x - width + 0.1D,
                  y,
                  z - width + 0.1D,
                  x + width - 0.1D,
                  y + height + 0.1D,
                  z + width - 0.1D);

          RenderUtil.drawBoundingBox(
              aabb, color.getRed(), color.getGreen(), color.getBlue(), 60, 1.8F);

          GL11.glDisable(GL11.GL_LINE_SMOOTH);
          GL11.glEnable(GL11.GL_TEXTURE_2D);
          GL11.glEnable(GL11.GL_DEPTH_TEST);
          GL11.glDepthMask(true);
          GL11.glDisable(GL11.GL_BLEND);

          GL11.glDisable(3042);
          GL11.glDisable(2848);
          GL11.glPopMatrix();
          GlStateManager.resetColor();
        }
      }
    }
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    } else {
      if (this.isEnabled() && this.target != null && this.canAttack()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    } else {
      if (this.isEnabled() && this.target != null && this.canAttack()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    } else {
      if (this.isEnabled() && this.target != null && this.canAttack()) {
        event.setCancelled(true);
      }
    }
  }

  @EventTarget
  public void onCancelUse(CancelUseEvent event) {
    if (this.isBlocking) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onEnabled() {
    this.target = null;
    this.switchTick = 0;
    this.hitRegistered = false;
    this.attackDelayMS = 0L;
    this.blockTick = 0;
    this.ticks = 255;
    this.rightHoldActive = false;
  }

  @Override
  public void onDisabled() {
    this.setRightHold(false);
    this.targetMap.clear();
    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    this.blockingState = false;
    this.isBlocking = false;
    this.fakeBlockState = false;

    this.hsResetMotion();
    this.hsSprintState = false;
    this.hsSet = false;
    this.hsSavedSlowdown = 0;
    this.hsAttackTime = -1L;
    this.hsCurrentShouldAttack = false;
    this.kdHitCounter = 0;
  }

  @Override
  public void verifyValue(String value) {
    boolean badCps =
        this.autoBlock.getValue() == 2
            || this.autoBlock.getValue() == 3
            || this.autoBlock.getValue() == 4
            || this.autoBlock.getValue() == 5
            || this.autoBlock.getValue() == 6
            || this.autoBlock.getValue() == 7
            || this.autoBlock.getValue() == 10;
    if (!this.autoBlock.getName().equals(value)) {
      if (this.swingRange.getName().equals(value)) {
        if (this.swingRange.getValue() < this.attackRange.getValue()) {
          this.attackRange.setValue(this.swingRange.getValue());
        }
      } else if (this.attackRange.getName().equals(value)) {
        if (this.swingRange.getValue() < this.attackRange.getValue()) {
          this.swingRange.setValue(this.attackRange.getValue());
        }
      }
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }

  private float getTacticalKDYaw(EntityPlayer target, float currentYaw) {
    float voidPushYaw = this.scanVoidPushKD(target, currentYaw);
    if (!Float.isNaN(voidPushYaw)) {
      return voidPushYaw;
    }

    float voidYaw = this.scanVoidKD(target);
    if (!Float.isNaN(voidYaw)) {
      return voidYaw;
    }

    Float wallYaw = this.scanWallKD(target);
    if (wallYaw != null) {
      return wallYaw;
    }

    Float nearWallYaw = this.scanNearWallKD(target, currentYaw);
    if (nearWallYaw != null) {
      return nearWallYaw;
    }

    float velYaw = this.predictVelocityKD(target, currentYaw);
    if (!Float.isNaN(velYaw)) {
      return velYaw;
    }

    return this.zigzagKD(currentYaw, target);
  }

  private float scanVoidKD(EntityPlayer target) {
    final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    for (int ring = 1; ring <= KD_VOID_RINGS; ring++) {
      final double radius = ring * KD_RING_STEP;

      for (int dir = 0; dir < KD_DIRECTIONS; dir++) {
        final double wx = target.posX + KD_COS[dir] * radius;
        final double wz = target.posZ + KD_SIN[dir] * radius;

        if (this.isVoidColumn(wx, target.posY, wz, cursor)) {
          return RotationUtil.calculate(new Vec3(wx, target.posY, wz))[0];
        }
      }
    }
    return Float.NaN;
  }

  private float scanVoidPushKD(EntityPlayer target, float aimYaw) {
    final double dx = target.posX - mc.thePlayer.posX;
    final double dz = target.posZ - mc.thePlayer.posZ;
    final double dist = Math.sqrt(dx * dx + dz * dz);
    if (dist < 0.01) return Float.NaN;

    final double nx = dx / dist;
    final double nz = dz / dist;

    final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    for (int ring = 1; ring <= KD_VOID_RINGS; ring++) {
      final double radius = ring * KD_RING_STEP;
      final double wx = target.posX + nx * radius;
      final double wz = target.posZ + nz * radius;

      if (this.isVoidColumn(wx, target.posY, wz, cursor)) {
        return aimYaw;
      }
    }
    return Float.NaN;
  }

  private boolean isVoidColumn(double x, double y, double z, BlockPos.MutableBlockPos cursor) {
    final int bx = MathHelper.floor_double(x);
    final int bz = MathHelper.floor_double(z);
    final int startY = MathHelper.floor_double(y) - 1;
    final int bottomY = Math.max(0, startY - KD_VOID_DEPTH);

    for (int by = startY; by >= bottomY; by--) {
      cursor.set(bx, by, bz);
      if (!mc.theWorld.isAirBlock(cursor)) {
        return false;
      }
    }
    return true;
  }

  private Float scanWallKD(EntityPlayer target) {
    final double dx = target.posX - mc.thePlayer.posX;
    final double dz = target.posZ - mc.thePlayer.posZ;
    final double dist = Math.sqrt(dx * dx + dz * dz);
    if (dist < 0.01) return null;

    final double nx = dx / dist;
    final double nz = dz / dist;
    final double sx = -nz;
    final double sz = nx;

    final double baseX = target.posX + nx * KD_WALL_BEHIND_DIST;
    final double baseZ = target.posZ + nz * KD_WALL_BEHIND_DIST;
    final int baseY = MathHelper.floor_double(target.posY);

    final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    for (int side = -1; side <= 1; side++) {
      final double cx = baseX + sx * side * KD_WALL_SIDE_STEP;
      final double cz = baseZ + sz * side * KD_WALL_SIDE_STEP;

      final int bx = MathHelper.floor_double(cx);
      final int bz = MathHelper.floor_double(cz);

      boolean solid = false;
      for (int by = baseY; by < baseY + 2; by++) {
        if (by < 0 || by >= 256) continue;
        cursor.set(bx, by, bz);
        if (mc.theWorld.getBlockState(cursor).getBlock().isFullBlock()) {
          solid = true;
          break;
        }
      }
      if (!solid) continue;

      final double wallCX = bx + 0.5;
      final double wallCZ = bz + 0.5;
      final double normDx = target.posX - wallCX;
      final double normDz = target.posZ - wallCZ;
      final double normLen = Math.sqrt(normDx * normDx + normDz * normDz);
      if (normLen < 0.01) continue;

      final float normalYaw = (float) Math.toDegrees(Math.atan2(normDz, normDx)) - 90.0F;
      final boolean slideRight = (target.ticksExisted & 2) == 0;
      return normalYaw + (slideRight ? 90.0F : -90.0F);
    }
    return null;
  }

  private Float scanNearWallKD(EntityPlayer target, float aimYaw) {
    if (mc.thePlayer == null) return null;

    // Direction vector from player → target
    final double dx = target.posX - mc.thePlayer.posX;
    final double dz = target.posZ - mc.thePlayer.posZ;
    final double dist = Math.sqrt(dx * dx + dz * dz);
    if (dist < 0.01) return null;

    final double nx = dx / dist;
    final double nz = dz / dist;

    final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    final int px = MathHelper.floor_double(mc.thePlayer.posX);
    final int py = MathHelper.floor_double(mc.thePlayer.posY + 0.5);
    final int pz = MathHelper.floor_double(mc.thePlayer.posZ);

    // Check both sides (perpendicular to player→target vector)
    for (int side : new int[] {-1, 1}) {
      final double sx = -nz * side;
      final double sz = nx * side;

      for (int d = 1; d <= 3; d++) {
        final int bx = px + (int) Math.round(sx * d);
        final int bz = pz + (int) Math.round(sz * d);

        for (int by = py - 1; by <= py + 1; by++) {
          if (by < 0 || by >= 256) continue;
          cursor.set(bx, by, bz);
          if (mc.theWorld.getBlockState(cursor).getBlock().isFullBlock()) {
            // Wall beside player → aim to push target into wall
            return aimYaw + side * 45.0F;
          }
        }
      }
    }
    return null;
  }

  private float predictVelocityKD(EntityPlayer target, float aimYaw) {
    // Determine which direction the target is moving
    final double velX = target.posX - target.prevPosX;
    final double velZ = target.posZ - target.prevPosZ;
    final double vel = Math.sqrt(velX * velX + velZ * velZ);

    if (vel < 0.03) return Float.NaN;

    // Heading the target is moving toward
    final float velYaw = (float) Math.toDegrees(Math.atan2(velZ, velX)) - 90.0F;
    final float diff = MathHelper.wrapAngleTo180_float(velYaw - aimYaw);

    // Only offset if they're moving away (positive knockback direction)
    if (Math.abs(diff) < 90.0F && Math.abs(diff) > 5.0F) {
      return aimYaw + diff * 0.35F;
    }
    return Float.NaN;
  }

  private float getTacticalKDPitch(EntityPlayer target, float currentPitch) {
    // If target is airborne → tilt pitch for extra vertical displacement
    if (!target.onGround && target.hurtTime <= 0 && target.fallDistance > 0.5F) {
      return Math.min(currentPitch + 8.0F, 15.0F);
    }
    return currentPitch;
  }

  private float zigzagKD(float currentYaw, EntityPlayer target) {
    final boolean phase = ((this.kdHitCounter / 2) & 1) == 0;
    float offset = phase ? this.kdOffset.getValue() : -this.kdOffset.getValue();
    offset += (target.ticksExisted % 3) * 2.0F - 3.0F;
    return currentYaw + offset;
  }

  private long getPing() {
    if (mc.getNetHandler() != null
        && mc.thePlayer != null
        && mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()) != null) {
      return mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
    }
    return 250;
  }

  public static class AttackData {
    private final EntityLivingBase entity;
    private final AxisAlignedBB box;
    private final double x;
    private final double y;
    private final double z;

    public AttackData(EntityLivingBase entityLivingBase) {
      this.entity = entityLivingBase;
      double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
      this.box =
          entityLivingBase
              .getEntityBoundingBox()
              .expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
      this.x = entityLivingBase.posX;
      this.y = entityLivingBase.posY;
      this.z = entityLivingBase.posZ;
    }

    public EntityLivingBase getEntity() {
      return this.entity;
    }

    public AxisAlignedBB getBox() {
      return this.box;
    }

    public double getX() {
      return this.x;
    }

    public double getY() {
      return this.y;
    }

    public double getZ() {
      return this.z;
    }
  }

  private final java.util.Map<Integer, LastAttackData> targetMap = new java.util.HashMap<>();

  private double getDamage(EntityLivingBase target) {
    float baseDamage = 1.0F;
    if (mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage) != null) {
      baseDamage =
          (float)
              mc.thePlayer
                  .getEntityAttribute(SharedMonsterAttributes.attackDamage)
                  .getAttributeValue();
    }
    float enchantmentBonus = 0.0F;
    if (mc.thePlayer.getHeldItem() != null) {
      enchantmentBonus =
          EnchantmentHelper.getModifierForCreature(
              mc.thePlayer.getHeldItem(),
              target != null ? target.getCreatureAttribute() : EnumCreatureAttribute.UNDEFINED);
    }
    boolean isCritical =
        mc.thePlayer.fallDistance > 0.0F
            && !mc.thePlayer.onGround
            && !mc.thePlayer.isOnLadder()
            && !mc.thePlayer.isInWater()
            && !mc.thePlayer.isPotionActive(Potion.blindness)
            && mc.thePlayer.ridingEntity == null;
    if (isCritical && baseDamage > 0.0F) {
      baseDamage *= 1.5F;
    }
    baseDamage += enchantmentBonus;
    return baseDamage;
  }

  public static class LastAttackData {
    private long time;
    private double damage;

    public LastAttackData(double damage) {
      this.time = System.currentTimeMillis();
      this.damage = damage;
    }

    public void reset(boolean reset, double damage) {
      if (reset) {
        this.time = System.currentTimeMillis();
      }
      this.damage = damage;
    }

    public long getTime() {
      return System.currentTimeMillis() - this.time;
    }

    public double getDamage() {
      return this.damage;
    }
  }
}
