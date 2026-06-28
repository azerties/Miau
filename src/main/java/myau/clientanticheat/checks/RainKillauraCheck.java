package myau.clientanticheat.checks;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.PlayerEligibility;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * Rain Anticheat port — KillAura detection. Merged from Rain's KillauraCheck (silent aim, movement
 * fix, consume, heuristics).
 */
public class RainKillauraCheck {
  private static final long COMBAT_WINDOW_TICKS = 70L;
  private static final long SESSION_RESET_TICKS = 140L;
  private static final int WINDOW_SIZE = 10;
  private static final float QUANTUM = 1.40625F;
  private static final float VL_LIMIT = 400.0F;
  private static final float VL_FADE_PER_TICK = 0.5F;

  // consume
  private static final int EAT_TIMEOUT = 33;
  private static final int MIN_USE_TIME = 6;
  private static final int CONSUME_FAIL_VL = 8;

  // silent(snap)
  private static final float BURST_STEP_MIN = 7.0F;
  private static final float BURST_QUIET = 2.5F;
  private static final int BURST_MAX_TICKS = 7;
  private static final float BURST_SUM_MIN = 20.0F;
  private static final float SNAP_PRE_ERROR_MIN = 20.0F;
  private static final int SNAP_MIN_HITS = 3;
  private static final float SNAP_VL = 90.0F;
  private static final float RETURN_VL = 55.0F;
  private static final long RETURN_PAIR_TICKS = 8L;

  // silent(track)
  private static final int TRACK_WINDOW = 24;
  private static final float TRACK_RATIO = 0.85F;
  private static final float TRACK_LOS_MIN = 2.5F;
  private static final float TRACK_LOS_MAX = 45.0F;
  private static final double TRACK_MIN_DIST = 2.2D;
  private static final float TRACK_VL = 80.0F;

  // movement fix
  private static final double MOVE_MIN_SPEED = 0.15D;
  private static final double MOVE_MAX_SPEED = 0.45D;
  private static final double MOVE_FLAT_DY = 0.001D;
  private static final double MOVE_SMOOTH_ACCEL = 0.022D;
  private static final int MOVE_WINDOW = 12;
  private static final float MOVE_MEAN_LIMIT = 7.5F;
  private static final float MOVE_DESYNC_RESIDUAL = 8.0F;
  private static final float MOVE_VL = 70.0F;
  private static final float LOCK_RESIDUAL = 13.0F;
  private static final int LOCK_HITS = 3;
  private static final float MOVE_LOCK_VL = 85.0F;
  private static final double SPRINT_ACCEL = 0.08D;
  private static final double SPRINT_MIN_SPEED = 0.25D;
  private static final float SPRINT_OFFSET = 62.0F;
  private static final int SPRINT_HITS = 4;
  private static final float MOVE_SPRINT_VL = 85.0F;
  private static final int MOVE_DECAY_TICKS = 40;

  private static final double TARGET_RANGE_SQ = 36.0D;
  private static final double HITBOX_HALF_WIDTH = 0.4D;
  private static final int TRAIL_LEN = 5;

  private final Map<UUID, State> states = new HashMap<>();
  private final Map<UUID, Trail> trails = new HashMap<>();

  private static final class State {
    float lastYaw, lastPitch;
    boolean hasRotation;
    long lastSwingTick = Long.MIN_VALUE;
    float aimVl;
    final List<Float> yawChangeWindow = new ArrayList<>();
    int snapStreak;
    int burstTicks;
    float burstSum, burstDir, preBurstYaw;
    int quietTicks, snapHits, snapMisses;
    long lastSnapHitTick = Long.MIN_VALUE;
    UUID lastTargetId;
    float lastBearing = Float.NaN;
    int trackSamples, trackTicks;
    double lastVelX, lastVelZ, lastMoveY;
    boolean hasVel;
    int moveSamples, moveDesyncTicks, lockDesync, sprintDesync, moveTickCounter;
    float residualSum;
    int useItemTicks;
    long lastEatTick;
    int consumeVl;
  }

  private static final class Trail {
    final double[] x = new double[TRAIL_LEN];
    final double[] z = new double[TRAIL_LEN];
    long lastTick = Long.MIN_VALUE;
    int size;

