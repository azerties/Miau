package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.StrafeEvent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.module.modules.player.scaffold.ScaffoldUtils;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.player.ItemUtil;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3i;
import org.lwjgl.input.Keyboard;

public class TowerFeature implements ScaffoldComponent {
  private final Scaffold scaffold;

  public final ModeProperty tower =
      new ModeProperty("tower", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY"});
  public final BooleanProperty hypixeltower =
      new BooleanProperty("hypixeltower", false, () -> this.tower.getValue() == 3);
  public final BooleanProperty safe =
      new BooleanProperty("safe", false, () -> this.tower.getValue() == 3);
  public final IntProperty safeStuckDelayTicksProperty =
      new IntProperty(
          "safe-delay-ticks", 1, 1, 3, () -> this.tower.getValue() == 3 && this.safe.getValue());

  public TowerFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(tower, hypixeltower, safe, safeStuckDelayTicksProperty);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (hypixeltower.getValue()
        && Scaffold.mc.thePlayer.motionY <= 0.0
        && Math.sqrt(
                Scaffold.mc.thePlayer.motionX * Scaffold.mc.thePlayer.motionX
                    + Scaffold.mc.thePlayer.motionZ * Scaffold.mc.thePlayer.motionZ)
            <= 0.02D
        && Scaffold.mc.thePlayer.motionY >= -0.09
        && !(Keyboard.isKeyDown(Scaffold.mc.gameSettings.keyBindForward.getKeyCode())
            || Keyboard.isKeyDown(Scaffold.mc.gameSettings.keyBindBack.getKeyCode())
            || Keyboard.isKeyDown(Scaffold.mc.gameSettings.keyBindLeft.getKeyCode())
            || Keyboard.isKeyDown(Scaffold.mc.gameSettings.keyBindRight.getKeyCode()))
        && Keyboard.isKeyDown(Scaffold.mc.gameSettings.keyBindJump.getKeyCode())) {
      Scaffold.mc.thePlayer.motionY = -0.38;
    }

    if (Scaffold.mc.thePlayer.onGround) {
      scaffold.towering = false;
    }
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (!Scaffold.mc.thePlayer.isCollidedHorizontally
        && Scaffold.mc.thePlayer.hurtTime <= 5
        && !Scaffold.mc.thePlayer.isPotionActive(Potion.jump)
        && Scaffold.mc.gameSettings.keyBindJump.isKeyDown()
        && ItemUtil.isHoldingBlock()) {
      int yState = (int) (Scaffold.mc.thePlayer.posY % 1.0 * 100.0);
      switch (this.tower.getValue()) {
        case 1:
          handleVanillaTower(event, yState);
          break;
        case 2:
          handleExtraTower(event, yState);
          break;
        default:
          scaffold.towerTick = 0;
          scaffold.towerDelay = 0;
      }
    } else {
      scaffold.towerTick = 0;
      scaffold.towerDelay = 0;
    }
  }

  public void updateSafeStuck() {
    if (this.safe.getValue()
        && this.tower.getValue() == 3
        && Scaffold.mc.gameSettings.keyBindJump.isKeyDown()) {
      float moveYaw =
          MoveUtil.adjustYaw(
              Scaffold.mc.thePlayer.rotationYaw,
              (float) MoveUtil.getForwardValue(),
              (float) MoveUtil.getLeftValue());
      boolean diagonal = ScaffoldUtils.isDiagonal(moveYaw);
      if (diagonal && !Scaffold.mc.thePlayer.onGround) {
        double motionY = Scaffold.mc.thePlayer.motionY;
        if (scaffold.safePrevMotionY > 0.0 && motionY <= 0.0) {
          double motionXZ =
              Math.sqrt(
                  Scaffold.mc.thePlayer.motionX * Scaffold.mc.thePlayer.motionX
                      + Scaffold.mc.thePlayer.motionZ * Scaffold.mc.thePlayer.motionZ);
          double motionXZSpeedBps = motionXZ * 20.0;
          if (scaffold.safeStuckDelayTicks <= 0
              && scaffold.safeStuckTicks <= 0
              && motionXZSpeedBps >= 4.67) {
            scaffold.safeStuckDelayTicks = this.safeStuckDelayTicksProperty.getValue();
          }
        }
        scaffold.safePrevMotionY = motionY;
      } else {
        scaffold.safePrevMotionY = Scaffold.mc.thePlayer.motionY;
      }
    } else {
      scaffold.safePrevMotionY = Scaffold.mc.thePlayer.motionY;
    }
  }

