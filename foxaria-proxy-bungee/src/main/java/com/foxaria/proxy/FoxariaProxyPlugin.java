package com.foxaria.proxy;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.Title;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
public final class FoxariaProxyPlugin extends Plugin implements Listener {

    private final Map<UUID, Long> sessionExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionIp = new ConcurrentHashMap<>();
    private final Set<UUID> authenticatedNow = ConcurrentHashMap.newKeySet();
    private final Map<String, Deque<Long>> authAttemptsByIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> connectionsByIp = new ConcurrentHashMap<>();
    private final Set<String> wipeLocked = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, ConnectRetry> pendingConnect = new ConcurrentHashMap<>();

    private AuthRepository repository;
    private SessionRepository sessionRepository;
    private PrivilegeRepository privilegeRepository;
    private PrivilegeAuditRepository auditRepository;
    private ProxyPunishmentRepository punishmentRepository;
    private FxPunishmentsJdbcRepository fxPunishments;
    private ProxyPunishmentCatalog punishmentCatalog;
    private Configuration cfg;
    private String authServer;
    private String lobbyServer;
    private String defaultGroup;
    private int minPasswordLength;
    private int maxPasswordLength;
    private long sessionTtlMs;
    private boolean requireSameIpForSession;
    private long loginCommandCooldownMs;
    private int maxLoginAttemptsPerWindow;
    private long loginAttemptWindowMs;
    private int maxConnectionsPerIpPerWindow;
    private long connectionWindowMs;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private ScheduledTask tabTask;

    /** Имя гильдии с игрового сервера — только для таба/имени на прокси. */
    private final Map<UUID, GuildSyncData> guildByPlayer = new ConcurrentHashMap<>();

    private record GuildSyncData(String guildId, String displayName, String colorLegacy) {
    }

    private final Map<String, Queue<UUID>> serverConnectQueues = new ConcurrentHashMap<>();

    /**
     * Пауза между успешными входами на анархию/игровой backend (FIFO), чтобы разгружать вход при массовом потоке.
     * Совмещается с {@link #serverConnectQueues}: если слотов нет — сначала очередь по лимиту, затем по интервалу.
     */
    private final ConcurrentLinkedQueue<GameJoinThrottleTicket> gameJoinThrottleFifo = new ConcurrentLinkedQueue<>();
    private final Set<UUID> gameJoinThrottleTracked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> gameJoinBypassOnce = ConcurrentHashMap.newKeySet();
    /** Следующий момент времени (epoch ms), начиная с которого можно пустить ещё одного игрока на интервальный backend. 0 = окно свободно. */
    private volatile long gameJoinThrottleNextAdmissionEarliestMs;
    private final Object gameJoinThrottleMutex = new Object();
    private final AtomicBoolean gameJoinThrottleDrainBusy = new AtomicBoolean(false);

    private record GameJoinThrottleTicket(UUID uuid, String serverName) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.cfg = loadConfig();
        this.authServer = cfg.getString("network.auth-server", "auth");
        this.lobbyServer = cfg.getString("network.lobby-server", "lobby");
        this.defaultGroup = cfg.getString("privileges.default-group", "default");
        this.minPasswordLength = cfg.getInt("auth.min-password-length", 6);
        this.maxPasswordLength = cfg.getInt("auth.max-password-length", 64);
        this.sessionTtlMs = cfg.getLong("auth.session-timeout-seconds", 18000L) * 1000L;
        this.requireSameIpForSession = cfg.getBoolean("auth.session-require-same-ip", true);
        this.loginCommandCooldownMs = cfg.getLong("auth.login-command-cooldown-ms", 1500L);
        this.maxLoginAttemptsPerWindow = cfg.getInt("auth.max-login-attempts-per-window", 6);
        this.loginAttemptWindowMs = cfg.getLong("auth.login-attempt-window-ms", 30000L);
        this.maxConnectionsPerIpPerWindow = cfg.getInt("antibot.max-connections-per-ip-per-window", 10);
        this.connectionWindowMs = cfg.getLong("antibot.connection-window-ms", 10000L);
        ProxyJdbcSupport.Settings proxyDb = ProxyJdbcSupport.resolve(this, cfg, "database");
        this.repository = new AuthRepository(proxyDb.jdbcUrl(), proxyDb.username(), proxyDb.password());
        this.sessionRepository = new SessionRepository(proxyDb.jdbcUrl(), proxyDb.username(), proxyDb.password(), getLogger());
        // Restore sessions that survived proxy restart
        sessionRepository.loadActive(System.currentTimeMillis()).forEach((uuid, rec) -> {
            sessionExpiry.put(uuid, rec.expiresAt());
            if (rec.ipAddress() != null) sessionIp.put(uuid, rec.ipAddress());
        });
        this.privilegeRepository = new PrivilegeRepository(proxyDb.jdbcUrl(), proxyDb.username(), proxyDb.password());
        this.auditRepository = new PrivilegeAuditRepository(proxyDb.jdbcUrl(), proxyDb.username(), proxyDb.password());
        this.punishmentRepository = new ProxyPunishmentRepository(proxyDb.jdbcUrl(), proxyDb.username(), proxyDb.password());
        ModerationBackendJdbc moderationJdbc = ModerationBackendJdbc.resolve(this, cfg);
        this.fxPunishments = new FxPunishmentsJdbcRepository(this, moderationJdbc);
        this.fxPunishments.probeOnStartup();
        this.punishmentCatalog = new ProxyPunishmentCatalog(cfg);
        this.punishmentRepository.ensureSchema();

