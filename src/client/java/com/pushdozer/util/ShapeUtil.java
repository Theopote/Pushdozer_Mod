package com.pushdozer.util;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.shapes.GeometryShapeFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ShapeUtil {
    public static GeometryShape createShape(PlayerEntity player, PushdozerConfig config, BlockPos basePos) {
        if (basePos == null) {
            PushdozerMod.LOGGER.error("(basePos) 为 null，Cannot create GeometryShape。");
            return null;
        }

        return GeometryShapeFactory.createShape(config.getShape(), config, basePos);
    }

    public static BlockPos getTargetBlockPos(PlayerEntity player, PushdozerConfig config) {
        int maxDistance = config.getMaxOperationDistance();
        Vec3d lookVec = player.getRotationVec(1.0F).normalize();
        Vec3d eyePos = player.getEyePos();
        HitResult hitResult = player.raycast(maxDistance, 1.0F, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            double distance = eyePos.distanceTo(blockHit.getPos());
            if (distance <= maxDistance) {
                return blockHit.getBlockPos();
            }
        }

        // 如果没有击中方块或距离太远，返回玩家视线方向上的最远点
        Vec3d farPoint = eyePos.add(lookVec.multiply(maxDistance));
        return new BlockPos((int) Math.floor(farPoint.x), (int) Math.floor(farPoint.y), (int) Math.floor(farPoint.z));
    }
}