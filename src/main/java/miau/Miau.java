package miau;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.florianmichael.viamcp.ViaMCP;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import me.ksyz.accountmanager.AccountManager;
import miau.command.CommandManager;
import miau.command.commands.*;
import miau.config.Config;
import miau.event.EventManager;
import miau.management.*;
import miau.module.Module;
import miau.module.ModuleManager;
import miau.module.modules.combat.*;
import miau.module.modules.ghost.*;
import miau.module.modules.minigames.*;
import miau.module.modules.misc.*;
import miau.module.modules.movement.*;
import miau.module.modules.network.*;
import miau.module.modules.player.*;
import miau.module.modules.render.*;
import miau.module.modules.render.Statistics;
import miau.notification.NotificationManager;
import miau.notification.NotificationRenderer;
import miau.property.Property;
import miau.property.PropertyManager;
import org.lwjgl.opengl.Display;

public class Miau {
  public static final boolean DEVELOPMENT_SWITCH = false;
  public static String clientName = "&7[&cM&6i&ea&au&7]&r ";
  public static String version = ClientInfo.VERSION;
  public static RotationManager rotationManager;
  public static FloatManager floatManager;
  public static BlinkManager blinkManager;
  public static DelayManager delayManager;
  public static LagManager lagManager;
  public static PlayerStateManager playerStateManager;
  public static FriendManager friendManager;
  public static TargetManager targetManager;
  public static PropertyManager propertyManager;
  public static ModuleManager moduleManager;
  public static CommandManager commandManager;
  public static DiscordRichPresence discordRichPresence;
  public static NotificationManager notificationManager;
  public static DragManager dragManager;
  public static miau.util.player.PlayerTracker playerTracker;
  public static miau.component.BadPacketsComponent badPacketsComponent;
  public static miau.component.SlotComponent slotComponent;

  public Miau() {
    this.init();
  }

