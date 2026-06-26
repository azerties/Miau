package myau.module.modules.network;

import java.util.List;
import java.util.stream.Collectors;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.impl.Render3DEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.module.modules.player.BedNuker;
import myau.property.properties.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.player.ItemUtil;
import myau.util.player.RotationUtil;
import myau.util.player.TeamUtil;
import myau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class LagRange extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final double MINIMUM_DISTANCE = 3.0;

  // === Existing settings ===
  public final IntProperty delay = new IntProperty("delay", 150, 0, 1000);
  public final FloatProperty range = new FloatProperty("range", 10.0F, 3.0F, 100.0F);
  public final BooleanProperty aggressive = new BooleanProperty("aggressive", false);
  public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
  public final BooleanProperty allowTools =
      new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
  public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
  public final BooleanProperty teams = new BooleanProperty("teams", true);
  public final ModeProperty showPosition =
      new ModeProperty("show-position", 0, new String[] {"NONE", "DEFAULT", "HUD"});

  public final BooleanProperty sprintReset = new BooleanProperty("sprint-reset", true);
  public final BooleanProperty blockSword = new BooleanProperty("block-sword", true);
  public final BooleanProperty splashPotion = new BooleanProperty("splash-potion", true);

  public final BooleanProperty realPosIndicator = new BooleanProperty("real-pos-indicator", true);
  public final BooleanProperty showFirstPerson = new BooleanProperty("show-first-person", false);
  public final FloatProperty indicatorLineWidth =
      new FloatProperty("indicator-line-width", 2.0F, 0.5F, 5.0F);
  public final BooleanProperty indicatorFilled = new BooleanProperty("indicator-filled", true);
  private int tickIndex = -1;
  private long delayCounter = 0L;
  private boolean hasTarget = false;
  private Vec3 lastPosition = null;
  private Vec3 currentPosition = null;
  private boolean isLagging = false;
  private int lastSelfHurtTime = 0;
  private int lastTargetHurtTime = 0;
  private int hitMarkedEntityId = -1;
  private boolean lastSprintState = false;
  private boolean lastBlockingState = false;
  private double lastDistSq = -1;
  private EntityPlayer currentTarget = null;

  private long lagStartTime = 0L;

  private final java.util.Set<Packet<?>> packetFastTrack =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Packet<?>, Boolean>());

  private Vec3 indicatorFrom = null;
  private Vec3 indicatorTo = null;
  private long indicatorStartMs = 0L;

  private boolean isValidTarget(EntityPlayer entityPlayer) {
    if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
      if (entityPlayer == mc.getRenderViewEntity()
          || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
        return false;
      } else if (entityPlayer.deathTime > 0) {
        return false;
      } else if (TeamUtil.isFriend(entityPlayer)) {
        return false;
      } else {
        return (!this.teams.getValue() || !TeamUtil.isSameTeam(entityPlayer))
            && (!this.botCheck.getValue() || !TeamUtil.isBot(entityPlayer));
      }
    } else {
      return false;
    }
  }

  /**
   * Mouse-over priority targeting (ported from raven-bS's CombatTargeting). Returns the player the
   * user is currently looking at, if valid and in range.
   */
  private EntityPlayer getMouseOverTarget(double rangeSq) {
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit
            == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
        && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) mc.objectMouseOver.entityHit;
      if (isValidTarget(player)) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double distSq = RotationUtil.distanceToBox(player, eyePos);
        if (distSq <= rangeSq) {
          return player;
        }
      }
    }
    return null;
  }

  // === Packet flush check ===
  private boolean shouldResetOnPacket(Packet<?> packet) {
    if (packet instanceof C02PacketUseEntity) {
      return true;
    } else if (packet instanceof C07PacketPlayerDigging) {
      return ((C07PacketPlayerDigging) packet).getStatus() != Action.RELEASE_USE_ITEM;
    } else if (packet instanceof C08PacketPlayerBlockPlacement) {
      ItemStack item = ((C08PacketPlayerBlockPlacement) packet).getStack();
      return item == null || !(item.getItem() instanceof ItemSword);
    } else {
      return false;
    }
  }

  public LagRange() {
    super("LagRange", false);
  }

  // === Core lag management (ported from raven-bS) ===

  private void startLag() {
    if (!isLagging) {
      this.isLagging = true;
      this.lagStartTime = System.currentTimeMillis();
      // Clear any stale fast-track reference from LagManager (fresh cycle)
      Myau.lagManager.fastTrackSet = null;
    }
    // Use ms-precision delay directly (raven-bS style)
    Myau.lagManager.setDelayMs(this.delay.getValue());
  }

  private void flushLag() {
    if (!isLagging) return;
    // FastTrack: supply our set to the LagManager so packets that were just flushed
    // bypass the queue on re-entry (prevents re-queuing loop).
    // The set persists in LagManager until consumed by handlePacket().
    Myau.lagManager.fastTrackSet = this.packetFastTrack;
    Myau.lagManager.packetQueue.forEach(lagPacket -> packetFastTrack.add(lagPacket.packet));
    Myau.lagManager.resetDelay();
    this.tickIndex = -1;
    this.delayCounter = 0L;
    this.isLagging = false;
    this.lagStartTime = 0L;
    clearIndicator();
  }

  private boolean sameTarget(EntityPlayer nextTarget) {
    if (currentTarget == null || nextTarget == null) {
      return currentTarget == nextTarget;
    }
    return currentTarget.getEntityId() == nextTarget.getEntityId();
  }

  private boolean isMoving() {
    return mc.thePlayer.moveForward != 0.0f || mc.thePlayer.moveStrafing != 0.0f;
  }

  private void clearIndicator() {
    indicatorFrom = null;
    indicatorTo = null;
    indicatorStartMs = 0L;
  }

  private void resetState() {
    currentTarget = null;
    lastDistSq = -1;
    isLagging = false;
    lastSelfHurtTime = 0;
    lastTargetHurtTime = 0;
    hitMarkedEntityId = -1;
    lastSprintState = false;
    lastBlockingState = false;
    tickIndex = -1;
    delayCounter = 0L;
    hasTarget = false;
    lastPosition = null;
    currentPosition = null;
    lagStartTime = 0L;
    packetFastTrack.clear();
    if (Myau.lagManager.fastTrackSet == this.packetFastTrack) {
      Myau.lagManager.fastTrackSet = null;
    }
    clearIndicator();
  }

  // === Events ===

  @EventTarget(Priority.LOW)
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      switch (event.getType()) {
        case PRE:
          onPreTick();
          break;
        case POST:
          onPostTick();
          break;
        default:
          break;
      }
    }
  }

  private void onPreTick() {
    Myau.lagManager.resetDelay();
    this.hasTarget = false;

    // --- Safety checks ---
    BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
    if ((bedNuker.isEnabled() && bedNuker.isReady())
        || ((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()
        || (mc.thePlayer.isUsingItem() && !mc.thePlayer.isBlocking())) {
      if (isLagging) flushLag();
      return;
    }

    // Weapon / tool check
    boolean weaponOk =
        !this.weaponsOnly.getValue()
            || ItemUtil.hasRawUnbreakingEnchant()
            || this.allowTools.getValue() && ItemUtil.isHoldingTool();
    if (!weaponOk) {
      if (isLagging) flushLag();
      return;
    }

    // --- Find valid targets ---
    List<EntityPlayer> players =
        mc.theWorld.loadedEntityList.stream()
            .filter(entity -> entity instanceof EntityPlayer)
            .map(entity -> (EntityPlayer) entity)
            .filter(this::isValidTarget)
            .collect(Collectors.toList());

    if (players.isEmpty()) {
      if (isLagging) flushLag();
      this.currentTarget = null;
      this.lastDistSq = -1;
      return;
    }

    // --- Pick target with mouse-over priority (ported from raven-bS) ---
    double rangeSq = (double) this.range.getValue();
    EntityPlayer nextTarget = getMouseOverTarget(rangeSq);
    double closestDist = Double.MAX_VALUE;

    if (nextTarget == null) {
      // Fall back to closest target
      Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
      for (EntityPlayer player : players) {
        double dist = RotationUtil.distanceToBox(player, eyePos);
        if (dist < closestDist) {
          closestDist = dist;
          nextTarget = player;
        }
      }
    } else {
      closestDist = RotationUtil.distanceToBox(nextTarget, mc.thePlayer.getPositionEyes(1.0f));
    }

    if (nextTarget == null || closestDist > rangeSq) {
      if (isLagging) flushLag();
      this.currentTarget = null;
      this.lastDistSq = -1;
      return;
    }

    // --- Same-target guard: flush + reset if we switched targets ---
    if (!sameTarget(nextTarget)) {
      if (isLagging) flushLag();
      this.lastDistSq = -1;
      this.hitMarkedEntityId = -1;
      this.lastTargetHurtTime = nextTarget.hurtTime;
    }
    this.currentTarget = nextTarget;

    double dist = closestDist;
    int selfHurtTime = mc.thePlayer.hurtTime;
    int targetHurtTime = currentTarget.hurtTime;
    boolean moving = isMoving();

    // ============ ALREADY LAGGING: check flush conditions ============
    if (isLagging) {
      // 1. Target out of range
      if (dist > (double) this.range.getValue()) {
        flushLag();
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }

      // 2. Not closing distance (or moving away)
      //    hitHold exception: keep lagging if we hit them at close range (< 3 blocks)
      if (this.lastDistSq >= 0 && dist >= this.lastDistSq) {
        boolean hitHold =
            this.hitMarkedEntityId == this.currentTarget.getEntityId()
                && dist <= MINIMUM_DISTANCE
                && selfHurtTime == 0;
        if (!hitHold) {
          flushLag();
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
      }

      // 3. Got hit (server knows real position now)
      if (selfHurtTime > this.lastSelfHurtTime) {
        flushLag();
        this.hitMarkedEntityId = -1;
        this.lastSelfHurtTime = selfHurtTime;
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }
      this.lastSelfHurtTime = selfHurtTime;

      // 4. No longer holding valid weapon/tool
      if (!weaponOk) {
        flushLag();
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }

      // 5. Sprint reset (started sprinting while lagging)
      if (this.sprintReset.getValue()) {
        boolean sprintingNow = mc.thePlayer.isSprinting();
        if (sprintingNow && !this.lastSprintState) {
          flushLag();
          this.lastSprintState = sprintingNow;
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
        this.lastSprintState = sprintingNow;
      }

      // 6. Sword blocking (started blocking while lagging)
      if (this.blockSword.getValue()) {
        boolean blockingNow = mc.thePlayer.isBlocking();
        if (blockingNow && !this.lastBlockingState) {
          flushLag();
          this.lastBlockingState = blockingNow;
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
        this.lastBlockingState = blockingNow;
      }

      // 7. Using a splash potion
      if (this.splashPotion.getValue() && mc.thePlayer.isUsingItem()) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null
            && held.getItem() instanceof ItemPotion
            && ItemPotion.isSplash(held.getMetadata())) {
          flushLag();
          lastDistSq = dist;
          lastTargetHurtTime = targetHurtTime;
          return;
        }
      }

      // 8. Time-based release (ported from raven-bS releaseExpiredPackets)
      long elapsedMs = System.currentTimeMillis() - this.lagStartTime;
      if (elapsedMs >= (long) this.delay.getValue()) {
        flushLag();
        lastDistSq = dist;
        lastTargetHurtTime = targetHurtTime;
        return;
      }

      // All clear → keep lagging
      this.lastDistSq = dist;
      this.lastTargetHurtTime = targetHurtTime;
      startLag();
      this.hasTarget = true;
      return;
    }

    // ============ NOT LAGGING: decide if we should START ============

    // Track hurtTime for hit-on-target detection
    if (selfHurtTime > this.lastSelfHurtTime) {
      this.hitMarkedEntityId = -1;
    }
    this.lastSelfHurtTime = selfHurtTime;
    this.lastSprintState = mc.thePlayer.isSprinting();
    this.lastBlockingState = mc.thePlayer.isBlocking();

    // If target just took damage (hurtTime went 0 → >0), we landed a hit
    if (selfHurtTime == 0 && this.lastTargetHurtTime == 0 && targetHurtTime > 0) {
      this.hitMarkedEntityId = this.currentTarget.getEntityId();
    }
    this.lastTargetHurtTime = targetHurtTime;

    // Closing distance: current dist < previous dist
    boolean closing = this.lastDistSq >= 0 && dist < this.lastDistSq;
    boolean outsideMinDist = dist > MINIMUM_DISTANCE;
    boolean hitMarkedHere = this.hitMarkedEntityId == this.currentTarget.getEntityId();
    boolean hitStart =
        hitMarkedHere && dist <= MINIMUM_DISTANCE && selfHurtTime == 0 && moving && weaponOk;

    this.lastDistSq = dist;

    // Aggressive = always lag when target is in range
    // Normal = only lag when closing distance (outside 3 blocks) or just hit someone (within 3
    // blocks)
    boolean shouldStartLag =
        this.aggressive.getValue()
            || (selfHurtTime == 0 && weaponOk && moving && (closing && outsideMinDist || hitStart));

    if (shouldStartLag) {
      startLag();
      this.hasTarget = true;
    }
  }

  private void onPostTick() {
    Vec3 savedPosition = Myau.lagManager.getLastPosition();
    if (this.currentPosition == null) {
      this.lastPosition = savedPosition;
    } else {
      this.lastPosition = this.currentPosition;
    }
    this.currentPosition = savedPosition;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      if (event.getType() != EventType.SEND) return;

      Packet<?> packet = event.getPacket();
      if (this.shouldResetOnPacket(packet)) {
        if (isLagging) {
          flushLag(); // flush before attack/action packets
        }
        Myau.lagManager.resetDelay();
      }
    }
  }

  @EventTarget(Priority.HIGH)
  public void onRender3D(Render3DEvent event) {
    if (this.isEnabled()
        && this.hasTarget
        && this.lastPosition != null
        && this.currentPosition != null) {

      // Visibility: indicator must be toggled on
      if (!realPosIndicator.getValue()) return;
      // Hide in first-person unless showFirstPerson is enabled (like raven-bS)
      if (mc.gameSettings.thirdPersonView == 0 && !showFirstPerson.getValue()) return;

      // Interpolated position for smooth indicator (like raven-bS)
      double x =
          RenderUtil.lerpDouble(
              this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
      double y =
          RenderUtil.lerpDouble(
              this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
      double z =
          RenderUtil.lerpDouble(
              this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());
      float size = mc.thePlayer.getCollisionBorderSize();
      AxisAlignedBB aabb =
          new AxisAlignedBB(
                  x - (double) mc.thePlayer.width / 2.0,
                  y,
                  z - (double) mc.thePlayer.width / 2.0,
                  x + (double) mc.thePlayer.width / 2.0,
                  y + (double) mc.thePlayer.height,
                  z + (double) mc.thePlayer.width / 2.0)
              .expand(size, size, size)
              .offset(
                  -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                  -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                  -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());

      java.awt.Color color = myau.util.render.Themes.getCurrentTheme().getFirstColor();

      RenderUtil.enableRenderState();
      org.lwjgl.opengl.GL11.glLineWidth((float) indicatorLineWidth.getValue());
      if (indicatorFilled.getValue()) {
        RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
      }
      net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(aabb);
      RenderUtil.disableRenderState();
    }
  }

  @Override
  public void onDisabled() {
    flushLag();
    resetState();
  }

  @Override
  public String[] getSuffix() {
    return new String[] {String.format("%dms", this.delay.getValue())};
  }
}