    void push(double px, double pz, long tick) {
      if (tick == this.lastTick && this.size > 0) return;
      System.arraycopy(this.x, 0, this.x, 1, TRAIL_LEN - 1);
      System.arraycopy(this.z, 0, this.z, 1, TRAIL_LEN - 1);
      this.x[0] = px;
      this.z[0] = pz;
      this.lastTick = tick;
      if (this.size < TRAIL_LEN) ++this.size;
    }
  }

  public void check(EntityPlayer player, long tick) {
    if (player == null) return;
    UUID uuid = player.getUniqueID();
    if (uuid == null) return;
    if (!PlayerEligibility.shouldCheckPlayer(player)) {
      forgetPlayer(uuid);
      return;
    }

    Minecraft mc = Minecraft.getMinecraft();
    State st = states.computeIfAbsent(uuid, k -> new State());

    trail(uuid).push(player.posX, player.posZ, tick);
    if (mc.thePlayer != null) {
      trail(mc.thePlayer.getUniqueID()).push(mc.thePlayer.posX, mc.thePlayer.posZ, tick);
    }

    if (player.isRiding()) return;

    consumeComponent(player, st, tick);

    if (player.isSwingInProgress) st.lastSwingTick = tick;

    float yaw = player.rotationYaw;
    float pitch = player.rotationPitch;
    if (!st.hasRotation) {
      st.lastYaw = yaw;
      st.lastPitch = pitch;
      st.hasRotation = true;
      return;
    }
    float prevYaw = st.lastYaw;
    float yawChange = MathHelper.wrapAngleTo180_float(yaw - st.lastYaw);
    float pitchChange = MathHelper.wrapAngleTo180_float(pitch - st.lastPitch);
    st.lastYaw = yaw;
    st.lastPitch = pitch;

    double moveX = player.posX - player.lastTickPosX;
    double moveY = player.posY - player.lastTickPosY;
    double moveZ = player.posZ - player.lastTickPosZ;
    if (moveX * moveX + moveZ * moveZ > 25.0D) {
      st.yawChangeWindow.clear();
      resetBurst(st);
      st.lastBearing = Float.NaN;
      st.lastTargetId = null;
      st.hasVel = false;
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
      return;
    }

    boolean inCombat =
        st.lastSwingTick != Long.MIN_VALUE
            && tick >= st.lastSwingTick
            && tick - st.lastSwingTick <= COMBAT_WINDOW_TICKS;
    if (!inCombat) {
      if (st.lastSwingTick != Long.MIN_VALUE && tick - st.lastSwingTick > SESSION_RESET_TICKS) {
        resetSession(st);
      }
      consumeFade(st);
      return;
    }

    float absYaw = Math.abs(yawChange);
    float absPitch = Math.abs(pitchChange);
    if (absYaw != 0.0F || absPitch != 0.0F) {
      st.yawChangeWindow.add(absYaw);
      if (st.yawChangeWindow.size() >= WINDOW_SIZE) {
        analyzeWindow(st, st.yawChangeWindow);
        st.yawChangeWindow.clear();
      }
    }

    List<EntityPlayer> targets = targetsNear(mc, player, tick);
    burstMachine(player, st, tick, yawChange, prevYaw, targets);
    trackComponent(player, st, yaw, targets);
    movementComponent(mc, player, st, moveX, moveY, moveZ, yaw, targets);

    if (st.aimVl > VL_LIMIT) {
      AlertManager.flag(player.getName(), "KillAura", "vl", (int) (st.aimVl / 10.0F));
      st.aimVl = 360.0F;
    }
    if (st.aimVl > 0.0F) {
      st.aimVl = Math.max(0.0F, st.aimVl - VL_FADE_PER_TICK);
    }
  }

