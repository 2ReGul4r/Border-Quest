package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.borderquest.StageDefinition.CategoryReq;
import static net.borderquest.StageDefinition.ItemReq;

/**
 * Configuration du mod chargée depuis config/borderquest.json.
 * Éditez ce fichier pour personnaliser les stades, les rayons et les récompenses.
 * Relancez le serveur ou tapez /bq reload pour appliquer les changements.
 */
public class BorderQuestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static BorderQuestConfig instance;

    // -----------------------------------------------------------------------
    // Paramètres généraux
    // -----------------------------------------------------------------------

    /** Durée des feux d'artifice de célébration en ticks (20 ticks = 1 seconde). */
    public int celebrationDurationTicks = 200;

    /** Dégâts infligés par seconde lorsque le joueur est hors de la barrière. */
    public double borderDamagePerBlock = 0.2;

    /** Distance d'avertissement avant le mur (en blocs). */
    public int borderWarningBlocks = 5;

    /** Durée de l'agrandissement de la bordure en ticks (20 ticks = 1 seconde). */
    public int borderExpansionDurationTicks = 200;

    /**
     * Diviseur de la barrière pour le Nether (coordonnées Nether = Overworld / 8).
     * Changez cette valeur si votre monde Nether utilise une échelle différente.
     */
    public double netherScale = 8.0;

    public String locale = "en_us";

    // -----------------------------------------------------------------------
    // Particules autels
    // -----------------------------------------------------------------------

    /** Active les particules autour des blocs d'autel. */
    public boolean altarParticlesEnabled = true;

    /** Fréquence des particules (en ticks). 20 ticks = 1 seconde. */
    public int altarParticlePeriodTicks = 20;

    // -----------------------------------------------------------------------
    // Annonces de don dans le chat
    // -----------------------------------------------------------------------

    /** Active les annonces globales quand un joueur fait un don significatif. */
    public boolean donationAnnouncementsEnabled = true;

    /** Nombre minimum d'objets donnés pour déclencher une annonce publique. */
    public int donationAnnounceMinItems = 16;

    // -----------------------------------------------------------------------
    // Verrous de dimension
    // -----------------------------------------------------------------------

    /**
     * Liste des mondes verrouillés jusqu'à un certain stade.
     * worldId : identifiant du monde (ex: "minecraft:the_nether")
     * requiredStage : stade minimum (1-based) pour y accéder
     */
    public List<WorldLock> worldLocks = defaultWorldLocks();

    public static class WorldLock {
        public String worldId      = "minecraft:the_nether";
        public int    requiredStage = 5;
        public WorldLock() {}
        public WorldLock(String worldId, int requiredStage) {
            this.worldId       = worldId;
            this.requiredStage = requiredStage;
        }
    }

    // -----------------------------------------------------------------------
    // Webhook Discord
    // -----------------------------------------------------------------------

    /** Active l'envoi de messages Discord lors d'un changement de stade. */
    public boolean discordEnabled = false;

    /** URL du webhook Discord (format : https://discord.com/api/webhooks/...). */
    public String discordWebhookUrl = "";

    /** Nom affiché par le webhook dans Discord. */
    public String discordUsername = "Border Quest";

    /** URL de l'avatar du bot Discord (optionnel, laisser vide pour l'icône par défaut). */
    public String discordAvatarUrl = "";

    // -----------------------------------------------------------------------
    // Texte du tableau de bord / sidebar

    /** Titre affiché en haut du Tab. */
    public String sidebarHeaderTitle = "★ Border Quest ★";

    /** Texte affiché quand la barrière est entièrement levée. */
    public String sidebarHeaderCompleteTitle = "LA BARRIERE EST TOMBEE !";

    /** Sous-texte affiché quand la quête est terminée. */
    public String sidebarHeaderCompleteSubtitle = "Felicitations, vous avez tout accompli !";

    /** Modèle pour la ligne de stade. */
    public String sidebarHeaderStageTemplate = "Stade %s/%s";

    /** Modèle pour la ligne de rayon. */
    public String sidebarHeaderRadiusTemplate = "Rayon actuel : %s blocs";

    /** Texte pour la section des ressources à collecter. */
    public String sidebarHeaderCollectTitle = "Ressources a collecter :";

    /** Texte pour la section Top Donateurs. */
    public String sidebarFooterTopDonors = "Top Donateurs";

    // -----------------------------------------------------------------------
    // Texte des célébrations
    // -----------------------------------------------------------------------

    /** Titre affiché lors de la célébration finale. */
    public String celebrationTitleFinal = "★ LIBERTE ! ★";

    /** Titre affiché lors de l'agrandissement de la zone. */
    public String celebrationTitleProgress = "✦ ZONE AGRANDIE ✦";

    /** Sous-titre affiché lors de la célébration finale. */
    public String celebrationSubtitleFinal = "Le monde vous appartient !";

    /** Sous-titre affiché lors de l'agrandissement de la zone. Utilise %s pour radius et titre. */
    public String celebrationSubtitleProgress = "Rayon : %s blocs | %s";

    // -----------------------------------------------------------------------
    // Stades de progression
    // -----------------------------------------------------------------------

    /**
     * Liste des stades dans l'ordre. Le dernier stade représente la liberté totale.
     * Chaque stade comporte :
     *   - borderRadius   : rayon de la zone en blocs (diamètre = radius × 2)
     *   - title          : nom du stade affiché aux joueurs
     *   - requirements   : objets fixes à déposer (itemId + count)
     *   - categoryRequirements : catégories résolues selon le biome (ex. "logs")
     */
    public List<StageDefinition> stages = defaultStages();

    // -----------------------------------------------------------------------
    // Accès singleton
    // -----------------------------------------------------------------------

    public static BorderQuestConfig get() {
        if (instance == null) load();
        return instance;
    }

    // -----------------------------------------------------------------------
    // Chargement / sauvegarde
    // -----------------------------------------------------------------------

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                instance = GSON.fromJson(json, BorderQuestConfig.class);
                if (instance == null) instance = new BorderQuestConfig();
                instance.validate();
                BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.configLoaded", instance.stages.size()));
                Localization.init(instance.locale);
            } catch (IOException e) {
                BorderQuest.LOGGER.error(Localization.translate("borderquest.logger.configLoadFailed", e.getMessage()));
                instance = new BorderQuestConfig();
                Localization.init(instance.locale);
            }
        } else {
            instance = new BorderQuestConfig();
            save();
            Localization.init(instance.locale);
            BorderQuest.LOGGER.info(Localization.translate("borderquest.logger.defaultConfigCreated", instance.stages.size()));
        }
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            BorderQuest.LOGGER.error(Localization.translate("borderquest.logger.configSaveFailed", e.getMessage()));
        }
    }

    /** Applique les valeurs par défaut aux champs manquants ou invalides. */
    private void validate() {
        if (stages == null || stages.isEmpty()) stages = defaultStages();
        if (celebrationDurationTicks <= 0) celebrationDurationTicks = 200;
        if (borderDamagePerBlock < 0) borderDamagePerBlock = 0.2;
        if (borderWarningBlocks < 0) borderWarningBlocks = 5;
        if (borderExpansionDurationTicks <= 0) borderExpansionDurationTicks = 200;
        if (netherScale <= 0) netherScale = 8.0;
        if (altarParticlePeriodTicks <= 0) altarParticlePeriodTicks = 20;
        if (donationAnnounceMinItems <= 0) donationAnnounceMinItems = 1;
        if (locale == null || locale.isBlank()) locale = "en_us";
        if (worldLocks == null) worldLocks = defaultWorldLocks();
        if (discordWebhookUrl == null) discordWebhookUrl = "";
        if (discordUsername == null || discordUsername.isBlank()) discordUsername = "Border Quest";
        if (discordAvatarUrl == null) discordAvatarUrl = "";
        if (sidebarHeaderTitle == null) sidebarHeaderTitle = "★ Border Quest ★";
        if (sidebarHeaderCompleteTitle == null) sidebarHeaderCompleteTitle = "LA BARRIERE EST TOMBEE !";
        if (sidebarHeaderCompleteSubtitle == null) sidebarHeaderCompleteSubtitle = "Felicitations, vous avez tout accompli !";
        if (sidebarHeaderStageTemplate == null) sidebarHeaderStageTemplate = "Stade %s/%s";
        if (sidebarHeaderRadiusTemplate == null) sidebarHeaderRadiusTemplate = "Rayon actuel : %s blocs";
        if (sidebarHeaderCollectTitle == null) sidebarHeaderCollectTitle = "Ressources a collecter :";
        if (sidebarFooterTopDonors == null) sidebarFooterTopDonors = "Top Donateurs";
        if (celebrationTitleFinal == null) celebrationTitleFinal = "★ LIBERTE ! ★";
        if (celebrationTitleProgress == null) celebrationTitleProgress = "✦ ZONE AGRANDIE ✦";
        if (celebrationSubtitleFinal == null) celebrationSubtitleFinal = "Le monde vous appartient !";
        if (celebrationSubtitleProgress == null) celebrationSubtitleProgress = "Rayon : %s blocs | %s";
        for (StageDefinition s : stages) {
            if (s.requirements == null) s.requirements = List.of();
            if (s.categoryRequirements == null) s.categoryRequirements = List.of();
            if (s.rewards == null) s.rewards = new java.util.ArrayList<>();
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("borderquest.json");
    }

    // -----------------------------------------------------------------------
    // Stades par défaut
    // -----------------------------------------------------------------------

    private static List<WorldLock> defaultWorldLocks() {
        return new java.util.ArrayList<>(List.of(
            new WorldLock("minecraft:the_nether", 5),
            new WorldLock("minecraft:the_end",    7)
        ));
    }

    private static List<StageDefinition> defaultStages() {
        return List.of(
            new StageDefinition(10, "Defricher la zone",
                List.of(new ItemReq("minecraft:cobblestone", 64)),
                List.of(new CategoryReq("logs", 64))),

            new StageDefinition(25, "Premiers pas vers la civilisation",
                List.of(
                    new ItemReq("minecraft:iron_ingot", 32),
                    new ItemReq("minecraft:bread", 32)
                )),

            new StageDefinition(50, "Expansion miniere",
                List.of(
                    new ItemReq("minecraft:iron_ingot", 64),
                    new ItemReq("minecraft:gold_ingot", 16)
                )),

            new StageDefinition(100, "Maitrise des metaux",
                List.of(
                    new ItemReq("minecraft:diamond", 8),
                    new ItemReq("minecraft:iron_ingot", 32)
                )),

            new StageDefinition(200, "Vers le Nether",
                List.of(
                    new ItemReq("minecraft:obsidian", 10),
                    new ItemReq("minecraft:diamond", 4)
                )),

            new StageDefinition(400, "Conquete du Nether",
                List.of(
                    new ItemReq("minecraft:blaze_rod", 16),
                    new ItemReq("minecraft:ender_pearl", 8)
                )),

            new StageDefinition(800, "Invocation du Wither",
                List.of(new ItemReq("minecraft:nether_star", 1))),

            new StageDefinition(29999984, "LIBERTE ! La barriere est tombee !",
                List.of())
        );
    }
}

