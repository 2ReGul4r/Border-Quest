package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.borderquest.map.MapIntegrationManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static net.borderquest.StageDefinition.CategoryReq;
import static net.borderquest.StageDefinition.ItemReq;

public class BorderQuestManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Raccourci vers la liste des stades (définie dans la config). */
    public static List<StageDefinition> STAGES() { return BorderQuestConfig.get().stages; }

    private final MinecraftServer server;
    private QuestState state;
    private final Path savePath;

    private List<ItemReq> resolvedRequirements = new ArrayList<>();
    private List<StageDefinition.TagReq> resolvedTagRequirements = new ArrayList<>();
    private List<StageDefinition.XpReq> resolvedXpRequirements = new ArrayList<>();
    private Set<RegistryKey<Biome>> detectedBiomes = new HashSet<>();
    private SidebarDisplay sidebarDisplay;

    /** Centre de la barrière (calé sur le spawn monde). */
    private double borderCenterX = 0.5;
    private double borderCenterZ = 0.5;

    /** Ticks de célébration restants (200 = 10 s). */
    private int celebrationTicksLeft = 0;

    /** Compteur pour le refresh périodique de la sidebar (toutes les 200 ticks). */
    private int sidebarRefreshCounter = 0;

    /** Compteur pour les particules autour des autels. */
    private int altarParticleCounter = 0;

    /** Gestionnaire d'intégrations cartographiques (BlueMap, Dynmap). Peut être null. */
    private MapIntegrationManager mapManager;

    private static final Random RANDOM = new Random();

    // -----------------------------------------------------------------------

    public BorderQuestManager(MinecraftServer server) {
        this.server = server;
        this.savePath = server.getSavePath(WorldSavePath.ROOT).resolve("borderquest_state.json");
        this.state = new QuestState();
        this.sidebarDisplay = new SidebarDisplay(server);
    }

    public void setMapManager(MapIntegrationManager mm) { this.mapManager = mm; }

    // -----------------------------------------------------------------------
    // Persistance
    // -----------------------------------------------------------------------

    public void load() {
        if (Files.exists(savePath)) {
            try {
                String json = Files.readString(savePath);
                state = GSON.fromJson(json, QuestState.class);
                if (state == null) state = new QuestState();
                if (state.submittedItems == null) state.submittedItems = new HashMap<>();
                if (state.playerDonations == null) state.playerDonations = new HashMap<>();
                if (state.playerXpDonations == null) state.playerXpDonations = new HashMap<>();
                if (state.submittedTagItems == null) state.submittedTagItems = new HashMap<>();
                if (state.playerNames == null) state.playerNames = new HashMap<>();
                if (state.altarPositions == null) state.altarPositions = new java.util.ArrayList<>();
                if (state.altarNames == null)     state.altarNames     = new java.util.HashMap<>();
                state.currentStage = Math.max(0, Math.min(state.currentStage, STAGES().size() - 1));
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.loadStatus", state.currentStage + 1));
            } catch (IOException e) {
                BorderQuest.LOGGER.error(Localization.translate("borderquest.logger.loadReportFailed", e.getMessage()));
                state = new QuestState();
            }
        } else {
            state = new QuestState();
            BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.questStateCreated"));
        }
    }

    public void save() {
        try {
            Files.createDirectories(savePath.getParent());
            Files.writeString(savePath, GSON.toJson(state));
        } catch (IOException e) {
            BorderQuest.LOGGER.error(Localization.translate("borderquest.logger.saveReportFailed", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Résolution biome -> requirements
    // -----------------------------------------------------------------------

    public void resolveRequirements() {
        StageDefinition stage = getCurrentStage();
        resolvedRequirements = new ArrayList<>();
        if (stage.requirements != null) resolvedRequirements.addAll(stage.requirements);

        if (stage.categoryRequirements != null && !stage.categoryRequirements.isEmpty()) {
            detectedBiomes = BiomeResourceResolver.scanBiomes(server.getOverworld(), stage.borderRadius);
            for (CategoryReq catReq : stage.categoryRequirements) {
                ItemReq resolved = BiomeResourceResolver.resolveCategory(
                    catReq.category(), catReq.count(), detectedBiomes);
                resolvedRequirements.add(resolved);
            }
        }

        resolvedTagRequirements = stage.tagRequirements != null
            ? new ArrayList<>(stage.tagRequirements)
            : List.of();

        if (stage.xpRequirements != null && !stage.xpRequirements.isEmpty()) {
            resolvedXpRequirements = new ArrayList<>(stage.xpRequirements);
        } else {
            resolvedXpRequirements = List.of();
        }
    }

    public List<ItemReq> getResolvedRequirements() { return resolvedRequirements; }
    public List<StageDefinition.TagReq> getResolvedTagRequirements() { return resolvedTagRequirements; }
    public List<StageDefinition.XpReq> getResolvedXpRequirements() { return resolvedXpRequirements; }

    // -----------------------------------------------------------------------
    // Centre de la barrière
    // -----------------------------------------------------------------------

    /** Lit le spawn monde pour centrer la barrière, avec fallback (0, 0). */
    private void refreshBorderCenter() {
        try {
            WorldProperties.SpawnPoint sp = server.getOverworld().getSpawnPoint();
            BlockPos pos = sp.getPos();
            borderCenterX = pos.getX() + 0.5;
            borderCenterZ = pos.getZ() + 0.5;
            BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.centerBarrier",
                (int) borderCenterX, (int) borderCenterZ));
        } catch (Exception e) {
            BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.centerBarrierFailed"));
            borderCenterX = 0.5;
            borderCenterZ = 0.5;
        }
    }

    public double getBorderCenterX() { return borderCenterX; }
    public double getBorderCenterZ() { return borderCenterZ; }

    // -----------------------------------------------------------------------
    // Gestion de la barrière
    // -----------------------------------------------------------------------

    public void applyBorder() {
        refreshBorderCenter();
        StageDefinition stage = STAGES().get(state.currentStage);
        double diameter = stage.getDiameter();
        BorderQuestConfig cfg = BorderQuestConfig.get();

        WorldBorder owBorder = server.getOverworld().getWorldBorder();
        owBorder.setCenter(borderCenterX, borderCenterZ);
        owBorder.setSize(diameter);
        owBorder.setDamagePerBlock(cfg.borderDamagePerBlock);
        owBorder.setWarningBlocks(cfg.borderWarningBlocks);

        var nether = server.getWorld(World.NETHER);
        if (nether != null) {
            WorldBorder netherBorder = nether.getWorldBorder();
            netherBorder.setCenter(borderCenterX / cfg.netherScale, borderCenterZ / cfg.netherScale);
            netherBorder.setSize(diameter / cfg.netherScale);
            netherBorder.setDamagePerBlock(cfg.borderDamagePerBlock);
            netherBorder.setWarningBlocks(cfg.borderWarningBlocks);
        }

        resolveRequirements();
        if (mapManager != null) mapManager.updateBorder(borderCenterX, borderCenterZ, stage.borderRadius);
        BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.borderApplied",
            (int) stage.borderRadius, (int) borderCenterX, (int) borderCenterZ));
    }

    private void animateBorderExpansion(double newDiameter) {
        BorderQuestConfig cfg = BorderQuestConfig.get();
        long durationMs = cfg.borderExpansionDurationSeconds * 50L;
        long now = System.currentTimeMillis();
        double scale = cfg.netherScale;
        server.getOverworld().getWorldBorder()
            .interpolateSize(server.getOverworld().getWorldBorder().getSize(), newDiameter, durationMs, now);
        var nether = server.getWorld(World.NETHER);
        if (nether != null) {
            nether.getWorldBorder()
                .interpolateSize(nether.getWorldBorder().getSize(), newDiameter / scale, durationMs, now);
        }
    }

    // -----------------------------------------------------------------------
    // Sidebar
    // -----------------------------------------------------------------------

    public void initSidebar() { sidebarDisplay.init(); }
    public void updateSidebar() { sidebarDisplay.update(this); }

    // -----------------------------------------------------------------------
    // Tick (feux d'artifice)
    // -----------------------------------------------------------------------

    public void tick() {
        // Refresh périodique de la sidebar (toutes les 200 ticks = 10 s)
        sidebarRefreshCounter++;
        if (sidebarRefreshCounter >= 200) {
            sidebarRefreshCounter = 0;
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                updateSidebar();
            }
        }

        // Particules autour des autels
        BorderQuestConfig cfg = BorderQuestConfig.get();
        if (cfg.altarParticlesEnabled && state.altarPositions != null && !state.altarPositions.isEmpty()) {
            altarParticleCounter++;
            if (altarParticleCounter >= cfg.altarParticlePeriodTicks) {
                altarParticleCounter = 0;
                spawnAltarParticles();
            }
        }

        if (celebrationTicksLeft <= 0) return;
        celebrationTicksLeft--;
        // Un burst tous les 10 ticks (0,5 s), soit 20 bursts sur 10 s
        if (celebrationTicksLeft % 10 == 0) {
            spawnFireworkBurst();
        }
    }

    private void spawnAltarParticles() {
        ServerWorld world = server.getOverworld();
        for (String posKey : state.altarPositions) {
            String[] parts = posKey.split(",");
            if (parts.length != 3) continue;
            try {
                double x = Double.parseDouble(parts[0]) + 0.5;
                double y = Double.parseDouble(parts[1]) + 1.2;
                double z = Double.parseDouble(parts[2]) + 0.5;
                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 3, 0.3, 0.3, 0.3, 0.04);
            } catch (NumberFormatException ignored) {}
        }
    }

    private void spawnFireworkBurst() {
        ServerWorld world = server.getOverworld();
        double radius = Math.min(getCurrentStage().borderRadius * 0.8, 20);

        for (int i = 0; i < 4; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double dist  = RANDOM.nextDouble() * radius;
            double fx = borderCenterX + Math.cos(angle) * dist;
            double fz = borderCenterZ + Math.sin(angle) * dist;
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) fx, (int) fz);
            double fy = topY + 1 + RANDOM.nextDouble() * 3;

            FireworkRocketEntity rocket = new FireworkRocketEntity(
                world, fx, fy, fz, buildFirework());
            world.spawnEntity(rocket);
        }
    }

    private ItemStack buildFirework() {
        FireworkExplosionComponent.Type[] shapes = FireworkExplosionComponent.Type.values();
        FireworkExplosionComponent.Type shape = shapes[RANDOM.nextInt(shapes.length)];

        // Couleurs vives aléatoires
        int[] palette = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFF8800, 0xFFFFFF};
        int color1 = palette[RANDOM.nextInt(palette.length)];
        int color2 = palette[RANDOM.nextInt(palette.length)];
        int fade   = palette[RANDOM.nextInt(palette.length)];

        FireworkExplosionComponent explosion = new FireworkExplosionComponent(
            shape,
            new IntArrayList(new int[]{color1, color2}),
            new IntArrayList(new int[]{fade}),
            RANDOM.nextBoolean(), // trail
            RANDOM.nextBoolean()  // twinkle
        );

        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponentTypes.FIREWORKS,
            new FireworksComponent(1, List.of(explosion)));
        return stack;
    }

    // -----------------------------------------------------------------------
    // Célébration (titre + son + feux d'artifice)
    // -----------------------------------------------------------------------

    private void celebrateStageComplete(boolean isFinal, StageDefinition newStage) {
        // Titre plein écran
        BorderQuestConfig cfg = BorderQuestConfig.get();
        Text title    = isFinal
            ? Text.literal(cfg.celebrationTitleFinal).formatted(Formatting.GOLD, Formatting.BOLD)
            : Text.literal(cfg.celebrationTitleProgress).formatted(Formatting.AQUA, Formatting.BOLD);
        Text subtitle = isFinal
            ? Text.literal(cfg.celebrationSubtitleFinal).formatted(Formatting.YELLOW)
            : Text.literal(String.format(cfg.celebrationSubtitleProgress,
                (int) newStage.borderRadius, newStage.title)).formatted(Formatting.WHITE);

        server.getPlayerManager().sendToAll(new TitleFadeS2CPacket(10, 80, 20));
        server.getPlayerManager().sendToAll(new SubtitleS2CPacket(subtitle));
        server.getPlayerManager().sendToAll(new TitleS2CPacket(title));

        // Son pour chaque joueur (à sa position)
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getOverworld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                isFinal ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.MASTER,
                2.0f, 1.0f
            );
        }

        // Déclenche les feux d'artifice (durée configurable)
        celebrationTicksLeft = BorderQuestConfig.get().celebrationDurationTicks;
    }

    // -----------------------------------------------------------------------
    // Logique de progression
    // -----------------------------------------------------------------------

    public QuestState getState() { return state; }
    public StageDefinition getCurrentStage() { return STAGES().get(state.currentStage); }
    public boolean isLastStage() { return state.currentStage >= STAGES().size() - 1; }

    public boolean isStageComplete() {
        if (isLastStage()) return true;
        for (ItemReq req : resolvedRequirements) {
            if (state.submittedItems.getOrDefault(req.itemId(), 0) < req.count()) return false;
        }
        for (StageDefinition.TagReq tagReq : resolvedTagRequirements) {
            if (countSubmittedForTag(tagReq.tagId()) < tagReq.count()) return false;
        }
        int totalXpRequired = resolvedXpRequirements.stream().mapToInt(StageDefinition.XpReq::count).sum();
        if (totalXpRequired > 0 && state.submittedXp < totalXpRequired) return false;
        return true;
    }

    public Text submitItems(ServerPlayerEntity player) {
        if (isLastStage())
            return Text.literal(Localization.translate("borderquest.msg.barrierAlreadyRaised")).formatted(Formatting.GOLD);

        if (resolvedRequirements.isEmpty() && resolvedTagRequirements.isEmpty()) {
            if (resolvedXpRequirements != null && !resolvedXpRequirements.isEmpty()) {
                return Text.literal(Localization.translate("borderquest.msg.useSubmitXp")).formatted(Formatting.YELLOW);
            }
            return Text.literal(Localization.translate("borderquest.msg.noResourceRequirements")).formatted(Formatting.YELLOW);
        }

        boolean submittedAnything = false;
        StringBuilder log = new StringBuilder();
        String playerUuid = player.getUuidAsString();
        String playerName = player.getName().getString();
        int totalDonated = 0;

        for (ItemReq req : resolvedRequirements) {
            int remaining = req.count() - state.submittedItems.getOrDefault(req.itemId(), 0);
            if (remaining <= 0) continue;
            Item targetItem = Registries.ITEM.get(Identifier.of(req.itemId()));
            if (targetItem == Items.AIR) continue;
            int toTake = Math.min(remaining, countInInventory(player, targetItem));
            if (toTake <= 0) continue;
            removeFromInventory(player, targetItem, toTake);
            state.submittedItems.merge(req.itemId(), toTake, Integer::sum);
            submittedAnything = true;
            totalDonated += toTake;
            int newTotal = Math.min(state.submittedItems.get(req.itemId()), req.count());
            log.append(String.format("  +%d %s (%d/%d)\n", toTake,
                req.itemId().replace("minecraft:", ""), newTotal, req.count()));
        }

        for (StageDefinition.TagReq tagReq : resolvedTagRequirements) {
            int submitted = countSubmittedForTag(tagReq.tagId());
            int remaining = tagReq.count() - submitted;
            if (remaining <= 0) continue;
            TagKey<Item> tag = parseItemTag(tagReq.tagId());
            int toTake = Math.min(remaining, countInInventory(player, tag));
            if (toTake <= 0) continue;
            Map<String, Integer> removed = removeFromInventory(player, tag, toTake);
            if (removed.isEmpty()) continue;
            submittedAnything = true;
            int donated = removed.values().stream().mapToInt(Integer::intValue).sum();
            totalDonated += donated;
            for (Map.Entry<String, Integer> entry : removed.entrySet()) {
                state.submittedItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            state.submittedTagItems.merge(tagReq.tagId(), donated, Integer::sum);
            log.append(String.format("  +%d %s (%d/%d)\n", donated,
                tagReq.tagId().replace("minecraft:", ""), submitted + donated, tagReq.count()));
        }

        if (!submittedAnything)
            return Text.literal(Localization.translate("borderquest.msg.noResourcesToSubmit")).formatted(Formatting.RED);

        state.playerDonations.merge(playerUuid, totalDonated, Integer::sum);
        state.playerNames.put(playerUuid, playerName);

        // Annonce publique si le don dépasse le seuil configuré
        BorderQuestConfig cfgAnnounce = BorderQuestConfig.get();
        if (cfgAnnounce.donationAnnouncementsEnabled && totalDonated >= cfgAnnounce.donationAnnounceMinItems) {
            server.getPlayerManager().broadcast(
                Text.literal(Localization.translate("borderquest.msg.publicDonation", playerName, totalDonated)),
                false
            );
        }

        save();
        updateSidebar();

        if (isStageComplete()) {
            advanceStage();
            return Text.literal(Localization.translate("borderquest.msg.stageCompleted") + "\n"
                + log.toString().trim()).formatted(Formatting.GREEN);
        }
        return Text.literal(Localization.translate("borderquest.msg.resourcesSubmitted") + "\n"
            + log.toString().trim()).formatted(Formatting.GREEN);
    }

    public Text submitXp(ServerPlayerEntity player, int amount) {
        if (isLastStage())
            return Text.literal(Localization.translate("borderquest.msg.barrierAlreadyRaised")).formatted(Formatting.GOLD);

        int totalXpRequired = resolvedXpRequirements.stream().mapToInt(StageDefinition.XpReq::count).sum();
        if (totalXpRequired <= 0)
            return Text.literal(Localization.translate("borderquest.msg.noXpRequired")).formatted(Formatting.YELLOW);

        if (amount <= 0)
            return Text.literal(Localization.translate("borderquest.msg.invalidXpAmount")).formatted(Formatting.RED);

        int currentXp = player.totalExperience;
        if (currentXp <= 0)
            return Text.literal(Localization.translate("borderquest.msg.notEnoughXp")).formatted(Formatting.RED);

        int remaining = totalXpRequired - state.submittedXp;
        if (remaining <= 0)
            return Text.literal(Localization.translate("borderquest.msg.xpObjectiveAlreadyMet")).formatted(Formatting.GREEN);

        int toDonate = Math.min(amount, Math.min(remaining, currentXp));
        player.addExperience(-toDonate);
        state.submittedXp += toDonate;

        String playerUuid = player.getUuidAsString();
        String playerName = player.getName().getString();
        state.playerNames.put(playerUuid, playerName);

        state.playerXpDonations.merge(playerUuid, toDonate, Integer::sum);

        if (isStageComplete()) {
            advanceStage();
            return Text.literal(Localization.translate("borderquest.msg.stageCompletedWithXp", toDonate)).formatted(Formatting.GREEN);
        }
        return Text.literal(Localization.translate("borderquest.msg.xpSubmitted", toDonate, state.submittedXp, totalXpRequired)).formatted(Formatting.GREEN);
    }

    private void advanceStage() {
        // Récupérer les récompenses avant d'incrémenter
        List<StageDefinition.Reward> rewards = STAGES().get(state.currentStage).rewards;

        state.currentStage++;
        state.submittedItems.clear();
        state.submittedTagItems.clear();
        state.submittedXp = 0;
        save();

        StageDefinition newStage = STAGES().get(state.currentStage);
        boolean isFinal = isLastStage();
        double scale = BorderQuestConfig.get().netherScale;

        server.getOverworld().getWorldBorder().setCenter(borderCenterX, borderCenterZ);
        animateBorderExpansion(newStage.getDiameter());
        var nether = server.getWorld(World.NETHER);
        if (nether != null) nether.getWorldBorder().setCenter(borderCenterX / scale, borderCenterZ / scale);

        resolveRequirements();
        updateSidebar();

        if (mapManager != null) mapManager.updateBorder(borderCenterX, borderCenterZ, newStage.borderRadius);

        // Annonce chat
        Text announcement = isFinal
            ? Text.literal(Localization.translate("borderquest.msg.stageCompleteFinalAnnouncement")).formatted(Formatting.GOLD)
            : Text.literal(Localization.translate("borderquest.msg.stageValidatedAnnouncement",
                state.currentStage, (int) newStage.borderRadius, newStage.title)).formatted(Formatting.AQUA);
        server.getPlayerManager().broadcast(announcement, false);

        // Récompenses pour tous les joueurs connectés
        if (rewards != null && !rewards.isEmpty()) {
            distributeRewards(rewards);
        }

        // Notification Discord
        String discordMsg = isFinal
            ? Localization.translate("borderquest.msg.discordStageCompleteFinal")
            : Localization.translate("borderquest.msg.discordStageValidated",
                state.currentStage, (int) newStage.borderRadius, newStage.title);
        DiscordWebhook.sendAsync(discordMsg);

        // Célébration (titre + son + feux d'artifice)
        celebrateStageComplete(isFinal, newStage);
    }

    private void distributeRewards(List<StageDefinition.Reward> rewards) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (StageDefinition.Reward r : rewards) {
                try {
                    switch (r.type) {
                        case "item" -> {
                            if (!r.itemId.isBlank()) {
                                Item item = Registries.ITEM.get(Identifier.of(r.itemId));
                                if (item != Items.AIR) {
                                    ItemStack stack = new ItemStack(item, r.count);
                                    if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) {
                                        player.dropItem(stack, false, false);
                                    }
                                }
                            }
                        }
                        case "effect" -> {
                            if (!r.effectId.isBlank()) {
                                Registries.STATUS_EFFECT.getEntry(Identifier.of(r.effectId))
                                    .ifPresent(e -> player.addStatusEffect(
                                        new StatusEffectInstance(e, r.duration, r.amplifier)));
                            }
                        }
                        case "xp" -> {
                            if (r.amount > 0) player.addExperience(r.amount);
                        }
                    }
                } catch (Exception e) {
                    BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.rewardDistributionError", e.getMessage()));
                }
            }
            player.sendMessage(Text.literal(Localization.translate("borderquest.msg.rewardsDistributed")).formatted(Formatting.YELLOW), false);
        }
    }

    // -----------------------------------------------------------------------
    // Affichage /bq status
    // -----------------------------------------------------------------------

    public Text getStatusText() {
        if (isLastStage())
            return Text.literal(Localization.translate("borderquest.msg.statusBarrierFallen"));

        StageDefinition stage = getCurrentStage();
        StringBuilder sb = new StringBuilder();
        sb.append(Localization.translate("borderquest.msg.statusHeader", state.currentStage + 1,
            STAGES().size() - 1)).append("\n");
        sb.append(Localization.translate("borderquest.msg.statusStageTitle", stage.title)).append("\n");
        sb.append(Localization.translate("borderquest.msg.statusRadius", (int) stage.borderRadius)).append("\n");
        for (ItemReq req : resolvedRequirements) {
            int submitted = Math.min(state.submittedItems.getOrDefault(req.itemId(), 0), req.count());
            boolean done = submitted >= req.count();
            String name = req.itemId().replace("minecraft:", "");
            sb.append(done ? "\u00a7a[OK] " : "\u00a7c[ ]  ")
              .append(name).append(": ").append(submitted).append("/").append(req.count()).append("\n");
        }

        for (StageDefinition.TagReq req : resolvedTagRequirements) {
            int submitted = Math.min(countSubmittedForTag(req.tagId()), req.count());
            boolean done = submitted >= req.count();
            String name = req.tagId().replace("minecraft:", "");
            sb.append(done ? "\u00a7a[OK] " : "\u00a7c[ ]  ")
              .append(name).append(": ").append(submitted).append("/").append(req.count()).append("\n");
        }

        int totalXpRequired = resolvedXpRequirements.stream().mapToInt(StageDefinition.XpReq::count).sum();
        if (totalXpRequired > 0) {
            int submittedXp = state.submittedXp;
            boolean done = submittedXp >= totalXpRequired;
            sb.append(done ? "\u00a7a[OK] " : "\u00a7c[ ]  ")
              .append(Localization.translate("borderquest.msg.statusXpLabel", submittedXp, totalXpRequired)).append("\n");
            sb.append(Localization.translate("borderquest.msg.statusSubmitXpHint")).append("\n");
        }

        sb.append(Localization.translate("borderquest.msg.statusSubmitHint"));
        return Text.literal(sb.toString());
    }

    // -----------------------------------------------------------------------
    // Autel
    // -----------------------------------------------------------------------

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public boolean isAltar(BlockPos pos) {
        if (state.altarPositions == null) return false;
        return state.altarPositions.contains(posKey(pos));
    }

    public boolean addAltar(BlockPos pos) {
        return addAltar(pos, "");
    }

    public boolean addAltar(BlockPos pos, String name) {
        if (state.altarPositions == null) state.altarPositions = new java.util.ArrayList<>();
        if (state.altarNames == null)     state.altarNames     = new java.util.HashMap<>();
        String key = posKey(pos);
        if (state.altarPositions.contains(key)) return false;
        state.altarPositions.add(key);
        if (!name.isBlank()) state.altarNames.put(key, name);
        save();
        String displayName = name.isBlank() ? Localization.translate("borderquest.general.altar") : name;
        if (mapManager != null) mapManager.addAltarMarker(pos, displayName);
        return true;
    }

    public void setAltarName(BlockPos pos, String name) {
        if (state.altarNames == null) state.altarNames = new java.util.HashMap<>();
        String key = posKey(pos);
        state.altarNames.put(key, name);
        save();
        if (mapManager != null) mapManager.addAltarMarker(pos, name.isBlank() ? Localization.translate("borderquest.general.altar") : name);
    }

    public String getAltarName(BlockPos pos) {
        if (state.altarNames == null) return "";
        return state.altarNames.getOrDefault(posKey(pos), "");
    }

    public boolean removeAltar(BlockPos pos) {
        if (state.altarPositions == null) return false;
        String key = posKey(pos);
        boolean removed = state.altarPositions.remove(key);
        if (removed) {
            if (state.altarNames != null) state.altarNames.remove(key);
            save();
            if (mapManager != null) mapManager.removeAltarMarker(pos);
        }
        return removed;
    }

    /** Retourne le top N des donateurs triés par total décroissant. */
    public List<Map.Entry<String, Integer>> getTopDonors(int n) {
        return state.playerDonations.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    public int getAltarCount() {
        return state.altarPositions == null ? 0 : state.altarPositions.size();
    }

    /**
     * Le joueur pose l'objet en main sur l'autel.
     * Seul l'objet tenu en main principale est pris, uniquement s'il est requis.
     * Retourne un message affiché dans l'action bar.
     */
    public Text donateFromHand(ServerPlayerEntity player) {
        if (isLastStage())
            return Text.literal(Localization.translate("borderquest.msg.barrierAlreadyRaised")).formatted(Formatting.GOLD);

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty())
            return Text.literal(Localization.translate("borderquest.msg.mustHoldRequiredItem")).formatted(Formatting.RED);

        String itemId = Registries.ITEM.getId(held.getItem()).toString();

        // Chercher si cet item est requis ou correspond à une TagRequirement
        ItemReq matching = null;
        for (ItemReq req : resolvedRequirements) {
            if (req.itemId().equals(itemId)) { matching = req; break; }
        }
        StageDefinition.TagReq matchingTag = null;
        if (matching == null) {
            for (StageDefinition.TagReq req : resolvedTagRequirements) {
                TagKey<Item> tag = parseItemTag(req.tagId());
                if (held.isIn(tag)) {
                    matchingTag = req;
                    break;
                }
            }
            if (matchingTag == null)
                return Text.literal(Localization.translate("borderquest.msg.itemNotRequiredHere")).formatted(Formatting.RED);
        }

        if (matching != null) {
            int alreadySubmitted = state.submittedItems.getOrDefault(itemId, 0);
            int remaining = matching.count() - alreadySubmitted;
            if (remaining <= 0)
                return Text.literal(Localization.translate("borderquest.msg.itemAlreadyComplete", itemId.replace("minecraft:", "")))
                    .formatted(Formatting.GREEN);

            int toTake = Math.min(remaining, held.getCount());
            held.decrement(toTake);
            state.submittedItems.merge(itemId, toTake, Integer::sum);
            state.playerDonations.merge(player.getUuidAsString(), toTake, Integer::sum);
            state.playerNames.put(player.getUuidAsString(), player.getName().getString());

            String name = itemId.replace("minecraft:", "");

            BorderQuestConfig cfgAlt = BorderQuestConfig.get();
            if (cfgAlt.donationAnnouncementsEnabled && toTake >= cfgAlt.donationAnnounceMinItems) {
                server.getPlayerManager().broadcast(
                    Text.literal(Localization.translate("borderquest.msg.publicAltarDepositAnnouncement",
                        player.getName().getString(), toTake, name)),
                    false
                );
            }

            save();
            updateSidebar();

            int newTotal = Math.min(state.submittedItems.get(itemId), matching.count());

            if (isStageComplete()) {
                advanceStage();
                return Text.literal(Localization.translate("borderquest.msg.altarDonationComplete",
                    toTake, name, newTotal, matching.count())).formatted(Formatting.GREEN);
            }
            return Text.literal(Localization.translate("borderquest.msg.altarDonationProgress",
                toTake, name, newTotal, matching.count())).formatted(Formatting.GREEN);
        }

        if (matchingTag != null) {
            int submitted = countSubmittedForTag(matchingTag.tagId());
            int remaining = matchingTag.count() - submitted;
            if (remaining <= 0)
                return Text.literal(Localization.translate("borderquest.msg.itemAlreadyComplete", matchingTag.tagId().replace("minecraft:", "")))
                    .formatted(Formatting.GREEN);

            int toTake = Math.min(remaining, held.getCount());
            held.decrement(toTake);
            state.submittedItems.merge(itemId, toTake, Integer::sum);
            state.submittedTagItems.merge(matchingTag.tagId(), toTake, Integer::sum);
            state.playerDonations.merge(player.getUuidAsString(), toTake, Integer::sum);
            state.playerNames.put(player.getUuidAsString(), player.getName().getString());

            String name = matchingTag.tagId().replace("minecraft:", "");
            BorderQuestConfig cfgAlt = BorderQuestConfig.get();
            if (cfgAlt.donationAnnouncementsEnabled && toTake >= cfgAlt.donationAnnounceMinItems) {
                server.getPlayerManager().broadcast(
                    Text.literal(Localization.translate("borderquest.msg.publicAltarDepositAnnouncement",
                        player.getName().getString(), toTake, name)),
                    false
                );
            }

            save();
            updateSidebar();

            int newTotal = Math.min(countSubmittedForTag(matchingTag.tagId()), matchingTag.count());
            if (isStageComplete()) {
                advanceStage();
                return Text.literal(Localization.translate("borderquest.msg.altarDonationComplete",
                    toTake, name, newTotal, matchingTag.count())).formatted(Formatting.GREEN);
            }
            return Text.literal(Localization.translate("borderquest.msg.altarDonationProgress",
                toTake, name, newTotal, matchingTag.count())).formatted(Formatting.GREEN);
        }
        return Text.literal(Localization.translate("borderquest.msg.itemNotRequiredHere")).formatted(Formatting.RED);
    }

    // -----------------------------------------------------------------------
    // Utilitaires inventaire
    // -----------------------------------------------------------------------

    private int countInInventory(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private void removeFromInventory(ServerPlayerEntity player, Item item, int amount) {
        int toRemove = amount;
        for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                int take = Math.min(stack.getCount(), toRemove);
                stack.decrement(take);
                toRemove -= take;
            }
        }
    }

    private TagKey<Item> parseItemTag(String tagId) {
        String namespace = "minecraft";
        String path = tagId;
        if (tagId.indexOf(':') >= 0) {
            String[] parts = tagId.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        return TagKey.of(RegistryKeys.ITEM, Identifier.of(namespace, path));
    }

    private int countSubmittedForTag(String tagId) {
        return state.submittedTagItems.getOrDefault(tagId, 0);
    }

    public int getSubmittedTagCount(String tagId) {
        return countSubmittedForTag(tagId);
    }

    private int countInInventory(ServerPlayerEntity player, TagKey<Item> tag) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isIn(tag)) count += stack.getCount();
        }
        return count;
    }

    private Map<String, Integer> removeFromInventory(ServerPlayerEntity player, TagKey<Item> tag, int amount) {
        Map<String, Integer> removed = new HashMap<>();
        int toRemove = amount;
        for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isIn(tag)) continue;
            int take = Math.min(stack.getCount(), toRemove);
            stack.decrement(take);
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            removed.merge(itemId, take, Integer::sum);
            toRemove -= take;
        }
        return removed;
    }
}

