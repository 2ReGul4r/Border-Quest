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
                Text.literal(Localization.translate("borderquest.msg.mustExecAsPlayer")).formatted(Formatting.RED));
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
            Text.literal(Localization.translate("borderquest.msg.reset")).formatted(Formatting.YELLOW),
            false
        );
        return 1;
    }

    private static int skip(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        if (mgr.isLastStage()) {
            ctx.getSource().sendMessage(
                Text.literal(Localization.translate("borderquest.msg.lastStage")).formatted(Formatting.YELLOW));
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
            Text.literal(Localization.translate("borderquest.msg.nextStage", state.currentStage + 1))
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
        ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.reload")).formatted(Formatting.GREEN));
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
            ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.mustExecAsPlayer")).formatted(Formatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.lookAtBlock")).formatted(Formatting.RED));
            return 0;
        }

        if (mgr.addAltar(pos, name)) {
            String label = name.isBlank() ? "" : " \"" + name + "\"";
            ctx.getSource().sendMessage(Text.literal(Localization.translate(
                "borderquest.msg.altarAdded", label, pos.getX(), pos.getY(), pos.getZ(), mgr.getAltarCount()))
                .formatted(Formatting.GREEN));
        } else {
            // Autel déjà existant → mettre à jour le nom si fourni
            if (!name.isBlank()) {
                mgr.setAltarName(pos, name);
                ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.altarNameUpdated", name)).formatted(Formatting.GREEN));
            } else {
                ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.altarAlreadyExists")).formatted(Formatting.YELLOW));
            }
        }
        return 1;
    }

    private static int ladder(CommandContext<ServerCommandSource> ctx) {
        BorderQuestManager mgr = BorderQuest.manager;
        if (mgr == null) { ctx.getSource().sendMessage(noManager()); return 0; }

        List<Map.Entry<String, Integer>> top = mgr.getTopDonors(10);
        if (top.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.noDonations")).formatted(Formatting.YELLOW));
            return 0;
        }

        MutableText t = Text.empty();
        t.append(Text.literal(Localization.translate("borderquest.msg.topDonors")).formatted(Formatting.GOLD, Formatting.BOLD));
        t.append(Text.literal("\n"));
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
            ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.mustExecAsPlayer")).formatted(Formatting.RED));
            return 0;
        }

        BlockPos pos = getLookedBlock(player);
        if (pos == null) {
            ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.lookAtBlock")).formatted(Formatting.RED));
            return 0;
        }

        if (mgr.removeAltar(pos)) {
            ctx.getSource().sendMessage(Text.literal(Localization.translate(
                "borderquest.msg.altarRemoved", mgr.getAltarCount())).formatted(Formatting.GREEN));
        } else {
            ctx.getSource().sendMessage(Text.literal(Localization.translate("borderquest.msg.notAnAltar")).formatted(Formatting.YELLOW));
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
        return Text.literal(Localization.translate("borderquest.msg.noManager")).formatted(Formatting.RED);
    }
}

