package com.foxaria.hubguard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerStateTracker {

    private final JavaPlugin plugin;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "foxaria-server-probe");
        t.setDaemon(true);
        return t;
    });

    public ServerStateTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int intervalSec = plugin.getConfig().getInt("lobby-menu.server-probe-interval-seconds", 8);
        executor.scheduleWithFixedDelay(this::probeAll, 0, intervalSec, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    /**
     * Статус сервера в лобби-меню.
     *
     * По умолчанию стараемся быть «нестрогими»: если для сервера не настроен/не сработал TCP-пинг,
     * не считаем это выключением (иначе при смене IP/host получаем ложные «Выключен»).
     */
    public boolean isOnline(String bungeeServerName) {
        Boolean v = cache.get(bungeeServerName);
        if (v == null) {
            return true;
        }
        return Boolean.TRUE.equals(v);
    }

    private void probeAll() {
        int intervalSec = plugin.getConfig().getInt("lobby-menu.server-probe-interval-seconds", 8);
        if (intervalSec <= 0) {
            cache.clear();
            return;
        }
        ConfigurationSection servers = plugin.getConfig().getConfigurationSection("lobby-menu.servers");
        if (servers == null) {
            return;
        }
        for (String key : servers.getKeys(false)) {
            ConfigurationSection s = servers.getConfigurationSection(key);
            if (s == null || s.getBoolean("coming-soon", false)) {
                continue;
            }
            String bungee = s.getString("bungee-server", "");
            String addressRaw = s.getString("address", "");
            String address = resolveAddress(addressRaw);
            if (bungee.isBlank() || address.isBlank()) {
                continue;
            }
            cache.put(bungee, tcpReachable(address));
        }
    }

    /**
     * Удобный формат адреса:
     * - "host:port" (как раньше)
     * - ":port" → host берётся из lobby-menu.server-probe-default-host
     * - "{host}:port" → {host} заменится на lobby-menu.server-probe-default-host
     */
    private String resolveAddress(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        String defaultHost = plugin.getConfig().getString("lobby-menu.server-probe-default-host", "127.0.0.1");
        if (defaultHost == null || defaultHost.isBlank()) {
            defaultHost = "127.0.0.1";
        }
        if (v.startsWith(":")) {
            return defaultHost + v;
        }
        if (v.contains("{host}")) {
            return v.replace("{host}", defaultHost);
        }
        return v;
    }

    private boolean tcpReachable(String address) {
        int colon = address.lastIndexOf(':');
        if (colon < 0) {
            return false;
        }
        try {
            String host = address.substring(0, colon);
            int port = Integer.parseInt(address.substring(colon + 1).trim());
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1200);
                return true;
            }
        } catch (IOException | NumberFormatException ignored) {
            return false;
        }
    }
}
