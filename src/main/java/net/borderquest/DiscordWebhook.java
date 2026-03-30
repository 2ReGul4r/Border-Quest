package net.borderquest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Envoie des notifications asynchrones vers un webhook Discord lors des changements de stade.
 * Toutes les requêtes HTTP sont effectuées sur un thread séparé pour ne pas bloquer le serveur.
 */
public class DiscordWebhook {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void sendAsync(String message) {
        BorderQuestConfig cfg = BorderQuestConfig.get();
        if (!cfg.discordEnabled || cfg.discordWebhookUrl == null || cfg.discordWebhookUrl.isBlank()) return;

        String json = buildPayload(cfg, message);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(cfg.discordWebhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.discordWebhookError",
                        response.statusCode(), response.body()));
                }
            } catch (Exception e) {
                BorderQuest.LOGGER.warn(Localization.translate("borderquest.logger.discordWebhookFailed", e.getMessage()));
            }
        });
    }

    private static String buildPayload(BorderQuestConfig cfg, String message) {
        String content  = escapeJson(message);
        String username = escapeJson(cfg.discordUsername);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"content\":\"").append(content).append("\"");
        if (!cfg.discordUsername.isBlank()) {
            sb.append(",\"username\":\"").append(username).append("\"");
        }
        if (cfg.discordAvatarUrl != null && !cfg.discordAvatarUrl.isBlank()) {
            sb.append(",\"avatar_url\":\"").append(escapeJson(cfg.discordAvatarUrl)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

