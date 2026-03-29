package net.borderquest;

import net.borderquest.map.MapIntegrationManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Formatting;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BorderQuest implements ModInitializer {

    public static final String MOD_ID = "borderquest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static BorderQuestManager manager;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(BorderQuestCommand::register);

        // Démarrage du serveur
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BorderQuestConfig.load();
            manager = new BorderQuestManager(server);
            manager.load();
            manager.setMapManager(new MapIntegrationManager(server));
            manager.applyBorder();
            manager.initSidebar();
            manager.updateSidebar();
            LOGGER.info(Text.translatable("borderquest.logger.loaded",
                manager.getState().currentStage + 1,
                BorderQuestManager.STAGES().size() - 1).getString());
        });

        // Arrêt
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) {
                manager.save();
                LOGGER.info(Text.translatable("borderquest.logger.saveStatus").getString());
            }
        });

        // Tick serveur (feux d'artifice de célébration)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) manager.tick();
        });

        // Connexion joueur
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (manager == null) return;

            ServerPlayerEntity player = handler.player;
            ServerWorld world = server.getOverworld();

            // Téléporter vers une position sûre si le joueur est sous-terre ou dans l'eau
            safeSpawnTeleport(player, world, manager);

            Text prefix = Text.translatable("borderquest.prefix")
                .formatted(Formatting.GREEN);

            Text message = Text.translatable(
                "borderquest.general.statusHint",
                Text.literal("/bq status").formatted(Formatting.WHITE)
            ).formatted(Formatting.GRAY);

            player.sendMessage(Text.empty()
                .append(prefix)
                .append(Text.literal(" "))
                .append(message)
            , false);

            // Décaler au tick suivant : Minecraft envoie ses paquets d'init (dont un
            // PlayerListHeaderS2CPacket vide) après l'événement JOIN, ce qui écraserait notre header.
            server.execute(() -> manager.updateSidebar());
        });

        // Clic droit sur un autel → don de l'objet en main
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (manager == null) return ActionResult.PASS;
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (!manager.isAltar(pos)) return ActionResult.PASS;

            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            Text result = manager.donateFromHand(serverPlayer);
            serverPlayer.sendMessage(result, true);  // action bar
            return ActionResult.SUCCESS;
        });

        LOGGER.info(Text.translatable("borderquest.logger.init").getString());
    }

    /**
     * Vérifie que le joueur spawn à la surface à l'intérieur de la zone.
     * Si sa position Y est sous le sol ou dans l'eau, il est téléporté à la surface.
     */
    private void safeSpawnTeleport(ServerPlayerEntity player, ServerWorld world,
                                   BorderQuestManager mgr) {
        double px = player.getX();
        double pz = player.getZ();
        double py = player.getY();

        double cx = mgr.getBorderCenterX();
        double cz = mgr.getBorderCenterZ();
        double radius = mgr.getCurrentStage().borderRadius;

        // Si le joueur est hors de la zone, le ramener au centre
        if (Math.abs(px - cx) > radius || Math.abs(pz - cz) > radius) {
            px = cx;
            pz = cz;
        }

        // Trouver le Y de surface (premier bloc solide non-liquide)
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) px, (int) pz);

        // Ne téléporter que si le joueur est clairement sous la surface ou dans l'eau
        if (py < topY - 2) {
            player.teleport(world, px, topY + 0.5, pz,
                java.util.Set.of(), player.getYaw(), player.getPitch(), false);
            LOGGER.info(Text.translatable("borderquest.logger.safeSpawnTeleport", player.getName().getString(), (int) px, topY, (int) pz).getString());
        }
    }
}
