package myau.util.player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeModContainer;

/**
 * Simulated player for predicting movement ticks ahead. Ported from RinBounce SimulatedPlayer.kt
 * Compatible with client user ONLY.
 */
public class SimulatedPlayer {

  private static final Minecraft mc = Minecraft.getMinecraft();

  // Player state
  private final EntityPlayerSP player;
  public AxisAlignedBB box;
  public MovementInput movementInput;
  private int jumpTicks;
  public double motionZ;
  public double motionY;
  public double motionX;
  private boolean inWater;
  public boolean onGround;
  private boolean isAirBorne;
  public float rotationYaw;
  public double posX;
  public double posY;
  public double posZ;
  private final PlayerCapabilities capabilities;
  private final Entity ridingEntity;
  private float jumpMovementFactor;
  private final World worldObj;
  public boolean isCollidedHorizontally;
  private boolean isCollidedVertically;
  private final WorldBorder worldBorder;
  private final IChunkProvider chunkProvider;
  private boolean isOutsideBorder;
  private Entity riddenByEntity;
  private BaseAttributeMap attributeMap;
  private final boolean isSpectator;
  public float fallDistance;
  private final float stepHeight;
  private boolean isCollided;
  private int fire;
  private float distanceWalkedModified;
  private float distanceWalkedOnStepModified;
  private int nextStepDistance;
  private final float height;
  private final float width;
  private final int fireResistance;
  private boolean isInWeb;
  private boolean noClip;
  private boolean isSprinting;
  private int foodLevel;

  private float moveForward = 0f;
  private float moveStrafing = 0f;
  private boolean isJumping = false;

  public boolean safeWalk = false;

  private static final float SPEED_IN_AIR = 0.02F;

  public SimulatedPlayer(
      EntityPlayerSP player,
      AxisAlignedBB box,
      MovementInput movementInput,
      int jumpTicks,
      double motionZ,
      double motionY,
      double motionX,
      boolean inWater,
      boolean onGround,
      boolean isAirBorne,
      float rotationYaw,
      double posX,
      double posY,
      double posZ,
      PlayerCapabilities capabilities,
      Entity ridingEntity,
      float jumpMovementFactor,
      World worldObj,
      boolean isCollidedHorizontally,
      boolean isCollidedVertically,
      WorldBorder worldBorder,
      IChunkProvider chunkProvider,
      boolean isOutsideBorder,
      Entity riddenByEntity,
      BaseAttributeMap attributeMap,
      boolean isSpectator,
      float fallDistance,
      float stepHeight,
      boolean isCollided,
      int fire,
      float distanceWalkedModified,
      float distanceWalkedOnStepModified,
      int nextStepDistance,
      float height,
      float width,
      int fireResistance,
      boolean isInWeb,
      boolean noClip,
      boolean isSprinting,
      int foodLevel) {
    this.player = player;
    this.box = box;
    this.movementInput = movementInput;
    this.jumpTicks = jumpTicks;
    this.motionZ = motionZ;
    this.motionY = motionY;
    this.motionX = motionX;
    this.inWater = inWater;
    this.onGround = onGround;
    this.isAirBorne = isAirBorne;
    this.rotationYaw = rotationYaw;
    this.posX = posX;
    this.posY = posY;
    this.posZ = posZ;
    this.capabilities = capabilities;
    this.ridingEntity = ridingEntity;
    this.jumpMovementFactor = jumpMovementFactor;
    this.worldObj = worldObj;
    this.isCollidedHorizontally = isCollidedHorizontally;
    this.isCollidedVertically = isCollidedVertically;
    this.worldBorder = worldBorder;
    this.chunkProvider = chunkProvider;
    this.isOutsideBorder = isOutsideBorder;
    this.riddenByEntity = riddenByEntity;
    this.attributeMap = attributeMap;
    this.isSpectator = isSpectator;
    this.fallDistance = fallDistance;
    this.stepHeight = stepHeight;
    this.isCollided = isCollided;
    this.fire = fire;
    this.distanceWalkedModified = distanceWalkedModified;
    this.distanceWalkedOnStepModified = distanceWalkedOnStepModified;
    this.nextStepDistance = nextStepDistance;
    this.height = height;
    this.width = width;
    this.fireResistance = fireResistance;
    this.isInWeb = isInWeb;
    this.noClip = noClip;
    this.isSprinting = isSprinting;
    this.foodLevel = foodLevel;
  }

  public Vec3 getPos() {
    return new Vec3(posX, posY, posZ);
  }

