package myau.module.modules.misc;

import java.util.*;
import myau.clientanticheat.AlertManager;
import myau.clientanticheat.PlayerEligibility;
import myau.clientanticheat.checks.*;
import myau.event.EventTarget;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Advanced HackerDetector module. Uses Rain Anticheat for KillAura + Scaffold + AutoBlock. Uses
 * custom checks for the rest (Reach, Velocity, NoSlow, Blink, Sprint, AutoClicker).
 */
public class Anticheat extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  // ── Toggles ─────────────────────────────────────────────────────────
  public final BooleanProperty enableKillaura = new BooleanProperty("killaura", true);
  public final BooleanProperty enableAutoBlock = new BooleanProperty("autoblock", true);
  public final BooleanProperty enableScaffold = new BooleanProperty("scaffold", true);
  public final BooleanProperty enableReach = new BooleanProperty("reach", true);
  public final BooleanProperty enableVelocity = new BooleanProperty("velocity", true);
  public final BooleanProperty enableNoSlow = new BooleanProperty("noslow", true);
  public final BooleanProperty enableBlink = new BooleanProperty("blink", true);
  public final BooleanProperty enableSprint = new BooleanProperty("sprint", true);
  public final BooleanProperty enableAutoClicker = new BooleanProperty("autoclicker", true);
  public final BooleanProperty addTarget = new BooleanProperty("add-target", true);
  public final BooleanProperty sound = new BooleanProperty("sound", true);

  // ── Rain checks (KillAura, Scaffold, AutoBlock) ─────────────────────
  private final RainKillauraCheck killauraCheck = new RainKillauraCheck();
  private final RainScaffoldCheck scaffoldCheck = new RainScaffoldCheck();
  private final RainAutoBlockCheck autoBlockCheck = new RainAutoBlockCheck();

  // ── New checks ──────────────────────────────────────────────────────
  private final ReachCheck reachCheck = new ReachCheck();
  private final VelocityCheck velocityCheck = new VelocityCheck();
  private final NoSlowCheck noSlowCheck = new NoSlowCheck();
  private final BlinkCheck blinkCheck = new BlinkCheck();
  private final SprintCheck sprintCheck = new SprintCheck();
  private final ClickerCheck clickerCheck = new ClickerCheck();

  // ── Shared state ────────────────────────────────────────────────────
  private final Set<UUID> flaggedPlayers = new HashSet<>();
  private long lastAlertSoundTime;
  private int flagCount;
  private static final int MAX_FLAG_COUNT = 10;

  public Anticheat() {
    super("Anticheat", false, false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.POST
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    if (mc.isSingleplayer()) {
      return;
    }

    long currentTick = mc.theWorld.getTotalWorldTime();

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == null || player.isDead) continue;
      UUID uuid = player.getUniqueID();
      if (uuid == null) continue;

      if (!PlayerEligibility.shouldCheckPlayer(player) || this.flaggedPlayers.contains(uuid)) {
        continue;
      }

      // ── KillAura (Rain port) ──────────────────────────────────
      if (this.enableKillaura.getValue()) {
        this.killauraCheck.check(player, currentTick);
      }

      // ── Scaffold (Rain port) ──────────────────────────────────
      if (this.enableScaffold.getValue()) {
        this.scaffoldCheck.check(player);
      }

      // ── AutoBlock (Rain port) ─────────────────────────────────
      if (this.enableAutoBlock.getValue()) {
        this.autoBlockCheck.check(player);
      }

      // ── Reach ─────────────────────────────────────────────────
      if (this.enableReach.getValue()) {
        // Reach is handled via AttackEvent in mixin layer if available
        // For now, we do simple distance check
        if (player.isSwingInProgress) {
          // Simple reach check: flag if swinging at > 4.5 blocks
          EntityPlayer nearest = getNearestTarget(player);
          if (nearest != null) {
            double dist = player.getDistanceToEntity(nearest);
            if (dist > 4.5D) {
              AlertManager.flag(
                  player.getName(), "Reach", String.format("%.2f blocks", dist), (int) (dist * 10));
            }
          }
        }
      }

      // ── Velocity ──────────────────────────────────────────────
      if (this.enableVelocity.getValue()) {
        this.velocityCheck.check(player);
      }

      // ── NoSlow ────────────────────────────────────────────────
      if (this.enableNoSlow.getValue()) {
        this.noSlowCheck.check(player);
      }

      // ── Blink ─────────────────────────────────────────────────
      if (this.enableBlink.getValue()) {
        this.blinkCheck.check(player);
      }

      // ── Sprint ────────────────────────────────────────────────
      if (this.enableSprint.getValue()) {
        this.sprintCheck.check(player);
      }

      // ── AutoClicker ───────────────────────────────────────────
      if (this.enableAutoClicker.getValue()) {
        this.clickerCheck.check(player, currentTick);
      }
    }
  }

  private EntityPlayer getNearestTarget(EntityPlayer attacker) {
    EntityPlayer nearest = null;
    double nearestDist = Double.MAX_VALUE;
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (p == attacker || p.isDead || !PlayerEligibility.isRealPlayer(p)) continue;
      double dist = attacker.getDistanceToEntity(p);
      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = p;
      }
    }
    return nearest;
  }

  @Override
  public void onDisabled() {
    this.clearAll();
  }

  public void clearAll() {
    this.flaggedPlayers.clear();
    this.flagCount = 0;
    this.lastAlertSoundTime = 0;

    this.killauraCheck.reset();
    this.scaffoldCheck.reset();
    this.autoBlockCheck.reset();
    this.reachCheck.reset();
    this.velocityCheck.reset();
    this.noSlowCheck.reset();
    this.blinkCheck.reset();
    this.sprintCheck.reset();
    this.clickerCheck.reset();

    AlertManager.clear();
  }
}
