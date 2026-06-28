package myau.clientanticheat.combat.killaura;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.PlayerEligibility;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * KillAuraMegaCheck — unified killaura detection in ONE file.
 *
 * <p>Merges all previous Miau killaura checks + Rain Anticheat's advanced detection components +
 * Miau-main's angle snap patterns + GCD/sensitivity heuristics. All components share one VL pool
 * with a 400-point limit; when exceeded a signal fires through ClientAntiCheatContext.
 *
 * <p>Components:
 *
 * <ul>
 *   <li>heuristic(aim|constant|sync) — Rain's windowed robotized rotation counts
 *   <li>pattern(snap) — both-direction big jump oscillation (Rain)
 *   <li>silent(snap|return) — yaw burst landing on/leaving target hitbox (Rain)
 *   <li>silent(track) — lock-on aim while strafing (Rain)
 *   <li>movement(fix|lock|sprint) — body/head desync from movement-corrected aim (Rain)
 *   <li>gcd/sensitivity — modulo patterns, GCD consistency, exact rotation landing
 *       (HeuristicsCheck)
 *   <li>stdDevAnalysis — low standard deviation of rotation deltas (HeuristicsCheck)
 *   <li>attackRate — swing delay <3 ticks (RotationSpeed)
 *   <li>wideAngle — attacking with yaw error >45° (RotationSpeed)
 *   <li>hitAccuracy — sustained high accuracy + low variance (RotationSpeed)
 *   <li>toolSwitch — rapid/combat item switching (ToolSwitchCheck)
 *   <li>noSwing — target hurt without arm swing (NoSwingCheck)
 *   <li>latency (freeze/burst) — frozen → burst attack pattern (LatencyCheck)
 *   <li>snapPattern — 3-tick snap, silent aim, entity snap, flick (AngleSnap)
 *   <li>consume — attacking while eating/drinking (Rain)
 * </ul>
 */
public class KillAuraMegaCheck {

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Shared economy ───────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final float QUANTUM = 1.40625F; // 360/256
  private static final long COMBAT_WINDOW_TICKS = 70L; // 3.5s after attack
  private static final long SESSION_RESET_TICKS = 140L; // 7s idle → full reset
  private static final float VL_LIMIT = 400.0F;
  private static final float VL_FADE_PER_TICK = 0.5F;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Heuristic window ─────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final int WINDOW_SIZE = 10;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Burst machine (silent snap/return) ───────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final float BURST_STEP_MIN = 7.0F;
  private static final float BURST_QUIET = 2.5F;
  private static final int BURST_MAX_TICKS = 7;
  private static final float BURST_SUM_MIN = 20.0F;
  private static final float SNAP_PRE_ERROR_MIN = 20.0F;
  private static final int SNAP_MIN_HITS = 3;
  private static final float SNAP_VL = 90.0F;
  private static final float RETURN_VL = 55.0F;
  private static final long RETURN_PAIR_TICKS = 8L;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Track component ──────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final int TRACK_WINDOW = 24;
  private static final float TRACK_RATIO = 0.85F;
  private static final float TRACK_LOS_MIN = 2.5F;
  private static final float TRACK_LOS_MAX = 45.0F;
  private static final double TRACK_MIN_DIST = 2.2D;
  private static final float TRACK_VL = 80.0F;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Movement fix desync ──────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final double MOVE_MIN_SPEED = 0.15D;
  private static final double MOVE_MAX_SPEED = 0.45D;
  private static final double MOVE_FLAT_DY = 0.001D;
  private static final double MOVE_SMOOTH_ACCEL = 0.022D;
  private static final int MOVE_WINDOW = 12;
  private static final float MOVE_MEAN_LIMIT = 7.5F;
  private static final float MOVE_DESYNC_RESIDUAL = 8.0F;
  private static final float LOCK_RESIDUAL = 13.0F;
  private static final int LOCK_HITS = 3;
  private static final double SPRINT_ACCEL = 0.08D;
  private static final double SPRINT_MIN_SPEED = 0.25D;
  private static final float SPRINT_OFFSET = 62.0F;
  private static final int SPRINT_HITS = 4;
  private static final int MOVE_DECAY_TICKS = 40;
  private static final float MOVE_VL = 70.0F;
  private static final float MOVE_LOCK_VL = 85.0F;
  private static final float MOVE_SPRINT_VL = 85.0F;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Consume ──────────────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final int EAT_TIMEOUT = 33;
  private static final int MIN_USE_TIME = 6;
  private static final int CONSUME_FAIL_VL = 8;
  private static final float CONSUME_VL_ADD = 50.0F;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Geometry ─────────────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final double TARGET_RANGE_SQ = 36.0D; // 6-block radius
  private static final double HITBOX_HALF_WIDTH = 0.4D;
  private static final int TRAIL_LEN = 5;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── RotationSpeed / attack rate ──────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final int ACCURACY_SAMPLE_SIZE = 25;
  private static final double HIGH_ACCURACY_THRESHOLD = 0.95D;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Heuristics (GCD / modulo / stdDev) ───────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final int STDDEV_SAMPLE_SIZE = 40;
  private static final double LOW_STDDEV_THRESHOLD = 0.3D;
  private static final double CONSISTENT_STDDEV_THRESHOLD = 0.9D;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── NoSwing ─────────────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private static final int SWING_HISTORY_SIZE = 5;
  private static final double NO_SWING_VL_THRESHOLD = 5.0D;

  // ═══════════════════════════════════════════════════════════════════════════
  // ── State ────────────────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private final Map<UUID, PlayerState> states = new HashMap<>();
  private final Map<UUID, Trail> trails = new HashMap<>();

  // ── RotationSpeed / extra per-player state (kept here for persistence) ──

