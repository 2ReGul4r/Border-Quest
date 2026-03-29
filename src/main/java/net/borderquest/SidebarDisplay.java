package net.borderquest;

import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Affiche l'état de la quête dans le header/footer du Tab (liste des joueurs).
 * La sidebar scoreboard est désactivée.
 */
public class SidebarDisplay {

    private static final String OBJECTIVE_NAME = "bq_sidebar";

    private final MinecraftServer server;

    public SidebarDisplay(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Supprime l'ancienne sidebar scoreboard si elle existait encore.
     */
    public void init() {
        var scoreboard = server.getScoreboard();
        ScoreboardObjective existing = scoreboard.getNullableObjective(OBJECTIVE_NAME);
        if (existing != null) scoreboard.removeObjective(existing);
        // S'assurer qu'aucun objectif n'est affiché dans la sidebar
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
    }

    /**
     * Envoie le header/footer à tous les joueurs connectés.
     */
    public void update(BorderQuestManager manager) {
        Text header = buildHeader(manager);
        Text footer = buildFooter(manager);
        var packet = new PlayerListHeaderS2CPacket(header, footer);
        server.getPlayerManager().sendToAll(packet);
    }

    public void clear() {
        // Vider le header/footer
        var packet = new PlayerListHeaderS2CPacket(Text.empty(), Text.empty());
        server.getPlayerManager().sendToAll(packet);
    }

    // -----------------------------------------------------------------------

    private Text buildHeader(BorderQuestManager manager) {
        BorderQuestConfig cfg = BorderQuestConfig.get();
        MutableText t = Text.empty();
        QuestState state = manager.getState();

        t.append(Text.literal(cfg.sidebarHeaderTitle + "\n").formatted(Formatting.GOLD, Formatting.BOLD));

        if (manager.isLastStage()) {
            t.append(Text.literal(cfg.sidebarHeaderCompleteTitle + "\n").formatted(Formatting.GREEN, Formatting.BOLD));
            t.append(Text.literal(cfg.sidebarHeaderCompleteSubtitle).formatted(Formatting.YELLOW));
            return t;
        }

        int stageNum    = state.currentStage + 1;
        int totalStages = BorderQuestManager.STAGES().size() - 1;
        StageDefinition stage = manager.getCurrentStage();

        t.append(Text.literal(String.format(cfg.sidebarHeaderStageTemplate, stageNum, totalStages)).formatted(Formatting.AQUA, Formatting.BOLD));
        t.append(Text.literal(" \u2014 ").formatted(Formatting.DARK_GRAY));
        t.append(Text.literal(stage.title + "\n").formatted(Formatting.WHITE));
        t.append(Text.literal(String.format(cfg.sidebarHeaderRadiusTemplate, (int) stage.borderRadius)).formatted(Formatting.GRAY));
        t.append(Text.literal("\n\n").formatted(Formatting.WHITE));
        t.append(Text.literal(cfg.sidebarHeaderCollectTitle + "\n").formatted(Formatting.YELLOW));

        for (StageDefinition.ItemReq req : manager.getResolvedRequirements()) {
            int submitted = Math.min(state.submittedItems.getOrDefault(req.itemId(), 0), req.count());
            boolean done  = submitted >= req.count();
            String name   = req.itemId().replace("minecraft:", "");
            Formatting color = done ? Formatting.GREEN : Formatting.RED;
            String symbol = done ? "\u2714 " : "\u2718 ";
            t.append(Text.literal("  " + symbol + name + " : " + submitted + "/" + req.count() + "\n").formatted(color));
        }

        return t;
    }

    private Text buildFooter(BorderQuestManager manager) {
        QuestState state = manager.getState();
        if (state.playerDonations.isEmpty()) return Text.empty();

        MutableText t = Text.empty();
        t.append(Text.literal("\n"));
        t.append(Text.literal(BorderQuestConfig.get().sidebarFooterTopDonors + "\n").formatted(Formatting.GOLD, Formatting.BOLD));

        List<Map.Entry<String, Integer>> top = state.playerDonations.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());

        String[] prefixes = {"\u00a76#1 ", "\u00a77#2 ", "\u00a77#3 "};
        for (int i = 0; i < top.size(); i++) {
            String name = state.playerNames.getOrDefault(top.get(i).getKey(), "???");
            t.append(Text.literal(prefixes[i] + name + " - " + top.get(i).getValue() + "\n"));
        }

        return t;
    }
}
