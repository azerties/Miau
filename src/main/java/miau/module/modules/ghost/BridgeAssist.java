package miau.module.modules.ghost;

import java.util.Objects;
import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.Priority;
import miau.module.Module;
import miau.module.modules.ghost.bridgeassist.mode.NormalMode;
import miau.module.modules.ghost.bridgeassist.mode.SilentMode;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.ItemUtil;
import net.minecraft.client.Minecraft;

public class BridgeAssist extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Normal", "Silent"});

  public final FloatProperty delayMs =
      new FloatProperty(
          "delay", 2.0F, 3.0F, 0.0F, 10.0F, () -> mode.getModeString().equals("Normal"));
  public final BooleanProperty directionCheck =
      new BooleanProperty("direction-check", true, () -> mode.getModeString().equals("Normal"));
  public final BooleanProperty jumpCheck =
      new BooleanProperty("jump-check", true, () -> mode.getModeString().equals("Normal"));
  public final BooleanProperty pitchCheck =
      new BooleanProperty("pitch-check", true, () -> mode.getModeString().equals("Normal"));
  public final BooleanProperty blocksOnly =
      new BooleanProperty("blocks-only", true, () -> mode.getModeString().equals("Normal"));
  public final BooleanProperty normalSneakOnly =
      new BooleanProperty("sneaking-only", false, () -> mode.getModeString().equals("Normal"));

  public final BooleanProperty silentSneakingOnly =
      new BooleanProperty("sneaking-only", false, () -> mode.getModeString().equals("Silent"));
  public final BooleanProperty silentEdgeOnly =
      new BooleanProperty("edge-only", true, () -> mode.getModeString().equals("Silent"));
  public final BooleanProperty silentMoveFix =
      new BooleanProperty("move-fix", true, () -> mode.getModeString().equals("Silent"));
  public final FloatProperty silentRotSpeed =
      new FloatProperty(
          "rot-speed", 12.0F, 5.0F, 20.0F, () -> mode.getModeString().equals("Silent"));

  private final NormalMode normalMode = new NormalMode(this);
  private final SilentMode silentMode = new SilentMode(this);

  public BridgeAssist() {
    super("Bridge Assist", false);
  }

  @Override
  public String[] getSuffix() {
    if (this.mode.getModeString().equals("Silent")) {
      return new String[] {"Silent"};
    }
    return Objects.equals(this.delayMs.getValue(), this.delayMs.getSecondValue())
        ? new String[] {String.valueOf(this.delayMs.getValue().intValue())}
        : new String[] {
          String.format(
              "%d-%d", this.delayMs.getValue().intValue(), this.delayMs.getSecondValue().intValue())
        };
  }

  @Override
  public void onDisabled() {
    normalMode.onDisabled();
    silentMode.onDisabled();
  }

  public boolean isHoldingBlock() {
    return ItemUtil.isHoldingBlock();
  }

  @EventTarget(Priority.LOWEST)
  public void onTick(TickEvent event) {
    if (!this.isEnabled()) return;
    if (!this.mode.getModeString().equals("Normal")) return;
    normalMode.onTick(event);
  }

  @EventTarget(Priority.LOWEST)
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled() || mc.currentScreen != null) return;

    if (this.mode.getModeString().equals("Normal")) {
      normalMode.onMoveInput(event);
    } else if (this.mode.getModeString().equals("Silent")) {
      silentMode.onMoveInput(event);
    }
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent e) {
    if (!this.isEnabled()) return;
    if (!this.mode.getModeString().equals("Silent")) return;
    silentMode.onUpdate(e);
  }
}