  private final Map<String, Long> lastAttackTicks = new HashMap<>();
  private final Map<String, CheckBuffer> rateBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> aimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> accuracyBuffers = new HashMap<>();
  private final Map<String, Queue<Double>> hitAccuracySamples = new HashMap<>();
  private final Map<String, CheckBuffer> switchBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> combatSwitchBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> noSwingBuffers = new HashMap<>();
  private final Map<String, Integer> lastTargetHurtTime = new HashMap<>();
  private final Map<String, LinkedList<Integer>> swingProgressHistory = new HashMap<>();
  private final Map<String, CheckBuffer> freezeBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> burstBuffers = new HashMap<>();
  private final Map<String, Integer> frozenTicks = new HashMap<>();
  private final Map<String, CheckBuffer> snapPatternBuffers = new HashMap<>();
  private final Map<String, float[]> yawHistory = new HashMap<>();
  private final Map<String, CheckBuffer> silentAimBuffers = new HashMap<>();
  private final Map<String, float[]> movementYawHistory = new HashMap<>();
  private final Map<String, CheckBuffer> entitySnapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> flickBuffers = new HashMap<>();

  private static final class PlayerState {
    // rotation stream
    float lastYaw, lastPitch;
    boolean hasRotation;
    long lastSwingTick = Long.MIN_VALUE;

    // shared VL economy
    float aimVl;
    float aimVlBuffer;
    int directAlertHits;

    // heuristic window
    final List<Float> yawChangeWindow = new ArrayList<>();
    int snapStreak;

    // burst machine
    int burstTicks;
    float burstSum, burstDir, preBurstYaw;
    int quietTicks, snapHits, snapMisses;
    long lastSnapHitTick = Long.MIN_VALUE;

    // track
    UUID lastTargetId;
    float lastBearing = Float.NaN;
    int trackSamples, trackTicks;

    // movement fix
    double lastVelX, lastVelZ, lastMoveY;
    boolean hasVel;
    int moveSamples, moveDesyncTicks, lockDesync, sprintDesync, moveTickCounter;
    float residualSum;

    // consume
    int useItemTicks;
    long lastEatTick;
    int consumeVl;

    // HeuristicsCheck GCD/sensitivity state
    final Queue<Float> yawGcdDeltaSamples = new ArrayDeque<>();
    final Queue<Float> pitchGcdDeltaSamples = new ArrayDeque<>();
    float lastGcd;
    boolean hasGcd;

    // extra RotationSpeed fields (kept here for persistence)
    long lastAttackTick;

