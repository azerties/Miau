package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.StrafeEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.module.modules.player.scaffold.ScaffoldUtils;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.math.RandomUtil;
import miau.util.player.MoveUtil;
import miau.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3i;

public class TowerFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty tower =
      new ModeProperty("tower", 0, new String[] {"NONE", "VANILLA", "TELLY"});
  public final BooleanProperty hypixeltower =
      new BooleanProperty("hypixeltower", false, () -> this.tower.getValue() == 2);
  public final BooleanProperty safe =
      new BooleanProperty("safe", false, () -> this.tower.getValue() == 2);
  public final IntProperty safeStuckDelayTicksProperty =
      new IntProperty(
          "safe-delay-ticks", 1, 1, 3, () -> this.tower.getValue() == 2 && this.safe.getValue());

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(tower, hypixeltower, safe, safeStuckDelayTicksProperty);
  }

  public TowerFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (this.hypixeltower.getValue()
        && mc.thePlayer.motionY <= 0.0
        && Math.sqrt(
                mc.thePlayer.motionX * mc.thePlayer.motionX
                    + mc.thePlayer.motionZ * mc.thePlayer.motionZ)
            <= 0.02D
        && mc.thePlayer.motionY >= -0.09
        && !(org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
            || org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())
            || org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
            || org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()))
        && org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
      mc.thePlayer.motionY = -0.38;
    }

    if (mc.thePlayer.onGround) {
      scaffold.towering = false;
    }
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (!mc.thePlayer.isCollidedHorizontally
        && mc.thePlayer.hurtTime <= 5
        && !mc.thePlayer.isPotionActive(Potion.jump)
        && mc.gameSettings.keyBindJump.isKeyDown()
        && miau.util.player.ItemUtil.isHoldingBlock()) {
      int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
      switch (this.tower.getValue()) {
        case 1:
          handleVanillaTower(event, yState);
          break;
        case 2:
          handleTellyTower(event, yState);
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

  private void handleVanillaTower(StrafeEvent event, int yState) {
    switch (scaffold.towerTick) {
      case 0:
        if (mc.thePlayer.onGround) {
          scaffold.towerTick = 1;
          mc.thePlayer.motionY = -0.0784000015258789;
        }
        return;
      case 1:
        if (yState == 0 && PlayerUtil.isAirBelow()) {
          scaffold.startY = MathHelper.floor_double(mc.thePlayer.posY);
          scaffold.towerTick = 2;
          mc.thePlayer.motionY = 0.42F;
          if (MoveUtil.isForwardPressed()) {
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
          } else {
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
        mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
        return;
      case 3:
        scaffold.towerTick = 1;
        mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
        return;
      default:
        scaffold.towerTick = 0;
        return;
    }
  }

  private void handleTellyTower(StrafeEvent event, int yState) {
    switch (scaffold.towerTick) {
      case 0:
        if (mc.thePlayer.onGround) {
          scaffold.towerTick = 1;
          mc.thePlayer.motionY = -0.0784000015258789;
        }
        return;
      case 1:
        if (yState == 0 && PlayerUtil.isAirBelow()) {
          scaffold.startY = MathHelper.floor_double(mc.thePlayer.posY);
          if (!MoveUtil.isForwardPressed()) {
            scaffold.towerDelay = 2;
            MoveUtil.setSpeed(0.0);
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            EnumFacing facing =
                ScaffoldUtils.yawToFacing(MathHelper.wrapAngleTo180_float(scaffold.yaw - 180.0F));
            double distance = ScaffoldUtils.distanceToEdge(mc, facing);
            if (distance > 0.1) {
              if (mc.thePlayer.onGround) {
                Vec3i directionVec = facing.getDirectionVec();
                double offset = Math.min(ScaffoldUtils.getRandomOffset(), distance - 0.05);
                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                AxisAlignedBB nextBox =
                    mc.thePlayer
                        .getEntityBoundingBox()
                        .offset(
                            (double) directionVec.getX() * (offset - jitter),
                            0.0,
                            (double) directionVec.getZ() * (offset - jitter));
                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                  mc.thePlayer.motionY = -0.0784000015258789;
                  mc.thePlayer.setPosition(
                      nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0,
                      nextBox.minY,
                      nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                }
                return;
              }
            } else {
              scaffold.towerTick = 2;
              scaffold.targetFacing = facing;
              mc.thePlayer.motionY = 0.42F;
            }
            return;
          } else {
            scaffold.towerTick = 2;
            scaffold.towerDelay++;
            mc.thePlayer.motionY = 0.42F;
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
        mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
        return;
      case 3:
        if (scaffold.towerDelay >= 4) {
          scaffold.towerTick = 4;
          scaffold.towerDelay = 0;
        } else {
          scaffold.towerTick = 1;
          mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
        }
        return;
      case 4:
        scaffold.towerTick = 5;
        return;
      case 5:
        if (!PlayerUtil.isAirBelow()) {
          scaffold.towerTick = 0;
        } else {
          scaffold.towerTick = 1;
          mc.thePlayer.motionY -= 0.08;
          mc.thePlayer.motionY *= 0.98F;
          mc.thePlayer.motionY -= 0.08;
          mc.thePlayer.motionY *= 0.98F;
        }
        return;
      default:
        scaffold.towerTick = 0;
        scaffold.towerDelay = 0;
        return;
    }
  }
}
