package myau.module.modules.combat;

import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.List;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.*;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.module.modules.combat.velocity.*;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class Velocity extends Module {
  public static final Minecraft mc = Minecraft.getMinecraft();

  public int chanceCounter = 0;
  public int delayChanceCounter = 0;
  public boolean pendingExplosion = false;
  public boolean allowNext = true;
  public boolean jumpFlag = false;
  public boolean reverseFlag = false;
  public boolean delayActive = false;

  public boolean shouldJump = false;
  public int jumpCooldown = 0;
  public boolean hasReceivedVelocity = false;
  public int legitSmartJumpCount = 0;
  public int intaveTick = 0;
  public int intaveDamageTick = 0;

  public final BooleanProperty onSwing = new BooleanProperty("on-swing", false);

  public final List<VelocityMode> modes = new ArrayList<>();

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            register(new ThreeFPracVelocity("3FPrac", this)),
            register(new StandardVelocity("Standard", this)),
            register(new LegitVelocity("Legit", this)),
            register(new IntaveVelocity("Intave", this)),
            register(new JumpResetVelocity("JumpReset", this)),
            register(new WatchdogPredictionVelocity("WatchdogPrediction", this)),
            register(new GrimReduceVelocity("GrimReduce", this))
          });

  private String register(VelocityMode m) {
    this.modes.add(m);
    return m.getName();
  }

  public boolean isInLiquidOrWeb() {
    return mc.thePlayer.isInWater()
        || mc.thePlayer.isInLava()
        || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
  }

  public boolean canDelay() {
    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
  }

  public Velocity() {
    super("Velocity", false);
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (VelocityMode m : modes) {
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
        } catch (Exception e) {
        }
      }
    }
    return props;
  }

  public VelocityMode getActiveMode() {
    return modes.stream()
        .filter(m -> m.getName().equals(mode.getModeString()))
        .findFirst()
        .orElse(modes.get(0));
  }

  @Override
  public void onEnabled() {
    getActiveMode().onEnable();
  }

  @EventTarget
  public void onKnockback(KnockbackEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onKnockback(event);
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onUpdate(event);
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onLivingUpdate(event);
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onStrafe(event);
    }
  }

  @EventTarget
  public void onJump(JumpEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onJump(event);
    }
  }

  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onRender3D(event);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onMoveInput(event);
    }
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onAttack(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onPacket(event);
    }
  }

  @EventTarget
  public void onLoadWorld(LoadWorldEvent event) {
    this.onDisabled();
  }

  @Override
  public void onDisabled() {
    getActiveMode().onDisable();
    this.pendingExplosion = false;
    this.allowNext = true;
    this.shouldJump = false;
    this.jumpCooldown = 0;
    this.hasReceivedVelocity = false;
    this.legitSmartJumpCount = 0;
    this.intaveTick = 0;
    this.intaveDamageTick = 0;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
