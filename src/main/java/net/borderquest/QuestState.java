package net.borderquest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * État persistant du mod : stade actuel + ressources déjà soumises pour ce stade.
 */
public class QuestState {

    /** Index (0-based) dans BorderQuestManager.STAGES */
    public int currentStage = 0;

    /** item_id -> quantité soumise pour le stade actuel */
    public Map<String, Integer> submittedItems = new HashMap<>();

    /** UUID (string) -> total items donnés tous stades confondus */
    public Map<String, Integer> playerDonations = new HashMap<>();

    /** UUID (string) -> dernier pseudo connu */
    public Map<String, String> playerNames = new HashMap<>();

    /** Positions des autels enregistrés, format "x,y,z" */
    public List<String> altarPositions = new ArrayList<>();

    /** Clé autel "x,y,z" -> nom d'affichage (vide si non nommé) */
    public Map<String, String> altarNames = new HashMap<>();

    public void reset() {
        currentStage = 0;
        submittedItems.clear();
        playerDonations.clear();
        playerNames.clear();
    }
}
