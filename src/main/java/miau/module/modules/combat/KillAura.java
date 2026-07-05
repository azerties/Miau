package miau.module.modules.combat;

import com.google.common.base.CaseFormat;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import miau.Miau;
import miau.component.PingSpoofComponent;
import miau.enums.BlinkModules;
import miau.event.*;
import miau.event.EventManager;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.mixin.IAccessorRenderManager;
import miau.module.Module;
import miau.module.modules.combat.killaura.autoblocks.*;
import miau.module.modules.misc.AntiBot;
import miau.module.modules.movement.NoSlow;
import miau.module.modules.player.AutoBlockIn;
import miau.module.modules.player.AutoHead;
import miau.module.modules.player.BedNuker;
import miau.module.modules.player.Scaffold;
import miau.module.modules.render.HUD;
import miau.property.properties.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
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
  public int switchTick = 0;
  public boolean hitRegistered = false;
  public boolean blockingState = false;
  public boolean isBlocking = false;
  public boolean fakeBlockState = false;
  public boolean blinkReset = false;
  public boolean rightHoldActive = false;
  public long attackDelayMS = 0L;
  public int blockTick = 0;
  public boolean cancelAttack = false;
  private int lastTickProcessed;
  public int ticksSinceVelocity = 0;
  private double expandRange = 0.0;
  public final ModeProperty mode;
  public final IntProperty switchDelay;
  public final ModeProperty autoBlock;
  public final java.util.List<miau.module.modules.combat.killaura.autoblocks.AutoBlockMode>
      autoBlockModes = new java.util.ArrayList<>();
  public final BooleanProperty autoBlockRequirePress;
  public final BooleanProperty preventServersideBlocking;
  public final ModeProperty sort;
  public final FloatProperty attackRange;
  public final FloatProperty swingRange;
  public final FloatProperty cps;
  public final FloatProperty autoBlockCps;
  public final ModeProperty rotations;
  public final PercentProperty smoothing;
  public final IntProperty angleStep;
  public final ModeProperty moveFix;
  public final BooleanProperty keepSprint;
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

  public int keepSprintBlinkTicks = 0;
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
  public final BooleanProperty antiSpawn = new BooleanProperty("anti-spawn", false);
  public final IntProperty spawnRadius = new IntProperty("spawn-radius", 25, 5, 100);

  private long getAttackDelay() {
    float min = this.cps.getValue();
    float max = this.cps.getSecondValue();
    if (this.isBlocking) {
      min = this.autoBlockCps.getValue();
      max = this.autoBlockCps.getSecondValue();
    }
    return 1000L / miau.util.math.RandomUtil.nextLong((int) min, (int) max);
  }

  private boolean performAttack(float yaw, float pitch) {
    if (this.badPacketsCheck.getValue()
        && miau.component.BadPacketsComponent.bad(false, false, false, true, true)) {
      return false;
    }
    if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
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

        if (this.keepSprint.getValue()) {
          miau.module.modules.movement.KeepSprint ks =
              (miau.module.modules.movement.KeepSprint)
                  Miau.moduleManager.modules.get(miau.module.modules.movement.KeepSprint.class);
          boolean ksWillPreserveSprint =
              ks != null && ks.isEnabled() && ks.shouldKeepSprint() && mc.thePlayer.isSprinting();

          if (ksWillPreserveSprint && !PingSpoofComponent.isOwnedBy("KillAuraKeepSprint")) {
            PingSpoofComponent.beginSession(
                "KillAuraKeepSprint", 40, false, false, false, false, false, true);
          }

          mc.thePlayer.swingItem();

          ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
          PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));

          if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            if (mc.thePlayer.fallDistance > 0.0F
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isPotionActive(Potion.blindness)
                && mc.thePlayer.ridingEntity == null) {
              mc.thePlayer.onCriticalHit(this.target.getEntity());
            }
          }

          LastAttackData lastAttack = this.targetMap.get(this.target.getEntity().getEntityId());
          if (lastAttack == null) {
            this.targetMap.put(
                this.target.getEntity().getEntityId(),
                new LastAttackData(this.getDamage(this.target.getEntity())));
          } else {
            lastAttack.reset(true, this.getDamage(this.target.getEntity()));
          }

          this.keepSprintBlinkTicks = ksWillPreserveSprint ? 1 : 0;
          this.hitRegistered = true;
          return true;
        }

        mc.thePlayer.swingItem();

        net.minecraft.util.MovingObjectPosition rayCastPos = null;
        boolean rayCastHit = false;

        boolean useRaycast = this.rayCast.getValue();

        if (this.rotations.getValue() != 0) {
          rayCastHit = true;
        } else if (useRaycast) {
          if (this.throughWalls.getValue()) {
            // Rise getEntityIntercept: ignores blocks, uses proper collision border size
            rayCastPos =
                miau.util.player.RayCastUtil.getEntityIntercept(
                    this.target.getEntity(), yaw, pitch, this.attackRange.getValue());
          } else {
            // Rise full raycast: checks blocks then entities
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

  public void setRightHold(boolean pressed) {
    int useKey = mc.gameSettings.keyBindUseItem.getKeyCode();
    if (pressed) {
      KeyBindUtil.setKeyBindState(useKey, true);
      this.rightHoldActive = true;
    } else if (this.rightHoldActive) {
      KeyBindUtil.updateKeyState(useKey);
      this.rightHoldActive = false;
    }
  }

  private void interactAttack(float yaw, float pitch) {
    this.interactAttack(yaw, pitch, true);
  }

  private void interactAttack(float yaw, float pitch, boolean sendInteractAt) {
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
        AutoHead autoHead = (AutoHead) Miau.moduleManager.modules.get(AutoHead.class);
        if (autoHead.isEnabled() && autoHead.isHealing()) {
          return false;
        } else {
          BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
          AutoBlockIn autoBlockIn = (AutoBlockIn) Miau.moduleManager.modules.get(AutoBlockIn.class);
          if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
          } else if (!this.whileScaffold.getValue()
              && Miau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
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
                    && this.isInRange((EntityLivingBase) entity));
  }

  private boolean isValidTarget(EntityLivingBase entityLivingBase) {
    return this.isValid(entityLivingBase)
        && (this.rotations.getValue() != 0
            || RotationUtil.angleToEntity(entityLivingBase) <= this.fov.getValue().floatValue())
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

  // --- Target Validation (inlined from Targets.java) -------------

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
    if (this.antiSpawn.getValue() && mc.theWorld != null && mc.theWorld.getSpawnPoint() != null) {
      BlockPos spawn = mc.theWorld.getSpawnPoint();
      double dist =
          player.getDistance((double) spawn.getX(), (double) spawn.getY(), (double) spawn.getZ());
      if (dist <= this.spawnRadius.getValue()) {
        return false;
      }
    }
    return this.allowSameTeam(player) && (isInvisible || !AntiBot.isBot(player));
  }

  private boolean allowTeamColor(EntityLivingBase entityLivingBase) {
    return this.targetTeams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase);
  }

  private boolean allowSameTeam(EntityPlayer player) {
    return this.targetTeams.getValue() || !TeamUtil.isSameTeam(player);
  }

  public int findEmptySlot(int currentSlot) {
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

  public int findSwordSlot(int currentSlot) {
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
    this.autoBlockModes.add(new NoneAutoBlock(this));
    this.autoBlockModes.add(new VanillaAutoBlock(this));
    this.autoBlockModes.add(new SpoofAutoBlock(this));
    this.autoBlockModes.add(new HypixelAutoBlock(this));
    this.autoBlockModes.add(new BlinkAutoBlock(this));
    this.autoBlockModes.add(new InteractAutoBlock(this));
    this.autoBlockModes.add(new LegitAutoBlock(this));
    this.autoBlockModes.add(new FakeAutoBlock(this));
    this.autoBlockModes.add(new TestAutoBlock(this));

    this.lastTickProcessed = 0;
    this.mode = new ModeProperty("Mode", 0, new String[] {"SINGLE", "SWITCH"});
    this.switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
    this.autoBlockModes.add(new miau.module.modules.combat.killaura.autoblocks.NoneAutoBlock(this));
    this.autoBlockModes.add(
        new miau.module.modules.combat.killaura.autoblocks.VanillaAutoBlock(this));
    this.autoBlockModes.add(
        new miau.module.modules.combat.killaura.autoblocks.SpoofAutoBlock(this));
    this.autoBlockModes.add(
        new miau.module.modules.combat.killaura.autoblocks.HypixelAutoBlock(this));
    this.autoBlockModes.add(
        new miau.module.modules.combat.killaura.autoblocks.BlinkAutoBlock(this));
    this.autoBlockModes.add(
        new miau.module.modules.combat.killaura.autoblocks.InteractAutoBlock(this));
    this.autoBlockModes.add(
        new miau.module.modules.combat.killaura.autoblocks.LegitAutoBlock(this));
    this.autoBlockModes.add(new miau.module.modules.combat.killaura.autoblocks.FakeAutoBlock(this));
    this.autoBlockModes.add(new miau.module.modules.combat.killaura.autoblocks.TestAutoBlock(this));

    String[] autoBlockNames =
        this.autoBlockModes.stream()
            .map(miau.module.modules.combat.killaura.autoblocks.AutoBlockMode::getName)
            .toArray(String[]::new);
    this.sort =
        new ModeProperty(
            "sort", 0, new String[] {"DISTANCE", "HEALTH", "HURT-TIME", "FOV", "ARMOR"});
    this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
    this.swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
    this.cps = new FloatProperty("aps", 14.0F, 14.0F, 1.0F, 20.0F);
    this.autoBlock =
        new ModeProperty(
            "auto-block",
            0,
            new String[] {
              "NONE",
              "VANILLA",
              "SPOOF",
              "HYPIXEL",
              "BLINK",
              "INTERACT",
              "LEGIT",
              "FAKE",
              "GRIM",
              "TEST"
            });
    this.autoBlockRequirePress = new BooleanProperty("autoblock-require-press", false);
    this.preventServersideBlocking = new BooleanProperty("prevent-serverside-blocking", false);
    this.autoBlockCps = new FloatProperty("autoblock-aps", 8.0F, 10.0F, 1.0F, 10.0F);
    this.rotations =
        new ModeProperty(
            "rotations", 1, new String[] {"NONE", "LEGIT/NORMAL", "SNAP", "NCP", "LOCK_VIEW"});
    this.smoothing = new PercentProperty("smoothing", 0);
    this.angleStep = new IntProperty("angle-step", 90, 30, 180);
    this.moveFix =
        new ModeProperty(
            "move-fix",
            0,
            new String[] {"OFF", "Normal", "Traditional", "Backwards_Sprint", "Silent"});
    this.keepSprint = new BooleanProperty("keep-sprint", false);
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
  }

  public EntityLivingBase getTarget() {
    return this.target != null ? this.target.getEntity() : null;
  }

  private boolean shouldDelayHit() {
    return false;
  }

  public boolean isAttackAllowed() {
    Scaffold scaffold = (Scaffold) Miau.moduleManager.modules.get(Scaffold.class);
    if (!this.whileScaffold.getValue() && scaffold.isEnabled()) {
      return false;
    } else if (!this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
      return !this.requirePress.getValue()
          || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
    } else if (this.shouldDelayHit()) {
      return false;
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

  public boolean isNoSlowAntiSwitchActive() {
    NoSlow noSlow = (NoSlow) Miau.moduleManager.modules.get(NoSlow.class);
    return noSlow.isEnabled() && noSlow.mode.getValue() == 3 && this.isPlayerBlocking();
  }

  @EventTarget(Priority.LOW)
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.POST && this.blinkReset) {
      this.blinkReset = false;
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      Miau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
    }
    if (event.getType() == EventType.POST) {
      if (this.keepSprintBlinkTicks > 0) {
        this.keepSprintBlinkTicks--;
        if (this.keepSprintBlinkTicks <= 0 && PingSpoofComponent.isOwnedBy("KillAuraKeepSprint")) {
          // Idle timeout: no attacks for a while, release session
          PingSpoofComponent.finishSession("KillAuraKeepSprint", true);
        }
      }
    }
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      this.ticksSinceVelocity++;
      if (mc.thePlayer.ticksExisted % 20 == 0) {
        this.expandRange = 3.0 + Math.random() * 0.5;
      }
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
        boolean swap = false;
        boolean blocked = false;
        if (block) {
          this.cancelAttack = false;
          AutoBlockMode mode = this.autoBlockModes.get(this.autoBlock.getValue());
          if (mode != null) swap = mode.processBlock(attack, block);
          if (this.cancelAttack) attack = false;
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
              case 4: // LOCK_VIEW (OpenMyau port)
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
                    // Directly apply rotation to player's head/body like OpenMyau
                    mc.thePlayer.rotationYaw = rotations[0];
                    mc.thePlayer.rotationPitch = rotations[1];
                    mc.thePlayer.rotationYawHead = rotations[0];
                    mc.thePlayer.renderYawOffset = rotations[0];
                    // Set prev rotation for move fix
                    event.setPervRotation(rotations[0], 1);
                  }
                }
                break;
            }

            float[] quantized =
                RotationUtil.flexRotation(rotations[0], rotations[1], lastRots[0], lastRots[1]);
            event.setRotation(quantized[0], quantized[1], 1);
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
          Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
          Miau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
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
      if (this.debugLog.getValue() == 1 && mc.thePlayer != null) {
        if (event.getPacket() instanceof S06PacketUpdateHealth) {
          float packet =
              ((S06PacketUpdateHealth) event.getPacket()).getHealth() - mc.thePlayer.getHealth();
          if (packet != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) {
            this.lastTickProcessed = mc.thePlayer.ticksExisted;
            ChatUtil.sendFormatted(
                String.format(
                    "%sHealth: %s&l%s&r (&otick: %d&r)&r",
                    Miau.clientName,
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
                          Miau.clientName,
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
      if (this.moveFix.getValue() == 4
          && this.rotations.getValue() != 4
          && RotationState.isActived()
          && MoveUtil.isForwardPressed()) {
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
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
              ((HUD) Miau.moduleManager.modules.get(HUD.class))
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
                  : ((HUD) Miau.moduleManager.modules.get(HUD.class))
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
                  : ((HUD) Miau.moduleManager.modules.get(HUD.class))
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

  @EventTarget
  public void onPostRaytrace(PostRaytraceEvent event) {
    if (!this.shouldOverrideMouseOver()) return;
    this.modifyMouseOverFromGetMouseOver(event.partialTicks);
  }

  public boolean shouldOverrideMouseOver() {
    if (!this.isEnabled()) return false;
    if (mc.thePlayer == null || mc.theWorld == null) return false;
    if (this.target == null || this.target.getEntity() == null) return false;
    if (this.target.getEntity().isDead) return false;
    double dist = RotationUtil.distanceToEntity(this.target.getEntity());
    if (dist > this.swingRange.getValue()) return false;
    if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) return false;
    return true;
  }

  public void modifyMouseOverFromGetMouseOver(float partialTicks) {
    if (this.target == null || this.target.getEntity() == null) return;

    Entity targetEntity = this.target.getEntity();
    Entity viewEntity = mc.getRenderViewEntity();
    if (viewEntity == null) return;

    Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
    Vec3 look = viewEntity.getLook(partialTicks);
    double reach = this.attackRange.getValue();
    Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

    float border = targetEntity.getCollisionBorderSize();
    AxisAlignedBB bb = targetEntity.getEntityBoundingBox().expand(border, border, border);
    MovingObjectPosition intercept = bb.calculateIntercept(eyes, rayEnd);
    boolean inside = bb.isVecInside(eyes);
    if (!inside && intercept == null) return;

    Vec3 hitVec = inside ? (intercept == null ? eyes : intercept.hitVec) : intercept.hitVec;

    if (!this.throughWalls.getValue()) {
      MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, hitVec, false, false, true);
      if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        return;
      }
    }

    mc.objectMouseOver = new MovingObjectPosition(targetEntity, hitVec);
    mc.pointedEntity = targetEntity;
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
    this.targetMap.clear();
    Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    this.blockingState = false;
    this.isBlocking = false;
    this.fakeBlockState = false;

    // Clean up keepSprint blink state – only if we own the session
    if (PingSpoofComponent.isOwnedBy("KillAuraKeepSprint")) {
      // Flush remaining queued packets so nothing is lost on disable
      PingSpoofComponent.finishSession("KillAuraKeepSprint", true);
    }
    this.keepSprintBlinkTicks = 0;
  }

  @Override
  public void verifyValue(String value) {
    boolean badCps =
        this.autoBlock.getValue() == 2
            || this.autoBlock.getValue() == 3
            || this.autoBlock.getValue() == 4
            || this.autoBlock.getValue() == 5
            || this.autoBlock.getValue() == 6;
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
