package com.foxaria.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.md_5.bungee.api.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Локальный HTTP: страница банлиста + JSON API для сайта (поиск на клиенте).
 */
public final class BansPublicHttp {

    private final Plugin plugin;
    private final ProxyPunishmentRepository punishments;
    private HttpServer server;

    public BansPublicHttp(Plugin plugin, ProxyPunishmentRepository punishments) {
        this.plugin = plugin;
        this.punishments = punishments;
    }

    public void start(String bindHost, int port, String token) {
        stop();
        if (port <= 0) {
            return;
        }
        try {
            String host = bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost.trim();
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/", ex -> handleRoot(ex, token));
            server.createContext("/banlist", ex -> handleRoot(ex, token));
            server.createContext("/api/bans", ex -> handleApi(ex, token));
            server.setExecutor(r -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    plugin.getLogger().warning("[banlist-http] " + t.getMessage());
                }
            });
            server.start();
            plugin.getLogger().info("[banlist-http] http://" + host + ":" + port + "/ — список банов (SQLite прокси).");
        } catch (IOException e) {
            plugin.getLogger().warning("[banlist-http] не удалось запустить порт " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleRoot(HttpExchange ex, String token) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        try (InputStream in = plugin.getResourceAsStream("web/banlist.html")) {
            if (in == null) {
                respond(ex, 404, "text/plain; charset=utf-8", "banlist.html missing in jar".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] raw = in.readAllBytes();
            String html = new String(raw, StandardCharsets.UTF_8)
                .replace("%TOKEN%", escapeHtml(token == null ? "" : token));
            respond(ex, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void handleApi(HttpExchange ex, String expectedToken) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String q = ex.getRequestURI().getQuery();
        String got = queryParam(q, "token");
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(got)) {
            respond(ex, 403, "application/json; charset=utf-8", "{\"error\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        long now = System.currentTimeMillis();
        List<ProxyPunishmentRepository.PunishmentRecord> rows = punishments.listActiveNetworkBans(now);
        String json = toJson(rows);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        respond(ex, 200, "application/json; charset=utf-8", body);
    }

    private static String queryParam(String query, String key) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = part.substring(0, i);
            String v = part.substring(i + 1);
            if (key.equalsIgnoreCase(k)) {
                return java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String toJson(List<ProxyPunishmentRepository.PunishmentRecord> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            ProxyPunishmentRepository.PunishmentRecord r = rows.get(i);
            sb.append('{');
            jsonField(sb, "id", r.id());
            sb.append(',');
            jsonField(sb, "targetUuid", r.targetUuid().toString());
            sb.append(',');
            jsonField(sb, "targetName", r.targetName());
            sb.append(',');
            jsonField(sb, "actorName", r.actorName());
            sb.append(',');
            jsonField(sb, "type", r.type());
            sb.append(',');
            jsonField(sb, "reasonCode", r.reasonCode());
            sb.append(',');
            jsonField(sb, "reasonTitle", r.reasonTitle());
            sb.append(',');
            jsonField(sb, "reasonDescription", r.reasonDescription());
            sb.append(',');
            sb.append("\"createdAt\":").append(r.createdAt()).append(',');
            sb.append("\"expiresAt\":").append(r.expiresAt());
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static void jsonField(StringBuilder sb, String key, String val) {
        sb.append('"').append(key).append("\":");
        if (val == null) {
            sb.append("null");
        } else {
            sb.append('"').append(jsonEscapeString(val)).append('"');
        }
    }

    private static String jsonEscapeString(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static void respond(HttpExchange ex, int code, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