  public void init() {
    rotationManager = new RotationManager();
    floatManager = new FloatManager();
    blinkManager = new BlinkManager();
    delayManager = new DelayManager();
    lagManager = new LagManager();
    playerStateManager = new PlayerStateManager();
    friendManager = new FriendManager();
    targetManager = new TargetManager();
    propertyManager = new PropertyManager();
    moduleManager = new ModuleManager();
    commandManager = new CommandManager();
    discordRichPresence = new DiscordRichPresence();
    notificationManager = new NotificationManager();
    dragManager = new DragManager();

    EventManager.register(rotationManager);
    EventManager.register(floatManager);
    EventManager.register(blinkManager);
    EventManager.register(delayManager);
    EventManager.register(lagManager);
    EventManager.register(moduleManager);
    EventManager.register(commandManager);
    EventManager.register(discordRichPresence);
    EventManager.register(NotificationRenderer.getInstance());
    EventManager.register(dragManager);

    badPacketsComponent = new miau.component.BadPacketsComponent();
    EventManager.register(badPacketsComponent);
    slotComponent = new miau.component.SlotComponent();
    EventManager.register(slotComponent);
    EventManager.register(new miau.component.RotationComponent());
    EventManager.register(new miau.component.PingSpoofComponent());
    playerTracker = new miau.util.player.PlayerTracker();
    EventManager.register(playerTracker);
    moduleManager.modules.put(AimAssist.class, new AimAssist());
    moduleManager.modules.put(AntiAFK.class, new AntiAFK());
    moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
    moduleManager.modules.put(AntiFireball.class, new AntiFireball());
    moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
    moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
    moduleManager.modules.put(AntiBot.class, new AntiBot());
    moduleManager.modules.put(AntiCheatDetector.class, new AntiCheatDetector());
    moduleManager.modules.put(KillSults.class, new KillSults());
    moduleManager.modules.put(AntiVoid.class, new AntiVoid());
    moduleManager.modules.put(AutoClicker.class, new AutoClicker());
    moduleManager.modules.put(AutoAnduril.class, new AutoAnduril());
    moduleManager.modules.put(AutoAuth.class, new AutoAuth());
    moduleManager.modules.put(AutoBuy.class, new AutoBuy());
    moduleManager.modules.put(AutoHead.class, new AutoHead());
    moduleManager.modules.put(AutoChest.class, new AutoChest());
    moduleManager.modules.put(AutoSoup.class, new AutoSoup());
    moduleManager.modules.put(AutoReconnect.class, new AutoReconnect());
    moduleManager.modules.put(AutoTool.class, new AutoTool());
    moduleManager.modules.put(BridgeAssist.class, new BridgeAssist());
    moduleManager.modules.put(AutoSwap.class, new AutoSwap());
    moduleManager.modules.put(AutoBedDef.class, new AutoBedDef());
    moduleManager.modules.put(BedNuker.class, new BedNuker());
    moduleManager.modules.put(BedESP.class, new BedESP());
    moduleManager.modules.put(BedwarUtils.class, new BedwarUtils());
    moduleManager.modules.put(ThePitUtils.class, new ThePitUtils());
    moduleManager.modules.put(Blink.class, new Blink());
    moduleManager.modules.put(BlockHit.class, new BlockHit());
    moduleManager.modules.put(BlockOverlay.class, new BlockOverlay());
    moduleManager.modules.put(BreakProgress.class, new BreakProgress());
    moduleManager.modules.put(HitSelect.class, new HitSelect());
    moduleManager.modules.put(BackTrack.class, new BackTrack());
    moduleManager.modules.put(PingSpoof.class, new PingSpoof());
    moduleManager.modules.put(Criticals.class, new Criticals());
    moduleManager.modules.put(Chams.class, new Chams());
    moduleManager.modules.put(ChestESP.class, new ChestESP());
    moduleManager.modules.put(ChestStealer.class, new ChestStealer());
    moduleManager.modules.put(ClientSpoofer.class, new ClientSpoofer());
    moduleManager.modules.put(Eagle.class, new Eagle());
    moduleManager.modules.put(LegitScaffold.class, new LegitScaffold());
    moduleManager.modules.put(ESP.class, new ESP());
    moduleManager.modules.put(Capes.class, new Capes());
    moduleManager.modules.put(FastPlace.class, new FastPlace());
    moduleManager.modules.put(AutoLadderClutch.class, new AutoLadderClutch());
    moduleManager.modules.put(Clutch.class, new Clutch());
    moduleManager.modules.put(FakeLag.class, new FakeLag());
    moduleManager.modules.put(Freeze.class, new Freeze());
    moduleManager.modules.put(Displace.class, new Displace());
    moduleManager.modules.put(Piercing.class, new Piercing());
    moduleManager.modules.put(KnockbackDelay.class, new KnockbackDelay());
    moduleManager.modules.put(Fly.class, new Fly());
    moduleManager.modules.put(FullBright.class, new FullBright());
    moduleManager.modules.put(FreeLook.class, new FreeLook());
    moduleManager.modules.put(Animations.class, new Animations());
    moduleManager.modules.put(GhostHand.class, new GhostHand());
    moduleManager.modules.put(ClickGUI.class, new ClickGUI());
    moduleManager.modules.put(HUD.class, new HUD());
    moduleManager.modules.put(Ambience.class, new Ambience());
    moduleManager.modules.put(Indicators.class, new Indicators());
    moduleManager.modules.put(InventoryClicker.class, new InventoryClicker());
    moduleManager.modules.put(InvManager.class, new InvManager());
    moduleManager.modules.put(InvWalk.class, new InvWalk());
    moduleManager.modules.put(ItemESP.class, new ItemESP());
    moduleManager.modules.put(ItemPhysics.class, new ItemPhysics());
    moduleManager.modules.put(Jesus.class, new Jesus());
    moduleManager.modules.put(KeepSprint.class, new KeepSprint());
    moduleManager.modules.put(Keystrokes.class, new Keystrokes());
    moduleManager.modules.put(HitBox.class, new HitBox());
    moduleManager.modules.put(KillAura.class, new KillAura());
    moduleManager.modules.put(LagRange.class, new LagRange());
    moduleManager.modules.put(LightningTracker.class, new LightningTracker());
    moduleManager.modules.put(LongJump.class, new LongJump());
    moduleManager.modules.put(MCF.class, new MCF());
    moduleManager.modules.put(MurderDetector.class, new MurderDetector());
    moduleManager.modules.put(StaffDetector.class, new StaffDetector());
    moduleManager.modules.put(NameTags.class, new NameTags());
    moduleManager.modules.put(NickHider.class, new NickHider());
    moduleManager.modules.put(NoClickDelay.class, new NoClickDelay());
    moduleManager.modules.put(NoFall.class, new NoFall());
    moduleManager.modules.put(Disabler.class, new Disabler());
    moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
    moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
    moduleManager.modules.put(NoRotate.class, new NoRotate());
    moduleManager.modules.put(NoSlow.class, new NoSlow());
    moduleManager.modules.put(Panic.class, new Panic());
    moduleManager.modules.put(ProjectileAimBot.class, new ProjectileAimBot());
    moduleManager.modules.put(MouseRawInput.class, new MouseRawInput());
    moduleManager.modules.put(Reach.class, new Reach());
    moduleManager.modules.put(Refill.class, new Refill());
    moduleManager.modules.put(RPC.class, new RPC());
    moduleManager.modules.put(SafeWalk.class, new SafeWalk());
    moduleManager.modules.put(Scaffold.class, new Scaffold());
    moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
    moduleManager.modules.put(Spammer.class, new Spammer());
    moduleManager.modules.put(Speed.class, new Speed());
    moduleManager.modules.put(SpeedMine.class, new SpeedMine());
    moduleManager.modules.put(AutoWalk.class, new AutoWalk());
    moduleManager.modules.put(Sprint.class, new Sprint());
    moduleManager.modules.put(TargetHUD.class, new TargetHUD());
    moduleManager.modules.put(Scoreboard.class, new Scoreboard());
    moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
    moduleManager.modules.put(Tracers.class, new Tracers());
    moduleManager.modules.put(Trajectories.class, new Trajectories());
    moduleManager.modules.put(Velocity.class, new Velocity());
    moduleManager.modules.put(ViewClip.class, new ViewClip());
    moduleManager.modules.put(Wtap.class, new Wtap());
    moduleManager.modules.put(Statistics.class, new Statistics());
    moduleManager.modules.put(CheatDetector.class, new CheatDetector());
    commandManager.commands.add(new BindCommand());
    commandManager.commands.add(new ConfigCommand());
    commandManager.commands.add(new OnlineConfigCommand());
    commandManager.commands.add(new UserConfigCommand());
    commandManager.commands.add(new DenickCommand());
    commandManager.commands.add(new FriendCommand());
    commandManager.commands.add(new HelpCommand());
    commandManager.commands.add(new HideCommand());
    commandManager.commands.add(new IgnCommand());
    commandManager.commands.add(new ItemCommand());
    commandManager.commands.add(new ListCommand());
    commandManager.commands.add(new ModuleCommand());
    commandManager.commands.add(new PlayerCommand());
    commandManager.commands.add(new ShowCommand());
    commandManager.commands.add(new TargetCommand());
    commandManager.commands.add(new ToggleCommand());
    commandManager.commands.add(new VclipCommand());
    commandManager.commands.add(new ReportCommand());
    for (Module module : moduleManager.modules.values()) {
      ArrayList<Property<?>> properties = new ArrayList<>();
      for (final Field field : module.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        final Object obj;
        try {
          obj = field.get(module);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
        if (obj instanceof Property<?>) {
          ((Property<?>) obj).setOwner(module);
          properties.add((Property<?>) obj);
        }
      }
      for (Property<?> p : module.getAdditionalProperties()) {
        p.setOwner(module);
        properties.add(p);
      }
      propertyManager.properties.put(module.getClass(), properties);
      EventManager.register(module);
    }

    Config config = new Config("default", true);
    if (config.file.exists()) {
      config.load();
    }
    if (friendManager.file.exists()) {
      friendManager.load();
    }
    if (targetManager.file.exists()) {
      targetManager.load();
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (moduleManager != null && propertyManager != null) {
                    config.save();
                  }
                }));

    try (InputStreamReader reader =
        new InputStreamReader(
            Objects.requireNonNull(Miau.class.getResourceAsStream("/version.json")),
            StandardCharsets.UTF_8)) {
      JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
      version = modInfo.get("version").getAsString();
    } catch (Exception e) {
      version = ClientInfo.VERSION;
    }
    Display.setTitle(ClientInfo.getDisplayVersion());

    // removed NametagOverlayRenderer
    AccountManager.init();
    ViaMCP.create();
  }

  public static Locale getLocale() {
    return Locale.getDefault();
  }

  public static void setLocale(Locale locale) {
    Locale.setDefault(locale);
  }

  public static void terminate() {}
}
