package net.borderquest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

public class BorderQuestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("bq")
                // /bq  (sans sous-commande) → statut
                .executes(BorderQuestCommand::status)

                // /bq status — affiche l'objectif actuel
                .then(CommandManager.literal("status")
                    .executes(BorderQuestCommand::status))

                // /bq submit — soumet les items de l'inventaire
                .then(CommandManager.literal("submit")
                    .executes(BorderQuestCommand::submit))

                // /bq submitxp <amount> — soumet de l'XP pour cet objectif
                .then(CommandManager.literal("submitxp")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                        .executes(BorderQuestCommand::submitXp)))

                // /bq reset — remet à zéro (op niveau 2)
                .then(CommandManager.literal("reset")
                    .requires(src -> src.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(BorderQuestCommand::reset))

                // /bq skip — passe au stade suivant sans remplir l'objectif (op)
                .then(CommandManager.literal("skip")
                    .requires(src -> src.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(BorderQuestCommand::skip))

                // /bq reload — recharge l'état depuis le fichier (op)
                .then(CommandManager.literal("reload")
                    .requires(src -> src.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(BorderQuestCommand::reload))

                // /bq setaltar [nom] — enregistre le bloc regardé comme autel (op)
                .then(CommandManager.literal("setaltar")
                    .requires(src -> src.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(BorderQuestCommand::setAltar)
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> setAltarNamed(ctx, StringArgumentType.getString(ctx, "name")))))

                // /bq removealtar — retire le bloc regardé des autels (op)
                .then(CommandManager.literal("removealtar")
                    .requires(src -> src.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .executes(BorderQuestCommand::removeAltar))

                // /bq ladder — classement des donateurs (accessible à tous)
                .then(CommandManager.literal("ladder")
                    .executes(BorderQuestCommand::ladder))
        );
    }

    // -----------------------------------------------------------------------

    private static int status(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) {
            ctx.getSource().sendMessage(noManager());
            return 0;
        }
        ctx.getSource().sendMessage(mgr.getStatusText());
        return 1;
    }

    private static int submit(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendMessage(
                Text.literal("Cette commande doit etre executee par un joueur.").formatted(Formatting.RED));
            return 0;
        }

        ctx.getSource().sendMessage(mgr.submitItems(player));
        return 1;
    }

    private static int submitXp(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        var player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendMessage(
                Text.translatable("borderquest.msg.mustExecAsPlayer").formatted(Formatting.RED));
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ctx.getSource().sendMessage(mgr.submitXp(player, amount));
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        mgr.getState().reset();
        mgr.save();
        mgr.applyBorder();
        mgr.updateSidebar();

        ctx.getSource().getServer().getPlayerManager().broadcast(
            Text.literal("[BorderQuest] Reinitialise au stade 1 par un operateur !").formatted(Formatting.YELLOW),
            false
        );
        return 1;
    }

    private static int skip(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        if (mgr.isLastStage()) {
            ctx.getSource().sendMessage(
                Text.literal("Deja au dernier stade !").formatted(Formatting.YELLOW));
            return 0;
        }

        // Marquer l'objectif comme completé artificiellement puis avancer
        // On remplit directement les exigences dans l'état pour déclencher advanceStage via submitItems
        // Approche directe : on manipule l'état et on appelle applyBorder
        var state = mgr.getState();
        for (var req : mgr.getResolvedRequirements()) {
            state.submittedItems.put(req.itemId(), req.count());
        }
        // On avance manuellement
        state.currentStage++;
        state.submittedItems.clear();
        state.submittedXp = 0;
        mgr.save();
        mgr.applyBorder();
        mgr.updateSidebar();

        ctx.getSource().getServer().getPlayerManager().broadcast(
            Text.literal("[BorderQuest] Stade passe ! Maintenant au stade " + (state.currentStage + 1))
                .formatted(Formatting.YELLOW),
            false
        );
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        BorderQuestConfig.load();
        mgr.load();
        mgr.applyBorder();
        mgr.updateSidebar();
        ctx.getSource().sendMessage(Text.literal("[BorderQuest] Config et etat recharges !").formatted(Formatting.GREEN));
        return 1;
    }

    private static int setAltar(CommandContext<ServerCommandSource> ctx) {
        return setAltarNamed(ctx, "");
    }

    private static int setAltarNamed(CommandContext<ServerCommandSource> ctx, String name) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendMessage(Text.literal("Commande reservee aux joueurs.").formatted(Formatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendMessage(Text.literal("Regardez un bloc pour le definir comme autel.").formatted(Formatting.RED));
            return 0;
        }

        if (mgr.addAltar(pos, name)) {
            String label = name.isBlank() ? "" : " \"" + name + "\"";
            ctx.getSource().sendMessage(Text.literal(
                "[BorderQuest] Autel" + label + " enregistre en "
                + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " (total: " + mgr.getAltarCount() + ")").formatted(Formatting.GREEN));
        } else {
            // Autel déjà existant → mettre à jour le nom si fourni
            if (!name.isBlank()) {
                mgr.setAltarName(pos, name);
                ctx.getSource().sendMessage(Text.literal(
                    "[BorderQuest] Nom de l'autel mis a jour : \"" + name + "\"").formatted(Formatting.GREEN));
            } else {
                ctx.getSource().sendMessage(Text.literal("[BorderQuest] Ce bloc est deja un autel.").formatted(Formatting.YELLOW));
            }
        }
        return 1;
    }

    private static int ladder(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        List<Map.Entry<String, Integer>> top = mgr.getTopDonors(10);
        if (top.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("Aucun don enregistre.").formatted(Formatting.YELLOW));
            return 0;
        }

        MutableText t = Text.empty();
        t.append(Text.literal("=== Classement des donateurs ===\n").formatted(Formatting.GOLD, Formatting.BOLD));
        for (int i = 0; i < top.size(); i++) {
            String name = mgr.getState().playerNames.getOrDefault(top.get(i).getKey(), "???");
            Formatting color = (i == 0) ? Formatting.GOLD : (i == 1) ? Formatting.GRAY : Formatting.WHITE;
            t.append(Text.literal("#" + (i + 1) + " " + name + " - " + top.get(i).getValue() + "\n").formatted(color));
        }
        ctx.getSource().sendMessage(t);
        return 1;
    }

    private static int removeAltar(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendMessage(Text.literal("Commande reservee aux joueurs.").formatted(Formatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendMessage(Text.literal("Regardez un autel pour le retirer.").formatted(Formatting.RED));
            return 0;
        }

        if (mgr.removeAltar(pos)) {
            ctx.getSource().sendMessage(Text.literal(
                "[BorderQuest] Autel retire (restants: " + mgr.getAltarCount() + ")").formatted(Formatting.GREEN));
        } else {
            ctx.getSource().sendMessage(Text.literal("[BorderQuest] Ce bloc n'est pas un autel.").formatted(Formatting.YELLOW));
        }
        return 1;
    }

    /** Retourne la position du bloc visé par le joueur (portée 5 blocs), ou null. */
    private static BlockPos getLookedBlock(ServerPlayerEntity player) {
        HitResult hit = player.raycast(5.0, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hit).getBlockPos();
    }

    // -----------------------------------------------------------------------

    private static Text noManager() {
        return Text.literal("[BorderQuest] Le mod n'est pas encore initialise.").formatted(Formatting.RED);
    }
}
