package com.foxaria.retention;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class DiscordWebhookClient {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String webhookUrl;
    private final String username;

    public DiscordWebhookClient(String webhookUrl, String username) {
        this.webhookUrl = webhookUrl;
        this.username = username;
    }

    public void send(String content) throws IOException, InterruptedException {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        String user = username.replace("\\", "\\\\").replace("\"", "\\\"");
        String payload = "{\"username\":\"" + user + "\",\"content\":\"" + escaped + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Discord webhook failed: " + response.statusCode() + " " + response.body());
        }
    }
}