        getProxy().registerChannel("foxaria:proxy");
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new RegisterCommand());
        getProxy().getPluginManager().registerCommand(this, new RegisterAliasCommand());
        getProxy().getPluginManager().registerCommand(this, new LoginCommand());
        getProxy().getPluginManager().registerCommand(this, new LoginAliasCommand());
        getProxy().getPluginManager().registerCommand(this, new GrantPrivilegeCommand());
        getProxy().getPluginManager().registerCommand(this, new GrantPackageCommand());
        getProxy().getPluginManager().registerCommand(this, new RevokePrivilegeCommand());
        getProxy().getPluginManager().registerCommand(this, new MyGroupCommand());
        getProxy().getPluginManager().registerCommand(this, new GroupInfoCommand());
        getProxy().getPluginManager().registerCommand(this, new AuditPrivilegeCommand());
        getProxy().getPluginManager().registerCommand(this, new RestartAllBackendsCommand());
        getProxy().getPluginManager().registerCommand(this, new LobbyCommand());
        getProxy().getPluginManager().registerCommand(this, new ServerPickerCommand());
        getProxy().getPluginManager().registerCommand(this, new ProxyHelpCommand());
        getProxy().getPluginManager().registerCommand(this, new PunishCommand());
        getProxy().getPluginManager().registerCommand(this, new UnpunishCommand());
        getProxy().getPluginManager().registerCommand(this, new RulesCommand());
        getProxy().getPluginManager().registerCommand(this, new WipeProxyCommand());
        startTabTask();
        getLogger().info("FoxariaProxy enabled. Auth via Bungee is active.");
    }

    @Override
    public void onDisable() {
        sessionExpiry.clear();
        sessionIp.clear();
        authenticatedNow.clear();
        authAttemptsByIp.clear();
        connectionsByIp.clear();
        guildByPlayer.clear();
        serverConnectQueues.clear();
        gameJoinThrottleFifo.clear();
        gameJoinThrottleTracked.clear();
        gameJoinBypassOnce.clear();
        if (tabTask != null) {
            tabTask.cancel();
            tabTask = null;
        }
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        PendingConnection conn = event.getConnection();
        String ip = safeIp(conn);
        if (ip == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!consumeWindow(connectionsByIp, ip, now, connectionWindowMs, maxConnectionsPerIpPerWindow)) {
            event.setCancelled(true);
            event.setCancelReason(TextComponent.fromLegacy(msg("antibot.kick-message", "&cСлишком много попыток подключения. Попробуйте позже.")));
        }
    }

    @EventHandler
    public void onJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        ProxyPunishmentRepository.PunishmentRecord activeBan = punishmentRepository.activeByTypes(
            player.getUniqueId(), System.currentTimeMillis(), "BAN", "TEMPBAN"
        );
        if (activeBan != null) {
            player.disconnect(TextComponent.fromLegacyText(ProxyColorUtil.colorize(formatPunishmentScreen(activeBan))));
            return;
        }
        boolean hasAccount = repository.find(player.getUniqueId()).isPresent();
        if (isSessionValid(player.getUniqueId(), safeIp(player))) {
            authenticatedNow.add(player.getUniqueId());
            // Do NOT route to lobby here — the Minecraft client hasn't finished connecting to
            // the first backend (auth) yet. Routing this early causes Paper's login timeout.
            // The redirect happens in onServerConnected once auth is fully established.
            scheduleTabDisplayNameResync();
            return;
        }
        authenticatedNow.remove(player.getUniqueId());
        // Priorities usually already point to auth; keep explicit routing with tiny delay to avoid connect races.
        getProxy().getScheduler().schedule(this,
            () -> connectToIfNeeded(player, authServer),
            150L, TimeUnit.MILLISECONDS);
        sendAuthPrompt(player, hasAccount);
        scheduleTabDisplayNameResync();
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        UUID oid = event.getPlayer().getUniqueId();
        authenticatedNow.remove(oid);
        guildByPlayer.remove(oid);
        gameJoinBypassOnce.remove(oid);
        purgeGameJoinThrottleQueue(oid);
    }

    private void purgeGameJoinThrottleQueue(UUID playerId) {
        synchronized (gameJoinThrottleMutex) {
            Iterator<GameJoinThrottleTicket> it = gameJoinThrottleFifo.iterator();
            while (it.hasNext()) {
                if (it.next().uuid().equals(playerId)) {
                    it.remove();
                    break;
                }
            }
            gameJoinThrottleTracked.remove(playerId);
        }
        scheduleGameJoinDrain(0L);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!"foxaria:proxy".equals(event.getTag())) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String kind = in.readUTF();
            if ("NetAnnounce".equals(kind)) {
                event.setCancelled(true);
                String main = in.readUTF();
                String hover = in.readUTF();
                broadcastNetAnnounce(main, hover);
                return;
            }
            if ("WipeSet".equals(kind)) {
                event.setCancelled(true);
                String serverName = in.readUTF();
                boolean locked = in.readBoolean();
                if (locked) wipeLocked.add(serverName);
                else wipeLocked.remove(serverName);
                broadcastWipeLockUpdate(serverName, locked);
                if (locked) {
                    kickNonAdminsFromLockedServer(serverName);
                }
                return;
            }
            if ("CaseRewardExec".equals(kind)) {
                event.setCancelled(true);
                in.readUTF();
                int commandCount = in.readInt();
                for (int i = 0; i < commandCount; i++) {
                    String cmd = in.readUTF();
                    getProxy().getPluginManager().dispatchCommand(getProxy().getConsole(), cmd);
                }
                return;
            }
            if ("CaseRewardForward".equals(kind)) {
                event.setCancelled(true);
                String targetServer = in.readUTF();
                int commandCount = in.readInt();
                java.util.List<String> commands = new java.util.ArrayList<>();
                for (int i = 0; i < commandCount; i++) {
                    commands.add(in.readUTF());
                }
                forwardCaseRewardToBackend(targetServer, commands);
                return;
            }

            ProxiedPlayer player = resolveProxiedPlayerForFoxariaMessage(event);
            if (player == null) {
                return;
            }
            if ("WipeLockSyncRequest".equals(kind)) {
                event.setCancelled(true);
                if (player != null && player.getServer() != null) {
                    sendWipeSyncAllTo(player);
                }
                return;
            }
            if ("QueueInfo".equals(kind)) {
                String serverName = in.readUTF();
                event.setCancelled(true);
                sendQueueInfoReply(player, serverName);
                return;
            }
            if ("RequestChatPrefix".equals(kind)) {
                event.setCancelled(true);
                sendChatPrefixToBackend(player);
                return;
            }
            if (!"GuildSync".equals(kind)) {
                return;
            }
            event.setCancelled(true);
            UUID uuid = UUID.fromString(in.readUTF());
            String guildId = in.readUTF();
            String displayName = in.readUTF();
            String colorLegacy = in.readUTF();
            if (guildId == null || guildId.isBlank()) {
                guildByPlayer.remove(uuid);
            } else {
                guildByPlayer.put(uuid, new GuildSyncData(guildId, displayName, colorLegacy == null ? "&f" : colorLegacy));
            }
            if (player.getUniqueId().equals(uuid)) {
                refreshPlayerDisplayName(player);
            }
        } catch (IOException ignored) {
        }
    }

    private void broadcastNetAnnounce(String mainLegacy, String hoverLegacyMultiline) {
        String mainColored = ProxyColorUtil.colorize(mainLegacy == null ? "" : mainLegacy);
        TextComponent root = new TextComponent("");
        for (var part : TextComponent.fromLegacyText(mainColored)) {
            root.addExtra(part);
        }
        if (hoverLegacyMultiline != null && !hoverLegacyMultiline.isBlank()) {
            String hoverColored = ProxyColorUtil.colorize(hoverLegacyMultiline);
            root.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(hoverColored))));
        }
        for (ProxiedPlayer pl : ProxyServer.getInstance().getPlayers()) {
            pl.sendMessage(root);
        }
    }

    private static ProxiedPlayer resolveProxiedPlayerForFoxariaMessage(PluginMessageEvent event) {
        if (event.getSender() instanceof ProxiedPlayer sp) {
            return sp;
        }
        if (event.getReceiver() instanceof ProxiedPlayer rp) {
            return rp;
        }
        return null;
    }

    /** Лобби запрашивает размер очереди на подключение к заполненному серверу (см. {@link #serverConnectQueues}). */
    /**
     * Тот же префикс ранга, что в табе/табе прокси, в UTF-16 — лобби/auth не читают sqlite.
     */
    private void sendChatPrefixToBackend(ProxiedPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        String chatPrefix = rankPrefixLineForChat(player);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("ChatPrefixSync");
            out.writeUTF(chatPrefix == null ? "" : chatPrefix);
            out.close();
            player.getServer().sendData("foxaria:proxy", baos.toByteArray());
        } catch (IOException ignored) {
        }
    }

    private String rankPrefixLineForChat(ProxiedPlayer player) {
        String group = privilegeRepository.resolveActiveGroup(player.getUniqueId(), defaultGroup, System.currentTimeMillis());
        String g = group == null ? defaultGroup : group.toLowerCase(Locale.ROOT);
        String dg = defaultGroup == null ? "default" : defaultGroup.toLowerCase(Locale.ROOT);
        String prefixLine = cfg.getString("privileges.groups." + g + ".prefix",
            cfg.getString("privileges.groups." + dg + ".prefix", "&7Игрок &8| &f"));
        return ProxyColorUtil.stripRankFromPrefix(prefixLine);
    }

    private void sendQueueInfoReply(ProxiedPlayer player, String serverName) {
        Queue<UUID> q = serverConnectQueues.get(serverName);
        int n = q == null ? 0 : q.size();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("QueueInfoReply");
            out.writeUTF(serverName);
            out.writeInt(n);
            out.close();
            if (player.getServer() != null) {
                player.getServer().sendData("foxaria:proxy", baos.toByteArray());
            }
        } catch (IOException ignored) {
        }
    }

    private void forwardCaseRewardToBackend(String targetServer, java.util.List<String> commands) {
        ServerInfo server = getProxy().getServerInfo(targetServer);
        if (server == null || commands == null || commands.isEmpty()) {
            return;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("CaseRewardExecute");
            out.writeUTF("");
            out.writeUTF("");
            out.writeInt(commands.size());
            for (String command : commands) {
                out.writeUTF(command);
            }
            out.close();
            byte[] data = baos.toByteArray();
            for (ProxiedPlayer player : server.getPlayers()) {
                if (player.getServer() != null) {
                    player.getServer().sendData("foxaria:proxy", data);
                    return;
                }
            }
            getLogger().warning("Case reward forward failed: no players online on " + targetServer);
        } catch (IOException ignored) {
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        refreshPlayerDisplayName(player);
        sendChatPrefixToBackend(player);
        ConnectRetry retry = pendingConnect.get(player.getUniqueId());
        if (retry != null && event.getServer() != null && event.getServer().getInfo() != null
            && event.getServer().getInfo().getName().equalsIgnoreCase(retry.targetServer)) {
            pendingConnect.remove(player.getUniqueId());
            send(player, msg("messages.network.connected", "&aПодключение успешно: &f%server%")
                .replace("%server%", retry.targetServer));
        }

        // Session-authenticated players arrive at auth first; redirect to lobby once fully connected.
        // This is the correct place (not PostLoginEvent) because the Minecraft client is now in
        // PLAY state and ready for a server switch. Doing it earlier caused Paper login timeouts.
        if (event.getServer() != null
                && authServer.equalsIgnoreCase(event.getServer().getInfo().getName())
                && authenticatedNow.contains(player.getUniqueId())) {
            getProxy().getScheduler().schedule(this,
                () -> {
                    if (player.isConnected()) {
                        connectToIfNeeded(player, lobbyServer);
                    }
                },
                500L, TimeUnit.MILLISECONDS);
        }
    }

    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent event) {
        String name = event.getTarget().getName();
        int cap = cfg.getInt("network.server-capacity." + name, -1);
        if (cap < 0) {
            return;
        }
        drainServerQueue(name, cap);
    }

    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        ServerInfo kickedFrom = event.getKickedFrom();
        if (kickedFrom == null && player.getServer() != null) {
            kickedFrom = player.getServer().getInfo();
        }
        if (kickedFrom == null) {
            return;
        }
        String kickedName = kickedFrom.getName();
        if (kickedName.equalsIgnoreCase(authServer)) {
            return;
        }
        if (kickedName.equalsIgnoreCase(lobbyServer)) {
            return;
        }
        // Redirect to lobby from any non-auth/non-lobby server.
        // No authentication check: if a player was on a game server they were authenticated.
        // The old auth check caused a regression where proxy restarts (clearing in-memory
        // sessions) prevented lobby redirect, sending players to auth instead.
        ServerInfo lobby = getProxy().getServerInfo(lobbyServer);
        if (lobby == null) {
            return;
        }
        event.setCancelled(true);
        event.setCancelServer(lobby);
        send(player, msg("messages.network.server-restarting-to-lobby",
            "&eСервер отключён/перезагружается. Переносим в лобби..."));
    }

    /** Кик при обрыве downstream (сервер выключен): {@code kickedFrom} иногда не игровой по списку, но текст — сетевой сбой. */
    private static String kickReasonPlain(ServerKickEvent event) {
        BaseComponent[] comp = event.getKickReasonComponent();
        if (comp != null && comp.length > 0) {
            String s = TextComponent.toLegacyText(comp);
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        // BungeeCord: getReason() — BaseComponent (не String)
        BaseComponent legacy = event.getReason();
        if (legacy != null) {
            String s = TextComponent.toLegacyText(new BaseComponent[]{legacy});
            return s == null ? "" : s;
        }
        return "";
    }

    private boolean isLikelyDownstreamConnectionKick(ServerKickEvent event) {
        String t = kickReasonPlain(event).toLowerCase(Locale.ROOT);
        if (t.isBlank()) {
            return true;
        }
        return t.contains("connection reset")
            || t.contains("connection refused")
            || t.contains("forcibly closed")
            || t.contains("timed out")
            || t.contains("timeout")
            || t.contains("lost connection")
            || t.contains("could not connect")
            || t.contains("не удалось подключ")
            || t.contains("ioexception")
            || t.contains("socketexception")
            || t.contains("прервано")
            || t.contains("remote host");
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        if (!(event.getPlayer() instanceof ProxiedPlayer player)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (isSessionValid(id, safeIp(player))) {
            authenticatedNow.add(id);
            applyPrefix(player);
            ServerInfo st = event.getTarget();
            if (st != null) {
                int cap = cfg.getInt("network.server-capacity." + st.getName(), -1);
                if (cap >= 0 && st.getPlayers().size() >= cap) {
                    event.setCancelled(true);
                    serverConnectQueues.computeIfAbsent(st.getName(), k -> new ConcurrentLinkedQueue<>()).add(id);
                    send(player, msg("messages.network.server-queued", "&eСервер &f%name% &eзаполнен (&f%cur%&e/&f%max%&e). Ты в очереди.")
                        .replace("%name%", st.getName())
                        .replace("%cur%", String.valueOf(st.getPlayers().size()))
                        .replace("%max%", String.valueOf(cap)));
                    return;
                }
                if (!gameJoinBypassOnce.remove(id)
                    && !tryAdmitOrQueueGameJoinThrottle(player, st.getName())) {
                    event.setCancelled(true);
                    return;
                }
                if (wipeLocked.contains(st.getName())
                        && !st.getName().equalsIgnoreCase(authServer)
                        && !st.getName().equalsIgnoreCase(lobbyServer)
                        && !isProxyAdmin(player)) {
                    event.setCancelled(true);
                    send(player, msg("messages.network.server-wipe-locked",
                        "&cСервер &f%name% &cсейчас заблокирован &8(идёт вайп)&c.")
                        .replace("%name%", st.getName()));
                    return;
                }
            }
            return;
        }
        String target = event.getTarget().getName();
        if (!target.equalsIgnoreCase(authServer)) {
            event.setCancelled(true);
            send(player, msg("auth.messages.need-login", "&eВыполни /login <пароль>"));
        }
    }

    private boolean isGameServer(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return false;
        }
        for (String name : cfg.getStringList("network.game-servers")) {
            if (name != null && name.equalsIgnoreCase(serverName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGameJoinThrottleEnabled() {
        return cfg.getBoolean("network.game-join-interval.enabled", true);
    }

    private long gameJoinThrottleGapMs() {
        return Math.max(250L, cfg.getLong("network.game-join-interval.min-interval-ms", 3000L));
    }

    private boolean isGameJoinThrottleBypassAdmin() {
        return cfg.getBoolean("network.game-join-interval.bypass-proxy-admins", true);
    }

    private boolean isGameJoinThrottleTarget(String serverName) {
        List<String> listed = cfg.getStringList("network.game-join-interval.servers");
        if (listed == null || listed.isEmpty()) {
            return isGameServer(serverName);
        }
        for (String name : listed) {
            if (name != null && name.equalsIgnoreCase(serverName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true — пропустить {@link ServerConnectEvent} дальше; false — уже поставлен в очередь, событие нужно отменить.
     */
    private boolean tryAdmitOrQueueGameJoinThrottle(ProxiedPlayer player, String serverName) {
        if (!isGameJoinThrottleEnabled() || !isGameJoinThrottleTarget(serverName)) {
            return true;
        }
        if (isGameJoinThrottleBypassAdmin() && isProxyAdmin(player)) {
            return true;
        }
        UUID id = player.getUniqueId();
        synchronized (gameJoinThrottleMutex) {
            if (gameJoinThrottleTracked.contains(id)) {
                sendGameJoinThrottleQueuedMessage(player, serverName);
                return false;
            }
            boolean backlog = !gameJoinThrottleFifo.isEmpty();
            long gap = gameJoinThrottleGapMs();
            long now = System.currentTimeMillis();
            long earliest = gameJoinThrottleNextAdmissionEarliestMs;
            boolean windowClosed = earliest > 0L && now < earliest;
            if (!backlog && !windowClosed) {
                gameJoinThrottleNextAdmissionEarliestMs = now + gap;
                return true;
            }
            gameJoinThrottleFifo.offer(new GameJoinThrottleTicket(id, serverName));
            gameJoinThrottleTracked.add(id);
            sendGameJoinThrottleQueuedMessage(player, serverName);
            scheduleGameJoinDrain(0L);
            return false;
        }
    }

    private void scheduleGameJoinDrain(long delayMs) {
        long d = Math.max(0L, delayMs);
        getProxy().getScheduler().schedule(this, this::drainGameJoinThrottleQueue, d, TimeUnit.MILLISECONDS);
    }

    /** Время до следующего «слота»; 0 — можно впускать. */
    private long peekGameJoinAdmissionWaitMillis() {
        long earliest = gameJoinThrottleNextAdmissionEarliestMs;
        if (earliest <= 0L) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        return Math.max(0L, earliest - now);
    }

    /**
     * Следующего ждущего отправляем через {@link #gameJoinBypassOnce} с учётом интервала/ёмкости/вайпа.
     */
    private void drainGameJoinThrottleQueue() {
        if (!gameJoinThrottleDrainBusy.compareAndSet(false, true)) {
            scheduleGameJoinDrain(40L);
            return;
        }
        try {
            for (int guard = 0; guard < 512; guard++) {
                GameJoinThrottleTicket ticket;
                ServerInfo target;
                ProxiedPlayer player;
                long now = System.currentTimeMillis();
                long gap = gameJoinThrottleGapMs();
                synchronized (gameJoinThrottleMutex) {
                    GameJoinThrottleTicket peek = gameJoinThrottleFifo.peek();
                    if (peek == null) {
                        return;
                    }
                    long waitAdmission = peekGameJoinAdmissionWaitMillis();
                    if (waitAdmission > 0L) {
                        return;
                    }
                    ServerInfo srv = ProxyServer.getInstance().getServerInfo(peek.serverName());
                    if (srv == null) {
                        gameJoinThrottleFifo.poll();
                        gameJoinThrottleTracked.remove(peek.uuid());
                        continue;
                    }
                    int cap = cfg.getInt("network.server-capacity." + srv.getName(), -1);
                    if (cap >= 0 && srv.getPlayers().size() >= cap) {
                        gameJoinThrottleFifo.poll();
                        gameJoinThrottleTracked.remove(peek.uuid());
                        UUID nid = peek.uuid();
                        serverConnectQueues.computeIfAbsent(srv.getName(), k -> new ConcurrentLinkedQueue<>()).add(nid);
                        ProxiedPlayer pWaiting = ProxyServer.getInstance().getPlayer(nid);
                        if (pWaiting != null) {
                            send(pWaiting, msg("messages.network.server-queued",
                                "&eСервер &f%name% &eзаполнен (&f%cur%&e/&f%max%&e). Ты в очереди.")
                                .replace("%name%", srv.getName())
                                .replace("%cur%", String.valueOf(srv.getPlayers().size()))
                                .replace("%max%", String.valueOf(cap)));
                        }
                        continue;
                    }
                    if (wipeLocked.contains(srv.getName())) {
                        gameJoinThrottleFifo.poll();
                        gameJoinThrottleTracked.remove(peek.uuid());
                        ProxiedPlayer pWait = ProxyServer.getInstance().getPlayer(peek.uuid());
                        if (pWait != null && !isProxyAdmin(pWait)) {
                            send(pWait, msg("messages.network.server-wipe-locked",
                                "&cСервер &f%name% &cсейчас заблокирован &8(идёт вайп)&c.")
                                .replace("%name%", srv.getName()));
                        }
                        continue;
                    }
                    ProxiedPlayer p = ProxyServer.getInstance().getPlayer(peek.uuid());
                    if (p == null) {
                        gameJoinThrottleFifo.poll();
                        gameJoinThrottleTracked.remove(peek.uuid());
                        continue;
                    }
                    ticket = gameJoinThrottleFifo.poll();
                    if (ticket == null) {
                        return;
                    }
                    gameJoinThrottleTracked.remove(ticket.uuid());
                    gameJoinThrottleNextAdmissionEarliestMs = now + gap;
                    target = srv;
                    player = p;
                }
                gameJoinBypassOnce.add(ticket.uuid());
                send(player, msg("messages.network.game-join-advance",
                    "&aОчередь входа: подключаем к &f%name%&a...")
                    .replace("%name%", target.getName()));
                player.connect(target);
                return;
            }
        } finally {
            gameJoinThrottleDrainBusy.set(false);
            synchronized (gameJoinThrottleMutex) {
                if (!gameJoinThrottleFifo.isEmpty()) {
                    long w = peekGameJoinAdmissionWaitMillis();
                    scheduleGameJoinDrain(w > 0L ? w : 25L);
                }
            }
        }
    }

    private void sendGameJoinThrottleQueuedMessage(ProxiedPlayer player, String serverName) {
        int pos = 0;
        for (GameJoinThrottleTicket t : gameJoinThrottleFifo) {
            pos++;
            if (t.uuid().equals(player.getUniqueId())) {
                break;
            }
        }
        if (pos <= 0) {
            pos = gameJoinThrottleFifo.size();
        }
        long gapSec = (gameJoinThrottleGapMs() + 999L) / 1000L;
        long estSec = Math.max(1L, (long) pos * gapSec);
        send(player, msg("messages.network.game-join-queued",
            "&eОчередь входа на &f%name%&e: место &f%pos%&e, ожидание ~&f%wait%s&e.")
            .replace("%name%", serverName)
            .replace("%pos%", String.valueOf(pos))
            .replace("%wait%", String.valueOf(estSec)));
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }
        if (!event.isCommand()) {
            ProxyPunishmentRepository.PunishmentRecord mute = punishmentRepository.activeByTypes(
                player.getUniqueId(), System.currentTimeMillis(), "MUTE", "TEMPMUTE"
            );
            if (mute != null) {
                event.setCancelled(true);
                send(player, msg("moderation.messages.muted", "&cВаш чат ограничен мутом: &f%reason%")
                    .replace("%reason%", mute.reasonTitle()));
            }
            return;
        }
        if (event.isCommand()) {
            if (!authenticatedNow.contains(player.getUniqueId())) {
                String raw = event.getMessage().toLowerCase();
                if (!raw.startsWith("/login") && !raw.startsWith("/l")
                    && !raw.startsWith("/register") && !raw.startsWith("/r")) {
                    event.setCancelled(true);
                    send(player, msg("auth.messages.need-login", "&eВыполни /login <пароль>"));
                }
            }
        }
    }

    private String formatPunishmentScreen(ProxyPunishmentRepository.PunishmentRecord record) {
        String tpl = cfg.getString("moderation.screen.message",
            "&6&lFOXARIA\n%block_notice%\n&cПричина: &f%reason_title%\n&7%reason_description%\n&eСрок: &f%expires%\n&cВыдал: &f%actor%");
        return tpl
            .replace("%block_notice%", punishmentBlockNoticeLine(record.type()))
            .replace("%reason_code%", record.reasonCode() == null ? "" : record.reasonCode())
            .replace("%reason_title%", record.reasonTitle() == null ? "Без причины" : record.reasonTitle())
            .replace("%reason_description%", record.reasonDescription() == null ? "" : record.reasonDescription())
            .replace("%expires%", humanDuration(record.expiresAt()))
            .replace("%actor%", record.actorName() == null || record.actorName().isBlank() ? "Администрация" : record.actorName());
    }

    private static String punishmentBlockNoticeLine(String type) {
        if (type != null && "KICK".equalsIgnoreCase(type)) {
            return "&eВас отключили от сети";
        }
        return "&cВы были заблокированы";
    }

    private String humanDuration(long expiresAt) {
        if (expiresAt <= 0L) {
            return "Перманентно";
        }
        long sec = Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
        if (sec >= 86400L && sec % 86400L == 0L) {
            return (sec / 86400L) + "д";
        }
        if (sec >= 3600L && sec % 3600L == 0L) {
            return (sec / 3600L) + "ч";
        }
        if (sec >= 60L && sec % 60L == 0L) {
            return (sec / 60L) + "м";
        }
        return sec + "с";
    }

    private final class RegisterCommand extends Command {
        RegisterCommand() {
            super("register", null, "r");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                send(sender, msg("common.only-player", "&cКоманда только для игроков."));
                return;
            }
            if (isSessionValid(player.getUniqueId(), safeIp(player))) {
                send(player, msg("auth.messages.already-authenticated", "&aВы уже авторизованы."));
                return;
            }
            if (!consumeAuthAttempt(player)) {
                send(player, msg("auth.messages.too-many-attempts", "&cСлишком много попыток. Подождите немного."));
                return;
            }
            if (args.length < 2) {
                send(player, msg("auth.messages.register-usage", "&eИспользование: /register <пароль> <повтор>"));
                return;
            }
            String p1 = args[0];
            String p2 = args[1];
            if (!p1.equals(p2)) {
                send(player, msg("auth.messages.password-mismatch", "&cПароли не совпадают."));
                return;
            }
            if (p1.length() < minPasswordLength || p1.length() > maxPasswordLength) {
                send(player, msg("auth.messages.password-length", "&cДлина пароля: %min%-%max% символов.")
                    .replace("%min%", String.valueOf(minPasswordLength))
                    .replace("%max%", String.valueOf(maxPasswordLength)));
                return;
            }
            UUID id = player.getUniqueId();
            if (repository.find(id).isPresent()) {
                send(player, msg("auth.messages.already-registered", "&cАккаунт уже зарегистрирован."));
                return;
            }
            String salt = Passwords.randomSaltBase64();
            String hash = Passwords.hashPassword(p1, salt);
            boolean ok = repository.register(id, player.getName(), hash, salt, System.currentTimeMillis());
            if (!ok) {
                send(player, msg("auth.messages.register-failed", "&cНе удалось завершить регистрацию."));
                return;
            }
            markAuthenticated(player);
            applyPrefix(player);
            send(player, msg("auth.messages.register-success", "&aРегистрация завершена. Переносим в лобби..."));
            sendSuccessTitle(player);
            connectToIfNeeded(player, lobbyServer);
        }
    }

    private final class LoginCommand extends Command {
        LoginCommand() {
            super("login", null, "l");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                send(sender, msg("common.only-player", "&cКоманда только для игроков."));
                return;
            }
            if (isSessionValid(player.getUniqueId(), safeIp(player))) {
                send(player, msg("auth.messages.already-authenticated", "&aВы уже авторизованы."));
                return;
            }
            if (!consumeAuthAttempt(player)) {
                send(player, msg("auth.messages.too-many-attempts", "&cСлишком много попыток. Подождите немного."));
                return;
            }
            if (args.length < 1) {
                send(player, msg("auth.messages.login-usage", "&eИспользование: /login <пароль>"));
                return;
            }
            UUID id = player.getUniqueId();
            Optional<AuthRepository.Account> opt = repository.find(id);
            if (opt.isEmpty()) {
                send(player, msg("auth.messages.not-registered", "&cАккаунт не зарегистрирован."));
                return;
            }
            AuthRepository.Account acc = opt.get();
            if (!Passwords.verify(args[0], acc.salt(), acc.hash())) {
                send(player, msg("auth.messages.bad-password", "&cНеверный пароль."));
                return;
            }
            repository.touchLogin(id, player.getName(), System.currentTimeMillis());
            markAuthenticated(player);
            applyPrefix(player);
            send(player, msg("auth.messages.login-success", "&aВход выполнен. Переносим в лобби..."));
            sendSuccessTitle(player);
            connectToIfNeeded(player, lobbyServer);
        }
    }

    private final class GrantPrivilegeCommand extends Command {
        GrantPrivilegeCommand() {
            super("grantpriv", "foxaria.proxy.admin", "gpriv");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                send(sender, msg("privileges.messages.grant-usage", "&eИспользование: /grantpriv <игрок> <группа> [секунды]"));
                return;
            }
            String group = args[1].toLowerCase(Locale.ROOT);
            if (!groupExists(group)) {
                send(sender, msg("privileges.messages.unknown-group", "&cНеизвестная группа: %group%").replace("%group%", group));
                return;
            }
            String rawName = args[0];
            ProxiedPlayer online = ProxyServer.getInstance().getPlayer(rawName);
            if (online != null) {
                // Player is online — grant and apply prefix immediately.
                applyGrant(sender, online.getUniqueId(), online.getName(), group, args);
                applyPrefix(online);
            } else {
                // Player is offline — look up UUID from proxy_accounts and write to DB.
                java.util.Optional<UUID> offlineUuid = privilegeRepository.findUuidByUsername(rawName);
                if (offlineUuid.isEmpty()) {
                    send(sender, msg("common.player-offline", "&cИгрок &f%player% &cне найден (никогда не входил на сервер).").replace("%player%", rawName));
                    return;
                }
                applyGrant(sender, offlineUuid.get(), rawName, group, args);
                send(sender, "&7(Игрок оффлайн — ранг применится при следующем входе.)");
            }
        }

        private void applyGrant(CommandSender sender, UUID uuid, String name, String group, String[] args) {
            if (args.length >= 3) {
                long seconds;
                try { seconds = Long.parseLong(args[2]); }
                catch (NumberFormatException ex) {
                    send(sender, msg("privileges.messages.bad-duration", "&cСрок должен быть числом (секунды).")); return;
                }
                privilegeRepository.grantTemporary(uuid, group, System.currentTimeMillis() + (seconds * 1000L));
                send(sender, msg("privileges.messages.temp-granted", "&aВыдана временная группа &f%group%&a игроку &f%player%&a на &f%seconds%s&a.")
                    .replace("%group%", group).replace("%player%", name).replace("%seconds%", String.valueOf(seconds)));
            } else {
                privilegeRepository.setPrimary(uuid, group);
                send(sender, msg("privileges.messages.primary-granted", "&aВыдана группа &f%group%&a игроку &f%player%&a.")
                    .replace("%group%", group).replace("%player%", name));
            }
        }
    }

    private final class GrantPackageCommand extends Command {
        GrantPackageCommand() {
            super("grantpkg", "foxaria.proxy.admin", "storegrantproxy");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                send(sender, msg("store.messages.grant-usage", "&eИспользование: /grantpkg <игрок> <packageId>"));
                return;
            }
            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(args[0]);
            if (target == null) {
                send(sender, msg("common.player-offline", "&cИгрок %player% не онлайн.").replace("%player%", args[0]));
                return;
            }
            String packageId = args[1];
            String action = cfg.getString("store.packages." + packageId + ".action", "");
            if (action.isBlank()) {
                send(sender, msg("store.messages.unknown-package", "&cПакет не найден: %package%").replace("%package%", packageId));
                return;
            }
            if (!applyPackageAction(target, action, senderName(sender), "grantpkg")) {
                send(sender, msg("store.messages.bad-action", "&cНе удалось применить action: %action%").replace("%action%", action));
                return;
            }
            send(sender, msg("store.messages.package-granted", "&aПакет &f%package%&a выдан игроку &f%player%&a.")
                .replace("%package%", packageId).replace("%player%", target.getName()));
        }
    }

    private final class RevokePrivilegeCommand extends Command {
        RevokePrivilegeCommand() {
            super("revokepriv", "foxaria.proxy.admin", "ungroup", "ungrantpriv");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                send(sender, msg("privileges.messages.revoke-usage", "&eИспользование: /revokepriv <игрок> [temp|all]"));
                return;
            }
            String rawName = args[0];
            String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
            ProxiedPlayer online = ProxyServer.getInstance().getPlayer(rawName);
            UUID uuid;
            String name;
            if (online != null) {
                uuid = online.getUniqueId();
                name = online.getName();
            } else {
                java.util.Optional<UUID> offlineUuid = privilegeRepository.findUuidByUsername(rawName);
                if (offlineUuid.isEmpty()) {
                    send(sender, msg("common.player-offline", "&cИгрок &f%player% &cне найден.").replace("%player%", rawName));
                    return;
                }
                uuid = offlineUuid.get();
                name = rawName;
            }
            if ("temp".equals(mode)) {
                privilegeRepository.revokeTemporary(uuid);
                send(sender, msg("privileges.messages.temp-revoked", "&aВременная группа снята у &f%player%&a.").replace("%player%", name));
            } else {
                privilegeRepository.resetToDefault(uuid, defaultGroup);
                send(sender, msg("privileges.messages.all-revoked", "&aПривилегии игрока &f%player% &aсброшены к группе &f%group%&a.")
                    .replace("%player%", name).replace("%group%", defaultGroup));
            }
            if (online != null) applyPrefix(online);
            else send(sender, "&7(Игрок оффлайн — изменения применятся при следующем входе.)");
        }
    }

    private final class MyGroupCommand extends Command {
        MyGroupCommand() {
            super("mygroup", null, "groupme", "megroup");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                send(sender, msg("common.only-player", "&cКоманда только для игроков."));
                return;
            }
            sendGroupInfo(sender, player.getUniqueId(), player.getName());
        }
    }

    private final class GroupInfoCommand extends Command {
        GroupInfoCommand() {
            super("groupinfo", "foxaria.proxy.admin", "pgroup", "privinfo");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                if (sender instanceof ProxiedPlayer player) {
                    sendGroupInfo(sender, player.getUniqueId(), player.getName());
                    return;
                }
                send(sender, msg("privileges.messages.groupinfo-usage", "&eИспользование: /groupinfo <игрок>"));
                return;
            }
            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(args[0]);
            if (target == null) {
                send(sender, msg("common.player-offline", "&cИгрок %player% не онлайн.").replace("%player%", args[0]));
                return;
            }
            sendGroupInfo(sender, target.getUniqueId(), target.getName());
        }
    }

    private final class AuditPrivilegeCommand extends Command {
        AuditPrivilegeCommand() {
            super("auditpriv", "foxaria.proxy.admin", "privaudit");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                send(sender, msg("audit.messages.usage", "&eИспользование: /auditpriv <игрок> [limit]"));
                return;
            }
            ProxiedPlayer target = ProxyServer.getInstance().getPlayer(args[0]);
            if (target == null) {
                send(sender, msg("common.player-offline", "&cИгрок %player% не онлайн.").replace("%player%", args[0]));
                return;
            }
            int limit = 10;
            if (args.length >= 2) {
                try {
                    limit = Math.max(1, Math.min(30, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                }
            }
            List<PrivilegeAuditRepository.AuditEntry> rows = auditRepository.recentForTarget(target.getUniqueId(), limit);
            if (rows.isEmpty()) {
                send(sender, msg("audit.messages.empty", "&7Для игрока %player% записей пока нет.").replace("%player%", target.getName()));
                return;
            }
            send(sender, msg("audit.messages.header", "&6&lFOXARIA &8» &fAudit для &e%player%").replace("%player%", target.getName()));
            for (PrivilegeAuditRepository.AuditEntry row : rows) {
                send(sender, msg("audit.messages.line", "&8#%id% &7[%time%] &f%action% &7by &e%actor% &8| &f%detail%")
                    .replace("%id%", String.valueOf(row.id()))
                    .replace("%time%", TS.format(Instant.ofEpochMilli(row.createdAt())))
                    .replace("%action%", row.action())
                    .replace("%actor%", row.actor())
                    .replace("%detail%", row.detail()));
            }
        }
    }

    private final class RegisterAliasCommand extends Command {
        RegisterAliasCommand() {
            super("r");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            new RegisterCommand().execute(sender, args);
        }
    }

    private final class LoginAliasCommand extends Command {
        LoginAliasCommand() {
            super("l");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            new LoginCommand().execute(sender, args);
        }
    }

    private final class RestartAllBackendsCommand extends Command {
        RestartAllBackendsCommand() {
            super("restartallbackends", "foxaria.proxy.admin", "rall");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            List<String> commands = cfg.getStringList("ops.restart-all-commands");
            if (commands == null || commands.isEmpty()) {
                send(sender, "&cСписок ops.restart-all-commands пуст.");
                return;
            }
            send(sender, "&eЗапускаю команды перезапуска backend...");
            for (String cmd : commands) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd);
                    pb.directory(getDataFolder().getParentFile().getParentFile());
                    pb.start();
                } catch (Exception ex) {
                    send(sender, "&cОшибка запуска: " + cmd + " -> " + ex.getMessage());
                }
            }
            send(sender, "&aКоманды перезапуска отправлены.");
        }
    }

    private final class ProxyHelpCommand extends Command {
        ProxyHelpCommand() {
            super("proxyhelp", "foxaria.proxy.admin", "phelp");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                send(sender, "&cЭта команда доступна только в консоли proxy.");
                return;
            }
            send(sender, "&6&lFOXARIA PROXY &8» &fДоступные команды:");
            sendAdminLines(sender);
            send(sender, "&e/lobby &7- отправить игрока в лобби");
        }

        private void sendAdminLines(CommandSender sender) {
            send(sender, "&e/grantpriv <игрок> <группа> [секунды]");
            send(sender, "&e/revokepriv <игрок> [temp|all]");
            send(sender, "&e/groupinfo <игрок>");
            send(sender, "&e/auditpriv <игрок> [limit]");
            send(sender, "&e/grantpkg <игрок> <packageId>");
            send(sender, "&e/restartallbackends");
        }
    }

    private final class LobbyCommand extends Command {
        LobbyCommand() {
            super("lobby", null, "hub");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                send(sender, msg("common.only-player", "&cКоманда только для игроков."));
                return;
            }
            String current = player.getServer() == null ? "" : player.getServer().getInfo().getName();
            if (current.equalsIgnoreCase(authServer)) {
                send(player, msg("messages.network.lobby-from-auth-denied", "&eСначала авторизуйтесь на auth-сервере."));
                return;
            }
            if (!authenticatedNow.contains(player.getUniqueId()) && !isSessionValid(player.getUniqueId(), safeIp(player))) {
                send(player, msg("auth.messages.need-login", "&eВыполни /login <пароль>"));
                return;
            }
            if (current.equalsIgnoreCase(lobbyServer)) {
                send(player, msg("messages.network.already-in-lobby", "&eВы уже находитесь в лобби."));
                return;
            }
            send(player, msg("messages.network.to-lobby", "&aПереносим в лобби..."));
            connectToIfNeeded(player, lobbyServer);
        }
    }

    /**
     * Список серверов и переход — только в лобби (vanilla /server отключён в config.yml Bungee).
     */
    private final class ServerPickerCommand extends Command {
        ServerPickerCommand() {
            super("server", null);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                send(sender, "&cКоманда только для игроков.");
                return;
            }
            if (!authenticatedNow.contains(player.getUniqueId()) && !isSessionValid(player.getUniqueId(), safeIp(player))) {
                send(player, msg("auth.messages.need-login", "&eВыполни /login <пароль>"));
                return;
            }
            String current = player.getServer() == null ? "" : player.getServer().getInfo().getName();
            if (!current.equalsIgnoreCase(lobbyServer)) {
                send(player, msg("messages.network.server-only-lobby",
                    "&cСписок серверов доступен только в лобби. Используй &e/lobby"));
                return;
            }
            if (args.length >= 1) {
                String targetName = args[0];
                if (targetName.equalsIgnoreCase(authServer)) {
                    send(player, msg("messages.network.server-no-auth",
                        "&eЧерез эту команду нельзя перейти на сервер авторизации."));
                    return;
                }
                ServerInfo target = ProxyServer.getInstance().getServerInfo(targetName);
                if (target == null) {
                    send(player, msg("messages.network.server-unknown", "&cСервер не найден: &f%name%")
                        .replace("%name%", targetName));
                    return;
                }
                send(player, msg("messages.network.server-connecting", "&aПодключение к &f%name%&a...")
                    .replace("%name%", target.getName()));
                player.connect(target);
                return;
            }
            send(player, msg("messages.network.server-picker-title", "&6&l━━ &eСерверы Foxaria &6&l━━"));
            for (String id : cfg.getStringList("network.server-picker-order")) {
                if (cfg.getStringList("network.server-picker-skip").contains(id)) {
                    continue;
                }
                ServerInfo si = ProxyServer.getInstance().getServerInfo(id);
                if (si == null) {
                    continue;
                }
                if (id.equalsIgnoreCase(authServer)) {
                    continue;
                }
                String title = cfg.getString("network.server-picker." + id + ".title", id);
                String hint = cfg.getString("network.server-picker." + id + ".hint", "");
                send(player, msg("messages.network.server-picker-line",
                    "&8▸ &f%title% &8│ &7%hint% &8→ &e/server %id%")
                    .replace("%title%", title)
                    .replace("%hint%", hint)
                    .replace("%id%", id));
            }
            send(player, msg("messages.network.server-picker-footer",
                "&7Указать сервер: &e/server &f<имя> &7· Сейчас: &fлобби"));
        }
    }

    private boolean canUseProxyPunish(ProxiedPlayer p) {
        if (p.hasPermission("foxaria.mod.punish")
            || p.hasPermission("foxaria.proxy.admin")
            || p.hasPermission("foxaria.admin.panel")) {
            return true;
        }
        return matchesProxyModerationStaffGroup(p);
    }

    private boolean canUseProxyUnpunish(ProxiedPlayer p) {
        if (p.hasPermission("foxaria.mod.unpunish")
            || p.hasPermission("foxaria.proxy.admin")
            || p.hasPermission("foxaria.admin.panel")) {
            return true;
        }
        return matchesProxyModerationStaffGroup(p);
    }

    private static ProxiedPlayer findOnlinePlayerByName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        ProxiedPlayer p = ProxyServer.getInstance().getPlayer(rawName);
        if (p != null) {
            return p;
        }
        for (ProxiedPlayer pl : ProxyServer.getInstance().getPlayers()) {
            if (pl.getName().equalsIgnoreCase(rawName)) {
                return pl;
            }
        }
        return null;
    }

    private boolean matchesProxyModerationStaffGroup(ProxiedPlayer p) {
        List<String> groups = cfg.getStringList("moderation.staff-groups");
        if (groups == null || groups.isEmpty()) {
            groups = List.of("admin", "moderator");
        }
        String active = privilegeRepository.resolveActiveGroup(p.getUniqueId(), defaultGroup, System.currentTimeMillis());
        if (active == null || active.isBlank()) {
            active = defaultGroup;
        }
        String al = active.toLowerCase(Locale.ROOT);
        for (String g : groups) {
            if (g != null && g.toLowerCase(Locale.ROOT).equals(al)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Снятие санкций на прокси по UUID (игрок может быть офлайн — например после бана).
     * Учитывает и Mojang-UUID, и Spigot offline-UUID по нику — иначе рассинхрон при cracked/offline.
     * Снимает все активные записи этого аккаунта (в т.ч. дубликаты одной причины). Только UUID/ник — без разбана по IP.
     */
    private void completeProxyUnpunish(CommandSender sender, UUID resolvedUuid, String displayName, boolean silent) {
        long now = System.currentTimeMillis();
        List<UUID> uuidList = new ArrayList<>();
        if (resolvedUuid != null) {
            uuidList.add(resolvedUuid);
        }
        UUID offlineUuid = SpigotOfflineUuid.fromPlayerName(displayName);
        if (offlineUuid != null && (resolvedUuid == null || !offlineUuid.equals(resolvedUuid))) {
            uuidList.add(offlineUuid);
        }
        if (uuidList.isEmpty() && (displayName == null || displayName.isBlank())) {
            send(sender, msg("moderation.messages.unpunish-unknown",
                "&cНе удалось определить UUID игрока &f%player%&c.").replace("%player%", displayName));
            return;
        }

        if (displayName != null && !displayName.isBlank()) {
            repository.findUuidByUsernameIgnoreCase(displayName).ifPresent(u -> {
                if (!uuidList.contains(u)) {
                    uuidList.add(u);
                }
            });
        }

        List<ProxyPunishmentRepository.PunishmentRecord> beforeProxy =
            punishmentRepository.listAllActiveForUnpunish(uuidList, displayName, now);

        LinkedHashSet<UUID> allUuids = new LinkedHashSet<>(uuidList);
        for (ProxyPunishmentRepository.PunishmentRecord r : beforeProxy) {
            allUuids.add(r.targetUuid());
        }
        BackendSanctionsResult backendResult = fxPunishments.listActiveForBroadcastResult(new ArrayList<>(allUuids), now);
        List<ProxyPunishmentRepository.PunishmentRecord> beforeBackend = backendResult.records();
        for (ProxyPunishmentRepository.PunishmentRecord r : beforeBackend) {
            allUuids.add(r.targetUuid());
        }

        if (beforeProxy.isEmpty() && beforeBackend.isEmpty()) {
            String nameTrimEarly = displayName != null ? displayName.trim() : null;
            int blindProxy = punishmentRepository.deactivateAllMatching(new ArrayList<>(uuidList), nameTrimEarly);
            int blindBackend = fxPunishments.isEnabled()
                ? fxPunishments.deactivateAllModerationSanctionsFor(new ArrayList<>(uuidList)) : 0;
            if (blindProxy > 0 || blindBackend > 0) {
                ProxiedPlayer onlineTarget = null;
                for (UUID u : uuidList) {
                    onlineTarget = ProxyServer.getInstance().getPlayer(u);
                    if (onlineTarget != null) {
                        break;
                    }
                }
                if (onlineTarget != null && !silent) {
                    send(onlineTarget, msg("moderation.messages.unpunish-target",
                        "&7[&6FOXARIA&7] &aС вас сняты ограничения в чате и другие активные санкции на сети."));
                }
                int totalBlind = blindProxy + blindBackend;
                send(sender, msg("moderation.messages.unpunish-ok", "&aСнято записей: &f%n% &a(прокси: &f%np%&a, игровые: &f%nb%&a) у игрока &f%player%&a.")
                    .replace("%n%", Integer.toString(totalBlind))
                    .replace("%np%", Integer.toString(blindProxy))
                    .replace("%nb%", Integer.toString(blindBackend))
                    .replace("%player%", displayName));
                return;
            }
            if (backendResult.queryFailed()) {
                send(sender, msg("moderation.messages.unpunish-backend-error",
                    "&cИгровая БД недоступна: &f%err%&c. В локальной БД прокси записей по UUID/нику не найдено.")
                    .replace("%err%", shortenErr(backendResult.errorMessage())));
                return;
            }
            send(sender, msg("moderation.messages.unpunish-nothing",
                "&cНет активных санкций для этого игрока."));
            return;
        }

        if (backendResult.queryFailed() && !beforeProxy.isEmpty()) {
            send(sender, msg("moderation.messages.unpunish-backend-partial",
                "&eИгровая БД недоступна — снимаю только санкции на прокси (SQLite)."));
        }

        List<ProxyPunishmentRepository.PunishmentRecord> forBroadcast = new ArrayList<>(beforeProxy.size() + beforeBackend.size());
        forBroadcast.addAll(beforeProxy);
        forBroadcast.addAll(beforeBackend);
        if (!silent) {
            broadcastProxyUnpunish(displayName, forBroadcast, sender instanceof ProxiedPlayer ? sender.getName() : "Консоль");
        }
        String nameTrim = displayName != null ? displayName.trim() : null;
        List<String> proxyRowIds = beforeProxy.stream().map(ProxyPunishmentRepository.PunishmentRecord::id).toList();
        List<String> backendRowIds = beforeBackend.stream().map(ProxyPunishmentRepository.PunishmentRecord::id).toList();
        int clearedProxy = punishmentRepository.deactivateByRecordIds(proxyRowIds);
        clearedProxy += punishmentRepository.deactivateAllMatching(new ArrayList<>(allUuids), nameTrim);
        int clearedBackend = fxPunishments.deactivateRecordsByIds(backendRowIds);
        clearedBackend += fxPunishments.deactivateAllModerationSanctionsFor(new ArrayList<>(allUuids));
        ProxiedPlayer onlineTarget = null;
        for (ProxyPunishmentRepository.PunishmentRecord r : beforeProxy) {
            onlineTarget = ProxyServer.getInstance().getPlayer(r.targetUuid());
            if (onlineTarget != null) {
                break;
            }
        }
        if (onlineTarget == null) {
            for (UUID u : uuidList) {
                onlineTarget = ProxyServer.getInstance().getPlayer(u);
                if (onlineTarget != null) {
                    break;
                }
            }
        }
        if (onlineTarget != null && !silent) {
            send(onlineTarget, msg("moderation.messages.unpunish-target",
                "&7[&6FOXARIA&7] &aС вас сняты ограничения в чате и другие активные санкции на сети."));
        }
        int total = clearedProxy + clearedBackend;
        send(sender, msg("moderation.messages.unpunish-ok", "&aСнято записей: &f%n% &a(прокси: &f%np%&a, игровые: &f%nb%&a) у игрока &f%player%&a.")
            .replace("%n%", Integer.toString(total))
            .replace("%np%", Integer.toString(clearedProxy))
            .replace("%nb%", Integer.toString(clearedBackend))
            .replace("%player%", displayName));
    }

    private static String shortenErr(String message) {
        if (message == null || message.isBlank()) {
            return "ошибка соединения";
        }
        String t = message.trim();
        return t.length() > 120 ? t.substring(0, 117) + "…" : t;
    }

    /**
     * Запись наказания на прокси по UUID (цель может быть офлайн — бан/мут применятся при входе).
     */
    private void completeProxyPunish(CommandSender sender, UUID targetUuid, String displayName,
                                     ProxyPunishmentCatalog.Entry entry, boolean silent) {
        long now = System.currentTimeMillis();
        long expiresAt = entry.durationSeconds() > 0L ? now + (entry.durationSeconds() * 1000L) : 0L;
        String actorName = sender instanceof ProxiedPlayer ? sender.getName() : "Консоль";
        ProxyPunishmentRepository.PunishmentRecord rec = new ProxyPunishmentRepository.PunishmentRecord(
            UUID.randomUUID().toString(),
            targetUuid,
            displayName,
            actorName,
            entry.type(),
            entry.code(),
            entry.title(),
            entry.description(),
            now,
            expiresAt,
            true
        );
        punishmentRepository.add(rec);
        if (!silent) {
            broadcastProxyPunish(displayName, rec);
        }
        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetUuid);
        if (isKickType(entry.type()) && target != null) {
            target.disconnect(TextComponent.fromLegacyText(ProxyColorUtil.colorize(formatPunishmentScreen(rec))));
        } else if (target != null && !silent && !isKickType(entry.type())) {
            send(target, msg("moderation.messages.muted", "&cВаш чат ограничен мутом: &f%reason%")
                .replace("%reason%", entry.title()));
        }
        send(sender, msg("moderation.messages.punish-applied", "&aНаказание %code% выдано игроку %player%.")
            .replace("%code%", entry.code())
            .replace("%player%", displayName));
    }

    private final class PunishCommand extends Command {
        PunishCommand() {
            super("punish", null);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer p) {
                if (!canUseProxyPunish(p)) {
                    send(sender, msg("moderation.messages.punish-no-proxy-permission", "&cНет прав."));
                    return;
                }
            }
            ParsedInput in = parseInput(args);
            if (in.args.length < 2) {
                send(sender, msg("moderation.messages.punish-usage", "&cИспользование: /punish <игрок> <код> [-s]"));
                return;
            }
            String rawName = in.args[0];
            ProxyPunishmentCatalog.Entry entry = punishmentCatalog.byCode(in.args[1]);
            if (entry == null) {
                send(sender, msg("moderation.messages.punish-unknown-code", "&cКод причины не найден: %code%")
                    .replace("%code%", in.args[1]));
                return;
            }
            ProxiedPlayer online = findOnlinePlayerByName(rawName);
            if (online != null) {
                completeProxyPunish(sender, online.getUniqueId(), online.getName(), entry, in.silent);
                return;
            }
            send(sender, msg("moderation.messages.punish-resolve",
                "&7Поиск UUID для &f%player%&7…").replace("%player%", rawName));
            getProxy().getScheduler().runAsync(FoxariaProxyPlugin.this, () -> {
                Optional<UUID> resolved = MojangUuidLookup.fromPlayerName(rawName);
                getProxy().getScheduler().schedule(FoxariaProxyPlugin.this, () -> {
                    if (resolved.isPresent()) {
                        completeProxyPunish(sender, resolved.get(), rawName, entry, in.silent);
                        return;
                    }
                    UUID offline = SpigotOfflineUuid.fromPlayerName(rawName);
                    if (offline == null) {
                        send(sender, msg("moderation.messages.punish-unknown",
                            "&cНе удалось определить UUID для &f%player%&c.")
                            .replace("%player%", rawName));
                        return;
                    }
                    send(sender, msg("moderation.messages.punish-offline-fallback",
                        "&eMojang недоступен — наказание по offline-UUID."));
                    completeProxyPunish(sender, offline, rawName, entry, in.silent);
                }, 0L, TimeUnit.MILLISECONDS);
            });
        }
    }

    private final class UnpunishCommand extends Command {
        UnpunishCommand() {
            super("unpunish", null);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer p) {
                if (!canUseProxyUnpunish(p)) {
                    send(sender, msg("moderation.messages.unpunish-no-proxy-permission", "&cНет прав."));
                    return;
                }
            }
            ParsedInput in = parseInput(args);
            if (in.args.length < 1) {
                send(sender, msg("moderation.messages.unpunish-usage", "&cИспользование: /unpunish <игрок> [-s]"));
                return;
            }
            String rawName = in.args[0];
            ProxiedPlayer online = findOnlinePlayerByName(rawName);
            if (online != null) {
                completeProxyUnpunish(sender, online.getUniqueId(), online.getName(), in.silent);
                return;
            }
            send(sender, msg("moderation.messages.unpunish-resolve",
                "&7Поиск UUID для &f%player%&7…").replace("%player%", rawName));
            getProxy().getScheduler().runAsync(FoxariaProxyPlugin.this, () -> {
                Optional<UUID> resolved = MojangUuidLookup.fromPlayerName(rawName);
                getProxy().getScheduler().schedule(FoxariaProxyPlugin.this, () -> {
                    if (resolved.isPresent()) {
                        completeProxyUnpunish(sender, resolved.get(), rawName, in.silent);
                        return;
                    }
                    UUID offline = SpigotOfflineUuid.fromPlayerName(rawName);
                    if (offline == null) {
                        send(sender, msg("moderation.messages.unpunish-unknown",
                            "&cНе удалось определить UUID для &f%player%&c.")
                            .replace("%player%", rawName));
                        return;
                    }
                    send(sender, msg("moderation.messages.unpunish-offline-fallback",
                        "&eMojang недоступен — ищу санкции по offline-UUID (как в singleplayer/cracked)."));
                    completeProxyUnpunish(sender, offline, rawName, in.silent);
                }, 0L, TimeUnit.MILLISECONDS);
            });
        }
    }

    private final class RulesCommand extends Command {
        RulesCommand() {
            super("rules");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            send(sender, msg("moderation.messages.rules-header", "&6&lFOXARIA &8» &fПричины наказаний"));
            for (ProxyPunishmentCatalog.Entry e : punishmentCatalog.all()) {
                String duration = e.durationSeconds() <= 0L ? "перманентно" : shortDuration(e.durationSeconds());
                send(sender, msg("moderation.messages.rules-line",
                    "&e%code% &8• &f%title% &8• &7%type% &8• &f%sanction%\n&8  %text%")
                    .replace("%code%", e.code())
                    .replace("%title%", e.title())
                    .replace("%type%", e.type())
                    .replace("%sanction%", duration)
                    .replace("%text%", e.rulesText()));
            }
        }
    }

    private record ParsedInput(String[] args, boolean silent) {
    }

    private ParsedInput parseInput(String[] raw) {
        List<String> cleaned = new ArrayList<>();
        boolean silent = false;
        for (String a : raw) {
            if ("-s".equalsIgnoreCase(a) || "--silent".equalsIgnoreCase(a)) {
                silent = true;
                continue;
            }
            cleaned.add(a);
        }
        return new ParsedInput(cleaned.toArray(String[]::new), silent);
    }

    private boolean isKickType(String type) {
        return "KICK".equalsIgnoreCase(type) || "BAN".equalsIgnoreCase(type) || "TEMPBAN".equalsIgnoreCase(type);
    }

    private String shortDuration(long sec) {
        if (sec % 86400L == 0L) {
            return (sec / 86400L) + "д";
        }
        if (sec % 3600L == 0L) {
            return (sec / 3600L) + "ч";
        }
        if (sec % 60L == 0L) {
            return (sec / 60L) + "м";
        }
        return sec + "с";
    }

    private void markAuthenticated(ProxiedPlayer player) {
        UUID id = player.getUniqueId();
        authenticatedNow.add(id);
        long expiresAt = System.currentTimeMillis() + sessionTtlMs;
        sessionExpiry.put(id, expiresAt);
        String ip = safeIp(player);
        if (ip != null) {
            sessionIp.put(id, ip);
        }
        // Persist session to DB so it survives proxy restarts.
        sessionRepository.save(id, ip, expiresAt);
    }

    private boolean isSessionValid(UUID uuid, String currentIp) {
        Long until = sessionExpiry.get(uuid);
        if (until == null) {
            // In-memory cache miss — check DB directly (covers proxy restart scenario).
            SessionRepository.SessionRecord rec = sessionRepository.findActive(uuid, System.currentTimeMillis());
            if (rec == null) return false;
            // Restore into in-memory cache for subsequent checks.
            until = rec.expiresAt();
            sessionExpiry.put(uuid, until);
            if (rec.ipAddress() != null) sessionIp.put(uuid, rec.ipAddress());
        }
        if (requireSameIpForSession) {
            String expectedIp = sessionIp.get(uuid);
            if (expectedIp != null && currentIp != null && !expectedIp.equals(currentIp)) {
                sessionExpiry.remove(uuid);
                sessionIp.remove(uuid);
                authenticatedNow.remove(uuid);
                sessionRepository.delete(uuid);
                return false;
            }
        }
        boolean valid = until >= System.currentTimeMillis();
        if (!valid) {
            sessionExpiry.remove(uuid);
            sessionIp.remove(uuid);
            authenticatedNow.remove(uuid);
        }
        return valid;
    }

    private boolean applyPackageAction(ProxiedPlayer player, String action, String actor, String source) {
        String[] parts = action.split(":");
        String kind = parts[0].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "group" -> {
                if (parts.length < 2) {
                    return false;
                }
                privilegeRepository.setPrimary(player.getUniqueId(), parts[1]);
                audit(source, player, "PACKAGE_GROUP", action, actor);
                applyPrefix(player);
                return true;
            }
            case "temp_group" -> {
                if (parts.length < 3) {
                    return false;
                }
                long seconds;
                try {
                    seconds = Long.parseLong(parts[2]);
                } catch (NumberFormatException ex) {
                    return false;
                }
                privilegeRepository.grantTemporary(player.getUniqueId(), parts[1], System.currentTimeMillis() + (seconds * 1000L));
                audit(source, player, "PACKAGE_TEMP_GROUP", action, actor);
                applyPrefix(player);
                return true;
            }
            case "proxy_command" -> {
                if (parts.length < 2) {
                    return false;
                }
                String raw = action.substring("proxy_command:".length())
                    .replace("%player%", player.getName());
                audit(source, player, "PACKAGE_PROXY_COMMAND", raw, actor);
                return ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), raw);
            }
            default -> {
                return false;
            }
        }
    }

    private void applyPrefix(ProxiedPlayer player) {
        refreshPlayerDisplayName(player);
        sendChatPrefixToBackend(player);
    }

    /** Гильдия в табе только на перечисленных серверах (лобби/auth — без тега гильдии). */
    private boolean showGuildInTab(ProxiedPlayer player) {
        List<String> allow = cfg.getStringList("tab.guild-tab-servers");
        if (allow == null || allow.isEmpty()) {
            allow = List.of("test-game");
        }
        String on = player.getServer() == null ? "" : player.getServer().getInfo().getName();
        for (String name : allow) {
            if (name != null && name.equalsIgnoreCase(on)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPlayerDisplayName(ProxiedPlayer player) {
        String group = privilegeRepository.resolveActiveGroup(player.getUniqueId(), defaultGroup, System.currentTimeMillis());
        String prefixLine = cfg.getString("privileges.groups." + group + ".prefix",
            cfg.getString("privileges.groups." + defaultGroup + ".prefix", "&7Игрок &8| &f"));
        String rank = ProxyColorUtil.stripRankFromPrefix(prefixLine);
        String nick = "&7" + player.getName();
        StringBuilder line = new StringBuilder();
        if (!rank.isEmpty()) {
            line.append(rank).append(" ");
        }
        line.append(nick);
        GuildSyncData g = guildByPlayer.get(player.getUniqueId());
        if (showGuildInTab(player) && g != null && g.displayName != null && !g.displayName.isBlank()) {
            line.append(" ").append(g.colorLegacy).append(g.displayName);
        }
        String rawLine = line.toString();
        player.setDisplayName(ProxyColorUtil.colorize(rawLine));
        ProxyTabListPackets.broadcastPlayerListDisplayName(player, rawLine);
    }

    /** После входа клиент получает tab list с дефолтными никами — переотправляем отображаемые имена всем (как в чате: префикс + ник + гильдия). */
    private void scheduleTabDisplayNameResync() {
        if (!cfg.getBoolean("tab.enabled", true)) {
            return;
        }
        getProxy().getScheduler().schedule(this, () -> {
            for (ProxiedPlayer p : getProxy().getPlayers()) {
                refreshPlayerDisplayName(p);
            }
        }, 250L, TimeUnit.MILLISECONDS);
    }

    private boolean groupExists(String group) {
        return cfg.getSection("privileges.groups") != null && cfg.getSection("privileges.groups").getSection(group) != null;
    }

    private void sendGroupInfo(CommandSender sender, UUID playerUuid, String playerName) {
        PrivilegeRepository.PrivilegeState state = privilegeRepository.state(playerUuid);
        String active = privilegeRepository.resolveActiveGroup(playerUuid, defaultGroup, System.currentTimeMillis());
        String primary = state == null || state.primaryGroup() == null || state.primaryGroup().isBlank() ? defaultGroup : state.primaryGroup();
        String temp = state == null ? null : state.tempGroup();
        Long until = state == null ? null : state.tempExpiresAt();
        List<String> lines = new ArrayList<>();
        lines.add(msg("privileges.messages.groupinfo-header", "&6&lFOXARIA &8» &fПривилегии игрока &e%player%").replace("%player%", playerName));
        lines.add(msg("privileges.messages.groupinfo-active", "&7Активная группа: &f%group%").replace("%group%", active));
        lines.add(msg("privileges.messages.groupinfo-primary", "&7Основная группа: &f%group%").replace("%group%", primary));
        if (temp != null && !temp.isBlank() && until != null && until > System.currentTimeMillis()) {
            lines.add(msg("privileges.messages.groupinfo-temp", "&7Временная группа: &f%group% &7до &e%time%")
                .replace("%group%", temp)
                .replace("%time%", TS.format(Instant.ofEpochMilli(until))));
        } else {
            lines.add(msg("privileges.messages.groupinfo-no-temp", "&7Временная группа: &8нет"));
        }
        for (String line : lines) {
            send(sender, line);
        }
    }

    private void audit(String source, ProxiedPlayer target, String action, String detail, String actor) {
        auditRepository.append(actor + "@" + source, target.getUniqueId(), target.getName(), action, detail, System.currentTimeMillis());
    }

    private String senderName(CommandSender sender) {
        return sender.getName() == null || sender.getName().isBlank() ? "CONSOLE" : sender.getName();
    }

    private void broadcastProxyUnpunish(String targetNick, List<ProxyPunishmentRepository.PunishmentRecord> removed, String removerName) {
        String nick = targetNick;
        String wasLine = proxyWasStatusLine(removed);
        String reasonLine = removed.stream()
            .map(r -> {
                String t = r.reasonTitle() == null ? "" : r.reasonTitle().trim();
                String d = r.reasonDescription() == null ? "" : r.reasonDescription().trim();
                if (t.isEmpty() && d.isEmpty()) {
                    return "";
                }
                if (d.isEmpty() || d.equals(t)) {
                    return t;
                }
                String shortD = d.length() > 80 ? d.substring(0, 77) + "…" : d;
                return t + " — " + shortD;
            })
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.joining("; "));
        if (reasonLine.isEmpty()) {
            reasonLine = "—";
        }
        String remover = removerName == null || removerName.isBlank() ? "Консоль" : removerName;
        net.md_5.bungee.api.chat.BaseComponent[] hover = new ComponentBuilder("")
            .append(wasLine).color(ChatColor.YELLOW).append("\n")
            .append("По причине: ").color(ChatColor.DARK_GRAY)
            .append(reasonLine).color(ChatColor.GRAY).append("\n")
            .append("Снял: ").color(ChatColor.DARK_GRAY)
            .append(remover).color(ChatColor.GREEN)
            .create();
        TextComponent line = new TextComponent("");
        TextComponent br1 = new TextComponent("[");
        br1.setColor(ChatColor.DARK_GRAY);
        TextComponent fox = new TextComponent("FOXARIA");
        fox.setColor(ChatColor.GOLD);
        fox.setBold(true);
        TextComponent br2 = new TextComponent("] ");
        br2.setColor(ChatColor.DARK_GRAY);
        TextComponent mid = new TextComponent("С игрока ");
        mid.setColor(ChatColor.GREEN);
        TextComponent nameC = new TextComponent(nick);
        nameC.setColor(ChatColor.WHITE);
        TextComponent end = new TextComponent(" было снято наказание.");
        end.setColor(ChatColor.GREEN);
        line.addExtra(br1);
        line.addExtra(fox);
        line.addExtra(br2);
        line.addExtra(mid);
        line.addExtra(nameC);
        line.addExtra(end);
        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
        for (ProxiedPlayer pl : ProxyServer.getInstance().getPlayers()) {
            pl.sendMessage(line);
        }
    }

    private void broadcastProxyPunish(String targetNick, ProxyPunishmentRepository.PunishmentRecord rec) {
        String nick = targetNick == null || targetNick.isBlank() ? "—" : targetNick;
        String actor = rec.actorName() == null || rec.actorName().isBlank() ? "Администрация" : rec.actorName();
        String duration = humanDuration(rec.expiresAt());
        String type = rec.type() == null ? "" : rec.type().toUpperCase(Locale.ROOT);
        String status = switch (type) {
            case "MUTE", "TEMPMUTE" -> "Был замучен";
            case "BAN", "TEMPBAN" -> "Был заблокирован";
            case "FREEZE" -> "Был заморожен";
            default -> "Выдано наказание";
        };
        String reason = rec.reasonTitle() == null || rec.reasonTitle().isBlank() ? "—" : rec.reasonTitle();

        String hoverLegacy = "&e" + status + "\n"
            + "&8Тип: &6" + type + "\n"
            + "&8Кем выдано: &b" + actor + "\n"
            + "&8По причине: &7" + reason + "\n"
            + "&8Срок: &e" + duration;
        broadcastNetAnnounce("&8[&6&lFOXARIA&8] &7Игрок &f" + nick + "&7 был наказан.", hoverLegacy);
    }

    private String proxyWasStatusLine(List<ProxyPunishmentRepository.PunishmentRecord> removed) {
        boolean mute = removed.stream().anyMatch(r -> {
            String t = r.type();
            return t != null && (t.equalsIgnoreCase("MUTE") || t.equalsIgnoreCase("TEMPMUTE"));
        });
        boolean ban = removed.stream().anyMatch(r -> {
            String t = r.type();
            return t != null && (t.equalsIgnoreCase("BAN") || t.equalsIgnoreCase("TEMPBAN"));
        });
        boolean freeze = removed.stream().anyMatch(r -> "FREEZE".equalsIgnoreCase(r.type()));
        List<String> parts = new ArrayList<>();
        if (mute) {
            parts.add("замучен");
        }
        if (ban) {
            parts.add("заблокирован");
        }
        if (freeze) {
            parts.add("заморожен");
        }
        if (parts.isEmpty()) {
            return "Снято наказание";
        }
        if (parts.size() == 1) {
            return "Был " + parts.get(0);
        }
        if (parts.size() == 2) {
            return "Был " + parts.get(0) + " и " + parts.get(1);
        }
        StringBuilder sb = new StringBuilder("Был ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(i == parts.size() - 1 ? " и " : ", ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private void kickNonAdminsFromLockedServer(String serverName) {
        ServerInfo lobby = getProxy().getServerInfo(lobbyServer);
        if (lobby == null) {
            return;
        }
        for (ProxiedPlayer pl : getProxy().getPlayers()) {
            if (pl.getServer() == null) {
                continue;
            }
            if (!pl.getServer().getInfo().getName().equalsIgnoreCase(serverName)) {
                continue;
            }
            if (isProxyAdmin(pl)) {
                continue;
            }
            send(pl, msg("messages.network.server-wipe-kicked",
                "&cАдминистрация перевела сервер &f%name% &cна вайп. Переносим в лобби...")
                .replace("%name%", serverName));
            pl.connect(lobby);
        }
    }

    private boolean isProxyAdmin(ProxiedPlayer player) {
        if (player.hasPermission("foxaria.proxy.admin")
            || player.hasPermission("foxaria.admin.wipe")
            || player.hasPermission("foxaria.admin")
            || player.hasPermission("foxaria.admin.panel")) {
            return true;
        }
        String group = privilegeRepository.resolveActiveGroup(player.getUniqueId(), defaultGroup, System.currentTimeMillis());
        return "admin".equalsIgnoreCase(group);
    }

    private void broadcastWipeLockUpdate(String serverName, boolean locked) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("WipeLockUpdate");
            out.writeUTF(serverName);
            out.writeBoolean(locked);
            out.close();
            byte[] data = baos.toByteArray();
            Set<String> sentServers = new java.util.HashSet<>();
            for (ProxiedPlayer pl : getProxy().getPlayers()) {
                if (pl.getServer() != null) {
                    String sn = pl.getServer().getInfo().getName();
                    if (sentServers.add(sn)) {
                        pl.getServer().sendData("foxaria:proxy", data);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void sendWipeSyncAllTo(ProxiedPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF("WipeLockSyncAll");
            List<String> lockedList = new ArrayList<>(wipeLocked);
            out.writeInt(lockedList.size());
            for (String s : lockedList) {
                out.writeUTF(s);
            }
            out.close();
            player.getServer().sendData("foxaria:proxy", baos.toByteArray());
        } catch (IOException ignored) {
        }
    }

    private final class WipeProxyCommand extends Command {
        WipeProxyCommand() {
            super("wipe", null);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer player && !isProxyAdmin(player)) {
                send(sender, "&cНедостаточно прав.");
                return;
            }
            if (args.length >= 1) {
                String serverName = args[0];
                boolean nowLocked;
                if (wipeLocked.remove(serverName)) {
                    nowLocked = false;
                } else {
                    wipeLocked.add(serverName);
                    nowLocked = true;
                }
                broadcastWipeLockUpdate(serverName, nowLocked);
                if (nowLocked) {
                    kickNonAdminsFromLockedServer(serverName);
                }
                send(sender, nowLocked
                    ? "&cСервер &f" + serverName + " &cзаблокирован &8(вайп)&c."
                    : "&aСервер &f" + serverName + " &aразблокирован.");
                return;
            }
            send(sender, "&4&lВайп &8\u00bb &cУправление серверами");
            send(sender, "&7Использование: &e/wipe <сервер> &7\u2014 переключить блокировку");
            for (Map.Entry<String, ServerInfo> entry : getProxy().getServers().entrySet()) {
                String name = entry.getKey();
                if (name.equalsIgnoreCase(authServer)) {
                    continue;
                }
                boolean locked = wipeLocked.contains(name);
                send(sender, (locked ? "&c[\u2718] &f" : "&a[\u2714] &f") + name
                    + " &8\u2192 &7/wipe " + name);
            }
        }
    }

    private void connectTo(ProxiedPlayer player, String serverName) {
        ServerInfo target = ProxyServer.getInstance().getServerInfo(serverName);
        if (target == null) {
            send(player, "&cСервер " + serverName + " не найден на прокси.");
            return;
        }
        player.connect(target);
    }

    private void connectToIfNeeded(ProxiedPlayer player, String serverName) {
        if (player.getServer() != null && player.getServer().getInfo().getName().equalsIgnoreCase(serverName)) {
            return;
        }
        int attempts = Math.max(1, cfg.getInt("network.connect-retries", 4));
        long delayMs = Math.max(250L, cfg.getLong("network.connect-retry-delay-ms", 900L));
        connectWithRetries(player, serverName, attempts, delayMs, true);
    }

    private void connectWithRetries(ProxiedPlayer player, String serverName, int attempts, long delayMs, boolean showFirstMessage) {
        if (player == null || serverName == null || serverName.isBlank()) {
            return;
        }
        if (player.getServer() != null && player.getServer().getInfo().getName().equalsIgnoreCase(serverName)) {
            pendingConnect.remove(player.getUniqueId());
            return;
        }
        ConnectRetry existing = pendingConnect.get(player.getUniqueId());
        if (existing != null && existing.targetServer.equalsIgnoreCase(serverName)) {
            // Already attempting the same target; avoid Bungee spam "Already connecting to this server!".
            return;
        }
        ServerInfo target = ProxyServer.getInstance().getServerInfo(serverName);
        if (target == null) {
            pendingConnect.remove(player.getUniqueId());
            send(player, msg("messages.network.server-missing", "&cСервер %server% не найден на прокси.")
                .replace("%server%", serverName));
            return;
        }
        ConnectRetry prev = pendingConnect.put(player.getUniqueId(), new ConnectRetry(serverName, attempts, System.currentTimeMillis()));
        if (showFirstMessage && (prev == null || !prev.targetServer.equalsIgnoreCase(serverName))) {
            send(player, msg("messages.network.connecting", "&eПодключаемся к &f%server%&e...")
                .replace("%server%", serverName));
        }
        player.connect(target, (result, error) -> {
            if (result) {
                return; // onServerConnected will confirm and clear
            }
            ConnectRetry state = pendingConnect.get(player.getUniqueId());
            if (state == null || !state.targetServer.equalsIgnoreCase(serverName)) {
                return;
            }
            String err = error == null ? "" : error.toString().toLowerCase(Locale.ROOT);
            if (err.contains("already connecting")) {
                // Do not decrement attempts for internal duplicate-connect race.
                getProxy().getScheduler().schedule(this, () -> {
                    ProxiedPlayer p = getProxy().getPlayer(player.getUniqueId());
                    if (p != null) {
                        pendingConnect.remove(player.getUniqueId());
                        connectWithRetries(p, serverName, state.leftAttempts, delayMs, false);
                    }
                }, Math.max(200L, delayMs / 2L), TimeUnit.MILLISECONDS);
                return;
            }
            int left = state.leftAttempts - 1;
            if (left <= 0) {
                pendingConnect.remove(player.getUniqueId());
                send(player, msg("messages.network.connect-failed", "&cНе удалось подключиться к &f%server%&c. Попробуйте позже.")
                    .replace("%server%", serverName));
                return;
            }
            pendingConnect.put(player.getUniqueId(), new ConnectRetry(serverName, left, state.startedAtMs));
            send(player, msg("messages.network.connect-retry", "&eПовтор подключения к &f%server%&e... (&f%left%&e)")
                .replace("%server%", serverName)
                .replace("%left%", String.valueOf(left)));
            getProxy().getScheduler().schedule(this, () -> {
                ProxiedPlayer p = getProxy().getPlayer(player.getUniqueId());
                if (p == null) {
                    pendingConnect.remove(player.getUniqueId());
                    return;
                }
                connectWithRetries(p, serverName, left, delayMs, false);
            }, delayMs, TimeUnit.MILLISECONDS);
        });
    }

    private record ConnectRetry(String targetServer, int leftAttempts, long startedAtMs) {}

    private void sendAuthPrompt(ProxiedPlayer player, boolean hasAccount) {
        if (hasAccount) {
            send(player, msg("auth.messages.need-login", "%brand% &8» &fВойди: &#FFAA55/login &f<пароль> &7(кратко: &#FFAA55/l&7)"));
            sendTitle(player,
                msg("auth.titles.need-login-title", "%brand%"),
                msg("auth.titles.need-login-subtitle", "&#FFAA55/login &7<пароль>  &7или  &#FFAA55/l")
            );
        } else {
            send(player, msg("auth.messages.need-register", "%brand% &8» &fРегистрация: &#FFAA55/register &f<пароль> <повтор>"));
            sendTitle(player,
                msg("auth.titles.need-register-title", "%brand%"),
                msg("auth.titles.need-register-subtitle", "&#FFAA55/register &7<пароль> <повтор>  &7или  &#FFAA55/r")
            );
        }
    }

    private void sendSuccessTitle(ProxiedPlayer player) {
        sendTitle(player,
            msg("auth.titles.success-title", "%brand_ok%"),
            msg("auth.titles.success-subtitle", "&fДобро пожаловать!")
        );
    }

    private boolean consumeAuthAttempt(ProxiedPlayer player) {
        String ip = safeIp(player);
        if (ip == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        return consumeWindow(authAttemptsByIp, ip, now, loginAttemptWindowMs, maxLoginAttemptsPerWindow);
    }

    private boolean consumeWindow(Map<String, Deque<Long>> bucket, String key, long now, long windowMs, int maxHits) {
        Deque<Long> q = bucket.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        while (true) {
            Long head = q.peekFirst();
            if (head == null || now - head <= windowMs) {
                break;
            }
            q.pollFirst();
        }
        Long last = q.peekLast();
        if (last != null && now - last < loginCommandCooldownMs) {
            return false;
        }
        if (q.size() >= maxHits) {
            return false;
        }
        q.addLast(now);
        return true;
    }

    private String safeIp(PendingConnection connection) {
        try {
            return connection.getAddress() == null ? null : connection.getAddress().getAddress().getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeIp(ProxiedPlayer player) {
        try {
            return player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveDefaultConfig() {
        try {
            if (!getDataFolder().exists()) {
                Files.createDirectories(getDataFolder().toPath());
            }
            File f = new File(getDataFolder(), "config.yml");
            if (!f.exists()) {
                try (var in = getResourceAsStream("config.yml")) {
                    Files.copy(in, f.toPath());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create default config.yml", e);
        }
    }

    private Configuration loadConfig() {
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class)
                .load(new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load config.yml", e);
        }
    }

    private String msg(String path, String def) {
        return cfg.getString(path, def);
    }

    private void send(CommandSender sender, String legacy) {
        sender.sendMessage(TextComponent.fromLegacy(ProxyColorUtil.colorize(expandAuthBrand(legacy))));
    }

    private void drainServerQueue(String serverName, int cap) {
        ServerInfo target = ProxyServer.getInstance().getServerInfo(serverName);
        if (target == null) {
            return;
        }
        Queue<UUID> q = serverConnectQueues.get(serverName);
        if (q == null || q.isEmpty()) {
            return;
        }
        while (target.getPlayers().size() < cap) {
            UUID next = q.poll();
            if (next == null) {
                break;
            }
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(next);
            if (p == null) {
                continue;
            }
            send(p, msg("messages.network.server-queue-advance", "&aОчередь: подключаем к &f%name%&a...")
                .replace("%name%", serverName));
            p.connect(target);
            break;
        }
    }

    private String expandAuthBrand(String raw) {
        return replaceBrand(raw == null ? "" : raw);
    }

    private String replaceBrand(String s) {
        String brandWord = cfg.getString("tab.brand-word", "FOXARIA");
        String brand = ProxyColorUtil.orangeGradient(brandWord);
        String ok = ProxyColorUtil.orangeGradientNoBold(cfg.getString("auth.titles.success-brand-word", "Готово"));
        return s.replace("%brand%", brand).replace("%brand_ok%", ok);
    }

    private void startTabTask() {
        if (!cfg.getBoolean("tab.enabled", true)) {
            return;
        }
        long period = Math.max(20L, cfg.getLong("tab.update-period-ms", 2000L));
        tabTask = getProxy().getScheduler().schedule(this, () -> {
            for (ProxiedPlayer player : getProxy().getPlayers()) {
                refreshPlayerDisplayName(player);
                updateTab(player);
            }
        }, 1000L, period, TimeUnit.MILLISECONDS);
    }

    private void updateTab(ProxiedPlayer player) {
        String server = player.getServer() == null ? "" : player.getServer().getInfo().getName();
        String mode = cfg.getString("tab.server-modes." + server, server.isBlank() ? "UNKNOWN" : server.toUpperCase(Locale.ROOT));
        String group = privilegeRepository.resolveActiveGroup(player.getUniqueId(), defaultGroup, System.currentTimeMillis());
        String prefixLine = cfg.getString("privileges.groups." + group + ".prefix",
            cfg.getString("privileges.groups." + defaultGroup + ".prefix", "&7Игрок &8| &f"));
        String rankColored = ProxyColorUtil.stripRankFromPrefix(prefixLine);

        String brandLine = ProxyColorUtil.orangeGradient(cfg.getString("tab.brand-word", "FOXARIA"));
        String commandsSuffix = tabCommandsBlock(server);
        String header = msg("tab.header", "%brand%\n&7Онлайн: &f%online% &8| &7РЕЖИМ: &#FFAA55%mode%%commands%");
        String footer = msg("tab.footer", "&7Привилегия: %rank_colored%");
        String online = String.valueOf(getProxy().getOnlineCount());

        header = replaceTabPlaceholders(header, online, mode, rankColored, brandLine, commandsSuffix);
        footer = replaceTabPlaceholders(footer, online, mode, rankColored, brandLine, commandsSuffix);
        player.setTabHeader(
            TextComponent.fromLegacy(ProxyColorUtil.colorize(header)),
            TextComponent.fromLegacy(ProxyColorUtil.colorize(footer))
        );
    }

    private String replaceTabPlaceholders(String s, String online, String mode, String rankColored, String brandLine, String commandsSuffix) {
        return s
            .replace("%online%", online)
            .replace("%mode%", mode)
            .replace("%rank_colored%", rankColored)
            .replace("%brand%", brandLine)
            .replace("%commands%", commandsSuffix == null ? "" : commandsSuffix);
    }

    /**
     * Подсказки команд для шапки таба: {@code tab.command-hints.<имя_сервера>} (строка или список строк).
     * Плейсхолдер {@code %commands%}: пусто или {@code \n} + строки из конфига.
     */
    private String tabCommandsBlock(String serverKey) {
        String hints = resolveTabCommandHints(serverKey);
        if (hints == null || hints.isBlank()) {
            return "";
        }
        return "\n" + hints;
    }

    private String resolveTabCommandHints(String serverKey) {
        if (serverKey == null || serverKey.isBlank()) {
            return cfg.getString("tab.command-hints._default", "");
        }
        String path = "tab.command-hints." + serverKey;
        List<String> lines = cfg.getStringList(path);
        if (lines != null && !lines.isEmpty()) {
            return String.join("\n", lines);
        }
        String single = cfg.getString(path, null);
        if (single != null && !single.isBlank()) {
            return single;
        }
        return cfg.getString("tab.command-hints._default", "");
    }

    private void sendTitle(ProxiedPlayer player, String title, String subtitle) {
        if (!cfg.getBoolean("auth.titles.enabled", true)) {
            return;
        }
        String t = ProxyColorUtil.colorize(expandAuthBrand(title));
        String sub = ProxyColorUtil.colorize(expandAuthBrand(subtitle));
        Title titleObj = ProxyServer.getInstance().createTitle()
            .title(TextComponent.fromLegacy(t))
            .subTitle(TextComponent.fromLegacy(sub))
            .fadeIn(10)
            .stay(45)
            .fadeOut(10);
        player.sendTitle(titleObj);
    }
}