  private void analyzeWindow(State st, List<Float> window) {
    float yawChangeFirst = window.get(0);
    float oldYawChange = yawChangeFirst;
    int machineKnownMovement = 0, constantRotations = 0, robotizedAmount = 0;
    int bigSwingUp = 0, bigSwingDown = 0;

    for (float yawChange : window) {
      float robotized = Math.abs(yawChange - yawChangeFirst);
      float diffBetweenYawChanges = yawChange - oldYawChange;
      if (robotized < QUANTUM * 1.5F && yawChange > QUANTUM * 2.0F) ++robotizedAmount;
      if (robotized < QUANTUM && yawChange > QUANTUM * 3.0F) ++machineKnownMovement;
      if (robotized < QUANTUM * 0.5F && yawChange > QUANTUM * 2.5F) ++constantRotations;
      if (diffBetweenYawChanges > 12.0F) ++bigSwingUp;
      if (diffBetweenYawChanges < -12.0F) ++bigSwingDown;
      oldYawChange = yawChange;
    }

    if (machineKnownMovement > 8) addVl(st, 100.0F);
    if (constantRotations > 6) addVl(st, 65.0F);
    if (robotizedAmount > 8) addVl(st, 50.0F);
    if (bigSwingUp > 1 && bigSwingDown > 1 && bigSwingUp + bigSwingDown > 4) {
      ++st.snapStreak;
      if (st.snapStreak > 2) addVl(st, 55.0F);
    } else {
      st.snapStreak = 0;
    }
  }

