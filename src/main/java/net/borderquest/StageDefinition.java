package net.borderquest;

import java.util.List;

/**
 * Définit un stade de progression : rayon de la barrière + ressources à collecter.
 */
public class StageDefinition {

    public double borderRadius; // rayon depuis le centre (le diamètre sera radius*2)
    public String title;
    public List<ItemReq> requirements;
    public List<CategoryReq> categoryRequirements;

    /** Constructeur no-arg requis par Gson. */
    public StageDefinition() {}

    public StageDefinition(double borderRadius, String title, List<ItemReq> requirements) {
        this(borderRadius, title, requirements, List.of());
    }

    public StageDefinition(double borderRadius, String title, List<ItemReq> requirements,
                           List<CategoryReq> categoryRequirements) {
        this.borderRadius = borderRadius;
        this.title = title;
        this.requirements = requirements;
        this.categoryRequirements = categoryRequirements;
    }

    public double getDiameter() {
        return borderRadius * 2.0;
    }

    public boolean hasRequirements() {
        return (requirements != null && !requirements.isEmpty())
            || (categoryRequirements != null && !categoryRequirements.isEmpty());
    }

    /**
     * Paire item ID -> quantité requise.
     */
    public record ItemReq(String itemId, int count) {}

    /**
     * Catégorie de ressource -> quantité requise.
     * La catégorie sera résolue en un item concret via BiomeResourceResolver.
     */
    public record CategoryReq(String category, int count) {}

    /**
     * Récompense donnée à tous les joueurs connectés quand ce stade est validé.
     *
     * Types disponibles :
     *   "item"   → itemId + count
     *   "effect" → effectId + duration (ticks) + amplifier (0 = niveau I)
     *   "xp"     → amount (points d'expérience)
     */
    public static class Reward {
        public String type     = "item";
        public String itemId   = "";    // pour type="item"
        public int    count    = 1;     // pour type="item"
        public String effectId = "";    // pour type="effect"
        public int    duration = 600;   // pour type="effect", en ticks
        public int    amplifier= 0;     // pour type="effect" (0 = niveau I)
        public int    amount   = 0;     // pour type="xp"

        public Reward() {}
    }

    /** Récompenses distribuées à tous les joueurs à la validation du stade. */
    public List<Reward> rewards = new java.util.ArrayList<>();
}
