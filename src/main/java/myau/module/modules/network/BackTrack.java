package myau.module.modules.network;

import java.awt.Color;
import java.util.*;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.AttackEvent;
import myau.event.impl.LoadWorldEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.Render3DEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.math.RandomUtil;
import myau.util.misc.BackTrackUtil;
import myau.util.misc.ITruePosition;
import myau.util.network.PacketUtil;
import myau.util.player.RotationUtil;
import myau.util.player.TeamUtil;
import myau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import org.lwjgl.opengl.GL11;

public class BackTrack extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final String[] NON_DELAYED_SOUND_SUBSTRINGS =
      new String[] {"game.player.hurt", "game.player.die"};

  public final ModeProperty mode =
      new ModeProperty("mode", 0, new String[] {"MODERN", "FAKE_PLAYER", "PACKET", "LEGACY"});
  public final IntProperty nextBacktrackDelay =
      new IntProperty("next-backtrack-delay", 0, 0, 2000, () -> mode.getValue() == 0);
  public final FloatProperty delayMs =
      new FloatProperty(
          "delay", 80.0F, 80.0F, 0.0F, 2000.0F, () -> mode.getValue() == 0 || mode.getValue() == 3);

  public final ModeProperty style =
      new ModeProperty("style", 1, new String[] {"PULSE", "SMOOTH"}, () -> mode.getValue() == 0);
  public final FloatProperty distance =
      new FloatProperty("distance", 2.0F, 3.0F, 0.0F, 10.0F, () -> mode.getValue() == 0);
  public final BooleanProperty smart =
      new BooleanProperty("smart", true, () -> mode.getValue() == 0);

  public final ModeProperty espMode =
      new ModeProperty(
          "esp", 1, new String[] {"NONE", "BOX", "MODEL", "WIREFRAME"}, () -> mode.getValue() == 0);
  public final FloatProperty wireframeWidth =
      new FloatProperty(
          "wireframe-width",
          1.0F,
          0.5F,
          5.0F,
          () -> mode.getValue() == 0 && espMode.getValue() == 3);
  public final ColorProperty espColor = new ColorProperty("color", 0xFF00FF00);
  public final IntProperty fakePlayerPulseDelay =
      new IntProperty("fake-player-pulse-delay", 200, 50, 500, () -> mode.getValue() == 1);
  public final IntProperty fakePlayerIntavePackets =
      new IntProperty("fake-player-intave-packets", 5, 1, 30, () -> mode.getValue() == 1);
  public final FloatProperty packetDistance =
      new FloatProperty("packet-distance", 4.0F, 5.0F, 0.0F, 7.0F, () -> mode.getValue() == 2);
  public final IntProperty packetTimer =
      new IntProperty("packet-timer", 200, 0, 2000, () -> mode.getValue() == 2);
  public final ModeProperty packetMode =
      new ModeProperty(
          "packet-mode", 0, new String[] {"PING", "DELAY"}, () -> mode.getValue() == 2);
  public final IntProperty packetPingSize =
      new IntProperty(
          "packet-ping-size", 0, 0, 2000, () -> mode.getValue() == 2 && packetMode.getValue() == 0);
  public final BooleanProperty packetPlayerModel =
      new BooleanProperty("packet-player-model", true, () -> mode.getValue() == 2);
  public final BooleanProperty packetResetVelocity =
      new BooleanProperty("packet-reset-velocity", false, () -> mode.getValue() == 2);
  public final ModeProperty legacyPos =
      new ModeProperty(
          "legacy-pos", 0, new String[] {"CLIENT_POS", "SERVER_POS"}, () -> mode.getValue() == 3);
  public final IntProperty maximumCachedPositions =
      new IntProperty("max-cached-positions", 10, 1, 20, () -> mode.getValue() == 3);
  private final Queue<QueuedPacket> packetQueue = new ConcurrentLinkedQueue<>();
  private final Queue<TimedPosition> positions = new ConcurrentLinkedQueue<>();
  private final Queue<PacketLog> packetPingQueue = new ConcurrentLinkedQueue<>();
  private final Queue<Packet<?>> packetDelayQueue = new ConcurrentLinkedQueue<>();
  private final TimerUtil packetTimerUtil = new TimerUtil();
  private final Map<UUID, List<BacktrackData>> backtrackedPlayer = new ConcurrentHashMap<>();

  private final TimerUtil globalTimer = new TimerUtil();
  private final TimerUtil fakePulseTimer = new TimerUtil();

  public EntityLivingBase target;
  private boolean shouldRender = true;
  private boolean ignoreWholeTick = false;
  private long delayForNextBacktrack = 0L;

  private int modernDelayValue = 80;
  private boolean modernDelayBoolean = false;
  private EntityOtherPlayerMP fakePlayer;
  private EntityLivingBase currentTarget;
  private boolean fakeShown;
  private EntityPlayer packetTarget;
  private double packetRealX;
  private double packetRealY;
  private double packetRealZ;

  private static BackTrack instance;

  public BackTrack() {
    super("BackTrack", false);
    instance = this;
  }

  public static <T> T runWithNearestTrackedDistance(Entity entity, Supplier<T> action) {
    if (entity == null || instance == null || !instance.isEnabled()) return action.get();
    if (instance.mode.getValue() != 3) return action.get();

    List<BacktrackData> data = instance.backtrackedPlayer.get(entity.getUniqueID());
    if (data == null || data.isEmpty()) return action.get();

    List<BacktrackData> sorted = new ArrayList<>(data);
    sorted.sort(
        Comparator.comparingDouble(
            d -> {
              Vec3 pos = new Vec3(d.x, d.y, d.z);
              return BackTrackUtil.runWithSimulatedPosition(
                  entity, pos, () -> mc.thePlayer.getDistance(pos.xCoord, pos.yCoord, pos.zCoord));
            }));

    BacktrackData nearest = sorted.get(0);
    return BackTrackUtil.runWithSimulatedPosition(
        entity, new Vec3(nearest.x, nearest.y, nearest.z), action::get);
  }

  public void loopThroughBacktrackData(Entity entity, Consumer<Vec3> action) {
    if (!this.isEnabled() || mode.getValue() != 3) return;
    if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) return;

    List<BacktrackData> data = backtrackedPlayer.get(entity.getUniqueID());
    if (data == null || data.isEmpty()) return;

    for (int i = data.size() - 1; i >= 0; i--) {
      BacktrackData d = data.get(i);
      action.accept(new Vec3(d.x, d.y, d.z));
    }
  }

  public double getNearestTrackedDistance(Entity entity) {
    if (!this.isEnabled() || mode.getValue() != 3) return 0.0;
    final double[] nearest = {0.0};
    loopThroughBacktrackData(
        entity,
        pos -> {
          double dist = entity.getDistance(pos.xCoord, pos.yCoord, pos.zCoord);
          if (dist < nearest[0] || nearest[0] == 0.0) nearest[0] = dist;
        });
    return nearest[0];
  }

  private int getSupposedDelay() {
    return mode.getValue() == 0 ? modernDelayValue : delayMs.getSecondValue().intValue();
  }

  @Override
  public void onDisabled() {
    clearPackets(true, true);
    clearPacketMode(true);
    backtrackedPlayer.clear();
    removeFakePlayer();
    reset();
  }

  @Override
  public void onEnabled() {
    reset();
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    if (mode.getValue() == 0) {
      clearPackets(false, true);
      target = null;
    }
    backtrackedPlayer.clear();
    removeFakePlayer();
    clearPacketMode(true);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()) return;
    if (mode.getValue() == 3) {
      handleLegacyTick();
      if (legacyPos.getValue() == 0) {
        handleLegacyClientPosTick();
      }
      return;
    }
    if (mode.getValue() == 1) {
      updateFakePlayer();
      return;
    }
    if (mode.getValue() == 2) {
      updatePacketMode();
      return;
    }
    if (mode.getValue() == 0) {
      if (shouldBacktrack() && target instanceof ITruePosition) {
        ITruePosition targetMixin = (ITruePosition) target;
        if (targetMixin.isTruePos()) {
          double trueDist =
              mc.thePlayer.getDistance(
                  targetMixin.getTrueX(), targetMixin.getTrueY(), targetMixin.getTrueZ());
          double dist = mc.thePlayer.getDistance(target.posX, target.posY, target.posZ);
          double boxDist = RotationUtil.distanceToBox(target.getEntityBoundingBox());

          if (trueDist <= 6f
              && (!smart.getValue() || trueDist >= dist)
              && (style.getValue() == 1 || !globalTimer.hasTimeElapsed(getSupposedDelay()))) {
            shouldRender = true;

            if (boxDist >= distance.getValue() && boxDist <= distance.getSecondValue()) {
              handlePackets();
            } else {
              handlePacketsRange();
            }
          } else {
            clear();
          }
        }
      } else {
        clear();
      }
    }
    ignoreWholeTick = false;

    updateDelayCooldown();
  }

  private boolean isWorldUpdatePacket(Packet<?> packet) {
    return packet instanceof S21PacketChunkData
        || packet instanceof S22PacketMultiBlockChange
        || packet instanceof S23PacketBlockChange
        || packet instanceof S24PacketBlockAction
        || packet instanceof S25PacketBlockBreakAnim
        || packet instanceof S26PacketMapChunkBulk
        || packet instanceof S35PacketUpdateTileEntity;
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.isCancelled()) return;
    if (Myau.blinkManager != null && Myau.blinkManager.isBlinking()) return;

    Packet<?> packet = event.getPacket();
    if (event.getType() == EventType.RECEIVE && this.isWorldUpdatePacket(packet)) {
      return;
    }
    if (mode.getValue() == 2) {
      handlePacketMode(event, packet);
      return;
    }
    if (mode.getValue() == 3) {
      if (event.getType() != EventType.RECEIVE) return;
      handleLegacyPacket(packet);
      return;
    }
    if (mode.getValue() == 0) {
      if (mc.isSingleplayer() || mc.getCurrentServerData() == null) {
        clearPackets(true, false);
        return;
      }

      if (packetQueue.isEmpty() && !shouldBacktrack()) return;

      if (packet instanceof C00Handshake
          || packet instanceof C00PacketServerQuery
          || packet instanceof S02PacketChat
          || packet instanceof S01PacketPong) return;

      if (packet instanceof S29PacketSoundEffect) {
        String soundName = ((S29PacketSoundEffect) packet).getSoundName();
        for (String s : NON_DELAYED_SOUND_SUBSTRINGS) {
          if (soundName.contains(s)) return;
        }
      }

      if (packet instanceof S06PacketUpdateHealth
          && ((S06PacketUpdateHealth) packet).getHealth() <= 0) {
        clearPackets(true, true);
        return;
      }

      if (packet instanceof S13PacketDestroyEntities && target != null) {
        for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
          if (id == target.getEntityId()) {
            clearPackets(true, true);
            reset();
            return;
          }
        }
      }

      if (packet instanceof S1CPacketEntityMetadata
          && target != null
          && ((S1CPacketEntityMetadata) packet).getEntityId() == target.getEntityId()) {
        if (isDeadMetadata((S1CPacketEntityMetadata) packet)) {
          clearPackets(true, true);
          reset();
          return;
        }
        return;
      }

      if (packet instanceof S19PacketEntityStatus && target != null) {
        Entity entity = ((S19PacketEntityStatus) packet).getEntity(mc.theWorld);
        if (entity != null && entity.getEntityId() == target.getEntityId()) {
          return;
        }
      }

      if (event.getType() == EventType.RECEIVE) {
        if (packet instanceof S14PacketEntity && target != null) {
          Entity entity = ((S14PacketEntity) packet).getEntity(mc.theWorld);
          if (entity != null && entity.getEntityId() == target.getEntityId()) {
            S14PacketEntity s14 = (S14PacketEntity) packet;
            double newX = target.posX + s14.func_149062_c() / 32.0D;
            double newY = target.posY + s14.func_149061_d() / 32.0D;
            double newZ = target.posZ + s14.func_149064_e() / 32.0D;
            positions.add(
                new TimedPosition(new Vec3(newX, newY, newZ), System.currentTimeMillis()));
          }
        } else if (packet instanceof S18PacketEntityTeleport
            && target != null
            && ((S18PacketEntityTeleport) packet).getEntityId() == target.getEntityId()) {
          S18PacketEntityTeleport s18 = (S18PacketEntityTeleport) packet;
          double newX = s18.getX() / 32.0D;
          double newY = s18.getY() / 32.0D;
          double newZ = s18.getZ() / 32.0D;
          positions.add(new TimedPosition(new Vec3(newX, newY, newZ), System.currentTimeMillis()));
        }

        event.setCancelled(true);
        packetQueue.add(new QueuedPacket(packet, System.currentTimeMillis()));
      }
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!isValidTarget(event.getTarget())) return;

    if (mode.getValue() == 1) {
      handleFakePlayerAttack(event);
      return;
    }
    if (mode.getValue() == 2) {
      handlePacketAttack(event);
      return;
    }

    if (mode.getValue() == 0 || mode.getValue() == 3) {
      EntityLivingBase living = (EntityLivingBase) event.getTarget();
      if (target != living) {
        if (mode.getValue() == 0) clearPackets(true, true);
        reset();
      }
      target = living;
    }
  }

  private boolean isValidTarget(Entity entity) {
    if (!(entity instanceof EntityLivingBase)) return false;
    EntityLivingBase living = (EntityLivingBase) entity;
    if (!living.isEntityAlive()) return false;
    if (living == mc.thePlayer) return false;
    if (living.isInvisible()) return false;
    if (living instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) living;
      if (player.isSpectator()) return false;
      if (TeamUtil.isBot(player)) return false;
      if (TeamUtil.isFriend(player)) return false;
    }
    return true;
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled() || mc.getRenderManager() == null) return;
    if (mode.getValue() == 2) {
      renderPacketMode(event);
      return;
    }
    if (mode.getValue() == 0) {
      if (!shouldBacktrack() || !shouldRender || target == null) return;

      TimedPosition renderPos = null;
      for (TimedPosition p : positions) {
        renderPos = p;
      }
      if (renderPos == null) return;

      double x = renderPos.position.xCoord - mc.getRenderManager().viewerPosX;
      double y = renderPos.position.yCoord - mc.getRenderManager().viewerPosY;
      double z = renderPos.position.zCoord - mc.getRenderManager().viewerPosZ;

      Color color = new Color(espColor.getValue());

      switch (espMode.getValue()) {
        case 1:
          AxisAlignedBB box =
              target
                  .getEntityBoundingBox()
                  .offset(
                      renderPos.position.xCoord - target.posX,
                      renderPos.position.yCoord - target.posY,
                      renderPos.position.zCoord - target.posZ);
          drawBacktrackBox(box, color);
          break;
        case 2:
          GlStateManager.pushMatrix();
          GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
          GlStateManager.color(0.6F, 0.6F, 0.6F, 1.0F);
          mc.getRenderManager()
              .doRenderEntity(
                  target,
                  x,
                  y,
                  z,
                  target.prevRotationYaw
                      + (target.rotationYaw - target.prevRotationYaw) * event.getPartialTicks(),
                  event.getPartialTicks(),
                  true);
          GL11.glPopAttrib();
          GL11.glColor4f(1f, 1f, 1f, 1f);
          GlStateManager.color(1f, 1f, 1f, 1f);
          GlStateManager.popMatrix();
          break;
        case 3:
          GlStateManager.pushMatrix();
          GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
          GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
          GL11.glDisable(GL11.GL_TEXTURE_2D);
          GL11.glDisable(GL11.GL_LIGHTING);
          GL11.glDisable(GL11.GL_DEPTH_TEST);
          GL11.glEnable(GL11.GL_LINE_SMOOTH);
          GL11.glEnable(GL11.GL_BLEND);
          GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
          GL11.glLineWidth(wireframeWidth.getValue());

          GL11.glColor4f(
              color.getRed() / 255.0F,
              color.getGreen() / 255.0F,
              color.getBlue() / 255.0F,
              color.getAlpha() / 255.0F);
          mc.getRenderManager()
              .doRenderEntity(
                  target,
                  x,
                  y,
                  z,
                  target.prevRotationYaw
                      + (target.rotationYaw - target.prevRotationYaw) * event.getPartialTicks(),
                  event.getPartialTicks(),
                  true);

          GL11.glPopAttrib();
          GL11.glColor4f(1f, 1f, 1f, 1f);
          GlStateManager.color(1f, 1f, 1f, 1f);
          GlStateManager.popMatrix();
          break;
      }
      return;
    }
    if (mode.getValue() == 3) {
      renderLegacyTrail();
    }
  }

  private void handleLegacyPacket(Packet<?> packet) {
    try {
      if (packet instanceof S0CPacketSpawnPlayer) {
        S0CPacketSpawnPlayer spawn = (S0CPacketSpawnPlayer) packet;
        addBacktrackData(
            spawn.getPlayer(),
            spawn.getX() / 32.0,
            spawn.getY() / 32.0,
            spawn.getZ() / 32.0,
            System.currentTimeMillis());
        return;
      }

      if (legacyPos.getValue() != 1) return;

      if (packet instanceof S14PacketEntity) {
        S14PacketEntity movePacket = (S14PacketEntity) packet;
        Entity entity = movePacket.getEntity(mc.theWorld);
        if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
          ITruePosition tp = (ITruePosition) entity;
          addBacktrackData(
              entity.getUniqueID(),
              tp.getTrueX(),
              tp.getTrueY(),
              tp.getTrueZ(),
              System.currentTimeMillis());
        }
        return;
      }

      if (packet instanceof S18PacketEntityTeleport) {
        S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
        Entity entity = mc.theWorld.getEntityByID(teleport.getEntityId());
        if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
          ITruePosition tp = (ITruePosition) entity;
          addBacktrackData(
              entity.getUniqueID(),
              tp.getTrueX(),
              tp.getTrueY(),
              tp.getTrueZ(),
              System.currentTimeMillis());
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void handleLegacyTick() {
    long cutoff = System.currentTimeMillis() - getSupposedDelay();
    Iterator<Map.Entry<UUID, List<BacktrackData>>> it = backtrackedPlayer.entrySet().iterator();
    while (it.hasNext()) {
      List<BacktrackData> data = it.next().getValue();
      data.removeIf(d -> d.time < cutoff);
      if (data.isEmpty()) it.remove();
    }

    if (legacyPos.getValue() == 0 && mc.theWorld != null) {
      for (Entity entity : mc.theWorld.loadedEntityList) {
        if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
          addBacktrackData(
              entity.getUniqueID(),
              entity.posX,
              entity.posY,
              entity.posZ,
              System.currentTimeMillis());
        }
      }
    }
  }

  private void addBacktrackData(UUID id, double x, double y, double z, long time) {
    List<BacktrackData> data = backtrackedPlayer.get(id);
    if (data != null) {
      if (data.size() >= maximumCachedPositions.getValue()) {
        data.remove(0);
      }
      data.add(new BacktrackData(x, y, z, time));
    } else {
      List<BacktrackData> newData = new ArrayList<>();
      newData.add(new BacktrackData(x, y, z, time));
      backtrackedPlayer.put(id, newData);
    }
  }

  private List<BacktrackData> getBacktrackData(UUID id) {
    return backtrackedPlayer.get(id);
  }

  private void handleLegacyClientPosTick() {
    if (mc.theWorld == null) return;
    for (Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
        addBacktrackData(
            entity.getUniqueID(),
            entity.posX,
            entity.posY,
            entity.posZ,
            System.currentTimeMillis());
      }
    }
  }

  private void removeBacktrackData(UUID id) {
    backtrackedPlayer.remove(id);
  }

  private void renderLegacyTrail() {
    if (mc.theWorld == null || mc.theWorld.loadedEntityList.isEmpty()) return;

    Color legColor = new Color(255, 0, 0);

    GL11.glPushMatrix();
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    mc.entityRenderer.disableLightmap();

    GL11.glBegin(GL11.GL_LINE_STRIP);
    GL11.glColor4f(
        legColor.getRed() / 255.0F,
        legColor.getGreen() / 255.0F,
        legColor.getBlue() / 255.0F,
        1.0F);

    for (Object obj : mc.theWorld.loadedEntityList) {
      if (!(obj instanceof EntityPlayer && obj != mc.thePlayer)) continue;
      Entity entity = (Entity) obj;
      loopThroughBacktrackData(
          entity,
          pos -> {
            double rx = pos.xCoord - mc.getRenderManager().viewerPosX;
            double ry = pos.yCoord - mc.getRenderManager().viewerPosY;
            double rz = pos.zCoord - mc.getRenderManager().viewerPosZ;
            GL11.glVertex3d(rx, ry, rz);
          });
    }

    GL11.glColor4d(1.0, 1.0, 1.0, 1.0);
    GL11.glEnd();
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glDisable(GL11.GL_LINE_SMOOTH);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glPopMatrix();
  }

  private void handlePackets() {
    long delay = getSupposedDelay();
    packetQueue.removeIf(
        queuedPacket -> {
          if (queuedPacket.time <= System.currentTimeMillis() - delay) {
            receiveQueuedPacket(queuedPacket.packet);
            return true;
          }
          return false;
        });

    positions.removeIf(pos -> pos.time < System.currentTimeMillis() - delay);
  }

  private void handlePacketsRange() {
    long time = getRangeTime();
    if (time == -1L) {
      clearPackets(true, true);
      return;
    }

    packetQueue.removeIf(
        queuedPacket -> {
          if (queuedPacket.time <= time) {
            receiveQueuedPacket(queuedPacket.packet);
            return true;
          }
          return false;
        });

    positions.removeIf(pos -> pos.time < time);
  }

  private long getRangeTime() {
    if (target == null) return -1L;
    long time = 0L;
    boolean found = false;

    for (TimedPosition data : positions) {
      time = data.time;
      AxisAlignedBB targetBox =
          target
              .getEntityBoundingBox()
              .offset(
                  data.position.xCoord - target.posX,
                  data.position.yCoord - target.posY,
                  data.position.zCoord - target.posZ);

      double dist = getDistanceToBox(targetBox);
      if (dist >= distance.getValue() && dist <= distance.getSecondValue()) {
        found = true;
        break;
      }
    }
    return found ? time : -1L;
  }

  private double getDistanceToBox(AxisAlignedBB box) {
    if (mc.thePlayer == null || box == null) return 0.0;
    double x = clamp(mc.thePlayer.posX, box.minX, box.maxX);
    double y = clamp(mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), box.minY, box.maxY);
    double z = clamp(mc.thePlayer.posZ, box.minZ, box.maxZ);
    return Math.sqrt(
        (mc.thePlayer.posX - x) * (mc.thePlayer.posX - x)
            + (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - y)
                * (mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - y)
            + (mc.thePlayer.posZ - z) * (mc.thePlayer.posZ - z));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private void clearPackets(boolean handlePackets, boolean stopRendering) {
    packetQueue.removeIf(
        queuedPacket -> {
          if (handlePackets) {
            receiveQueuedPacket(queuedPacket.packet);
          }
          return true;
        });

    positions.clear();

    if (stopRendering) {
      shouldRender = false;
      ignoreWholeTick = true;
    }
  }

  private void updateDelayCooldown() {
    boolean shouldChangeDelay = packetQueue.isEmpty();
    if (!shouldChangeDelay) {
      modernDelayBoolean = false;
    }
    if (shouldChangeDelay && !modernDelayBoolean && !shouldBacktrack()) {
      delayForNextBacktrack = System.currentTimeMillis() + nextBacktrackDelay.getValue();
      modernDelayValue =
          randomInt(delayMs.getValue().intValue(), delayMs.getSecondValue().intValue());
      modernDelayBoolean = true;
    }
  }

  private void clear() {
    clearPackets(true, true);
    globalTimer.reset();
  }

  private void reset() {
    target = null;
    globalTimer.reset();
  }

  private boolean shouldBacktrack() {
    if (mc.thePlayer == null
        || mc.theWorld == null
        || target == null
        || mc.thePlayer.getHealth() <= 0
        || !(Float.isNaN(target.getHealth()) || target.getHealth() > 0)
        || mc.playerController.getCurrentGameType() == WorldSettings.GameType.SPECTATOR
        || System.currentTimeMillis() < delayForNextBacktrack
        || mc.thePlayer.ticksExisted <= 20
        || ignoreWholeTick) {
      return false;
    }
    if (!target.isEntityAlive()) return false;
    if (target == mc.thePlayer) return false;
    if (target.isInvisible()) return false;
    if (target instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) target;
      if (player.isSpectator()) return false;
      if (TeamUtil.isBot(player)) return false;
      if (TeamUtil.isFriend(player)) return false;
    }
    return true;
  }

  private boolean isDeadMetadata(S1CPacketEntityMetadata packet) {
    if (packet.func_149376_c() == null) return false;
    for (Object watchedObject : packet.func_149376_c()) {
      if (!(watchedObject instanceof net.minecraft.entity.DataWatcher.WatchableObject)) continue;
      net.minecraft.entity.DataWatcher.WatchableObject data =
          (net.minecraft.entity.DataWatcher.WatchableObject) watchedObject;
      if (data.getDataValueId() != 6 || data.getObject() == null) continue;
      try {
        double value = Double.parseDouble(data.getObject().toString());
        if (!Double.isNaN(value) && value <= 0.0D) return true;
      } catch (NumberFormatException ignored) {
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private void receiveQueuedPacket(Packet<?> packet) {
    if (packet == null || mc.getNetHandler() == null || mc.theWorld == null || mc.thePlayer == null)
      return;
    try {
      PacketUtil.handlePacket((Packet<INetHandlerPlayClient>) packet);
    } catch (RuntimeException ignored) {
    }
  }

  private void renderBacktrackModel(
      Entity entity, double x, double y, double z, float partialTicks, Color color) {
    GlStateManager.pushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GlStateManager.color(0.6F, 0.6F, 0.6F, 1.0F);
    mc.getRenderManager()
        .doRenderEntity(
            entity,
            x,
            y,
            z,
            entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks,
            partialTicks,
            true);
    GL11.glPopAttrib();
    GlStateManager.resetColor();
    GlStateManager.popMatrix();
  }

  private void renderBacktrackWireframe(
      Entity entity, double x, double y, double z, float partialTicks, Color color) {
    GlStateManager.pushMatrix();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glLineWidth(wireframeWidth.getValue());
    GL11.glColor4f(
        color.getRed() / 255.0F,
        color.getGreen() / 255.0F,
        color.getBlue() / 255.0F,
        color.getAlpha() / 255.0F);
    mc.getRenderManager()
        .doRenderEntity(
            entity,
            x,
            y,
            z,
            entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks,
            partialTicks,
            true);
    GL11.glPopAttrib();
    GlStateManager.resetColor();
    GlStateManager.popMatrix();
  }

  private void drawBacktrackBox(AxisAlignedBB box, Color color) {
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GlStateManager.pushMatrix();
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDepthMask(false);
    RenderGlobal.drawOutlinedBoundingBox(
        new AxisAlignedBB(
            box.minX - mc.getRenderManager().viewerPosX,
            box.minY - mc.getRenderManager().viewerPosY,
            box.minZ - mc.getRenderManager().viewerPosZ,
            box.maxX - mc.getRenderManager().viewerPosX,
            box.maxY - mc.getRenderManager().viewerPosY,
            box.maxZ - mc.getRenderManager().viewerPosZ),
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        color.getAlpha());
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glDepthMask(true);
    GlStateManager.popMatrix();
    GL11.glPopAttrib();
    GlStateManager.resetColor();
  }

  private boolean shouldPacketBacktrack() {
    return mc.thePlayer != null
        && mc.theWorld != null
        && packetTarget != null
        && !packetTarget.isDead
        && packetTarget.isEntityAlive()
        && mc.thePlayer.getDistanceToEntity(packetTarget) >= packetDistance.getValue()
        && mc.thePlayer.getDistanceToEntity(packetTarget) <= packetDistance.getSecondValue()
        && !TeamUtil.isBot(packetTarget);
  }

  private void handlePacketAttack(AttackEvent event) {
    if (!(event.getTarget() instanceof EntityPlayer)
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    EntityPlayer attacked = (EntityPlayer) event.getTarget();
    if (packetTarget != attacked) {
      clearPacketMode(true);
      packetTarget = attacked;
      packetRealX = attacked.posX;
      packetRealY = attacked.posY;
      packetRealZ = attacked.posZ;
    }
  }

  private void handlePacketMode(PacketEvent event, Packet<?> packet) {
    if (mc.thePlayer == null
        || mc.theWorld == null
        || packetTarget == null
        || event.getType() != EventType.RECEIVE) {
      return;
    }
    if (!packet.getClass().getSimpleName().toLowerCase().startsWith("s")) {
      return;
    }

    if (packet instanceof S14PacketEntity) {
      Entity entity = ((S14PacketEntity) packet).getEntity(mc.theWorld);
      if (entity != null && entity.getEntityId() == packetTarget.getEntityId()) {
        packetRealX += ((S14PacketEntity) packet).func_149062_c() / 32.0D;
        packetRealY += ((S14PacketEntity) packet).func_149061_d() / 32.0D;
        packetRealZ += ((S14PacketEntity) packet).func_149064_e() / 32.0D;
      }
    } else if (packet instanceof S18PacketEntityTeleport
        && ((S18PacketEntityTeleport) packet).getEntityId() == packetTarget.getEntityId()) {
      packetRealX = ((S18PacketEntityTeleport) packet).getX() / 32.0D;
      packetRealY = ((S18PacketEntityTeleport) packet).getY() / 32.0D;
      packetRealZ = ((S18PacketEntityTeleport) packet).getZ() / 32.0D;
    } else if (packet instanceof S12PacketEntityVelocity
        && packetResetVelocity.getValue()
        && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
      clearPacketMode(true);
      return;
    }

    event.setCancelled(true);
    if (packetMode.getValue() == 0) {
      packetPingQueue.add(new PacketLog(packet, System.currentTimeMillis()));
    } else {
      packetDelayQueue.add(packet);
    }
  }

  private void updatePacketMode() {
    if (!shouldPacketBacktrack()) {
      clearPacketMode(true);
      return;
    }
    if (packetMode.getValue() == 0) {
      clearPacketPing(packetPingSize.getValue());
    } else if (packetTimerUtil.hasTimeElapsed(packetTimer.getValue())) {
      clearPacketDelay();
    }
  }

  private void clearPacketMode(boolean handlePackets) {
    if (handlePackets) {
      clearPacketPing(0);
      clearPacketDelay();
    }
    packetPingQueue.clear();
    packetDelayQueue.clear();
    packetTarget = null;
    packetRealX = packetRealY = packetRealZ = 0.0D;
  }

  private void clearPacketPing(int delay) {
    packetPingQueue.removeIf(
        log -> {
          if (delay == 0 || System.currentTimeMillis() > log.time + delay) {
            receiveQueuedPacket(log.packet);
            return true;
          }
          return false;
        });
  }

  private void clearPacketDelay() {
    packetDelayQueue.removeIf(
        packet -> {
          receiveQueuedPacket(packet);
          packetTimerUtil.reset();
          return true;
        });
  }

  private void renderPacketMode(Render3DEvent event) {
    if (packetTarget == null || !packetPlayerModel.getValue()) {
      return;
    }
    GlStateManager.pushMatrix();
    mc.getRenderManager()
        .doRenderEntity(
            packetTarget,
            packetRealX - mc.getRenderManager().viewerPosX,
            packetRealY - mc.getRenderManager().viewerPosY,
            packetRealZ - mc.getRenderManager().viewerPosZ,
            packetTarget.rotationYaw,
            event.getPartialTicks(),
            true);
    GlStateManager.popMatrix();
    GlStateManager.resetColor();
  }

  private void createFakePlayer(EntityLivingBase target) {
    if (mc.theWorld == null || mc.getNetHandler() == null || !(target instanceof EntityPlayer))
      return;
    NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(target.getUniqueID());
    if (playerInfo == null) return;

    EntityOtherPlayerMP faker = new EntityOtherPlayerMP(mc.theWorld, playerInfo.getGameProfile());
    faker.rotationYawHead = target.rotationYawHead;
    faker.renderYawOffset = target.renderYawOffset;
    faker.copyLocationAndAnglesFrom(target);
    faker.setHealth(target.getHealth());
    copyEquipment(target, faker);
    mc.theWorld.addEntityToWorld(-1337, faker);
    fakePlayer = faker;
    fakeShown = true;
  }

  private void removeFakePlayer() {
    if (fakePlayer != null && mc.theWorld != null) mc.theWorld.removeEntity(fakePlayer);
    fakePlayer = null;
    currentTarget = null;
    fakeShown = false;
  }

  private void handleFakePlayerAttack(AttackEvent event) {
    if (!(event.getTarget() instanceof EntityLivingBase)) return;
    EntityLivingBase attacked = (EntityLivingBase) event.getTarget();

    if (fakePlayer != null && attacked.getEntityId() == fakePlayer.getEntityId()) {
      if (currentTarget != null) {
        mc.thePlayer.swingItem();
        PacketUtil.sendPacket(
            new C02PacketUseEntity(currentTarget, C02PacketUseEntity.Action.ATTACK));
        if (mc.playerController != null)
          mc.thePlayer.attackTargetEntityWithCurrentItem(currentTarget);
      }
      event.setCancelled(true);
      return;
    }

    if (attacked == mc.thePlayer) return;
    if (fakePlayer == null || attacked != currentTarget) {
      removeFakePlayer();
      currentTarget = attacked;
      createFakePlayer(attacked);
      fakePulseTimer.reset();
    }
  }

  private void updateFakePlayer() {
    if (currentTarget == null || fakePlayer == null) {
      if (!fakeShown && currentTarget != null) createFakePlayer(currentTarget);
      return;
    }
    if (currentTarget.isDead || !currentTarget.isEntityAlive() || !fakePlayer.isEntityAlive()) {
      removeFakePlayer();
      return;
    }
    fakePlayer.setHealth(currentTarget.getHealth());
    copyEquipment(currentTarget, fakePlayer);

    if (mc.thePlayer.ticksExisted % Math.max(fakePlayerIntavePackets.getValue(), 1) == 0
        || fakePulseTimer.hasTimeElapsed(fakePlayerPulseDelay.getValue())) {
      fakePlayer.rotationYawHead = currentTarget.rotationYawHead;
      fakePlayer.renderYawOffset = currentTarget.renderYawOffset;
      fakePlayer.copyLocationAndAnglesFrom(currentTarget);
      fakePulseTimer.reset();
    }
  }

  private void copyEquipment(EntityLivingBase src, EntityLivingBase dst) {
    for (int i = 0; i <= 4; i++)
      dst.setCurrentItemOrArmor(
          i, src.getEquipmentInSlot(i) == null ? null : src.getEquipmentInSlot(i).copy());
  }

  private static int randomInt(int min, int max) {
    return RandomUtil.nextInt(Math.min(min, max), Math.max(min, max));
  }

  private static class BacktrackData {
    private final double x, y, z;
    private final long time;

    BacktrackData(double x, double y, double z, long time) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.time = time;
    }
  }

  private static class PacketLog {
    private final Packet<?> packet;
    private final long time;

    PacketLog(Packet<?> packet, long time) {
      this.packet = packet;
      this.time = time;
    }
  }

  private static class QueuedPacket {
    private final Packet<?> packet;
    private final long time;

    QueuedPacket(Packet<?> packet, long time) {
      this.packet = packet;
      this.time = time;
    }
  }

  private static class TimedPosition {
    private final Vec3 position;
    private final long time;

    TimedPosition(Vec3 position, long time) {
      this.position = position;
      this.time = time;
    }
  }
}
