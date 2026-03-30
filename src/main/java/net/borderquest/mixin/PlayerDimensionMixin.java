package net.borderquest.mixin;

import net.borderquest.BorderQuest;
import net.borderquest.BorderQuestConfig;
import net.borderquest.BorderQuestManager;
import net.borderquest.Localization;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bloque l'accès à certaines dimensions jusqu'à un stade minimum configurable.
 * Exemple : le Nether est verrouillé jusqu'au stade 5, l'End jusqu'au stade 7.
 * Cible la méthode teleportTo(TeleportTarget) (1.21.x+).
 */
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerDimensionMixin {

    @Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
    private void bq_onTeleportTo(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) return;
        if (target == null || target.world() == null) return;

        String worldId = target.world().getRegistryKey().getValue().toString();
        BorderQuestConfig cfg = BorderQuestConfig.get();
        int currentStage1Based = mgr.getState().currentStage + 1;

        for (BorderQuestConfig.WorldLock lock : cfg.worldLocks) {
            if (lock.worldId.equals(worldId) && currentStage1Based < lock.requiredStage) {
                ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
                self.sendMessage(
                    Text.literal(Localization.translate("borderquest.general.dimensionLock", lock.requiredStage, currentStage1Based))
                        .formatted(Formatting.RED),
                    true
                );
                cir.setReturnValue(null);
                return;
            }
        }
    }
}

