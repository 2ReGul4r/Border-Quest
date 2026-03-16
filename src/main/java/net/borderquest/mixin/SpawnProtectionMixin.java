package net.borderquest.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Désactive la spawn protection pour que tous les joueurs puissent
 * casser des blocs dans la zone de jeu, quelle que soit leur distance au spawn.
 */
@Mixin(MinecraftServer.class)
public class SpawnProtectionMixin {

    /**
     * @author BorderQuest
     * @reason Tous les joueurs doivent pouvoir interagir librement dans la zone.
     */
    @Overwrite
    public boolean isSpawnProtected(ServerWorld world, BlockPos pos, PlayerEntity player) {
        return false;
    }
}