  public static SimulatedPlayer fromClientPlayer(MovementInput input) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) throw new IllegalStateException("Player is null");

    PlayerCapabilities capabilities = createCapabilitiesCopy(player);

    MovementInput mi = new MovementInput();
    mi.jump = input.jump;
    mi.moveForward = input.moveForward;
    mi.moveStrafe = input.moveStrafe;
    mi.sneak = input.sneak;

    int food = 20;
    try {
      food = player.getFoodStats().getFoodLevel();
    } catch (Exception ignored) {
    }

    return new SimulatedPlayer(
        player,
        player.getEntityBoundingBox(),
        mi,
        getPrivateInt(player, EntityLivingBase.class, "jumpTicks"),
        player.motionZ,
        player.motionY,
        player.motionX,
        player.isInWater(),
        player.onGround,
        player.isAirBorne,
        player.rotationYaw,
        player.posX,
        player.posY,
        player.posZ,
        capabilities,
        player.ridingEntity,
        player.jumpMovementFactor,
        player.worldObj,
        player.isCollidedHorizontally,
        player.isCollidedVertically,
        player.worldObj.getWorldBorder(),
        player.worldObj.getChunkProvider(),
        getPrivateBoolean(player, Entity.class, "isOutsideBorder"),
        player.riddenByEntity,
        player.getAttributeMap(),
        player.isSpectator(),
        player.fallDistance,
        player.stepHeight,
        player.isCollided,
        getPrivateInt(player, Entity.class, "fire"),
        player.distanceWalkedModified,
        player.distanceWalkedOnStepModified,
        getPrivateInt(player, Entity.class, "nextStepDistance"),
        player.height,
        player.width,
        player.fireResistance,
        getPrivateBoolean(player, Entity.class, "isInWeb"),
        player.noClip,
        player.isSprinting(),
        food);
  }

  private static PlayerCapabilities createCapabilitiesCopy(EntityPlayerSP player) {
    NBTTagCompound nbt = new NBTTagCompound();
    PlayerCapabilities caps = new PlayerCapabilities();
    player.capabilities.writeCapabilitiesToNBT(nbt);
    caps.readCapabilitiesFromNBT(nbt);
    return caps;
  }

  // --- Private field accessors via reflection (MCP private fields in 1.8.9) ---
  private static int getPrivateInt(Object obj, Class<?> clazz, String fieldName) {
    try {
      Field f = clazz.getDeclaredField(fieldName);
      f.setAccessible(true);
      return f.getInt(obj);
    } catch (Exception ignored) {
      return 0;
    }
  }

  private static boolean getPrivateBoolean(Object obj, Class<?> clazz, String fieldName) {
    try {
      Field f = clazz.getDeclaredField(fieldName);
      f.setAccessible(true);
      return f.getBoolean(obj);
    } catch (Exception ignored) {
      return false;
    }
  }

  // --- Core tick method ---
  public void tick() {
    if (!onEntityUpdate() || player.isRiding()) {
      return;
    }
    playerUpdate(false);
    clientPlayerLivingUpdate();
    playerUpdate(true);
  }

  // --- Movement simulation ---
  private void clientPlayerLivingUpdate() {
    pushOutOfBlocks(posX - width * 0.35, box.minY + 0.5, posZ + width * 0.35);
    pushOutOfBlocks(posX - width * 0.35, box.minY + 0.5, posZ - width * 0.35);
    pushOutOfBlocks(posX + width * 0.35, box.minY + 0.5, posZ - width * 0.35);
    pushOutOfBlocks(posX + width * 0.35, box.minY + 0.5, posZ + width * 0.35);

    boolean flag3 = foodLevel > 6.0f || capabilities.allowFlying;
    float f = 0.8f;

    boolean shouldSprint = mc.thePlayer.isSprinting();
    float forwardSpeed = movementInput.moveForward;

    if (onGround
        && forwardSpeed >= f
        && !isSprinting()
        && flag3
        && !player.isUsingItem()
        && !isPotionActive(Potion.blindness)
        && shouldSprint) {
      setSprinting(true);
    }

    if (!isSprinting()
        && forwardSpeed >= f
        && flag3
        && !player.isUsingItem()
        && !isPotionActive(Potion.blindness)
        && shouldSprint) {
      setSprinting(true);
    }

    if (movementInput.sneak) {
      setSprinting(false);
    }

    if (isSprinting() && (forwardSpeed < 0.8f || isCollidedHorizontally || !flag3)) {
      setSprinting(false);
    }

    if (capabilities.allowFlying) {
      if (mc.playerController.isSpectatorMode()) {
        if (!capabilities.isFlying) {
          capabilities.isFlying = true;
        }
      }
    }

    if (capabilities.isFlying) {
      if (movementInput.sneak) {
        motionY -= capabilities.getFlySpeed() * 3.0f;
      }
      if (movementInput.jump) {
        motionY += capabilities.getFlySpeed() * 3.0f;
      }
    }

    livingEntityUpdate();
  }

  private void playerUpdate(boolean post) {
    if (!post) {
      noClip = this.isSpectator;
      if (this.isSpectator) {
        onGround = false;
      }
    } else {
      clampPositionFromEntityPlayer();
    }
  }

  private void livingEntityUpdate() {
    if (jumpTicks > 0) {
      --jumpTicks;
    }

    if (Math.abs(motionX) < 0.005) motionX = 0.0;
    if (Math.abs(motionY) < 0.005) motionY = 0.0;
    if (Math.abs(motionZ) < 0.005) motionZ = 0.0;

    if (isMovementBlocked()) {
      isJumping = false;
      moveStrafing = 0.0f;
      moveForward = 0.0f;
    } else {
      updateLivingEntityInput();
    }

    if (isJumping && movementInput.jump) {
      if (isInWater() || isInLava()) {
        updateAITick();
      } else if (onGround && jumpTicks == 0) {
        jump();
        // NoJumpDelay not ported - skip
      }
    } else {
      jumpTicks = 0;
    }

    moveStrafing *= 0.98f;
    moveForward *= 0.98f;
    playerSideMoveEntityWithHeading(moveStrafing, moveForward);

    jumpMovementFactor = SPEED_IN_AIR;
    if (isSprinting()) {
      jumpMovementFactor = (float) ((double) jumpMovementFactor + SPEED_IN_AIR * 0.3);
    }

    if (this.onGround && this.capabilities.isFlying && !isSpectator) {
      this.capabilities.isFlying = false;
    }
  }

  private boolean onEntityUpdate() {
    handleWaterMovement();
    if (worldObj.isRemote) {
      fire = 0;
    } else if (fire > 0) {
      --fire;
    }

    if (isInLava()) {
      setOnFireFromLava();
      fallDistance *= 0.5f;
    }

    return posY >= -64.0;
  }

  private void clampPositionFromEntityPlayer() {
    double d3 = MathHelper.clamp_double(posX, -2.9999999E7, 2.9999999E7);
    double d4 = MathHelper.clamp_double(posZ, -2.9999999E7, 2.9999999E7);
    if (d3 != posX || d4 != posZ) {
      setPosition(d3, posY, d4);
    }
  }

  private void setPosition(double x, double y, double z) {
    posX = x;
    posY = y;
    posZ = z;
    float f = width / 2.0f;
    float f1 = height;
    setEntityBoundingBox(new AxisAlignedBB(x - f, y, z - f, x + f, y + f1, z + f));
  }

  private void setSprinting(boolean state) {
    isSprinting = state;
  }

  private boolean pushOutOfBlocks(double x, double y, double z) {
    if (noClip) return false;
    BlockPos blockPos = new BlockPos(x, y, z);
    double d0 = x - (double) blockPos.getX();
    double d1 = z - (double) blockPos.getZ();
    int entHeight = Math.max((int) Math.ceil(height), 1);
    boolean inTranslucentBlock = !isHeadspaceFree(blockPos, entHeight);

    if (inTranslucentBlock) {
      int i = -1;
      double d2 = 9999.0;
      if (isHeadspaceFree(blockPos.west(), entHeight) && d0 < d2) {
        d2 = d0;
        i = 0;
      }
      if (isHeadspaceFree(blockPos.east(), entHeight) && 1.0 - d0 < d2) {
        d2 = 1.0 - d0;
        i = 1;
      }
      if (isHeadspaceFree(blockPos.north(), entHeight) && d1 < d2) {
        d2 = d1;
        i = 4;
      }
      if (isHeadspaceFree(blockPos.south(), entHeight) && 1.0 - d1 < d2) {
        i = 5;
      }

      float f2 = 0.1f;
      if (i == 0) motionX = -f2;
      else if (i == 1) motionX = f2;
      else if (i == 4) motionZ = -f2;
      else if (i == 5) motionZ = f2;
    }
    return false;
  }

  private boolean isHeadspaceFree(BlockPos pos, int height) {
    for (int y = 0; y < height; y++) {
      if (!isOpenBlockSpace(pos.add(0, y, 0))) {
        return false;
      }
    }
    return true;
  }

  private boolean isOpenBlockSpace(BlockPos pos) {
    IBlockState state = getBlockState(pos);
    return state == null || !state.getBlock().isNormalCube();
  }

  private void playerSideMoveEntityWithHeading(float strafing, float forward) {
    if (capabilities.isFlying && ridingEntity == null) {
      double d3 = motionY;
      float f = jumpMovementFactor;
      jumpMovementFactor = capabilities.getFlySpeed() * (isSprinting() ? 2 : 1);
      livingEntitySideMoveEntityWithHeading(strafing, forward);
      motionY = d3 * 0.6;
      jumpMovementFactor = f;
    } else {
      livingEntitySideMoveEntityWithHeading(strafing, forward);
    }
  }

  private void livingEntitySideMoveEntityWithHeading(float strafing, float forwards) {
    if (!isServerWorld()) return;

    double d0;
    float f5, f6;

    if (isInWater() && !capabilities.isFlying) {
      // Water movement
      if (isInLava() && !capabilities.isFlying) {
        d0 = posY;
        moveFlying(strafing, forwards, 0.02f);
        moveEntity(motionX, motionY, motionZ);
        motionX *= 0.5;
        motionY *= 0.5;
        motionZ *= 0.5;
        motionY -= 0.02;
        if (isCollidedHorizontally
            && isOffsetPositionInLiquid(
                motionX, motionY + 0.6000000238418579 - posY + d0, motionZ)) {
          motionY = 0.30000001192092896;
        }
      } else {
        d0 = posY;
        f5 = 0.8f;
        f6 = 0.02f;
        float f3 = EnchantmentHelper.getDepthStriderModifier(player);
        if (f3 > 3.0f) f3 = 3.0f;
        if (!onGround) f3 *= 0.5f;
        if (f3 > 0.0f) {
          f5 += (0.54600006f - f5) * f3 / 3.0f;
          f6 += (getAIMoveSpeed() * 1.0f - f6) * f3 / 3.0f;
        }
        moveFlying(strafing, forwards, f6);
        moveEntity(motionX, motionY, motionZ);
        motionX *= f5;
        motionY *= 0.800000011920929;
        motionZ *= f5;
        motionY -= 0.02;
        if (isCollidedHorizontally
            && isOffsetPositionInLiquid(
                motionX, motionY + 0.6000000238418579 - posY + d0, motionZ)) {
          motionY = 0.30000001192092896;
        }
      }
    } else {
      // Normal/ground movement
      if (isInLava() && !capabilities.isFlying) {
        d0 = posY;
        moveFlying(strafing, forwards, 0.02f);
        moveEntity(motionX, motionY, motionZ);
        motionX *= 0.5;
        motionY *= 0.5;
        motionZ *= 0.5;
        motionY -= 0.02;
        if (isCollidedHorizontally
            && isOffsetPositionInLiquid(
                motionX, motionY + 0.6000000238418579 - posY + d0, motionZ)) {
          motionY = 0.30000001192092896;
        }
      } else {
        float f4 = 0.91f;
        if (onGround) {
          f4 =
              worldObj
                      .getBlockState(
                          new BlockPos(
                              MathHelper.floor_double(posX),
                              MathHelper.floor_double(box.minY) - 1,
                              MathHelper.floor_double(posZ)))
                      .getBlock()
                      .slipperiness
                  * 0.91f;
        }

        float f = 0.16277136f / (f4 * f4 * f4);
        f5 = onGround ? getAIMoveSpeed() * f : jumpMovementFactor;

        moveFlying(strafing, forwards, f5);
        f4 = 0.91f;
        if (onGround) {
          f4 =
              worldObj
                      .getBlockState(
                          new BlockPos(
                              MathHelper.floor_double(posX),
                              MathHelper.floor_double(box.minY) - 1,
                              MathHelper.floor_double(posZ)))
                      .getBlock()
                      .slipperiness
                  * 0.91f;
        }

        if (isOnLadder()) {
          f6 = 0.15f;
          motionX = MathHelper.clamp_double(motionX, -f6, f6);
          motionZ = MathHelper.clamp_double(motionZ, -f6, f6);
          fallDistance = 0.0f;
          if (motionY < -0.15) motionY = -0.15;
          boolean flag = isSneaking();
          if (flag && motionY < 0.0) motionY = 0.0;
        }

        moveEntity(motionX, motionY, motionZ);
        if (isCollidedHorizontally && isOnLadder()) motionY = 0.2;

        if (worldObj.isRemote
            && (!worldObj.isBlockLoaded(new BlockPos((int) posX, 0, (int) posZ))
                || !worldObj
                    .getChunkFromBlockCoords(new BlockPos((int) posX, 0, (int) posZ))
                    .isLoaded())) {
          motionY = posY > 0.0 ? -0.1 : 0.0;
        } else {
          motionY -= 0.08;
        }

        motionY *= 0.9800000190734863;
        motionX *= f4;
        motionZ *= f4;
      }
    }
  }

  private void moveEntity(double xMotion, double yMotion, double zMotion) {
    double velocityX = xMotion;
    double velocityY = yMotion;
    double velocityZ = zMotion;

    if (noClip) {
      setEntityBoundingBox(box.offset(velocityX, velocityY, velocityZ));
      resetPositionToBB();
      return;
    }

    double d0 = posX;
    double d1 = posY;
    double d2 = posZ;

    if (isInWeb) {
      isInWeb = false;
      velocityX *= 0.25;
      velocityY *= 0.05000000074505806;
      velocityZ *= 0.25;
      motionX = 0.0;
      motionY = 0.0;
      motionZ = 0.0;
    }

    double d3 = velocityX;
    double d4 = velocityY;
    double d5 = velocityZ;

    boolean flag = onGround && (isSneaking() || safeWalk);
    if (flag) {
      double[] collisionCheck = checkForCollision(velocityX, velocityZ);
      d3 = collisionCheck[0];
      d5 = collisionCheck[1];
    }

    List<AxisAlignedBB> list1 =
        worldObj.getCollidingBoundingBoxes(player, box.addCoord(velocityX, velocityY, velocityZ));
    AxisAlignedBB axisalignedbb = box;

    for (AxisAlignedBB aabb : list1) {
      velocityY = aabb.calculateYOffset(box, velocityY);
    }

    setEntityBoundingBox(box.offset(0.0, velocityY, 0.0));
    boolean flag1 = onGround || (d4 != velocityY && d4 < 0);

    for (AxisAlignedBB aabb : list1) {
      velocityX = aabb.calculateXOffset(box, velocityX);
    }
    setEntityBoundingBox(box.offset(velocityX, 0.0, 0.0));

    for (AxisAlignedBB aabb : list1) {
      velocityZ = aabb.calculateZOffset(box, velocityZ);
    }
    setEntityBoundingBox(box.offset(0.0, 0.0, velocityZ));

    if (stepHeight > 0.0f && flag1 && (d3 != velocityX || d5 != velocityZ)) {
      double d11 = velocityX;
      double d7 = velocityY;
      double d8 = velocityZ;
      AxisAlignedBB axisalignedbb3 = box;
      setEntityBoundingBox(axisalignedbb);
      velocityY = stepHeight;
      List<AxisAlignedBB> list =
          worldObj.getCollidingBoundingBoxes(player, box.addCoord(d3, velocityY, d5));
      AxisAlignedBB axisalignedbb4 = box;
      AxisAlignedBB axisalignedbb5 = axisalignedbb4.addCoord(d3, 0.0, d5);
      double d9 = velocityY;

      for (AxisAlignedBB aabb : list) {
        d9 = aabb.calculateYOffset(axisalignedbb5, d9);
      }
      axisalignedbb4 = axisalignedbb4.offset(0.0, d9, 0.0);
      double d15 = d3;
      for (AxisAlignedBB aabb : list) {
        d15 = aabb.calculateXOffset(axisalignedbb4, d15);
      }
      axisalignedbb4 = axisalignedbb4.offset(d15, 0.0, 0.0);
      double d16 = d5;
      for (AxisAlignedBB aabb : list) {
        d16 = aabb.calculateZOffset(axisalignedbb4, d16);
      }
      axisalignedbb4 = axisalignedbb4.offset(0.0, 0.0, d16);
      AxisAlignedBB axisalignedbb14 = box;
      double d17 = velocityY;
      for (AxisAlignedBB aabb : list) {
        d17 = aabb.calculateYOffset(axisalignedbb14, d17);
      }
      axisalignedbb14 = axisalignedbb14.offset(0.0, d17, 0.0);
      double d18 = d3;
      for (AxisAlignedBB aabb : list) {
        d18 = aabb.calculateXOffset(axisalignedbb14, d18);
      }
      axisalignedbb14 = axisalignedbb14.offset(d18, 0.0, 0.0);
      double d19 = d5;
      for (AxisAlignedBB aabb : list) {
        d19 = aabb.calculateZOffset(axisalignedbb14, d19);
      }
      axisalignedbb14 = axisalignedbb14.offset(0.0, 0.0, d19);

      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;

      if (d20 > d10) {
        velocityX = d15;
        velocityZ = d16;
        velocityY = -d9;
        setEntityBoundingBox(axisalignedbb4);
      } else {
        velocityX = d18;
        velocityZ = d19;
        velocityY = -d17;
        setEntityBoundingBox(axisalignedbb14);
      }

      for (AxisAlignedBB aabb : list) {
        velocityY = aabb.calculateYOffset(box, velocityY);
      }
      setEntityBoundingBox(box.offset(0.0, velocityY, 0.0));

      if (d11 * d11 + d8 * d8 >= velocityX * velocityX + velocityZ * velocityZ) {
        velocityX = d11;
        velocityY = d7;
        velocityZ = d8;
        setEntityBoundingBox(axisalignedbb3);
      }
    }

    resetPositionToBB();
    isCollidedHorizontally = d3 != velocityX || d5 != velocityZ;
    isCollidedVertically = d4 != velocityY;
    onGround = isCollidedVertically && d4 < 0.0;
    isCollided = isCollidedHorizontally || isCollidedVertically;

    int i = MathHelper.floor_double(posX);
    int j = MathHelper.floor_double(posY - 0.20000000298023224);
    int k = MathHelper.floor_double(posZ);
    BlockPos blockPos = new BlockPos(i, j, k);
    Block block1 = worldObj.getBlockState(blockPos).getBlock();
    if (block1.getMaterial() == Material.air) {
      Block block = worldObj.getBlockState(blockPos.down()).getBlock();
      if (block instanceof BlockFence
          || block instanceof BlockWall
          || block instanceof BlockFenceGate) {
        block1 = block;
      }
    }

    updateFallState(velocityY, onGround);
    if (d3 != velocityX) motionX = 0.0;
    if (d5 != velocityZ) motionZ = 0.0;
    if (d4 != velocityY) onLanded(block1);

    if (canTriggerWalking() && !flag && ridingEntity == null) {
      double d12 = posX - d0;
      double d13 = posY - d1;
      double d14 = posZ - d2;
      if (block1 != Blocks.ladder) d13 = 0.0;
      if (block1 != null && onGround) onEntityCollidedWithBlock(block1);
      distanceWalkedModified =
          (float)
              ((double) distanceWalkedModified
                  + MathHelper.sqrt_double(d12 * d12 + d14 * d14) * 0.6);
      distanceWalkedOnStepModified =
          (float)
              ((double) distanceWalkedOnStepModified
                  + MathHelper.sqrt_double(d12 * d12 + d13 * d13 + d14 * d14) * 0.6);
      if (distanceWalkedOnStepModified > (float) nextStepDistance
          && block1.getMaterial() != Material.air) {
        nextStepDistance = (int) distanceWalkedOnStepModified + 1;
      }
    }

    doBlockCollisions();

    boolean flag2 = isWet();
    if (worldObj.isFlammableWithin(box.contract(0.001, 0.001, 0.001))) {
      if (!flag2) {
        ++fire;
        if (fire == 0) setFire(8);
      }
    } else if (fire <= 0) {
      fire = -fireResistance;
    }

    if (flag2 && fire > 0) {
      fire = -fireResistance;
    }
  }

  private double[] checkForCollision(double velocityX, double velocityZ) {
    double d3 = velocityX;
    double d5 = velocityZ;

    double d6 = 0.05;
    for (;
        velocityX != 0
            && worldObj.getCollidingBoundingBoxes(player, box.offset(velocityX, -1, 0)).isEmpty();
        d3 = velocityX) {
      if (velocityX < d6 && velocityX >= -d6) velocityX = 0;
      else if (velocityX > 0) velocityX -= d6;
      else velocityX += d6;
    }

    for (;
        velocityZ != 0
            && worldObj.getCollidingBoundingBoxes(player, box.offset(0, -1, velocityZ)).isEmpty();
        d5 = velocityZ) {
      if (velocityZ < d6 && velocityZ >= -d6) velocityZ = 0;
      else if (velocityZ > 0) velocityZ -= d6;
      else velocityZ += d6;
    }

    for (;
        velocityX != 0
            && velocityZ != 0
            && worldObj
                .getCollidingBoundingBoxes(player, box.offset(velocityX, -1, velocityZ))
                .isEmpty();
        d5 = velocityZ) {
      if (velocityX < 0.05 && velocityX >= -0.05) velocityX = 0;
      else if (velocityX > 0) velocityX -= 0.05;
      else velocityX += 0.05;

      d3 = velocityX;

      if (velocityZ < 0.05 && velocityZ >= -0.05) velocityZ = 0;
      else if (velocityZ > 0) velocityZ -= 0.05;
      else velocityZ += 0.05;
    }

    return new double[] {d3, d5};
  }

  public AxisAlignedBB getEntityBoundingBox() {
    return box;
  }

  private void setEntityBoundingBox(AxisAlignedBB box) {
    this.box = box;
  }

  private void setOnFireFromLava() {
    setFire(15);
  }

  private void setFire(int seconds) {
    int i = seconds * 20;
    i = net.minecraft.enchantment.EnchantmentProtection.getFireTimeForEntity(player, i);
    if (fire < i) fire = i;
  }

  private boolean isWet() {
    return inWater
        || isRainingAt(new BlockPos(posX, posY, posZ))
        || isRainingAt(new BlockPos(posX, posY + height, posZ));
  }

  private void doBlockCollisions() {
    BlockPos blockpos = new BlockPos(box.minX + 0.001, box.minY + 0.001, box.minZ + 0.001);
    BlockPos blockpos1 = new BlockPos(box.maxX - 0.001, box.maxY - 0.001, box.maxZ - 0.001);
    if (isAreaLoaded(
        blockpos.getX(),
        blockpos.getY(),
        blockpos.getZ(),
        blockpos1.getX(),
        blockpos.getY(),
        blockpos1.getZ(),
        true)) {
      for (int ix = blockpos.getX(); ix <= blockpos1.getX(); ix++) {
        for (int j = blockpos.getY(); j <= blockpos1.getY(); j++) {
          for (int k = blockpos.getZ(); k <= blockpos1.getZ(); k++) {
            BlockPos pos = new BlockPos(ix, j, k);
            IBlockState state = worldObj.getBlockState(pos);
            try {
              Block block = state.getBlock();
              if (block instanceof BlockWeb) {
                isInWeb = true;
              } else if (block instanceof BlockSoulSand) {
                motionX *= 0.4;
                motionZ *= 0.4;
              }
            } catch (Throwable t) {
              t.printStackTrace();
            }
          }
        }
      }
    }
  }

  private void updateFallState(double motionY, boolean onGround) {
    if (!isInWater()) handleWaterMovement();
    if (onGround) {
      if (fallDistance > 0.0f) fallDistance = 0.0f;
    } else if (motionY < 0.0) {
      fallDistance = (float) ((double) fallDistance - motionY);
    }
  }

  private boolean handleWaterMovement() {
    if (handleMaterialAcceleration(
        box.expand(0.0, -0.4000000059604645, 0.0).contract(0.001, 0.001, 0.001), Material.water)) {
      fallDistance = 0.0f;
      inWater = true;
      fire = 0;
    } else {
      inWater = false;
    }
    return inWater;
  }

  private boolean handleMaterialAcceleration(AxisAlignedBB boundingBox, Material material) {
    int i = MathHelper.floor_double(boundingBox.minX);
    int j = MathHelper.floor_double(boundingBox.maxX + 1.0);
    int k = MathHelper.floor_double(boundingBox.minY);
    int l = MathHelper.floor_double(boundingBox.maxY + 1.0);
    int i1 = MathHelper.floor_double(boundingBox.minZ);
    int j1 = MathHelper.floor_double(boundingBox.maxZ + 1.0);

    if (!isAreaLoaded(i, k, i1, j, l, j1, true)) return false;

    boolean flag = false;
    Vec3 vec3 = new Vec3(0.0, 0.0, 0.0);
    BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);

    for (int k1 = i; k1 < j; k1++) {
      for (int l1 = k; l1 < l; l1++) {
        for (int i2 = i1; i2 < j1; i2++) {
          blockPos.set(k1, l1, i2);
          IBlockState state = getBlockState(blockPos);
          if (state != null && state.getBlock().getMaterial() == material) {
            float liquidHeight =
                BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL));
            double d0 = (float) (l1 + 1) - liquidHeight;
            if ((double) l >= d0) {
              flag = true;
              vec3 = state.getBlock().modifyAcceleration(worldObj, blockPos, player, vec3);
            }
          }
        }
      }
    }

    // PooledMutableBlockPos not available in 1.8.9, using MutableBlockPos instead

    if (vec3.lengthVector() > 0.0 && isPushedByWater()) {
      vec3 = vec3.normalize();
      double d1 = 0.014;
      motionX += vec3.xCoord * d1;
      motionY += vec3.yCoord * d1;
      motionZ += vec3.zCoord * d1;
    }

    return flag;
  }

  private boolean isAreaLoaded(
      int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean idfk) {
    int minX1 = minX;
    int minZ1 = minZ;
    int maxX1 = maxX;
    int maxZ1 = maxZ;
    if (maxY >= 0 && minY < 256) {
      minX1 = minX1 >> 4;
      minZ1 = minZ1 >> 4;
      maxX1 = maxX1 >> 4;
      maxZ1 = maxZ1 >> 4;
      for (int i = minX1; i <= maxX1; i++) {
        for (int j = minZ1; j <= maxZ1; j++) {
          if (!isChunkLoaded(i, j, idfk)) return false;
        }
      }
      return true;
    }
    return false;
  }

  private void onEntityCollidedWithBlock(Block block) {
    if (block instanceof BlockSlime) {
      if (Math.abs(motionY) < 0.1 && !isSneaking()) {
        double motion = 0.4 + Math.abs(motionY) * 0.2;
        motionX *= motion;
        motionZ *= motion;
      }
    }
  }

  private boolean canTriggerWalking() {
    return !capabilities.isFlying;
  }

  public boolean isOnLadder() {
    int i = MathHelper.floor_double(posX);
    int j = MathHelper.floor_double(box.minY);
    int k = MathHelper.floor_double(posZ);
    Block block = worldObj.getBlockState(new BlockPos(i, j, k)).getBlock();
    return isLivingOnLadder(block, worldObj, new BlockPos(i, j, k), player);
  }

  private void moveFlying(float strafe, float forward, float friction) {
    float newStrafe = strafe;
    float newForward = forward;
    float f = newStrafe * newStrafe + newForward * newForward;
    if (f >= 1.0E-4f) {
      f = MathHelper.sqrt_float(f);
      if (f < 1.0f) f = 1.0f;
      f = friction / f;
      newStrafe *= f;
      newForward *= f;
      float f1 = MathHelper.sin(rotationYaw * (float) Math.PI / 180.0f);
      float f2 = MathHelper.cos(rotationYaw * (float) Math.PI / 180.0f);
      motionX += (double) (newStrafe * f2 - newForward * f1);
      motionZ += (double) (newForward * f2 + newStrafe * f1);
    }
  }

  private void jump() {
    motionY = (double) getJumpUpwardsMotion();
    if (isPotionActive(Potion.jump)) {
      motionY += (double) ((float) (getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1f);
    }
    if (isSprinting()) {
      float f = rotationYaw * 0.017453292f;
      motionX -= (double) (MathHelper.sin(f) * 0.2f);
      motionZ += (double) (MathHelper.cos(f) * 0.2f);
    }
    isAirBorne = true;
  }

  private boolean isSprinting() {
    return isSprinting;
  }

  private boolean isPotionActive(Potion potion) {
    return player.getActivePotionEffect(potion) != null;
  }

  private PotionEffect getActivePotionEffect(Potion potion) {
    return player.getActivePotionEffect(potion);
  }

  private float getJumpUpwardsMotion() {
    return 0.42f;
  }

  public boolean isInWater() {
    return inWater;
  }

  private void updateLivingEntityInput() {
    moveForward = movementInput.moveForward;
    moveStrafing = movementInput.moveStrafe;
    isJumping = movementInput.jump;
  }

  private boolean isServerWorld() {
    return true;
  }

  private boolean isMovementBlocked() {
    return player.getHealth() <= 0f || getPrivateBoolean(player, EntityPlayer.class, "sleeping");
  }

  public boolean isInLava() {
    return worldObj.isMaterialInBB(
        box.expand(-0.10000000149011612, -0.4000000059604645, -0.10000000149011612), Material.lava);
  }

  private void updateAITick() {
    motionY += 0.03999999910593033;
  }

  private boolean isOffsetPositionInLiquid(double x, double y, double z) {
    AxisAlignedBB box2 = box.offset(x, y, z);
    return isLiquidPresentInAABB(box2);
  }

  private boolean isLiquidPresentInAABB(AxisAlignedBB box2) {
    return worldObj.getCollidingBoundingBoxes(player, box2).isEmpty()
        && !worldObj.isAnyLiquid(box2);
  }

  public List<AxisAlignedBB> getCollidingBoundingBoxes(AxisAlignedBB box2) {
    List<AxisAlignedBB> list = new ArrayList<>();
    int i = MathHelper.floor_double(box2.minX);
    int j = MathHelper.floor_double(box2.maxX + 1.0);
    int k = MathHelper.floor_double(box2.minY);
    int l = MathHelper.floor_double(box2.maxY + 1.0);
    int i1 = MathHelper.floor_double(box2.minZ);
    int j1 = MathHelper.floor_double(box2.maxZ + 1.0);
    WorldBorder wb = getWorldBorder();
    boolean flag = isOutsideBorder;
    boolean flag1 = isInsideBorder(wb, flag);
    IBlockState iblockstate = Blocks.stone.getDefaultState();
    BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);

    for (int k1 = i; k1 < j; k1++) {
      for (int l1 = i1; l1 < j1; l1++) {
        if (isBlockLoaded(blockPos.set(k1, 64, l1))) {
          for (int i2 = k - 1; i2 < l; i2++) {
            blockPos.set(k1, i2, l1);
            if (flag && flag1) isOutsideBorder = false;
            else if (!flag && !flag1) isOutsideBorder = true;
            IBlockState state = iblockstate;
            if (wb.contains(blockPos) || !flag1) {
              state = this.getBlockState(blockPos);
            }
            if (state != null) {
              state
                  .getBlock()
                  .addCollisionBoxesToList(worldObj, blockPos, state, box2, list, player);
            }
          }
        }
      }
    }

    // blockPos.release() - replaced PooledMutableBlockPos with MutableBlockPos

    double d0 = 0.25;
    @SuppressWarnings("unchecked")
    List<Entity> entities =
        worldObj.getEntitiesWithinAABBExcludingEntity(player, box2.expand(d0, d0, d0));
    for (Entity entity : entities) {
      if (riddenByEntity != entity && ridingEntity != entity) {
        AxisAlignedBB boundingBox = entity.getCollisionBoundingBox();
        if (boundingBox != null && boundingBox.intersectsWith(box2)) {
          list.add(boundingBox);
        }
        AxisAlignedBB collisionBox = getCollisionBox(player, entity);
        if (collisionBox != null && collisionBox.intersectsWith(box2)) {
          list.add(collisionBox);
        }
      }
    }

    return list;
  }

  public IBlockState getBlockState(BlockPos blockPos) {
    return worldObj.getBlockState(blockPos);
  }

  private Chunk getChunkFromBlockCoords(BlockPos blockPos) {
    return getChunkFromChunkCoords(blockPos.getX() >> 4, blockPos.getZ() >> 4);
  }

  private Chunk getChunkFromChunkCoords(int x, int z) {
    return chunkProvider.provideChunk(x, z);
  }

  private boolean isValid(BlockPos pos) {
    return pos.getX() >= -30000000
        && pos.getZ() >= -30000000
        && pos.getX() < 30000000
        && pos.getZ() < 30000000
        && pos.getY() >= 0
        && pos.getY() < 256;
  }

  private WorldBorder getWorldBorder() {
    return worldBorder;
  }

  private boolean isInsideBorder(WorldBorder border, boolean insideBorder) {
    double d0 = border.minX();
    double d1 = border.minZ();
    double d2 = border.maxX();
    double d3 = border.maxZ();
    if (insideBorder) {
      ++d0;
      ++d1;
      --d2;
      --d3;
    } else {
      --d0;
      --d1;
      ++d2;
      ++d3;
    }
    return posX > d0 && posX < d2 && posZ > d1 && posZ < d3;
  }

  private boolean isBlockLoaded(BlockPos pos) {
    return isBlockLoaded(pos, true);
  }

  private boolean isBlockLoaded(BlockPos pos, boolean check2) {
    return isValid(pos) && isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4, check2);
  }

  private boolean isChunkLoaded(int x, int z, boolean flag) {
    return chunkProvider.chunkExists(x, z) && (flag || !chunkProvider.provideChunk(x, z).isEmpty());
  }

  private AxisAlignedBB getCollisionBox(Entity player, Entity entity) {
    if (entity instanceof net.minecraft.entity.item.EntityBoat) {
      return entity.getEntityBoundingBox();
    }
    if (entity instanceof net.minecraft.entity.item.EntityMinecart) {
      return player.getCollisionBox(entity);
    }
    return null;
  }

  private float getAIMoveSpeed() {
    return (float) getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
  }

  private net.minecraft.entity.ai.attributes.IAttributeInstance getEntityAttribute(
      net.minecraft.entity.ai.attributes.IAttribute iAttribute) {
    return getAttributeMap().getAttributeInstance(iAttribute);
  }

  private BaseAttributeMap getAttributeMap() {
    if (attributeMap == null) {
      attributeMap = new ServersideAttributeMap();
    }
    return attributeMap;
  }

  private boolean isLivingOnLadder(
      Block block, World world, BlockPos pos, EntityLivingBase entity) {
    if (isSpectator) return false;
    if (!ForgeModContainer.fullBoundingBoxLadders) {
      return block != null && block.isLadder(world, pos, entity);
    }
    AxisAlignedBB bb = box;
    int mX = MathHelper.floor_double(bb.minX);
    int mY = MathHelper.floor_double(bb.minY);
    int mZ = MathHelper.floor_double(bb.minZ);
    for (int y2 = mY; (double) y2 < bb.maxY; y2++) {
      for (int x2 = mX; (double) x2 < bb.maxX; x2++) {
        for (int z2 = mZ; (double) z2 < bb.maxZ; z2++) {
          BlockPos tmp = new BlockPos(x2, y2, z2);
          if (world.getBlockState(tmp).getBlock().isLadder(world, tmp, entity)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void resetPositionToBB() {
    posX = (box.minX + box.maxX) / 2.0;
    posY = box.minY;
    posZ = (box.minZ + box.maxZ) / 2.0;
  }

  private void onLanded(Block block) {
    if (block instanceof BlockSlime) {
      if (isSneaking()) {
        motionY = 0.0;
      } else if (motionY < 0.0) {
        motionY = -motionY;
      }
    } else {
      motionY = 0.0;
    }
  }

  public boolean isSneaking() {
    return movementInput.sneak && !getPrivateBoolean(player, EntityPlayer.class, "sleeping");
  }

  private boolean isRainingAt(BlockPos pos) {
    if (worldObj.getRainStrength(1.0F) <= 0.2) return false;
    if (!canSeeSky(pos)) return false;
    if (worldObj.getPrecipitationHeight(pos).getY() > pos.getY()) return false;
    net.minecraft.world.biome.BiomeGenBase base = worldObj.getBiomeGenForCoords(pos);
    if (base.getEnableSnow()) return false;
    if (worldObj.canSnowAt(pos, false)) return false;
    return base.canRain();
  }

  private boolean canSeeSky(BlockPos pos) {
    return getChunkFromBlockCoords(pos).canSeeSky(pos);
  }

  private boolean isPushedByWater() {
    return !capabilities.isFlying;
  }
}
