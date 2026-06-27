package myau.module.modules.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import myau.Myau;
import myau.clientanticheat.AntiCheatAlertStyle;
import myau.clientanticheat.CheckDataManager;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.combat.autoblock.AutoBlockCheck;
import myau.clientanticheat.combat.autoclicker.ClickSpeedCheck;
import myau.clientanticheat.combat.killaura.KillAuraAngleSnap;
import myau.clientanticheat.combat.killaura.KillAuraHeuristicsCheck;
import myau.clientanticheat.combat.killaura.KillAuraLatencyCheck;
import myau.clientanticheat.combat.killaura.KillAuraNoSwingCheck;
import myau.clientanticheat.combat.killaura.KillAuraRotationSpeed;
import myau.clientanticheat.combat.killaura.KillAuraToolSwitchCheck;
import myau.clientanticheat.combat.reach.HitboxRaytraceCheck;
import myau.clientanticheat.movement.blink.BlinkCheck;
import myau.clientanticheat.movement.blink.FakeLagCheck;
import myau.clientanticheat.movement.blink.MicroBlinkCheck;
import myau.clientanticheat.movement.noslow.NoSlowCheck;
import myau.clientanticheat.movement.velocity.VelocityCheck;
import myau.clientanticheat.player.scaffold.ScaffoldPlacementCheck;
import myau.clientanticheat.player.scaffold.ScaffoldRotationCheck;
import myau.clientanticheat.player.scaffold.ScaffoldSneakCheck;
import myau.event.EventTarget;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class HackerDetector extends Module implements ClientAntiCheatContext {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final int FLAG_WINDOW_SECONDS = 8;
  private static final int ALERT_COOLDOWN_SECONDS = 5;

  public final BooleanProperty scaffold = new BooleanProperty("scaffold", true);
  public final BooleanProperty killAura = new BooleanProperty("killaura", true);
  public final BooleanProperty autoBlock = new BooleanProperty("autoblock", true);
  public final BooleanProperty noSlow = new BooleanProperty("noslow", true);
  public final BooleanProperty blink = new BooleanProperty("blink", true);
  public final BooleanProperty reach = new BooleanProperty("reach", true);
  public final BooleanProperty velocity = new BooleanProperty("velocity", true);
  public final BooleanProperty autoClicker = new BooleanProperty("autoclicker", true);
  public final BooleanProperty addTarget = new BooleanProperty("add-target", true);
  public final BooleanProperty sound = new BooleanProperty("sound", true);
  public final BooleanProperty self = new BooleanProperty("self", false);

  private final ScaffoldSneakCheck scaffoldSneakCheck = new ScaffoldSneakCheck();
  private final ScaffoldRotationCheck scaffoldRotationCheck = new ScaffoldRotationCheck();
  private final ScaffoldPlacementCheck scaffoldPlacementCheck = new ScaffoldPlacementCheck();
  private final KillAuraHeuristicsCheck kaHeuristicsCheck = new KillAuraHeuristicsCheck();
  private final KillAuraAngleSnap kaAngleSnap = new KillAuraAngleSnap();
  private final KillAuraLatencyCheck kaLatencyCheck = new KillAuraLatencyCheck();
  private final KillAuraRotationSpeed kaRotationSpeed = new KillAuraRotationSpeed();
  private final KillAuraNoSwingCheck kaNoSwingCheck = new KillAuraNoSwingCheck();
  private final KillAuraToolSwitchCheck kaToolSwitchCheck = new KillAuraToolSwitchCheck();
  private final AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
  private final NoSlowCheck noSlowCheck = new NoSlowCheck();
  private final BlinkCheck blinkCheck = new BlinkCheck();
  private final FakeLagCheck fakeLagCheck = new FakeLagCheck();
  private final MicroBlinkCheck microBlinkCheck = new MicroBlinkCheck();
  private final HitboxRaytraceCheck reachCheck = new HitboxRaytraceCheck();
  private final VelocityCheck velocityCheck = new VelocityCheck();
  private final ClickSpeedCheck clickSpeedCheck = new ClickSpeedCheck();
  private final CheckDataManager checkDataManager = new CheckDataManager();
  private final Map<String, int[]> flagMap = new HashMap<>();
  private final Map<String, Integer> alertCooldowns = new HashMap<>();
  private final Set<String> whitelist = new HashSet<>();

  public HackerDetector() {
    super("HackerDetector", false, false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.POST
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }

    World world = mc.theWorld;
    this.checkDataManager.update(world);
    long currentTick = world.getTotalWorldTime();
    for (EntityPlayer player : new ArrayList<>(world.playerEntities)) {
      if (player.isDead || player.getName() == null) {
        continue;
      }
      if (player == mc.thePlayer && !this.self.getValue()) {
        continue;
      }
      PlayerCheckData data = this.checkDataManager.get(player);
      if (this.scaffold.getValue()) {
        this.scaffoldSneakCheck.check(player, world, data, this);
        this.scaffoldRotationCheck.check(player, world, data, this);
        this.scaffoldPlacementCheck.check(player, world, data, this);
      }
      if (this.killAura.getValue()) {
        this.kaHeuristicsCheck.check(player, data, this);
        this.kaAngleSnap.check(player, data, this);
        this.kaLatencyCheck.check(player, data, this);
        this.kaRotationSpeed.check(player, world, data, currentTick, this);
        this.kaNoSwingCheck.check(player, data, this);
        this.kaToolSwitchCheck.check(player, data, currentTick, this);
      }
      if (this.autoBlock.getValue()) {
        this.autoBlockCheck.check(player, data, currentTick, this);
      }
      if (this.noSlow.getValue()) {
        this.noSlowCheck.check(player, data, currentTick, this);
      }
      if (this.blink.getValue()) {
        this.blinkCheck.check(player, data, this);
        this.fakeLagCheck.check(player, data, this);
        this.microBlinkCheck.check(player, data, this);
      }
      if (this.reach.getValue()) {
        this.reachCheck.check(player, world, data, this);
      }
      if (this.velocity.getValue()) {
        this.velocityCheck.check(player, data, this);
      }
      if (this.autoClicker.getValue()) {
        this.clickSpeedCheck.check(player, data, currentTick, this);
      }
    }
    this.pruneFlags();
  }

  @Override
  public void receiveSignal(String playerName, String cheatName) {
    receiveSignal(playerName, cheatName, "behavior anomaly", 0);
  }

  @Override
  public void receiveSignal(String playerName, String cheatName, String detail, int vl) {
    if (playerName == null || playerName.isEmpty() || cheatName == null) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (playerName.equalsIgnoreCase(mc.thePlayer.getName()) && !this.self.getValue()) return;
    if (this.isWhitelisted(playerName)) return;

    int currentTime = (int) (mc.theWorld.getTotalWorldTime() / 20);
    String flagKey = this.getFlagKey(playerName, cheatName);
    int[] flagData = this.flagMap.getOrDefault(flagKey, new int[] {0, currentTime});
    if (currentTime - flagData[1] > FLAG_WINDOW_SECONDS) {
      flagData[0] = 0;
    }
    flagData[0] += 1;
    flagData[1] = currentTime;
    this.flagMap.put(flagKey, flagData);

    int maxFlagCount = this.maxFlagsFor(cheatName);
    int lastAlert = this.alertCooldowns.getOrDefault(flagKey, -ALERT_COOLDOWN_SECONDS);
    if (flagData[0] >= maxFlagCount && currentTime - lastAlert >= ALERT_COOLDOWN_SECONDS) {
      AntiCheatAlertStyle.displayFlag(playerName, cheatName, detail, vl, flagData[0], maxFlagCount);
      if (this.sound.getValue()) {
        mc.thePlayer.playSound("random.orb", 0.3F, 1.0F);
      }
      if (this.addTarget.getValue() && Myau.targetManager != null) {
        Myau.targetManager.add(playerName);
      }
      this.alertCooldowns.put(flagKey, currentTime);
      this.flagMap.remove(flagKey);
    }
  }

  @Override
  public PlayerCheckData getPlayerData(EntityPlayer player) {
    return this.checkDataManager.get(player);
  }

  private void pruneFlags() {
    int currentTime = (int) (mc.theWorld.getTotalWorldTime() / 20);
    Map<String, int[]> nextFlagMap = new HashMap<>();
    for (Map.Entry<String, int[]> entry : this.flagMap.entrySet()) {
      int[] flagData = entry.getValue();
      if (currentTime - flagData[1] <= FLAG_WINDOW_SECONDS) {
        nextFlagMap.put(entry.getKey(), flagData);
      }
    }
    this.flagMap.clear();
    this.flagMap.putAll(nextFlagMap);
    this.alertCooldowns
        .entrySet()
        .removeIf(entry -> currentTime - entry.getValue() > ALERT_COOLDOWN_SECONDS);
  }

  private boolean isWhitelisted(String playerName) {
    for (String name : this.whitelist) {
      if (name.equalsIgnoreCase(playerName)) {
        return true;
      }
    }
    return false;
  }

  private int maxFlagsFor(String cheatName) {
    if (cheatName.startsWith("AutoBlock")) return 5;
    if (cheatName.startsWith("Noslow") || cheatName.startsWith("NoSlow")) return 4;
    if (cheatName.startsWith("KillAura")) return 4;
    if (cheatName.startsWith("Scaffold")) return 4;
    if (cheatName.startsWith("Blink")) return 3;
    if (cheatName.startsWith("FakeLag")) return 3;
    if (cheatName.startsWith("MicroBlink")) return 3;
    if (cheatName.startsWith("Reach")) return 3;
    if (cheatName.startsWith("Velocity")) return 3;
    if (cheatName.startsWith("AutoClicker")) return 3;
    return 3;
  }

  private String getFlagKey(String playerName, String cheatName) {
    return playerName.toLowerCase(Locale.ROOT) + ":" + cheatName;
  }

  @Override
  public void onDisabled() {
    this.scaffoldSneakCheck.reset();
    this.scaffoldRotationCheck.reset();
    this.scaffoldPlacementCheck.reset();
    this.kaHeuristicsCheck.reset();
    this.kaAngleSnap.reset();
    this.kaLatencyCheck.reset();
    this.kaRotationSpeed.reset();
    this.kaNoSwingCheck.reset();
    this.kaToolSwitchCheck.reset();
    this.autoBlockCheck.reset();
    this.noSlowCheck.reset();
    this.blinkCheck.reset();
    this.fakeLagCheck.reset();
    this.microBlinkCheck.reset();
    this.reachCheck.reset();
    this.velocityCheck.reset();
    this.clickSpeedCheck.reset();
    this.checkDataManager.reset();
    this.flagMap.clear();
    this.alertCooldowns.clear();
  }
}
