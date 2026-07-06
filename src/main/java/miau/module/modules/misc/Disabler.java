package miau.module.modules.misc;

import java.util.ArrayList;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.module.Module;
<<<<<<< HEAD
import miau.module.modules.misc.disabler.*;
=======
import miau.module.modules.misc.disabler.DelayedTransaction;
import miau.module.modules.misc.disabler.DisablerMode;
import miau.module.modules.misc.disabler.VulcanDisabler;
>>>>>>> c5b379497bf77fb128062e7b70077db4c79e836b
import miau.property.Property;
import miau.property.properties.ModeProperty;

public class Disabler extends Module {

  public final List<DisablerMode> modes = new ArrayList<>();

  // -- Mode instances --
  public final VulcanDisabler vulcan = new VulcanDisabler("Vulcan", this);
  public final WatchdogDisabler watchdog = new WatchdogDisabler("Watchdog", this);
  public final Verus2Disabler verus2 = new Verus2Disabler("Verus2", this);
  public final VerusCustomDisabler verusCustom = new VerusCustomDisabler("VerusCustom", this);
  public final CubeCraftDisabler cubeCraft = new CubeCraftDisabler("CubeCraft", this);
  public final MineLandKickDisabler mineLand = new MineLandKickDisabler("MineLand", this);
  public final VehicleDisabler vehicle = new VehicleDisabler("Vehicle", this);
  public final TeleportDisabler teleport = new TeleportDisabler("Teleport", this);
  public final ConvertMovingPacketsDisabler convertPackets =
      new ConvertMovingPacketsDisabler("ConvertPackets", this);
  public final SprintDisabler sprint = new SprintDisabler("Sprint", this);
  public final OmniSprintDisabler omniSprint = new OmniSprintDisabler("OmniSprint", this);
  public final UniversoCraftDisabler universoCraft =
      new UniversoCraftDisabler("UniversoCraft", this);
  public final RiseBalanceDisabler riseBalance = new RiseBalanceDisabler("RiseBalance", this);
  public final TransactionDisabler transaction = new TransactionDisabler("Transaction", this);
  public final MMCReachDisabler mmcReach = new MMCReachDisabler("MMCReach", this);
  public final AbilitiesDisabler abilities = new AbilitiesDisabler("Abilities", this);
  public final InputDisabler input = new InputDisabler("Input", this);
  public final SpectateDisabler spectate = new SpectateDisabler("Spectate", this);
  public final KeepAliveDisabler keepAlive = new KeepAliveDisabler("KeepAlive", this);
  public final NoRulesDisabler noRules = new NoRulesDisabler("NoRules", this);
  public final ExperimentalDisabler experimental = new ExperimentalDisabler("Experimental", this);
  public final TestDisabler test = new TestDisabler("Test", this);
  public final LunarDisabler lunar = new LunarDisabler("Lunar", this);
  public final GhostlyDisabler ghostly = new GhostlyDisabler("Ghostly", this);
  public final DynamicPVPDisabler dynamicPVP = new DynamicPVPDisabler("DynamicPVP", this);
  public final BlockDisabler block = new BlockDisabler("Block", this);

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            "Vulcan", "Watchdog", "Verus2", "VerusCustom",
            "CubeCraft", "MineLand", "Vehicle", "Teleport",
            "ConvertPackets", "Sprint", "OmniSprint", "UniversoCraft",
            "RiseBalance", "Transaction", "MMCReach", "Abilities",
            "Input", "Spectate", "KeepAlive", "NoRules",
            "Experimental", "Test", "Lunar", "Ghostly",
            "DynamicPVP", "Block"
          });

  public Disabler() {
    super("Disabler", false);
    modes.add(vulcan);
    modes.add(watchdog);
    modes.add(verus2);
    modes.add(verusCustom);
    modes.add(cubeCraft);
    modes.add(mineLand);
    modes.add(vehicle);
    modes.add(teleport);
    modes.add(convertPackets);
    modes.add(sprint);
    modes.add(omniSprint);
    modes.add(universoCraft);
    modes.add(riseBalance);
    modes.add(transaction);
    modes.add(mmcReach);
    modes.add(abilities);
    modes.add(input);
    modes.add(spectate);
    modes.add(keepAlive);
    modes.add(noRules);
    modes.add(experimental);
    modes.add(test);
    modes.add(lunar);
    modes.add(ghostly);
    modes.add(dynamicPVP);
    modes.add(block);
  }

  public DisablerMode getActiveMode() {
    String modeName = mode.getModeString();
    for (DisablerMode m : modes) {
      if (m.getName().equalsIgnoreCase(modeName)) {
        return m;
      }
    }
    return modes.isEmpty() ? null : modes.get(0);
  }

  @Override
  public void onEnabled() {
    DisablerMode active = getActiveMode();
    if (active != null) active.onEnable();
  }

  @Override
  public void onDisabled() {
    DisablerMode active = getActiveMode();
    if (active != null) active.onDisable();
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (DisablerMode m : modes) {
      for (java.lang.reflect.Field field : m.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        try {
          Object obj = field.get(m);
          if (obj instanceof Property<?>) {
            Property<?> prop = (Property<?>) obj;
            java.util.function.BooleanSupplier original = prop.getVisibleChecker();
            prop.setVisibleChecker(
                () -> this.getActiveMode() == m && (original == null || original.getAsBoolean()));
            props.add(prop);
          }
        } catch (Exception ignored) {
        }
      }
    }
    return props;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onTick(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onPacket(event);
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onStrafe(event);
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onLivingUpdate(event);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onMoveInput(event);
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onJump(event);
    }
  }

  @EventTarget
  public void onRender2D(Render2DEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onRender2D(event);
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    if (this.isEnabled()) {
      DisablerMode active = getActiveMode();
      if (active != null) active.onLoadWorld(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }
}
