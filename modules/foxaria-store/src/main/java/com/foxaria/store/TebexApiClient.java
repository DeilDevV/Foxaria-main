package com.foxaria.store;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class TebexApiClient {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String secret;

    public TebexApiClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.secret = secret;
    }

    public DuePlayersResponse duePlayers() throws IOException, InterruptedException {
        return get("/queue", DuePlayersResponse.class);
    }

    public CommandsResponse offlineCommands() throws IOException, InterruptedException {
        return get("/queue/offline-commands", CommandsResponse.class);
    }

    public CommandsResponse onlineCommands(int playerId) throws IOException, InterruptedException {
        return get("/queue/online-commands/" + playerId, CommandsResponse.class);
    }

    public void deleteCommands(List<Integer> ids) throws IOException, InterruptedException {
        String json = gson.toJson(new DeletePayload(ids));
        HttpRequest request = requestBuilder("/queue")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("Unexpected Tebex delete status: " + response.statusCode() + " body=" + response.body());
        }
    }

    private <T> T get(String path, Class<T> type) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(path).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected Tebex status: " + response.statusCode() + " body=" + response.body());
        }
        return gson.fromJson(response.body(), type);
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(15))
            .header("X-Tebex-Secret", secret)
            .header("Accept", "application/json");
    }

    private record DeletePayload(List<Integer> ids) {
    }

    public record DuePlayersResponse(Meta meta, List<DuePlayer> players) {
        public record Meta(@SerializedName("execute_offline") boolean executeOffline, @SerializedName("next_check") int nextCheck, boolean more) {
        }
    }

    public record DuePlayer(int id, String name, String uuid) {
    }

    public record CommandsResponse(List<QueuedCommand> commands) {
    }

    public record QueuedCommand(int id, String command, int payment, @SerializedName("package") int packageId, Conditions conditions, QueuePlayer player) {
    }

    public record Conditions(int delay, int slots) {
    }

    public record QueuePlayer(int id, String name, String uuid) {
    }
}