    // flag to avoid double-alert per tick
    boolean flaggedThisTick;
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

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Public check (called once per player per tick) ─────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  public void check(
      EntityPlayer player, PlayerCheckData data, long tick, ClientAntiCheatContext context) {
    if (player == null || data == null) return;
    UUID uuid = player.getUniqueID();
    if (uuid == null) return;
    String name = player.getName();
    if (name == null || !PlayerEligibility.shouldCheckPlayer(player)) {
      forgetPlayer(uuid);
      return;
    }

    PlayerState st = states.computeIfAbsent(uuid, k -> new PlayerState());
    st.flaggedThisTick = false;

    Minecraft mc = Minecraft.getMinecraft();
    trail(uuid).push(player.posX, player.posZ, tick);
    if (mc.thePlayer != null) {
      trail(mc.thePlayer.getUniqueID()).push(mc.thePlayer.posX, mc.thePlayer.posZ, tick);
    }

    // ── Ride / teleport guard ─────────────────────────────────────
    if (player.isRiding()) return;

    // ── Consume ───────────────────────────────────────────────────
    consumeCheck(player, st, tick, name, data, context);

    // ── Swing gate ────────────────────────────────────────────────
    if (player.isSwingInProgress) st.lastSwingTick = tick;

    // ── Rotation stream ───────────────────────────────────────────
    float yaw = player.rotationYaw;
    float pitch = player.rotationPitch;
    if (!st.hasRotation) {
      st.lastYaw = yaw;
      st.lastPitch = pitch;
      st.hasRotation = true;
      return;
    }
    float yawChange = MathHelper.wrapAngleTo180_float(yaw - st.lastYaw);
    float pitchChange = MathHelper.wrapAngleTo180_float(pitch - st.lastPitch);
    float absYaw = Math.abs(yawChange);
    float absPitch = Math.abs(pitchChange);
    st.lastYaw = yaw;
    st.lastPitch = pitch;

    // Teleport guard
    double moveX = player.posX - player.lastTickPosX;
    double moveY = player.posY - player.lastTickPosY;
    double moveZ = player.posZ - player.lastTickPosZ;
    if (moveX * moveX + moveZ * moveZ > 25.0D) {
      resetBurst(st);
      st.lastBearing = Float.NaN;
      st.lastTargetId = null;
      st.hasVel = false;
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
      return;
    }

    // ── Combat gate ───────────────────────────────────────────────
    boolean inCombat =
        st.lastSwingTick != Long.MIN_VALUE
            && tick >= st.lastSwingTick
            && tick - st.lastSwingTick <= COMBAT_WINDOW_TICKS;
    if (!inCombat) {
      if (st.lastSwingTick != Long.MIN_VALUE && tick - st.lastSwingTick > SESSION_RESET_TICKS) {
        resetSession(st);
      }
      // Still run non-combat-gated checks
      runNonCombatChecks(
          player,
          data,
          name,
          st,
          tick,
          absYaw,
          absPitch,
          yawChange,
          pitchChange,
          yaw,
          pitch,
          moveX,
          moveY,
          moveZ,
          context);
      return;
    }

    // ── Rotation heuristic window (Rain style) ────────────────────
    if (absYaw != 0.0F || absPitch != 0.0F) {
      st.yawChangeWindow.add(absYaw);
      if (st.yawChangeWindow.size() >= WINDOW_SIZE) {
        analyzeWindow(player, st, st.yawChangeWindow, name, context);
        st.yawChangeWindow.clear();
      }
    }

    // ── GCD / modulo / constant aim heuristics ────────────────────
    runGcdHeuristics(
        player, data, name, st, absYaw, absPitch, yawChange, pitchChange, yaw, pitch, context);

    // ── Geometry components (share one target scan) ───────────────
    List<EntityPlayer> targets = targetsNear(mc, player, tick);

    // ── Burst ─────────────────────────────────────────────────────
    burstMachine(player, st, tick, yawChange, yaw, targets, context);

    // ── Track ─────────────────────────────────────────────────────
    trackComponent(player, st, yaw, targets, name, context);

    // ── Movement fix ──────────────────────────────────────────────
    movementComponent(mc, player, st, moveX, moveY, moveZ, yaw, targets, name, context);

    // ── Non-combat-gated checks that also run in combat ───────────
    runNonCombatChecks(
        player,
        data,
        name,
        st,
        tick,
        absYaw,
        absPitch,
        yawChange,
        pitchChange,
        yaw,
        pitch,
        moveX,
        moveY,
        moveZ,
        context);

    // ── Shared VL economy ─────────────────────────────────────────
    if (st.aimVl > VL_LIMIT && !st.flaggedThisTick) {
      st.flaggedThisTick = true;
      context.receiveSignal(name, "KillAura", "vl", (int) (st.aimVl / 10.0F));
      st.aimVl = 360.0F;
    }
    if (st.aimVl > 0.0F) {
      st.aimVl = Math.max(0.0F, st.aimVl - VL_FADE_PER_TICK);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── Non-combat-gated checks ────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void runNonCombatChecks(
      EntityPlayer player,
      PlayerCheckData data,
      String name,
      PlayerState st,
      long tick,
      float absYaw,
      float absPitch,
      float yawChange,
      float pitchChange,
      float yaw,
      float pitch,
      double moveX,
      double moveY,
      double moveZ,
      ClientAntiCheatContext context) {

    // ── RotationSpeed extra checks ────────────────────────────────
    checkRotationSpeed(player, data, name, st, tick, yaw, pitch, context);

    // ── Tool switch ───────────────────────────────────────────────
    checkToolSwitch(player, data, name, context);

    // ── NoSwing ───────────────────────────────────────────────────
    checkNoSwing(player, data, name, context);

    // ── Latency freeze/burst ──────────────────────────────────────
    checkLatency(player, data, name, absYaw, absPitch, context);

    // ── Snap patterns (AngleSnap) ─────────────────────────────────
    checkSnapPatterns(player, data, name, absYaw, yawChange, pitchChange, yaw, context);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── addVl / fire helpers ────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void addVl(
      PlayerState st, float vl, ClientAntiCheatContext context, String name, String detail) {
    st.aimVl += vl;
  }

  /** Direct alert for confirmed cheats (bypasses VL pool). */
  private void fireDirect(
      EntityPlayer player, PlayerState st, String reason, int vl, ClientAntiCheatContext context) {
    st.directAlertHits++;
    st.flaggedThisTick = true;
    if (player != null) {
      context.receiveSignal(player.getName(), "KillAura", reason, vl);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── HEURISTIC: analyzeWindow (Rain's AimBasicCheck) ────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void analyzeWindow(
      EntityPlayer player,
      PlayerState st,
      List<Float> window,
      String name,
      ClientAntiCheatContext context) {
    float yawChangeFirst = window.get(0);
    float oldYawChange = yawChangeFirst;
    int machineKnownMovement = 0;
    int constantRotations = 0;
    int robotizedAmount = 0;
    int bigSwingUp = 0;
    int bigSwingDown = 0;

    for (float yawChange : window) {
      float robotized = Math.abs(yawChange - yawChangeFirst);
      float diffBetweenYawChanges = yawChange - oldYawChange;
      if (robotized < QUANTUM * 1.5F && yawChange > QUANTUM * 2.0F) {
        ++robotizedAmount;
      }
      if (robotized < QUANTUM && yawChange > QUANTUM * 3.0F) {
        ++machineKnownMovement;
      }
      if (robotized < QUANTUM * 0.5F && yawChange > QUANTUM * 2.5F) {
        ++constantRotations;
      }
      if (diffBetweenYawChanges > 12.0F) {
        ++bigSwingUp;
      }
      if (diffBetweenYawChanges < -12.0F) {
        ++bigSwingDown;
      }
      oldYawChange = yawChange;
    }

    // Rain weights: aim=100, constant=65, sync=50
    if (machineKnownMovement > 8) {
      addVl(st, 100.0F, context, name, "heuristic(aim)");
    }
    if (constantRotations > 6) {
      addVl(st, 65.0F, context, name, "heuristic(constant)");
    }
    if (robotizedAmount > 8) {
      addVl(st, 50.0F, context, name, "heuristic(sync)");
    }

    // pattern(snap): both-direction big jumps with persistence
    if (bigSwingUp > 1 && bigSwingDown > 1 && bigSwingUp + bigSwingDown > 4) {
      ++st.snapStreak;
      if (st.snapStreak > 2) {
        addVl(st, 55.0F, context, name, "pattern(snap)");
      }
    } else {
      st.snapStreak = 0;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── HEURISTIC: GCD / modulo / constant aim / stdDev / exact rotation ───
  // ═══════════════════════════════════════════════════════════════════════════

  private void runGcdHeuristics(
      EntityPlayer player,
      PlayerCheckData data,
      String name,
      PlayerState st,
      float absYaw,
      float absPitch,
      float yawChange,
      float pitchChange,
      float yaw,
      float pitch,
      ClientAntiCheatContext context) {

    // ── Constant aim check ──────────────────────────────────────────
    // If yaw acceleration is very low while yaw delta is high → robotic
    float accelYaw = data.yawAcceleration;
    if (absYaw > 2.0F && Math.abs(accelYaw) < 0.0005F) {
      addVl(st, 10.0F, context, name, "constantAim");
    }

    // ── Modulo check ────────────────────────────────────────────────
    float divisorX = yawChange % 1.5F;
    float divisorY = pitchChange % 1.5F;
    float divisorGcdX = yawChange % 0.05F;
    float divisorGcdY = pitchChange % 0.05F;

    if (absYaw > 5.0F && (Math.abs(divisorX) < 0.001F || Math.abs(divisorY) < 0.001F)) {
      addVl(st, 15.0F, context, name, "modulo(macro)");
    } else if (absYaw > 2.0F && Math.abs(divisorGcdX) < 0.001F && Math.abs(divisorGcdY) < 0.001F) {
      addVl(st, 8.0F, context, name, "modulo(gcd)");
    }

    // ── Sensitivity change check ─────────────────────────────────────
    if (data.sensitivityChangeCount > 5) {
      addVl(st, 12.0F, context, name, "sensitivityChange");
    }

    // ── StdDev analysis ──────────────────────────────────────────────
    if (absYaw > 0.5F) {
      st.yawGcdDeltaSamples.add(absYaw);
      st.pitchGcdDeltaSamples.add(absPitch);
      if (st.yawGcdDeltaSamples.size() >= STDDEV_SAMPLE_SIZE) {
        double yawStdDev = StatisticalUtils.standardDeviation(st.yawGcdDeltaSamples);
        double pitchStdDev = StatisticalUtils.standardDeviation(st.pitchGcdDeltaSamples);
        if (yawStdDev < LOW_STDDEV_THRESHOLD
            && pitchStdDev < LOW_STDDEV_THRESHOLD
            && absYaw > 3.0F) {
          addVl(st, 25.0F, context, name, "stdDev(low)");
        } else if (yawStdDev < CONSISTENT_STDDEV_THRESHOLD
            && pitchStdDev < CONSISTENT_STDDEV_THRESHOLD
            && StatisticalUtils.coefficientOfVariation(st.yawGcdDeltaSamples) < 0.06D) {
          addVl(st, 12.0F, context, name, "stdDev(consistent)");
        }
        st.yawGcdDeltaSamples.clear();
        st.pitchGcdDeltaSamples.clear();
      }
    }

    // ── Exact rotation check ─────────────────────────────────────────
    if (data.nearestTarget != null && data.nearestTargetDistance < 6.0D) {
      EntityPlayer target = data.nearestTarget;
      float perfectYaw = yawTo(player, target);
      float perfectPitch = pitchTo(player, target);
      float yawError = Math.abs(MathHelper.wrapAngleTo180_float(yaw - perfectYaw));
      float pitchError = Math.abs(pitch - perfectPitch);

      if (yawError < 0.08F && pitchError < 0.08F && absYaw > 5.0F) {
        addVl(st, 20.0F, context, name, "exactRotation(perfect)");
      } else if (yawError < 0.3F && pitchError < 0.3F && absYaw > 15.0F) {
        addVl(st, 12.0F, context, name, "exactRotation(close)");
      }
    }

    // ── Modulo / GCD reset check ─────────────────────────────────────
    if (data.lastSensitivityGcd > 0.001F) {
      if (st.hasGcd && st.lastGcd > 0.001F) {
        float gcdDiff = Math.abs(data.lastSensitivityGcd - st.lastGcd);
        if (gcdDiff > 0.05F && absYaw > 3.0F) {
          float moduloOfOldGcd = yawChange % st.lastGcd;
          if (moduloOfOldGcd < 0.01F || Math.abs(moduloOfOldGcd - st.lastGcd) < 0.01F) {
            addVl(st, 18.0F, context, name, "gcdReset");
          }
        }
      }
      st.lastGcd = data.lastSensitivityGcd;
      st.hasGcd = true;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── BURST MACHINE (silent snap / return) (Rain) ─────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void burstMachine(
      EntityPlayer player,
      PlayerState st,
      long tick,
      float yawChange,
      float currentYaw,
      List<EntityPlayer> targets,
      ClientAntiCheatContext context) {
    float absYaw = Math.abs(yawChange);

    if (st.burstTicks > 0) {
      boolean sameDir = yawChange * st.burstDir >= 0.0F;
      if (absYaw < BURST_QUIET) {
        if (st.burstSum >= BURST_SUM_MIN) {
          evaluateBurst(player, st, tick, targets, currentYaw, context);
        }
        resetBurst(st);
        st.quietTicks = 1;
      } else if (sameDir) {
        ++st.burstTicks;
        st.burstSum += absYaw;
        if (st.burstTicks > BURST_MAX_TICKS) {
          st.burstTicks = -1;
        }
      } else if (absYaw > BURST_STEP_MIN) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = currentYaw - yawChange;
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
        st.preBurstYaw = currentYaw - yawChange;
        st.quietTicks = 0;
      } else if (absYaw < BURST_QUIET) {
        ++st.quietTicks;
      } else {
        st.quietTicks = 0;
      }
    }
  }

  private void evaluateBurst(
      EntityPlayer player,
      PlayerState st,
      long tick,
      List<EntityPlayer> targets,
      float currentYaw,
      ClientAntiCheatContext context) {
    if (targets.isEmpty()) return;

    float bestErr = Float.MAX_VALUE;
    float bestPre = 0.0F;
    float bestPreInside = Float.MAX_VALUE;

    for (EntityPlayer target : targets) {
      Trail trail = trail(target.getUniqueID());
      float err = minInsideError(player, trail, currentYaw);
      if (err < bestErr) {
        bestErr = err;
        float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
        bestPre = Math.abs(MathHelper.wrapAngleTo180_float(st.preBurstYaw - bearingNow));
      }
      bestPreInside = Math.min(bestPreInside, minInsideError(player, trail, st.preBurstYaw));
    }

    // silent(snap): burst lands inside target hitbox after starting far off
    if (bestErr <= QUANTUM && bestPre > SNAP_PRE_ERROR_MIN) {
      ++st.snapHits;
      st.lastSnapHitTick = tick;

      if (st.snapHits >= SNAP_MIN_HITS && st.snapHits > st.snapMisses) {
        // DIRECT alert — confirmed silent aim snap
        addVl(st, SNAP_VL, context, player != null ? player.getName() : null, "silent(snap)");
        if (context != null && player != null && !st.flaggedThisTick) {
          st.flaggedThisTick = true;
          context.receiveSignal(
              player.getName(), "KillAura", "silent(snap)", (int) (st.aimVl / 10.0F));
        }
        st.snapHits = 0;
      }
    } else if (bestPreInside <= QUANTUM && bestErr > SNAP_PRE_ERROR_MIN * 0.75F) {
      // silent(return): burst left a target shortly after a snap hit
      if (st.lastSnapHitTick != Long.MIN_VALUE && tick - st.lastSnapHitTick <= RETURN_PAIR_TICKS) {
        addVl(st, RETURN_VL, context, player != null ? player.getName() : null, "silent(return)");
      }
    } else if (bestPre > SNAP_PRE_ERROR_MIN && bestErr > QUANTUM * 2.0F) {
      ++st.snapMisses;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── TRACK COMPONENT (lock-on) (Rain) ────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void trackComponent(
      EntityPlayer player,
      PlayerState st,
      float yaw,
      List<EntityPlayer> targets,
      String name,
      ClientAntiCheatContext context) {
    if (targets.isEmpty()) {
      st.lastTargetId = null;
      st.lastBearing = Float.NaN;
      return;
    }

    EntityPlayer target = targets.get(0);
    double bestDistSq = player.getDistanceSqToEntity(target);
    for (int i = 1; i < targets.size(); i++) {
      double dsq = player.getDistanceSqToEntity(targets.get(i));
      if (dsq < bestDistSq) {
        bestDistSq = dsq;
        target = targets.get(i);
      }
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
        if (minInsideError(player, trail, yaw) <= QUANTUM * 0.5F) {
          ++st.trackTicks;
        }
        if (st.trackSamples >= TRACK_WINDOW) {
          if ((float) st.trackTicks >= TRACK_RATIO * (float) st.trackSamples) {
            addVl(st, TRACK_VL, context, name, "silent(track)");
          }
          st.trackSamples = 0;
          st.trackTicks = 0;
        }
      }
    }
    st.lastTargetId = targetId;
    st.lastBearing = bearingNow;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── MOVEMENT FIX DESYNC (body/head desync) (Rain) ──────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void movementComponent(
      Minecraft mc,
      EntityPlayer player,
      PlayerState st,
      double moveX,
      double moveY,
      double moveZ,
      float yaw,
      List<EntityPlayer> targets,
      String name,
      ClientAntiCheatContext context) {

    if (++st.moveTickCounter >= MOVE_DECAY_TICKS) {
      st.moveTickCounter = 0;
      st.lockDesync = Math.max(0, st.lockDesync - 1);
      st.sprintDesync = Math.max(0, st.sprintDesync - 1);
    }

    boolean flat =
        st.hasVel && Math.abs(moveY) < MOVE_FLAT_DY && Math.abs(st.lastMoveY) < MOVE_FLAT_DY;
    boolean haveAccel = st.hasVel;
    double ax = moveX - st.lastVelX;
    double az = moveZ - st.lastVelZ;
    st.lastVelX = moveX;
    st.lastVelZ = moveZ;
    st.lastMoveY = moveY;
    st.hasVel = true;
    if (!haveAccel) return;

    double accel = Math.sqrt(ax * ax + az * az);
    double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);

    if (!flat || player.hurtTime > 0 || speed < MOVE_MIN_SPEED || speed > MOVE_MAX_SPEED) {
      return;
    }
    Block ground =
        mc.theWorld
            .getBlockState(new BlockPos(player.posX, player.posY - 0.5D, player.posZ))
            .getBlock();
    if (ground == Blocks.ice || ground == Blocks.packed_ice) return;

    float moveBearing = (float) Math.toDegrees(Math.atan2(-moveX, moveZ));
    float offset = MathHelper.wrapAngleTo180_float(moveBearing - yaw);
    float residual = bucketResidual(offset);

    // Sprint leak
    if (player.isSprinting()
        && speed > SPRINT_MIN_SPEED
        && accel < SPRINT_ACCEL
        && Math.abs(offset) > SPRINT_OFFSET) {
      ++st.sprintDesync;
      if (st.sprintDesync >= SPRINT_HITS) {
        addVl(st, MOVE_SPRINT_VL, context, name, "movement(sprint)");
        st.sprintDesync -= SPRINT_HITS;
      }
    }

    if (accel > MOVE_SMOOTH_ACCEL) return;

    // Lock desync
    if (residual > LOCK_RESIDUAL) {
      for (EntityPlayer target : targets) {
        if (minInsideError(player, trail(target.getUniqueID()), yaw) <= QUANTUM) {
          ++st.lockDesync;
          if (st.lockDesync >= LOCK_HITS) {
            addVl(st, MOVE_LOCK_VL, context, name, "movement(lock)");
            st.lockDesync -= LOCK_HITS;
          }
          break;
        }
      }
    }

    // Windowed mean residual
    ++st.moveSamples;
    st.residualSum += residual;
    if (residual > MOVE_DESYNC_RESIDUAL) ++st.moveDesyncTicks;

    if (st.moveSamples >= MOVE_WINDOW) {
      float mean = st.residualSum / (float) st.moveSamples;
      if (mean > MOVE_MEAN_LIMIT) {
        addVl(st, MOVE_VL, context, name, "movement(fix)");
      }
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── CONSUME CHECK (Rain) ───────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void consumeCheck(
      EntityPlayer player,
      PlayerState st,
      long tick,
      String name,
      PlayerCheckData data,
      ClientAntiCheatContext context) {
    ItemStack held = player.getHeldItem();
    boolean using = player.isUsingItem();
    boolean consumable = held != null && isConsumable(held.getItem());
    // USE data.startedSwinging() — the raw swingProgressInt is always true
    // during sustained attacks, but startedSwinging() only triggers on transition.
    boolean attacking = data.startedSwinging();

    if (using && consumable) {
      ++st.useItemTicks;
    } else {
      if (st.useItemTicks > 0) st.lastEatTick = tick;
      st.useItemTicks = 0;
    }

    long sinceEat = tick - st.lastEatTick;
    if (attacking && st.useItemTicks > MIN_USE_TIME && sinceEat < EAT_TIMEOUT && consumable) {
      ++st.consumeVl;
      if (st.consumeVl >= CONSUME_FAIL_VL) {
        addVl(st, CONSUME_VL_ADD, context, name, "consume");
        st.consumeVl = 0;
      }
    } else if (st.consumeVl > 0) {
      --st.consumeVl;
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── ROTATION SPEED EXTRAS (attack rate, wide-angle, accuracy) ──────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void checkRotationSpeed(
      EntityPlayer player,
      PlayerCheckData data,
      String name,
      PlayerState st,
      long tick,
      float yaw,
      float pitch,
      ClientAntiCheatContext context) {

    if (data.recentlyTeleported() || data.recentlyHurt()) return;
    if (!data.startedSwinging()) return;
    if (data.nearestTarget == null || data.nearestTargetDistance > 6.0D) return;

    CheckBuffer rateBuffer = rateBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer aimBuffer = aimBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer accuracyBuffer = accuracyBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    long lastAttack = lastAttackTicks.getOrDefault(name, tick - 20L);
    long delay = tick - lastAttack;
    lastAttackTicks.put(name, tick);

    // ── Attack speed ────────────────────────────────────────────────
    if (delay > 0L && delay < 3L) {
      rateBuffer.flag(1.0D, 999.0D);
    } else {
      rateBuffer.decay(0.4D);
    }

    // ── Wide-angle silent aim ───────────────────────────────────────
    EntityPlayer target = data.nearestTarget;
    float yawError = Math.abs(MathHelper.wrapAngleTo180_float(yawTo(player, target) - yaw));
    float pitchError = Math.abs(pitchTo(player, target) - pitch);
    if (yawError > 45.0F || pitchError > 35.0F) {
      aimBuffer.flag(1.25D, 999.0D);
    } else {
      aimBuffer.decay(0.5D);
    }

    // ── Hit accuracy ────────────────────────────────────────────────
    Queue<Double> accuracySamples =
        hitAccuracySamples.computeIfAbsent(name, k -> new ArrayDeque<>());
    double totalAngleError = yawError + pitchError;
    double accuracy = Math.max(0.0, 1.0 - (totalAngleError / 90.0));
    accuracySamples.add(accuracy);

    if (accuracySamples.size() >= ACCURACY_SAMPLE_SIZE) {
      double avgAccuracy = 0.0;
      for (double sample : accuracySamples) avgAccuracy += sample;
      avgAccuracy /= accuracySamples.size();

      boolean highAccuracy = avgAccuracy > HIGH_ACCURACY_THRESHOLD;

      double varianceSum = 0.0;
      for (double sample : accuracySamples) {
        varianceSum += (sample - avgAccuracy) * (sample - avgAccuracy);
      }
      double variance = varianceSum / accuracySamples.size();
      boolean lowVariance = variance < 0.002D;

      if (highAccuracy || (lowVariance && avgAccuracy > 0.85D)) {
        accuracyBuffer.flag(highAccuracy ? 1.5D : 1.0D, 999.0D);
      } else {
        accuracyBuffer.decay(0.25D);
      }
      accuracySamples.clear();
    }

    // ── Fire signals ────────────────────────────────────────────────
    if (rateBuffer.get() > 4.0D && aimBuffer.get() > 2.0D) {
      addVl(st, 15.0F, context, name, "aimRate");
      rateBuffer.reset();
      aimBuffer.reset();
    }
    if (accuracyBuffer.get() > 3.0D) {
      addVl(st, 12.0F, context, name, "accuracy");
      accuracyBuffer.reset();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── TOOL SWITCH CHECK ───────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void checkToolSwitch(
      EntityPlayer player, PlayerCheckData data, String name, ClientAntiCheatContext context) {
    CheckBuffer switchBuffer = switchBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer combatSwitchBuffer =
        combatSwitchBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    if (data.heldItemChangeTicks > 4) {
      switchBuffer.flag(1.0D, 999.0D);
    } else {
      switchBuffer.decay(0.25D);
    }

    boolean inCombat =
        data.startedSwinging() && data.nearestTarget != null && data.nearestTargetDistance < 5.0D;

    if (inCombat && data.heldItemSlot != data.lastHeldItemSlot) {
      ItemStack currentItem = player.getHeldItem();
      boolean holdsSword = currentItem != null && currentItem.getItem() instanceof ItemSword;
      if (holdsSword && data.heldItemChangeTicks > 0) {
        combatSwitchBuffer.flag(1.0D, 999.0D);
      } else {
        combatSwitchBuffer.decay(0.3D);
      }
    } else {
      combatSwitchBuffer.decay(0.3D);
    }

    if (switchBuffer.get() > 6.0D) {
      addVl(
          states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
          12.0F,
          context,
          name,
          "rapidSwitch");
      switchBuffer.reset();
    }
    if (combatSwitchBuffer.get() > 6.0D) {
      addVl(
          states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
          12.0F,
          context,
          name,
          "combatSwitch");
      combatSwitchBuffer.reset();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── NO SWING CHECK ─────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void checkNoSwing(
      EntityPlayer player, PlayerCheckData data, String name, ClientAntiCheatContext context) {
    CheckBuffer buffer = noSwingBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    LinkedList<Integer> swingHistory =
        swingProgressHistory.computeIfAbsent(name, k -> new LinkedList<>());
    swingHistory.addFirst(player.swingProgressInt);
    if (swingHistory.size() > SWING_HISTORY_SIZE) swingHistory.removeLast();

    if (data.nearestTarget != null && data.nearestTargetDistance < 4.5D) {
      EntityPlayer target = data.nearestTarget;
      int prevHurtTime = lastTargetHurtTime.getOrDefault(name, 0);
      lastTargetHurtTime.put(name, target.hurtTime);

      boolean targetJustHurt = prevHurtTime == 0 && target.hurtTime > 0;

      if (targetJustHurt) {
        boolean noSwingRecent = true;
        for (int sw : swingHistory) {
          if (sw > 0) {
            noSwingRecent = false;
            break;
          }
        }
        if (noSwingRecent) {
          buffer.flag(2.0D, NO_SWING_VL_THRESHOLD);
          if (buffer.get() >= NO_SWING_VL_THRESHOLD) {
            addVl(
                states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
                15.0F,
                context,
                name,
                "noSwing");
            buffer.reset();
          }
        } else {
          buffer.decay(0.5D);
        }
      }
    } else {
      buffer.decay(0.1D);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── LATENCY CHECK (freeze / burst) ─────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void checkLatency(
      EntityPlayer player,
      PlayerCheckData data,
      String name,
      float absYaw,
      float absPitch,
      ClientAntiCheatContext context) {
    if (data.recentlyTeleported() || data.recentlyHurt()) return;
    if (player.isDead || player.ticksExisted < 40) return;

    CheckBuffer freezeBuffer = freezeBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer burstBuffer = burstBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    boolean frozen =
        data.totalDelta < 0.002D && absYaw < 0.02F && absPitch < 0.02F && data.stillTicks > 3;

    if (frozen) {
      frozenTicks.put(name, frozenTicks.getOrDefault(name, 0) + 1);
      return;
    }

    int frozenBefore = frozenTicks.getOrDefault(name, 0);
    frozenTicks.remove(name);

    if (frozenBefore < 10) {
      freezeBuffer.decay(0.15D);
      burstBuffer.decay(0.15D);
      return;
    }

    boolean targetNearby = data.nearestTarget != null && data.nearestTargetDistance < 6.0D;
    boolean burst = data.totalDelta > 0.8D && data.totalDelta < 8.0D;
    boolean attacked = data.startedSwinging();

    if (targetNearby && burst && attacked && data.burstTicks > 2) {
      burstBuffer.flag(1.5D, 7.0D);
      if (burstBuffer.get() >= 7.0D) {
        addVl(
            states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
            18.0F,
            context,
            name,
            "latencyBurst");
        burstBuffer.reset();
      }
    } else if (targetNearby && burst) {
      freezeBuffer.flag(1.0D, 6.0D);
      if (freezeBuffer.get() >= 6.0D) {
        addVl(
            states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
            12.0F,
            context,
            name,
            "latencyFreeze");
        freezeBuffer.reset();
      }
    } else {
      freezeBuffer.decay(0.2D);
      burstBuffer.decay(0.2D);
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── SNAP PATTERNS (AngleSnap from old Miau-main) ───────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private void checkSnapPatterns(
      EntityPlayer player,
      PlayerCheckData data,
      String name,
      float absYaw,
      float yawChange,
      float pitchChange,
      float yaw,
      ClientAntiCheatContext context) {
    if (data.recentlyTeleported()) return;

    CheckBuffer snapPatternBuffer =
        snapPatternBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer silentAimBuf = silentAimBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer entitySnapBuffer = entitySnapBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer flickBuffer = flickBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    // ── Three-tick snap pattern ──────────────────────────────────────
    float[] yawHist = yawHistory.computeIfAbsent(name, k -> new float[3]);
    yawHist[2] = yawHist[1];
    yawHist[1] = yawHist[0];
    yawHist[0] = absYaw;

    boolean threeTickSnap = yawHist[2] < 9.0F && yawHist[1] > 55.0F && yawHist[0] < 9.0F;
    boolean liteSnap = yawHist[2] < 9.0F && yawHist[1] > 35.0F && yawHist[0] < 9.0F;

    if (threeTickSnap) {
      snapPatternBuffer.flag(2.0D, 999.0D);
    } else if (liteSnap) {
      snapPatternBuffer.flag(1.0D, 999.0D);
    } else {
      snapPatternBuffer.decay(0.2D);
    }

    // ── Silent aim (movement stable + yaw snap) ──────────────────────
    float[] movYawHist = movementYawHistory.computeIfAbsent(name, k -> new float[2]);
    float movementYaw = (float) (Math.atan2(data.deltaZ, data.deltaX) * 180.0 / Math.PI);
    movYawHist[1] = movYawHist[0];
    movYawHist[0] = movementYaw;

    float movementYawDelta =
        Math.abs(MathHelper.wrapAngleTo180_float(movYawHist[0] - movYawHist[1]));
    boolean movementStable = movementYawDelta < 5.0F && data.horizontalDelta > 0.1D;
    boolean aimSnapped = absYaw > 45.0F;

    if (movementStable
        && aimSnapped
        && data.nearestTarget != null
        && data.nearestTargetDistance < 5.0D) {
      silentAimBuf.flag(1.5D, 999.0D);
    } else {
      silentAimBuf.decay(0.25D);
    }

    // ── Entity snap (wasn't looking → exactly looking) ───────────────
    if (data.nearestTarget != null && data.nearestTargetDistance < 6.0D && absYaw > 30.0F) {
      EntityPlayer target = data.nearestTarget;
      float yawToTarget = yawTo(player, target);

      float currentError = Math.abs(MathHelper.wrapAngleTo180_float(yaw - yawToTarget));
      // Approximate last error without storing all rotations
      boolean wasNotLooking = data.lastYawDelta > 30.0F || currentError > 30.0F;
      boolean nowLooking = currentError < 5.0F;

      if (wasNotLooking && nowLooking) {
        entitySnapBuffer.flag(1.5D, 999.0D);
      } else {
        entitySnapBuffer.decay(0.25D);
      }
    } else {
      entitySnapBuffer.decay(0.2D);
    }

    // ── Flick patterns ───────────────────────────────────────────────
    if (absYaw > 120.0F || data.yawAcceleration > 85.0F) {
      flickBuffer.flag(1.0D, 999.0D);
    } else {
      flickBuffer.decay(0.35D);
    }

    // ── Fire signals ────────────────────────────────────────────────
    if (snapPatternBuffer.get() > 5.0D) {
      addVl(
          states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
          10.0F,
          context,
          name,
          "snapPattern");
      snapPatternBuffer.reset();
    }
    if (silentAimBuf.get() > 5.0D) {
      addVl(
          states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
          10.0F,
          context,
          name,
          "silentAimSnap");
      silentAimBuf.reset();
    }
    if (entitySnapBuffer.get() > 5.0D) {
      addVl(
          states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
          12.0F,
          context,
          name,
          "entitySnap");
      entitySnapBuffer.reset();
    }
    if (flickBuffer.get() > 5.0D) {
      addVl(
          states.computeIfAbsent(player.getUniqueID(), k -> new PlayerState()),
          8.0F,
          context,
          name,
          "flick");
      flickBuffer.reset();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── GEOMETRY HELPERS ────────────────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  private List<EntityPlayer> targetsNear(Minecraft mc, EntityPlayer attacker, long tick) {
    List<EntityPlayer> out = new ArrayList<>();
    if (mc.theWorld == null) return out;
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (!PlayerEligibility.shouldUseAsTarget(p, attacker)) continue;
      double dx = p.posX - attacker.posX;
      double dy = p.posY - attacker.posY;
      double dz = p.posZ - attacker.posZ;
      if (dx * dx + dy * dy + dz * dz > TARGET_RANGE_SQ) continue;
      trail(p.getUniqueID()).push(p.posX, p.posZ, tick);
      out.add(p);
    }
    return out;
  }

  private float minInsideError(EntityPlayer attacker, Trail trail, float yaw) {
    float best = Float.MAX_VALUE;
    for (int i = 0; i < trail.size; ++i) {
      double dx = trail.x[i] - attacker.posX;
      double dz = trail.z[i] - attacker.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);
      if (horizDist < 0.5D) continue;
      float bearing = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
      float err = Math.abs(MathHelper.wrapAngleTo180_float(yaw - bearing));
      float halfWidth = (float) Math.toDegrees(Math.atan2(HITBOX_HALF_WIDTH, horizDist));
      best = Math.min(best, Math.max(0.0F, err - halfWidth));
    }
    return best;
  }

  private static float bearingTo(EntityPlayer attacker, double x, double z) {
    double dx = x - attacker.posX;
    double dz = z - attacker.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private static float bucketResidual(float offset) {
    float nearest = 45.0F * Math.round(offset / 45.0F);
    return Math.abs(MathHelper.wrapAngleTo180_float(offset - nearest));
  }

  private static float yawTo(EntityPlayer from, EntityPlayer to) {
    double dx = to.posX - from.posX;
    double dz = to.posZ - from.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private static float pitchTo(EntityPlayer from, EntityPlayer to) {
    double dx = to.posX - from.posX;
    double dy = (to.posY + to.getEyeHeight()) - (from.posY + from.getEyeHeight());
    double dz = to.posZ - from.posZ;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    return (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
  }

  private static boolean isConsumable(Item item) {
    return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
  }

  private Trail trail(UUID uuid) {
    return trails.computeIfAbsent(uuid, k -> new Trail());
  }

  private void resetBurst(PlayerState st) {
    st.burstTicks = 0;
    st.burstSum = 0.0F;
    st.burstDir = 0.0F;
  }

  private void resetSession(PlayerState st) {
    resetBurst(st);
    st.yawChangeWindow.clear();
    st.snapStreak = 0;
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
    st.consumeVl = 0;
    st.yawGcdDeltaSamples.clear();
    st.pitchGcdDeltaSamples.clear();
    st.hasGcd = false;
    st.lastGcd = 0;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // ── PUBLIC: lifecycle management ────────────────────────────────────────
  // ═══════════════════════════════════════════════════════════════════════════

  public void forgetPlayer(UUID uuid) {
    if (uuid == null) return;
    states.remove(uuid);
    trails.remove(uuid);
    String key = uuid.toString();
    lastAttackTicks.remove(key);
    hitAccuracySamples.remove(key);
    switchBuffers.remove(key);
    combatSwitchBuffers.remove(key);
    noSwingBuffers.remove(key);
    lastTargetHurtTime.remove(key);
    swingProgressHistory.remove(key);
    freezeBuffers.remove(key);
    burstBuffers.remove(key);
    frozenTicks.remove(key);
    snapPatternBuffers.remove(key);
    yawHistory.remove(key);
    silentAimBuffers.remove(key);
    movementYawHistory.remove(key);
    entitySnapBuffers.remove(key);
    flickBuffers.remove(key);
  }

  public void reset() {
    states.clear();
    trails.clear();
    lastAttackTicks.clear();
    rateBuffers.clear();
    aimBuffers.clear();
    accuracyBuffers.clear();
    hitAccuracySamples.clear();
    switchBuffers.clear();
    combatSwitchBuffers.clear();
    noSwingBuffers.clear();
    lastTargetHurtTime.clear();
    swingProgressHistory.clear();
    freezeBuffers.clear();
    burstBuffers.clear();
    frozenTicks.clear();
    snapPatternBuffers.clear();
    yawHistory.clear();
    silentAimBuffers.clear();
    movementYawHistory.clear();
    entitySnapBuffers.clear();
    flickBuffers.clear();
  }
}
