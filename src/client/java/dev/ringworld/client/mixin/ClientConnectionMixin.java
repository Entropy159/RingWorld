package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingInteractionCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.state.properties.TestBlockMode;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Sends canonical ring coordinates while the client keeps logical ones.
 */
@Mixin(Connection.class)
public abstract class ClientConnectionMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), argsOnly = true, name = "packet")
    private Packet<?> ringworld$canonicalizeOutboundPosition(Packet<?> packet) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return packet;
        if (packet instanceof ServerboundPlayerActionPacket action) {
            return new ServerboundPlayerActionPacket(action.getAction(), canonical(action.getPos(), geometry), action.getDirection(), action.getSequence());
        }
        if (packet instanceof ServerboundUseItemOnPacket(
                InteractionHand hand, BlockHitResult hit, int sequence
        )) {
            Vec3 hitPosition = hit.getLocation();
            RingInteractionCoordinates.CanonicalBlockHit canonical = RingInteractionCoordinates.canonicalizeBlockHit(geometry, hit.getBlockPos().getX(), hitPosition.x);
            BlockHitResult canonicalHit = new BlockHitResult(new Vec3(canonical.hitX(), hitPosition.y, hitPosition.z), hit.getDirection(), new BlockPos(canonical.blockX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ()), hit.isInside(), hit.isWorldBorderHit());
            return new ServerboundUseItemOnPacket(hand, canonicalHit, sequence);
        }
        if (packet instanceof ServerboundSignUpdatePacket(
                BlockPos pos1, List<String> lines, SignTextSlot slot
        )) {
            return new ServerboundSignUpdatePacket(canonical(pos1, geometry), lines, slot);
        }
        if (packet instanceof ServerboundPickItemFromBlockPacket(BlockPos pos, boolean includeData)) {
            return new ServerboundPickItemFromBlockPacket(canonical(pos, geometry), includeData);
        }
        if (packet instanceof ServerboundBlockEntityTagQueryPacket query) {
            return new ServerboundBlockEntityTagQueryPacket(query.getTransactionId(), canonical(query.getPos(), geometry));
        }
        if (packet instanceof ServerboundSetCommandBlockPacket commandBlock) {
            return new ServerboundSetCommandBlockPacket(canonical(commandBlock.getPos(), geometry), commandBlock.getCommand(), commandBlock.getMode(), commandBlock.isTrackOutput(), commandBlock.isConditional(), commandBlock.isAutomatic());
        }
        if (packet instanceof ServerboundSetStructureBlockPacket structureBlock) {
            return new ServerboundSetStructureBlockPacket(canonical(structureBlock.getPos(), geometry), structureBlock.getUpdateType(), structureBlock.getMode(), structureBlock.getName(), structureBlock.getOffset(), structureBlock.getSize(), structureBlock.getMirror(), structureBlock.getRotation(), structureBlock.getData(), structureBlock.isIgnoreEntities(), structureBlock.isStrict(), structureBlock.isShowAir(), structureBlock.isShowBoundingBox(), structureBlock.getIntegrity(), structureBlock.getSeed());
        }
        if (packet instanceof ServerboundSetJigsawBlockPacket jigsawBlock) {
            return new ServerboundSetJigsawBlockPacket(canonical(jigsawBlock.getPos(), geometry), jigsawBlock.getName(), jigsawBlock.getTarget(), jigsawBlock.getPool(), jigsawBlock.getFinalState(), jigsawBlock.getJoint(), jigsawBlock.getSelectionPriority(), jigsawBlock.getPlacementPriority());
        }
        if (packet instanceof ServerboundJigsawGeneratePacket jigsawGenerate) {
            return new ServerboundJigsawGeneratePacket(canonical(jigsawGenerate.getPos(), geometry), jigsawGenerate.levels(), jigsawGenerate.keepJigsaws());
        }
        if (packet instanceof ServerboundSetTestBlockPacket(
                BlockPos position, TestBlockMode mode, String message
        )) {
            return new ServerboundSetTestBlockPacket(canonical(position, geometry), mode, message);
        }
        if (packet instanceof ServerboundTestInstanceBlockActionPacket(
                BlockPos pos, ServerboundTestInstanceBlockActionPacket.Action action, TestInstanceBlockEntity.Data data
        )) {
            return new ServerboundTestInstanceBlockActionPacket(canonical(pos, geometry), action, data);
        }
        return packet;
    }

    @Unique
    private static BlockPos canonical(BlockPos pos, RingGeometry geometry) {
        return new BlockPos(geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ());
    }
}