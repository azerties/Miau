package miau.util.player;

import miau.mixin.IAccessorEntity;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;

public class RotationUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final float FAR_THRESHOLD = 180.0f;

  public static float serverYaw;
  public static float serverPitch;
  public static boolean customRots;

  public static float wrapAngleDiff(float angle, float target) {
    return target + MathHelper.wrapAngleTo180_float(angle - target);
  }

  public static float clampAngle(float angle, float maxAngle) {
    maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
    if (angle > maxAngle) {
      angle = maxAngle;
    } else if (angle < -maxAngle) {
      angle = -maxAngle;
    }
    return angle;
  }

  public static float smoothAngle(float angle, float smoothFactor) {
    return angle
        * (0.5f
            + 0.5f
                * (1.0f
                    - Math.max(
                        0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
  }

  public static float quantizeAngle(float angle) {
    return (float) ((double) angle - (double) angle % (double) 0.0096f);
  }

  public static float[] getRotationsToBox(
      AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
    double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
    double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
    double deltaY =
        eyePos.yCoord >= maxTargetY
            ? maxTargetY - eyePos.yCoord
            : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
    double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
    return getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
  }

  public static float[] getRotationsTo(
      double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
    return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
  }

  public static float[] getRotations(
      double targetX,
      double targetY,
      double targetZ,
      float currentYaw,
      float currentPitch,
      float maxAngle,
      float smoothFactor) {
    double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
    float yawDelta =
        MathHelper.wrapAngleTo180_float(
            (float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
    float pitchDelta =
        MathHelper.wrapAngleTo180_float(
            (float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
    yawDelta =
        Math.abs(yawDelta) <= 1.0f
            ? 0.0f
            : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
    pitchDelta =
        Math.abs(pitchDelta) <= 1.0f
            ? 0.0f
            : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
    return new float[] {
      quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta)
    };
  }

  public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
    double[] coords = new double[] {vector.xCoord, vector.yCoord, vector.zCoord};
    double[] minCoords = new double[] {boundingBox.minX, boundingBox.minY, boundingBox.minZ};
    double[] maxCoords = new double[] {boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ};
    for (int i = 0; i < 3; ++i) {
      if (coords[i] > maxCoords[i]) {
        coords[i] = maxCoords[i];
        continue;
      }
      if (!(coords[i] < minCoords[i])) continue;
      coords[i] = minCoords[i];
    }
    return new Vec3(coords[0], coords[1], coords[2]);
  }

  public static double distanceToEntity(Entity entity) {
    float borderSize = entity.getCollisionBorderSize();
    AxisAlignedBB boundingBox =
        entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
    return distanceToBox(boundingBox);
  }

  public static double distanceToBox(Entity entity, Vec3 point) {
    float borderSize = entity.getCollisionBorderSize();
    return clampVecToBox(
        entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
  }

  public static double distanceToBox(AxisAlignedBB boundingBox) {
    return clampVecToBox(boundingBox, mc.thePlayer.getPositionEyes(1.0f));
  }

  public static double clampVecToBox(AxisAlignedBB boundingBox, Vec3 point) {
    if (boundingBox.isVecInside(point)) {
      return 0.0;
    }
    Vec3 clampedPoint = clampVecToBox(point, boundingBox);
    double deltaX = clampedPoint.xCoord - point.xCoord;
    double deltaY = clampedPoint.yCoord - point.yCoord;
    double deltaZ = clampedPoint.zCoord - point.zCoord;
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
  }

  public static float angleToEntity(Entity entity) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    float borderSize = entity.getCollisionBorderSize();
    AxisAlignedBB boundingBox =
        entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
    if (boundingBox.isVecInside(eyePos)) {
      return 0.0f;
    }
    double deltaX = entity.posX - eyePos.xCoord;
    double deltaZ = entity.posZ - eyePos.zCoord;
    return Math.abs(
            MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI)
                    - 90.0f
                    - mc.thePlayer.rotationYaw))
        * 2.0f;
  }

  public static float getYawBetween(double x1, double z1, double x2, double z2) {
    return MathHelper.wrapAngleTo180_float(
        (float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI)
            - 90.0f
            - mc.thePlayer.rotationYaw);
  }

  public static MovingObjectPosition rayTrace(
      float yaw, float pitch, double distance, float partialTicks) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(partialTicks);
    Vec3 lookVec = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
    Vec3 targetPos =
        eyePos.addVector(
            lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
    return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
  }

  public static MovingObjectPosition rayTrace(Entity entity) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    float borderSize = entity.getCollisionBorderSize();
    Vec3 targetPos =
        clampVecToBox(
            eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
    return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
  }

  public static float[] getRotationsToPoint(
      double x, double y, double z, float baseYaw, float basePitch) {
    double deltaX = x - mc.thePlayer.posX;
    double deltaZ = z - mc.thePlayer.posZ;
    double deltaY = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
    double horizDistSq = deltaX * deltaX + deltaZ * deltaZ;

    float yaw;
    float targetPitch;
    if (horizDistSq < 1.0E-12) {
      yaw = baseYaw;
      targetPitch = (float) (-(Math.atan2(deltaY, 0) * 57.295780181884766));
    } else {
      float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 57.295780181884766) - 90.0f;
      yaw = baseYaw + MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
      double horizDist = MathHelper.sqrt_double(horizDistSq);
      targetPitch = (float) (-(Math.atan2(deltaY, horizDist) * 57.295780181884766));
    }

    float pitch = basePitch + MathHelper.wrapAngleTo180_float(targetPitch - basePitch) + 3.0f;
    return new float[] {yaw, clampPitch(pitch)};
  }

  public static float clampPitch(final float n) {
    return MathHelper.clamp_float(n, -90.0f, 90.0f);
  }

  public static float[] getRotations(Entity entity) {
    double yOffset =
        Math.max(
            0,
            Math.min(
                mc.thePlayer.posY - entity.posY + mc.thePlayer.getEyeHeight(),
                (entity.getEntityBoundingBox().maxY - entity.getEntityBoundingBox().minY) * 0.9));
    return calculate(new Vec3(entity.posX, entity.posY + yOffset, entity.posZ));
  }

  private static float randomAngle = 0.0f;
  private static float offsetX = 0.0f;
  private static float offsetY = 0.0f;

  public static float[] calculate(Vec3 to) {
    Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
    double diffX = to.xCoord - from.xCoord;
    double diffY = to.yCoord - from.yCoord;
    double diffZ = to.zCoord - from.zCoord;
    double distance = Math.hypot(diffX, diffZ);
    float yaw = (float) (MathHelper.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
    float pitch = (float) (-(MathHelper.atan2(diffY, distance) * 180.0 / Math.PI));
    return new float[] {
      MathHelper.wrapAngleTo180_float(yaw), MathHelper.wrapAngleTo180_float(pitch)
    };
  }

  public static float[] calculate(Entity entity) {
    double yOffset =
        Math.max(
            0,
            Math.min(
                mc.thePlayer.posY - entity.posY + mc.thePlayer.getEyeHeight(),
                (entity.getEntityBoundingBox().maxY - entity.getEntityBoundingBox().minY) * 0.9));
    return calculate(new Vec3(entity.posX, entity.posY + yOffset, entity.posZ));
  }

  public static float[] calculate(Entity entity, boolean adaptive, double range) {
    float[] normalRotations = calculate(entity);
    if (!adaptive || rayCastHit(normalRotations, range, entity)) {
      return normalRotations;
    }

    AxisAlignedBB boundingBox = entity.getEntityBoundingBox();
    double width = boundingBox.maxX - boundingBox.minX;
    double height = boundingBox.maxY - boundingBox.minY;
    double depth = boundingBox.maxZ - boundingBox.minZ;

    for (double yPercent = 1.0; yPercent >= 0.0; yPercent -= 0.25 + Math.random() * 0.1) {
      for (double xPercent = 1.0; xPercent >= -0.5; xPercent -= 0.5) {
        for (double zPercent = 1.0; zPercent >= -0.5; zPercent -= 0.5) {
          Vec3 targetPoint =
              new Vec3(
                  boundingBox.minX + width * xPercent,
                  boundingBox.minY + height * yPercent,
                  boundingBox.minZ + depth * zPercent);
          float[] adaptiveRotations = calculate(targetPoint);
          if (rayCastHit(adaptiveRotations, range, entity)) {
            return adaptiveRotations;
          }
        }
      }
    }

    return normalRotations;
  }

  public static boolean rayCastHit(float[] rotations, double range, Entity target) {
    MovingObjectPosition mop = RayCastUtil.rayCast(rotations[0], rotations[1], range, 0.0f, target);
    return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
  }

  public static float[] applySensitivityPatch(
      float yaw, float pitch, float prevYaw, float prevPitch) {
    float mouseSensitivity =
        (float)
            (mc.gameSettings.mouseSensitivity * (1.0 + Math.random() / 10000000.0) * 0.6F + 0.2F);
    double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
    float fixedYaw = prevYaw + (float) (Math.round((yaw - prevYaw) / multiplier) * multiplier);
    float fixedPitch =
        prevPitch + (float) (Math.round((pitch - prevPitch) / multiplier) * multiplier);
    return new float[] {fixedYaw, MathHelper.clamp_float(fixedPitch, -90.0F, 90.0F)};
  }

  public static float[] smoothRotation(
      float baseYaw,
      float basePitch,
      float targetYaw,
      float targetPitch,
      int speed,
      float randomizationPercent) {
    if (speed <= 0) {
      return new float[] {baseYaw, clampPitch(basePitch)};
    }
    if (speed >= 30) {
      return new float[] {targetYaw, clampPitch(targetPitch)};
    }
    float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
    float deltaPitch = targetPitch - basePitch;
    float magnitude = (float) MathHelper.sqrt_double(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
    if (magnitude < 0.001f) {
      return new float[] {targetYaw, clampPitch(targetPitch)};
    }
    float t = speed / 30f;
    float stepSize = t * t * 180f;
    float range = 0.6f * (float) (randomizationPercent / 100.0);
    float multiplier =
        (range <= 0.001f) ? 1.0f : (1.0f - range / 2f + (float) (Math.random() * range));
    stepSize *= multiplier;
    float proximityFactor = Math.min(1f, magnitude / FAR_THRESHOLD);
    proximityFactor = (float) Math.pow(proximityFactor, 0.7);
    float maxSlowdown = (float) (randomizationPercent / 100.0);
    float proximityMult = Math.max(0.8f, 1.0f - maxSlowdown * (1.0f - proximityFactor));
    stepSize *= proximityMult;
    float stepLength = Math.min(stepSize, magnitude);
    float scale = stepLength / magnitude;
    float stepYaw = deltaYaw * scale;
    float stepPitch = deltaPitch * scale;
    float yaw = baseYaw + stepYaw;
    float pitch = basePitch + stepPitch;
    return new float[] {yaw, clampPitch(pitch)};
  }

  public static float[] flexRotation(
      float targetYaw, float targetPitch, float baseYaw, float basePitch) {
    float sensitivity =
        (float)
            (mc.gameSettings.mouseSensitivity * (1.0 + Math.random() / 10000000.0) * 0.6F + 0.2F);
    double multiplier = sensitivity * sensitivity * sensitivity * 8.0F * 0.15D;
    float yaw = baseYaw + (float) (Math.round((targetYaw - baseYaw) / multiplier) * multiplier);
    float pitch =
        basePitch + (float) (Math.round((targetPitch - basePitch) / multiplier) * multiplier);
    return new float[] {yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
  }

  public static float[] antiDetectionRotation(
      float targetYaw,
      float targetPitch,
      float baseYaw,
      float basePitch,
      long lastPitchQuotient,
      boolean clampPitchForScaffoldE) {
    float mcpSensitivity =
        (float)
            (mc.gameSettings.mouseSensitivity * (1.0 + Math.random() / 10000000.0) * 0.6F + 0.2F);
    double multiplier = mcpSensitivity * mcpSensitivity * mcpSensitivity * 8.0F * 0.15D;
    if (multiplier < 0.01) multiplier = 0.01;

    float rawYawDelta = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
    long yawK = Math.round(rawYawDelta / multiplier);
    if (yawK == 0L) {
      yawK = rawYawDelta > 0.0f ? 1L : -1L;
    }
    float yaw = baseYaw + (float) ((double) yawK * multiplier);

    float rawPitchDelta = targetPitch - basePitch;
    long pitchK = Math.round(rawPitchDelta / multiplier);

    if (pitchK == 0L) {
      pitchK = rawPitchDelta > 0.0f ? 1L : -1L;
    }

    long absPitchK = Math.abs(pitchK);
    long absLastPitchK = Math.abs(lastPitchQuotient);
    if (absLastPitchK > 0L && absPitchK > 0L && gcd(absPitchK, absLastPitchK) > 1L) {
      long alt1 = pitchK > 0L ? pitchK + 1L : pitchK - 1L;
      long alt2 = pitchK > 0L ? pitchK - 1L : pitchK + 1L;
      if (alt2 == 0L) alt2 = pitchK > 0L ? 1L : -1L;

      long absAlt1 = Math.abs(alt1);
      long absAlt2 = Math.abs(alt2);
      if (absAlt1 > 0L && gcd(absAlt1, absLastPitchK) == 1L) {
        pitchK = alt1;
      } else if (absAlt2 > 0L && gcd(absAlt2, absLastPitchK) == 1L) {
        pitchK = alt2;
      }
    }

    double estimatedYawDelta = (double) yawK * multiplier;
    double estimatedPitchDelta = (double) pitchK * multiplier;
    if (clampPitchForScaffoldE
        && Math.abs(estimatedYawDelta) > 95.0
        && Math.abs(estimatedPitchDelta) > 8.0) {
      int sign = pitchK > 0L ? 1 : -1;
      pitchK = (long) ((double) sign * Math.floor(7.5 / multiplier));
      if (pitchK == 0L) pitchK = (long) sign;
    }

    float pitch = basePitch + (float) ((double) pitchK * multiplier);
    pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

    return new float[] {yaw, pitch};
  }

  private static long gcd(long a, long b) {
    if (b == 0L) return a;
    return gcd(b, a % b);
  }

  public static float[] getRotationsFromEye(Vec3 eye, double tx, double ty, double tz) {
    double dx = tx - eye.xCoord;
    double dy = ty - eye.yCoord;
    double dz = tz - eye.zCoord;
    double dist = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
    return new float[] {yaw, pitch};
  }

  public static MovingObjectPosition rayCastBlock(double distance, float yaw, float pitch) {
    Vec3 eyeVec = mc.thePlayer.getPositionEyes(1.0f);
    float f = MathHelper.cos(-yaw * ((float) Math.PI / 180F) - (float) Math.PI);
    float f1 = MathHelper.sin(-yaw * ((float) Math.PI / 180F) - (float) Math.PI);
    float f2 = -MathHelper.cos(-pitch * ((float) Math.PI / 180F));
    float f3 = MathHelper.sin(-pitch * ((float) Math.PI / 180F));
    Vec3 lookVec = new Vec3(f1 * f2, f3, f * f2);
    Vec3 sumVec =
        eyeVec.addVector(
            lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyeVec, sumVec, false, false, false);
    if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
    return mop;
  }

  public static float[] smooth(
      float[] lastRotation,
      float[] targetRotation,
      double speed,
      Entity targetEntity,
      double range) {
    float targetYaw = targetRotation[0];
    float targetPitch = targetRotation[1];
    float lastYaw = lastRotation[0];
    float lastPitch = lastRotation[1];

    if (targetEntity != null
        && (Math.abs(targetYaw - lastYaw) > 5 || Math.abs(targetPitch - lastPitch) > 5)) {
      double driftSpeed = (Math.random() * Math.random() * Math.random()) * 20.0;
      randomAngle +=
          (float)
              ((20.0
                      + (Math.random() - 0.5)
                          * (Math.random() * Math.random() * Math.random() * 360.0))
                  * (mc.thePlayer.ticksExisted / 10 % 2 == 0 ? -1 : 1));

      offsetX += -MathHelper.sin((float) Math.toRadians(randomAngle)) * driftSpeed;
      offsetY += MathHelper.cos((float) Math.toRadians(randomAngle)) * driftSpeed;

      targetYaw += offsetX;
      targetPitch += offsetY;

      if (!rayCastHit(new float[] {targetYaw, targetPitch}, range, targetEntity)) {
        randomAngle =
            (float)
                    Math.toDegrees(
                        Math.atan2(targetRotation[0] - targetYaw, lastPitch - targetRotation[1]))
                - 180.0F;
        targetYaw -= offsetX;
        targetPitch -= offsetY;

        offsetX += -MathHelper.sin((float) Math.toRadians(randomAngle)) * driftSpeed;
        offsetY += MathHelper.cos((float) Math.toRadians(randomAngle)) * driftSpeed;

        targetYaw += offsetX;
        targetPitch += offsetY;
      }

      if (!rayCastHit(new float[] {targetYaw, targetPitch}, range, targetEntity)) {
        offsetX = 0;
        offsetY = 0;
        targetYaw = (float) (targetRotation[0] + Math.random() * 2);
        targetPitch = (float) (targetRotation[1] + Math.random() * 2);
      }
    }

    if (speed != 0) {
      double deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - lastYaw);
      double deltaPitch = (targetPitch - lastPitch);
      double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

      if (distance > 0.001) {
        double distributionYaw = Math.abs(deltaYaw / distance);
        double distributionPitch = Math.abs(deltaPitch / distance);

        double maxYaw = speed * distributionYaw;
        double maxPitch = speed * distributionPitch;

        float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        float yaw = lastYaw + moveYaw;
        float pitch = lastPitch + movePitch;

        for (int i = 1; i <= (int) (Minecraft.getDebugFPS() / 20.0F + Math.random() * 10); ++i) {
          if (Math.abs(moveYaw) + Math.abs(movePitch) > 0.0001) {
            yaw += (Math.random() - 0.5) / 1000.0;
            pitch -= Math.random() / 200.0;
          }

          float[] fixedRotations = applySensitivityPatch(yaw, pitch, lastYaw, lastPitch);
          yaw = fixedRotations[0];
          pitch = Math.max(-90.0F, Math.min(90.0F, fixedRotations[1]));
        }
        return new float[] {yaw, pitch};
      }
    }
    return targetRotation;
  }

  public static Vec3 closestPointOnAabb(AxisAlignedBB box, Vec3 point) {
    double x = Math.max(box.minX, Math.min(box.maxX, point.xCoord));
    double y = Math.max(box.minY, Math.min(box.maxY, point.yCoord));
    double z = Math.max(box.minZ, Math.min(box.maxZ, point.zCoord));
    return new Vec3(x, y, z);
  }

  public static double distanceSqFromEyeToClosestOnAABB(Entity entity) {
    if (entity == null || mc.thePlayer == null) return Double.MAX_VALUE;
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    float borderSize = entity.getCollisionBorderSize();
    AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
    Vec3 closest = closestPointOnAabb(bb, eye);
    double dx = eye.xCoord - closest.xCoord;
    double dy = eye.yCoord - closest.yCoord;
    double dz = eye.zCoord - closest.zCoord;
    return dx * dx + dy * dy + dz * dz;
  }
}
