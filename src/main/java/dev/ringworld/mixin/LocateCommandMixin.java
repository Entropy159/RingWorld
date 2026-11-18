package dev.ringworld.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.server.RingWorldServer;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {
    @ModifyArg(method = "locateStructure", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"), index = 3)
    private static int increaseRange(int maxSearchRadius, @Local(name = "serverLevel") ServerLevel serverLevel) {
        var geometry = RingWorldServer.geometryFor(serverLevel);
        return geometry == null ? maxSearchRadius : geometry.circumferenceChunks();
    }
}
