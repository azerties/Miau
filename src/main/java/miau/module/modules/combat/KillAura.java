package miau.module.modules.combat;

import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import java.awt.AWTException;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventManager;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorMinecraft;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.module.modules.combat.killaura.autoblocks.*;
import miau.module.modules.movement.NoSlow;
import miau.module.modules.player.AutoBlockIn;
import miau.module.modules.player.AutoSoup;
import miau.module.modules.player.BedNuker;
import miau.module.modules.player.Scaffold;
import miau.module.modules.render.HUD;
import miau.property.properties.*;
import miau.util.math.*;
import miau.util.network.*;
import miau.util.player.ItemUtil;
import miau.util.player.PlayerUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import miau.util.render.ColorUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
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
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

public class KillAura extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode;
  public final ModeProperty sort;
  public ModeProperty autoBlock;
  public final BooleanProperty noSwap =
      new BooleanProperty("NoSwap", true, () -> this.autoBlock.getValue() == 6);
  public final IntProperty maxTick =
      new IntProperty("MaxTick", 3, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty startBlinkTick =
      new IntProperty("StartBlinkTick", 0, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty stopBlinkTick =
      new IntProperty("StopBlinkTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty swapTick =
      new IntProperty("SwapTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty switchBackTick =
      new IntProperty("SwitchBackTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty stopBlockTick =
      new IntProperty("StopBlockTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty attackTick =
      new IntProperty("AttackTick", 0, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final IntProperty startBlockTick =
      new IntProperty("StartBlockTick", 0, 1, 5, () -> this.autoBlock.getValue() == 6);
  public final BooleanProperty postStartBlock =
      new BooleanProperty("PostBlock", false, () -> this.autoBlock.getValue() == 6);
  public final BooleanProperty autoBlockRequirePress;
  public final IntProperty autoBlockCPS;
  public final FloatProperty autoBlockRange;

  public final FloatProperty swingRange;
  public final FloatProperty attackRange;
  public final IntProperty fov;
  public final IntProperty minCPS;
  public final IntProperty maxCPS;
  public final IntProperty switchDelay;
  public final ModeProperty rotations;
  public final ModeProperty moveFix;
  public final PercentProperty smoothing;
  public final IntProperty angleStep;
  public final BooleanProperty throughWalls;
  public final BooleanProperty rayCast;
  public final BooleanProperty requirePress;
  public final BooleanProperty allowMining;
  public final BooleanProperty weaponsOnly;
  public final BooleanProperty allowTools;
  public final BooleanProperty inventoryCheck;
  public final BooleanProperty lowTimerCheck;
  public final BooleanProperty botCheck;
  public final BooleanProperty targetPlayers;
  public final BooleanProperty targetBosses;
  public final BooleanProperty targetMobs;
  public final BooleanProperty targetAnimals;
  public final BooleanProperty targetGolems;
  public final BooleanProperty targetSilverfish;
  public final BooleanProperty targetTeams;
  public final BooleanProperty targetInvisibles;
  private final java.util.Map<Integer, LastAttackData> targetMap = new java.util.HashMap<>();
  public final ModeProperty showTarget;

  private final TimerUtil timer = new TimerUtil();
  private AttackData target = null;
  private int switchTick = 0;
  public boolean hitRegistered = false;
  public boolean blockingState = false;
  public boolean isBlocking = false;
  public boolean fakeBlockState = false;
  public long attackDelayMS = 0L;
  public int blockTick = 0;
  public boolean swapped = false;
  public boolean postBlock = false;
  public boolean postSwap = false;

  public boolean attackFlag = false;
  public boolean swapFlag = false;
  public boolean blockedFlag = false;

  public final java.util.List<AutoBlockMode> autoBlockModes = new java.util.ArrayList<>();

  public KillAura() {
    super("KillAura", false);
    this.mode = new ModeProperty("Mode", 0, new String[] {"Single", "Switch"});
    this.sort =
        new ModeProperty("Sort", 0, new String[] {"Distance", "Health", "Hurt Time", "FOV"});

    this.autoBlock =
        new ModeProperty(
            "AutoBlock",
            0,
            new String[] {"None", "Vanilla", "Hypixel", "Legit", "Fake", "Test", "Custom"});

    this.autoBlockModes.add(new NoneAutoBlock(this));
    this.autoBlockModes.add(new VanillaAutoBlock(this));
    this.autoBlockModes.add(new HypixelAutoBlock(this));
    this.autoBlockModes.add(new LegitAutoBlock(this));
    this.autoBlockModes.add(new FakeAutoBlock(this));
    this.autoBlockModes.add(new HypixelTestAutoBlock(this));
    this.autoBlockModes.add(new HypixelCustomAutoBlock(this));

    this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
    this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20);
    this.autoBlockRange = new FloatProperty("AutoBlock Range", 6.0F, 3.0F, 8.0F);
    this.swingRange = new FloatProperty("Swing Range", 3.5F, 3.0F, 6.0F);
    this.attackRange = new FloatProperty("Attack Range", 3.0F, 3.0F, 6.0F);
    this.fov = new IntProperty("Fov", 360, 30, 360);
    this.minCPS = new IntProperty("Min Aps", 14, 1, 20);
    this.maxCPS = new IntProperty("Max Aps", 14, 1, 20);
    this.switchDelay = new IntProperty("Switch Delay", 150, 0, 1000);
    this.rotations =
        new ModeProperty("Rotations", 1, new String[] {"None", "Legit/Normal", "Lock View"});
    this.moveFix = new ModeProperty("Move Fix", 1, new String[] {"None", "Silent", "Strict"});
    this.smoothing = new PercentProperty("Smoothing", 0);
    this.angleStep = new IntProperty("Angle Step", 90, 30, 180);
    this.throughWalls = new BooleanProperty("Through Walls", true);
    this.rayCast = new BooleanProperty("Ray Cast", false);
    this.requirePress = new BooleanProperty("Require Press", false);
    this.allowMining = new BooleanProperty("Allow Mining", false);
    this.weaponsOnly = new BooleanProperty("Weapons Only", false);
    this.allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
    this.inventoryCheck = new BooleanProperty("Inventory Check", true);
    this.lowTimerCheck = new BooleanProperty("Low Timer Check", true);
    this.botCheck = new BooleanProperty("Bot Check", true);
    this.targetPlayers = new BooleanProperty("Players", true);
    this.targetBosses = new BooleanProperty("Bosses", false);
    this.targetMobs = new BooleanProperty("Mobs", false);
    this.targetAnimals = new BooleanProperty("Animals", false);
    this.targetGolems = new BooleanProperty("Golems", false);
    this.targetSilverfish = new BooleanProperty("Silverfish", false);
    this.targetTeams = new BooleanProperty("Teams", true);
    this.targetInvisibles = new BooleanProperty("Invisibles", false);
    this.showTarget =
        new ModeProperty("Show Target", 0, new String[] {"None", "Default", "Hud", "SigmaRing"});
  }

  private long getAttackDelay() {
    return this.isBlocking
        ? (long) (1000.0F / this.autoBlockCPS.getValue())
        : 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
  }

  private boolean performAttack(float yaw, float pitch) {
    if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
      if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
        return false;
      } else if (this.attackDelayMS > 0L) {
        return false;
      } else {
        this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
        mc.thePlayer.swingItem();

        net.minecraft.util.MovingObjectPosition rayCastPos = null;
        boolean rayCastHit = false;

        boolean useRaycast = this.rayCast.getValue();

        if (this.rotations.getValue() != 0) {
          rayCastHit = true;
        } else if (useRaycast) {
          if (this.throughWalls.getValue()) {
            rayCastPos =
                miau.util.player.RayCastUtil.getEntityIntercept(
                    this.target.getEntity(), yaw, pitch, this.attackRange.getValue());
          } else {
            rayCastPos =
                miau.util.player.RayCastUtil.rayCast(yaw, pitch, this.attackRange.getValue());
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

  public void sendUseItem() {
    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    this.startBlock(mc.thePlayer.getHeldItem());
  }

  public void startBlock(ItemStack itemStack) {
    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
    mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
    this.blockingState = true;
  }

  public void stopBlock() {
    PacketUtil.sendPacket(
        new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    mc.thePlayer.stopUsingItem();
    this.blockingState = false;
  }

  private void interactAttack(float yaw, float pitch) {
    if (this.target != null) {
      net.minecraft.util.Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
      net.minecraft.util.Vec3 lookVec =
          miau.util.player.RayCastUtil.getVectorForRotation(pitch, yaw);
      net.minecraft.util.Vec3 targetPos =
          eyePos.addVector(lookVec.xCoord * 8.0, lookVec.yCoord * 8.0, lookVec.zCoord * 8.0);
      net.minecraft.util.MovingObjectPosition mop =
          this.target.getBox().calculateIntercept(eyePos, targetPos);
      if (mop != null) {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        PacketUtil.sendPacket(
            new C02PacketUseEntity(
                this.target.getEntity(),
                new Vec3(
                    mop.hitVec.xCoord - this.target.getX(),
                    mop.hitVec.yCoord - this.target.getY(),
                    mop.hitVec.zCoord - this.target.getZ())));
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        mc.thePlayer.setItemInUse(
            mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        this.blockingState = true;
      }
    }
  }

  private boolean canAttack() {
    if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
      return false;
    }
    if (((IAccessorMinecraft) mc).getTimer().timerSpeed < 1F && lowTimerCheck.getValue()) {
      return false;
    } else if (!(Boolean) this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
      if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
        return false;
      } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
        return false;
      } else {
        AutoSoup autoSoup = (AutoSoup) Miau.moduleManager.modules.get(AutoSoup.class);
        if (autoSoup.isEnabled() && autoSoup.isHealing()) {
          return false;
        } else {
          BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
          AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
          if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
          } else if (Miau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
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

  public boolean hasValidTarget() {
    return mc.theWorld.loadedEntityList.stream()
        .anyMatch(
            entity ->
                entity instanceof EntityLivingBase
                    && this.isValidTarget((EntityLivingBase) entity)
                    && this.isInBlockRange((EntityLivingBase) entity));
  }

  private boolean isValidTarget(EntityLivingBase entityLivingBase) {
    if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
      return false;
    } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
      if (entityLivingBase == mc.getRenderViewEntity()
          || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
        return false;
      } else if (entityLivingBase.deathTime > 0) {
        return false;
      } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
        return false;
      } else if (!this.throughWalls.getValue() && !mc.thePlayer.canEntityBeSeen(entityLivingBase)) {
        return false;
      } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
        if (!this.targetPlayers.getValue()) {
          return false;
        } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
          return false;
        } else {
          return (!this.targetTeams.getValue()
              || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase));
        }
      } else if (entityLivingBase instanceof EntityDragon
          || entityLivingBase instanceof EntityWither) {
        return this.targetBosses.getValue();
      } else if (!(entityLivingBase instanceof EntityMob)
          && !(entityLivingBase instanceof EntitySlime)) {
        if (entityLivingBase instanceof EntityAnimal
            || entityLivingBase instanceof EntityBat
            || entityLivingBase instanceof EntitySquid
            || entityLivingBase instanceof EntityVillager) {
          return this.targetAnimals.getValue();
        } else if (!(entityLivingBase instanceof EntityIronGolem)) {
          return false;
        } else {
          return this.targetGolems.getValue()
              && (!this.targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
        }
      } else if (!(entityLivingBase instanceof EntitySilverfish)) {
        return this.targetMobs.getValue();
      } else {
        return this.targetSilverfish.getValue()
            && (!this.targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
      }
    } else {
      return false;
    }
  }

  private boolean isInRange(EntityLivingBase entityLivingBase) {
    return this.isInBlockRange(entityLivingBase)
        || this.isInSwingRange(entityLivingBase)
        || this.isInAttackRange(entityLivingBase);
  }

  private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase)
        <= (double) this.autoBlockRange.getValue();
  }

  private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
  }

  public boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
    return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
  }

  private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
    return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
  }

  public boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
    return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
  }

  private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
    return entityLivingBase instanceof EntityPlayer
        && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
  }

  public EntityLivingBase getTarget() {
    return this.target != null ? this.target.getEntity() : null;
  }

  public boolean isAttackAllowed() {
    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    if (scaffold.isEnabled()) {
      return false;
    } else if (!this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
      return !this.requirePress.getValue() || mc.gameSettings.keyBindAttack.isKeyDown();
    } else {
      return false;
    }
  }

  public boolean shouldAutoBlock() {
    if (this.isPlayerBlocking() && this.isBlocking) {
      return !mc.thePlayer.isInWater()
          && !mc.thePlayer.isInLava()
          && (this.autoBlock.getValue() == 2
              || this.autoBlock.getValue() == 3
              || this.autoBlock.getValue() == 5
              || this.autoBlock.getValue() == 6);
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

  @EventTarget(Priority.LOW)
  public void onUpdate(UpdateEvent event) throws AWTException {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (this.attackDelayMS > 0L) {
        this.attackDelayMS -= 50L;
      }
      boolean attack = this.target != null && this.canAttack();
      boolean block = attack && this.canAutoBlock();
      if (!block) {
        Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.blockTick = 0;
      }
      if (attack) {
        this.attackFlag = true;
        this.swapFlag = false;
        this.blockedFlag = false;

        if (block) {
          AutoBlockMode activeMode = this.autoBlockModes.get(this.autoBlock.getValue());
          if (activeMode != null) {
            activeMode.processBlock(this.attackFlag, block);
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
              case 1:
                {
                  if (rotSpeed != 0) {
                    rotations =
                        RotationUtil.smooth(
                            lastRots,
                            targetRots,
                            rotSpeed,
                            this.target.getEntity(),
                            this.attackRange.getValue());
                  } else {
                    rotations = lastRots;
                  }
                }
                break;
              case 2: // Lock View
                rotations =
                    RotationUtil.getRotationsToBox(
                        this.target.getBox(),
                        event.getYaw(),
                        event.getPitch(),
                        (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                        (float) this.smoothing.getValue() / 100.0F);
                Miau.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                event.setPervRotation(rotations[0], 1);
                break;
              case 3:
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
              case 4:
                {
                  float[] lockViewRots =
                      RotationUtil.getRotationsToBox(
                          this.target.getBox(),
                          event.getYaw(),
                          event.getPitch(),
                          (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                          (float) this.smoothing.getValue() / 100.0F);
                  if (lockViewRots != null) {
                    rotations = new float[] {lockViewRots[0], lockViewRots[1]};
                    mc.thePlayer.rotationYaw = rotations[0];
                    mc.thePlayer.rotationPitch = rotations[1];
                    mc.thePlayer.rotationYawHead = rotations[0];
                    mc.thePlayer.renderYawOffset = rotations[0];
                    event.setPervRotation(rotations[0], 1);
                  }
                }
                break;
            }

            float[] quantized =
                RotationUtil.flexRotation(rotations[0], rotations[1], lastRots[0], lastRots[1]);
            event.setRotation(quantized[0], quantized[1], 1);
          }
          if (this.attackFlag) {
            attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
          }
        }
        if (this.swapFlag) {
          if (attacked) {
            this.interactAttack(event.getNewYaw(), event.getNewPitch());
          } else {
            if (!postBlock) this.sendUseItem();
          }
        }
        if (this.blockedFlag) {
          Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
          Miau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        }
      }
    }
    if (event.getType() == EventType.POST && this.isEnabled()) {
      if (postSwap) {
        int randomSlot = new Random().nextInt(9);
        while (randomSlot == mc.thePlayer.inventory.currentItem) {
          randomSlot = new Random().nextInt(9);
        }
        PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
        mc.getNetHandler()
            .addToSendQueue(
                new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        this.stopBlock();
        postSwap = false;
      }
      if (postBlock) {
        sendUseItem();
        postBlock = false;
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
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onPacket(PacketEvent event) {
    if (this.isEnabled() && !event.isCancelled()) {
      if (event.getPacket() instanceof C07PacketPlayerDigging) {
        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
        if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
          this.blockingState = false;
        }
      }
      if (event.getPacket() instanceof C09PacketHeldItemChange) {
        this.blockingState = false;
        if (this.isBlocking) {
          mc.thePlayer.stopUsingItem();
        }
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      if (this.moveFix.getValue() == 1
          && this.rotations.getValue() != 2
          && RotationState.isActived()) {
        event.setYaw(RotationState.getRotationYawHead());
      } else if (this.moveFix.getValue() == 2
          && this.rotations.getValue() != 2
          && RotationState.isActived()) {
        event.setYaw(RotationState.getRotationYawHead());
      }
    }
  }

  @EventTarget
  public void onRender(Render3DEvent event) {
    if (this.isEnabled() && target != null) {
      if (this.showTarget.getValue() != 0
          && TeamUtil.isEntityLoaded(this.target.getEntity())
          && this.isAttackAllowed()
          && this.showTarget.getValue() != 3) {
        Color color = new Color(-1);
        switch (this.showTarget.getValue()) {
          case 1:
            if (this.target.getEntity().hurtTime > 0) {
              color = new Color(16733525);
            } else {
              color = new Color(5635925);
            }
            break;
          case 2:
            color =
                ((HUD) Miau.moduleManager.modules.get(HUD.class))
                    .getColor(System.currentTimeMillis());
        }
        miau.util.render.RenderUtil.enableRenderState();
        miau.util.render.RenderUtil.drawEntityBox(
            this.target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
        miau.util.render.RenderUtil.disableRenderState();
      }
      if (this.showTarget.getValue() == 3) {
        renderScan(event);
      }
    }
  }

  public static Vec3 interpolate(Vec3 previousVec, Vec3 currentVec, float progress) {
    return new Vec3(
        previousVec.xCoord + (currentVec.xCoord - previousVec.xCoord) * progress,
        previousVec.yCoord + (currentVec.yCoord - previousVec.yCoord) * progress,
        previousVec.zCoord + (currentVec.zCoord - previousVec.zCoord) * progress);
  }

  private void renderScan(Render3DEvent event) {
    if (target == null || target.getEntity() == null) return;
    final float partialTicks = event.getPartialTicks();
    EntityLivingBase player = this.target.getEntity();

    if (mc.getRenderManager() == null) return;

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

    final Color color =
        ((HUD) Miau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
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

    for (float i = 0; i <= Math.PI * 2 + ((Math.PI * 2) / 25); i += (float) ((Math.PI * 2) / 25)) {
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
  }

  @Override
  public void onDisabled() {
    Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    if ((this.autoBlock.getValue() == 2
            || this.autoBlock.getValue() == 5
            || this.autoBlock.getValue() == 6)
        && Miau.moduleManager.getModule(NoSlow.class).isEnabled()) {
      int randomSlot = new Random().nextInt(9);
      while (randomSlot == mc.thePlayer.inventory.currentItem) {
        randomSlot = new Random().nextInt(9);
      }
      PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
      PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
      this.stopBlock();
    }
    this.blockingState = false;
    this.isBlocking = false;
    this.fakeBlockState = false;
  }

  @Override
  public void verifyValue(String mode) {
    if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
      if (this.swingRange.getName().equals(mode)) {
        if (this.swingRange.getValue() < this.attackRange.getValue()) {
          this.attackRange.setValue(this.swingRange.getValue());
        }
      } else if (this.attackRange.getName().equals(mode)) {
        if (this.swingRange.getValue() < this.attackRange.getValue()) {
          this.swingRange.setValue(this.attackRange.getValue());
        }
      } else if (this.minCPS.getName().equals(mode)) {
        if (this.minCPS.getValue() > this.maxCPS.getValue()) {
          this.maxCPS.setValue(this.minCPS.getValue());
        }
      } else {
        if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
          this.minCPS.setValue(this.maxCPS.getValue());
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