  private void handleVanillaTower(StrafeEvent event, int yState) {
    switch (scaffold.towerTick) {
      case 0:
        if (Scaffold.mc.thePlayer.onGround) {
          scaffold.towerTick = 1;
          Scaffold.mc.thePlayer.motionY = -0.0784000015258789;
        }
        return;
      case 1:
        if (yState == 0 && PlayerUtil.isAirBelow()) {
          scaffold.startY = MathHelper.floor_double(Scaffold.mc.thePlayer.posY);
          scaffold.towerTick = 2;
          Scaffold.mc.thePlayer.motionY = 0.42F;
          if (MoveUtil.isForwardPressed())
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
          else {
            MoveUtil.setSpeed(0.0);
            event.setForward(0.0F);
            event.setStrafe(0.0F);
          }
          return;
        } else {
          scaffold.towerTick = 0;
          return;
        }
      case 2:
        scaffold.towerTick = 3;
        Scaffold.mc.thePlayer.motionY = 0.75 - Scaffold.mc.thePlayer.posY % 1.0;
        return;
      case 3:
        scaffold.towerTick = 1;
        Scaffold.mc.thePlayer.motionY = 1.0 - Scaffold.mc.thePlayer.posY % 1.0;
        return;
      default:
        scaffold.towerTick = 0;
        return;
    }
  }

  private void handleExtraTower(StrafeEvent event, int yState) {
    switch (scaffold.towerTick) {
      case 0:
        if (Scaffold.mc.thePlayer.onGround) {
          scaffold.towerTick = 1;
          Scaffold.mc.thePlayer.motionY = -0.0784000015258789;
        }
        return;
      case 1:
        if (yState == 0 && PlayerUtil.isAirBelow()) {
          scaffold.startY = MathHelper.floor_double(Scaffold.mc.thePlayer.posY);
          if (!MoveUtil.isForwardPressed()) {
            scaffold.towerDelay = 2;
            MoveUtil.setSpeed(0.0);
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            EnumFacing facing =
                ScaffoldUtils.yawToFacing(MathHelper.wrapAngleTo180_float(scaffold.yaw - 180.0F));
            double distance = ScaffoldUtils.distanceToEdge(Scaffold.mc, facing);
            if (distance > 0.1) {
              if (Scaffold.mc.thePlayer.onGround) {
                Vec3i directionVec = facing.getDirectionVec();
                double offset = Math.min(ScaffoldUtils.getRandomOffset(), distance - 0.05);
                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                AxisAlignedBB nextBox =
                    Scaffold.mc
                        .thePlayer
                        .getEntityBoundingBox()
                        .offset(
                            (double) directionVec.getX() * (offset - jitter),
                            0.0,
                            (double) directionVec.getZ() * (offset - jitter));
                if (Scaffold.mc
                    .theWorld
                    .getCollidingBoundingBoxes(Scaffold.mc.thePlayer, nextBox)
                    .isEmpty()) {
                  Scaffold.mc.thePlayer.motionY = -0.0784000015258789;
                  Scaffold.mc.thePlayer.setPosition(
                      nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0,
                      nextBox.minY,
                      nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                }
                return;
              }
            } else {
              scaffold.towerTick = 2;
              scaffold.targetFacing = facing;
              Scaffold.mc.thePlayer.motionY = 0.42F;
            }
            return;
          } else {
            scaffold.towerTick = 2;
            scaffold.towerDelay++;
            Scaffold.mc.thePlayer.motionY = 0.42F;
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
            return;
          }
        } else {
          scaffold.towerTick = 0;
          scaffold.towerDelay = 0;
          return;
        }
      case 2:
        scaffold.towerTick = 3;
        Scaffold.mc.thePlayer.motionY -= RandomUtil.nextDouble(0.00101, 0.00109);
        return;
      case 3:
        if (scaffold.towerDelay >= 4) {
          scaffold.towerTick = 4;
          scaffold.towerDelay = 0;
        } else {
          scaffold.towerTick = 1;
          Scaffold.mc.thePlayer.motionY = 1.0 - Scaffold.mc.thePlayer.posY % 1.0;
        }
        return;
      case 4:
        scaffold.towerTick = 5;
        return;
      case 5:
        if (!PlayerUtil.isAirBelow()) scaffold.towerTick = 0;
        else {
          scaffold.towerTick = 1;
          Scaffold.mc.thePlayer.motionY -= 0.08;
          Scaffold.mc.thePlayer.motionY *= 0.98F;
          Scaffold.mc.thePlayer.motionY -= 0.08;
          Scaffold.mc.thePlayer.motionY *= 0.98F;
        }
        return;
      default:
        scaffold.towerTick = 0;
        scaffold.towerDelay = 0;
        return;
    }
  }
}