  private void burstMachine(
      EntityPlayer player,
      State st,
      long tick,
      float yawChange,
      float prevYaw,
      List<EntityPlayer> targets) {
    float absYaw = Math.abs(yawChange);
    if (st.burstTicks > 0) {
      boolean sameDir = yawChange * st.burstDir >= 0.0F;
      if (absYaw < BURST_QUIET) {
        if (st.burstSum >= BURST_SUM_MIN) evaluateBurst(player, st, tick, targets);
        resetBurst(st);
        st.quietTicks = 1;
      } else if (sameDir) {
        ++st.burstTicks;
        st.burstSum += absYaw;
        if (st.burstTicks > BURST_MAX_TICKS) st.burstTicks = -1;
      } else if (absYaw > BURST_STEP_MIN) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = prevYaw;
        st.quietTicks = 0;
      } else {
        resetBurst(st);
        st.quietTicks = 0;
      }
    } else if (st.burstTicks == -1) {
      if (absYaw < BURST_QUIET) {
        resetBurst(st);
        st.quietTicks = 1;
      }
    } else {
      if (absYaw > BURST_STEP_MIN && st.quietTicks >= 2) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = prevYaw;
        st.quietTicks = 0;
      } else if (absYaw < BURST_QUIET) ++st.quietTicks;
      else st.quietTicks = 0;
    }
  }

  private void evaluateBurst(EntityPlayer player, State st, long tick, List<EntityPlayer> targets) {
    if (targets.isEmpty()) return;
    float bestErr = Float.MAX_VALUE, bestPre = 0.0F, bestPreInside = Float.MAX_VALUE;
    for (EntityPlayer target : targets) {
      Trail trail = trail(target.getUniqueID());
      float err = minInsideError(player, trail, st.lastYaw);
      if (err < bestErr) {
        bestErr = err;
        bestPre =
            Math.abs(
                MathHelper.wrapAngleTo180_float(
                    st.preBurstYaw - bearingTo(player, trail.x[0], trail.z[0])));
      }
      bestPreInside = Math.min(bestPreInside, minInsideError(player, trail, st.preBurstYaw));
    }
    if (bestErr <= QUANTUM && bestPre > SNAP_PRE_ERROR_MIN) {
      ++st.snapHits;
      st.lastSnapHitTick = tick;
      if (st.snapHits >= SNAP_MIN_HITS && st.snapHits > st.snapMisses) {
        addVl(st, SNAP_VL);
        AlertManager.flag(player.getName(), "KillAura", "silent(snap)", (int) (st.aimVl / 10.0F));
        st.snapHits = 0;
      }
    } else if (bestPreInside <= QUANTUM && bestErr > SNAP_PRE_ERROR_MIN * 0.75F) {
      if (st.lastSnapHitTick != Long.MIN_VALUE && tick - st.lastSnapHitTick <= RETURN_PAIR_TICKS)
        addVl(st, RETURN_VL);
    } else if (bestPre > SNAP_PRE_ERROR_MIN && bestErr > QUANTUM * 2.0F) ++st.snapMisses;
  }

  private void trackComponent(
      EntityPlayer player, State st, float yaw, List<EntityPlayer> targets) {
    if (targets.isEmpty()) {
      st.lastTargetId = null;
      st.lastBearing = Float.NaN;
      return;
    }
    EntityPlayer target = null;
    double bestDistSq = Double.MAX_VALUE;
    for (EntityPlayer t : targets) {
      double dx = t.posX - player.posX;
      double dy = t.posY - player.posY;
      double dz = t.posZ - player.posZ;
      double distSq = dx * dx + dy * dy + dz * dz;
      if (distSq < bestDistSq) {
        bestDistSq = distSq;
        target = t;
      }
    }
    if (target == null) {
      st.lastTargetId = null;
      st.lastBearing = Float.NaN;
      return;
    }
    UUID targetId = target.getUniqueID();
    Trail trail = trail(targetId);
    float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
    if (targetId.equals(st.lastTargetId) && !Float.isNaN(st.lastBearing)) {
      float losDelta = Math.abs(MathHelper.wrapAngleTo180_float(bearingNow - st.lastBearing));
      double dx = target.posX - player.posX;
      double dz = target.posZ - player.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);
      if (losDelta > TRACK_LOS_MIN && losDelta < TRACK_LOS_MAX && horizDist >= TRACK_MIN_DIST) {
        ++st.trackSamples;
        if (minInsideError(player, trail, yaw) <= QUANTUM * 0.5F) ++st.trackTicks;
        if (st.trackSamples >= TRACK_WINDOW
            && (float) st.trackTicks >= TRACK_RATIO * (float) st.trackSamples) {
          addVl(st, TRACK_VL);
          st.trackSamples = 0;
          st.trackTicks = 0;
        }
      }
    }
    st.lastTargetId = targetId;
    st.lastBearing = bearingNow;
  }

  private void movementComponent(
      Minecraft mc,
      EntityPlayer player,
      State st,
      double moveX,
      double moveY,
      double moveZ,
      float yaw,
      List<EntityPlayer> targets) {
    if (++st.moveTickCounter >= MOVE_DECAY_TICKS) {
      st.moveTickCounter = 0;
      st.lockDesync = Math.max(0, st.lockDesync - 1);
      st.sprintDesync = Math.max(0, st.sprintDesync - 1);
    }
    boolean flat =
        st.hasVel && Math.abs(moveY) < MOVE_FLAT_DY && Math.abs(st.lastMoveY) < MOVE_FLAT_DY;
    double ax = moveX - st.lastVelX, az = moveZ - st.lastVelZ;
    st.lastVelX = moveX;
    st.lastVelZ = moveZ;
    st.lastMoveY = moveY;
    st.hasVel = true;
    double accel = Math.sqrt(ax * ax + az * az);
    double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);
    if (!flat || player.hurtTime > 0 || speed < MOVE_MIN_SPEED || speed > MOVE_MAX_SPEED) return;
    Block ground =
        mc.theWorld
            .getBlockState(new BlockPos(player.posX, player.posY - 0.5D, player.posZ))
            .getBlock();
    if (ground == Blocks.ice || ground == Blocks.packed_ice) return;

    float moveBearing = (float) Math.toDegrees(Math.atan2(-moveX, moveZ));
    float offset = MathHelper.wrapAngleTo180_float(moveBearing - yaw);
    float residual = bucketResidual(offset);

    if (player.isSprinting()
        && speed > SPRINT_MIN_SPEED
        && accel < SPRINT_ACCEL
        && Math.abs(offset) > SPRINT_OFFSET) {
      ++st.sprintDesync;
      if (st.sprintDesync >= SPRINT_HITS) {
        addVl(st, MOVE_SPRINT_VL);
        st.sprintDesync -= SPRINT_HITS;
      }
    }
    if (accel > MOVE_SMOOTH_ACCEL) return;
    if (residual > LOCK_RESIDUAL) {
      for (EntityPlayer target : targets) {
        if (minInsideError(player, trail(target.getUniqueID()), yaw) <= QUANTUM) {
          ++st.lockDesync;
          if (st.lockDesync >= LOCK_HITS) {
            addVl(st, MOVE_LOCK_VL);
            st.lockDesync -= LOCK_HITS;
          }
          break;
        }
      }
    }
    ++st.moveSamples;
    st.residualSum += residual;
    if (residual > MOVE_DESYNC_RESIDUAL) ++st.moveDesyncTicks;
    if (st.moveSamples >= MOVE_WINDOW) {
      float mean = st.residualSum / (float) st.moveSamples;
      if (mean > MOVE_MEAN_LIMIT) addVl(st, MOVE_VL);
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
    }
  }

  private void consumeComponent(EntityPlayer player, State st, long tick) {
    ItemStack heldItem = player.getHeldItem();
    boolean isUsingItem = player.isUsingItem();
    boolean isConsumable =
        heldItem != null
            && (heldItem.getItem() instanceof ItemFood
                || heldItem.getItem() instanceof ItemPotion
                || heldItem.getItem() instanceof ItemBucketMilk);
    boolean isAttacking = player.swingProgressInt > 0;
    if (isUsingItem && isConsumable) ++st.useItemTicks;
    else {
      if (st.useItemTicks > 0) st.lastEatTick = tick;
      st.useItemTicks = 0;
    }
    long sinceLastEat = tick - st.lastEatTick;
    if (isAttacking
        && st.useItemTicks > MIN_USE_TIME
        && sinceLastEat < EAT_TIMEOUT
        && isConsumable) {
      ++st.consumeVl;
      if (st.consumeVl >= CONSUME_FAIL_VL) {
        AlertManager.flag(player.getName(), "KillAura", "consume", st.consumeVl);
      }
    } else if (st.consumeVl > 0) --st.consumeVl;
  }

  private void consumeFade(State st) {
    if (st.consumeVl > 0) {
      st.consumeVl = Math.max(0, st.consumeVl - 1);
    }
  }

  private List<EntityPlayer> targetsNear(Minecraft mc, EntityPlayer attacker, long tick) {
    List<EntityPlayer> out = new ArrayList<>();
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (!PlayerEligibility.shouldUseAsTarget(p, attacker)) continue;
      double dx = p.posX - attacker.posX, dy = p.posY - attacker.posY, dz = p.posZ - attacker.posZ;
      if (dx * dx + dy * dy + dz * dz > TARGET_RANGE_SQ) continue;
      trail(p.getUniqueID()).push(p.posX, p.posZ, tick);
      out.add(p);
    }
    return out;
  }

  private float minInsideError(EntityPlayer attacker, Trail trail, float yaw) {
    float best = Float.MAX_VALUE;
    for (int i = 0; i < trail.size; ++i) {
      double dx = trail.x[i] - attacker.posX, dz = trail.z[i] - attacker.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);
      if (horizDist < 0.5D) continue;
      float bearing = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
      float err = Math.abs(MathHelper.wrapAngleTo180_float(yaw - bearing));
      float halfWidth = (float) Math.toDegrees(Math.atan2(HITBOX_HALF_WIDTH, horizDist));
      best = Math.min(best, Math.max(0.0F, err - halfWidth));
    }
    return best;
  }

  private float bearingTo(EntityPlayer attacker, double x, double z) {
    double dx = x - attacker.posX, dz = z - attacker.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private static float bucketResidual(float offset) {
    return Math.abs(MathHelper.wrapAngleTo180_float(offset - 45.0F * Math.round(offset / 45.0F)));
  }

  private void addVl(State st, float vl) {
    st.aimVl += vl;
  }

  private Trail trail(UUID uuid) {
    return trails.computeIfAbsent(uuid, k -> new Trail());
  }

  private void resetBurst(State st) {
    st.burstTicks = 0;
    st.burstSum = 0.0F;
    st.burstDir = 0.0F;
  }

  private void resetSession(State st) {
    resetBurst(st);
    st.quietTicks = 0;
    st.snapHits = 0;
    st.snapMisses = 0;
    st.lastSnapHitTick = Long.MIN_VALUE;
    st.trackSamples = 0;
    st.trackTicks = 0;
    st.lastTargetId = null;
    st.lastBearing = Float.NaN;
    st.hasVel = false;
    st.moveSamples = 0;
    st.moveDesyncTicks = 0;
    st.residualSum = 0.0F;
    st.lockDesync = 0;
    st.sprintDesync = 0;
    st.moveTickCounter = 0;
  }

  public void forgetPlayer(UUID uuid) {
    if (uuid == null) return;
    states.remove(uuid);
    trails.remove(uuid);
  }

  public void reset() {
    states.clear();
    trails.clear();
  }
}
