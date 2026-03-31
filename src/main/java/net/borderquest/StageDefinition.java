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
    public List<TagReq> tagRequirements;
    public List<XpReq> xpRequirements;

    /** Constructeur no-arg requis par Gson. */
    public StageDefinition() {}

    private StageDefinition(Builder builder) {
        this.borderRadius = builder.borderRadius;
        this.title = builder.title;
        this.requirements = builder.requirements;
        this.categoryRequirements = builder.categoryRequirements;
        this.tagRequirements = builder.tagRequirements;
        this.xpRequirements = builder.xpRequirements;
        this.rewards = builder.rewards;
    }

    public double getDiameter() {
        return borderRadius * 2.0;
    }

    public boolean hasRequirements() {
        return (requirements != null && !requirements.isEmpty())
            || (categoryRequirements != null && !categoryRequirements.isEmpty())
            || (tagRequirements != null && !tagRequirements.isEmpty())
            || (xpRequirements != null && !xpRequirements.isEmpty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double borderRadius;
        private String title;
        private List<ItemReq> requirements = List.of();
        private List<CategoryReq> categoryRequirements = List.of();
        private List<TagReq> tagRequirements = List.of();
        private List<XpReq> xpRequirements = List.of();
        private List<Reward> rewards = new java.util.ArrayList<>();

        public Builder borderRadius(double borderRadius) {
            this.borderRadius = borderRadius;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder requirements(List<ItemReq> requirements) {
            this.requirements = requirements == null ? List.of() : requirements;
            return this;
        }

        public Builder categoryRequirements(List<CategoryReq> categoryRequirements) {
            this.categoryRequirements = categoryRequirements == null ? List.of() : categoryRequirements;
            return this;
        }

        public Builder tagRequirements(List<TagReq> tagRequirements) {
            this.tagRequirements = tagRequirements == null ? List.of() : tagRequirements;
            return this;
        }

        public Builder xpRequirements(List<XpReq> xpRequirements) {
            this.xpRequirements = xpRequirements == null ? List.of() : xpRequirements;
            return this;
        }

        public Builder rewards(List<Reward> rewards) {
            this.rewards = rewards == null ? new java.util.ArrayList<>() : rewards;
            return this;
        }

        public StageDefinition build() {
            return new StageDefinition(this);
        }
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

    public record TagReq(String tagId, int count) {}

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

    /** Quantité d'XP à donner pour ce stade. */
    public record XpReq(int count) {}

    /** Récompenses distribuées à tous les joueurs à la validation du stade. */
    public List<Reward> rewards = new java.util.ArrayList<>();
}
