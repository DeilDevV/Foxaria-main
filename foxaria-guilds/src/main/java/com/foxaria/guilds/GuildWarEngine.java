package com.foxaria.guilds;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.foxaria.guilds.GuildModels.GuildMemberRecord;
import static com.foxaria.guilds.GuildModels.GuildRecord;

public final class GuildWarEngine {

    private final JavaPlugin plugin;
    private final GuildRepository repository;
    private final MessageService messages;
    private final GuildService guilds;
    private final long durationMillis;
    private final Map<Integer, Set<String>> queuesBySize = new ConcurrentHashMap<>();
    private final Map<String, WarInvite> invitesByTargetGuild = new ConcurrentHashMap<>();
    private final Map<String, ActiveWar> activeWars = new ConcurrentHashMap<>();
    private final Map<UUID, String> participantWar = new ConcurrentHashMap<>();
    private final Map<String, ArenaReservation> arenaReservations = new ConcurrentHashMap<>();

    public GuildWarEngine(JavaPlugin plugin, GuildRepository repository, MessageService messages, GuildService guilds, long durationMillis) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
        this.guilds = guilds;
        this.durationMillis = durationMillis;
    }

    public void queue(Player initiator, int size, String arenaId) {
        cleanupExpiredReservations();
        if (size < 2 || size > 10) {
            messages.send(initiator, "guild.war-size-invalid", "&cРазмер войны должен быть 2-10.");
            return;
        }
        guilds.guildOf(initiator.getUniqueId()).thenAccept(optGuild -> {
            if (optGuild.isEmpty()) {
                messages.send(initiator, "guild.not-in-guild", "&cВы не в гильдии.");
                return;
            }
            GuildRecord guild = optGuild.get();
            if (isGuildBusy(guild.id())) {
                messages.send(initiator, "guild.war-busy", "&cГильдия уже в бою/очереди.");
                return;
            }
            queuesBySize.computeIfAbsent(size, k -> ConcurrentHashMap.newKeySet()).add(guild.id());
            repository.appendActivity(guild.id(), initiator.getName(), "WAR_QUEUE_JOIN", String.valueOf(size));
            messages.send(initiator, "guild.war-queue-joined", "&aГильдия добавлена в очередь <size>v<size>.",
                new MessageService.Placeholder("size", String.valueOf(size)));
            tryMatch(size, arenaId);
        });
    }

    public void leaveQueue(Player actor) {
        guilds.guildOf(actor.getUniqueId()).thenAccept(optGuild -> {
            if (optGuild.isEmpty()) {
                return;
            }
            String guildId = optGuild.get().id();
            for (Set<String> set : queuesBySize.values()) {
                set.remove(guildId);
            }
            messages.send(actor, "guild.war-queue-left", "&eГильдия покинула очередь войны.");
        });
    }

    public void invite(Player initiator, String targetGuildName, int size, String arenaId) {
        cleanupExpiredReservations();
        if (size < 2 || size > 10) {
            messages.send(initiator, "guild.war-size-invalid", "&cРазмер войны должен быть 2-10.");
            return;
        }
        guilds.guildOf(initiator.getUniqueId()).thenCompose(optMine -> {
            if (optMine.isEmpty()) {
                messages.send(initiator, "guild.not-in-guild", "&cВы не в гильдии.");
                return CompletableFuture.completedFuture(null);
            }
            GuildRecord mine = optMine.get();
            return repository.byName(targetGuildName).thenAccept(optTarget -> {
                if (optTarget.isEmpty()) {
                    messages.send(initiator, "guild.war-target-missing", "&cГильдия не найдена.");
                    return;
                }
                GuildRecord target = optTarget.get();
                if (mine.id().equals(target.id())) {
                    messages.send(initiator, "guild.war-self", "&cНельзя вызвать свою гильдию.");
                    return;
                }
                if (isGuildBusy(mine.id()) || isGuildBusy(target.id())) {
                    messages.send(initiator, "guild.war-busy", "&cОдна из гильдий уже занята.");
                    return;
                }
                String resolvedArena = resolveArenaForInvite(arenaId);
                if (resolvedArena == null) {
                    messages.send(initiator, "guild.war-no-arena", "&cНет доступных арен для вызова.");
                    return;
                }
                long expiresAt = System.currentTimeMillis() + 180_000L;
                if (!reserveArena(resolvedArena, mine.id(), target.id(), expiresAt)) {
                    messages.send(initiator, "guild.war-arena-busy", "&cЭта арена сейчас недоступна.");
                    return;
                }
                invitesByTargetGuild.put(target.id(), new WarInvite(mine.id(), target.id(), size, resolvedArena, expiresAt));
                repository.appendActivity(mine.id(), initiator.getName(), "WAR_INVITE_SENT", target.name() + ":" + size);
                messages.send(initiator, "guild.war-invite-sent", "&aПриглашение на войну отправлено гильдии <guild>.",
                    new MessageService.Placeholder("guild", target.name()));
                notifyGuild(target.id(), "&6[GuildWar] &eВаша гильдия получила вызов от &6" + mine.name() + "&e. Откройте GUI -> ВОЙНЫ -> Входящие вызовы.");
            });
        });
    }

    public void accept(Player actor, String challengerName) {
        guilds.guildOf(actor.getUniqueId()).thenCompose(optMine -> {
            if (optMine.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            GuildRecord mine = optMine.get();
            return repository.byName(challengerName).thenCompose(optChallenger -> {
                if (optChallenger.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                GuildRecord challenger = optChallenger.get();
                WarInvite invite = invitesByTargetGuild.get(mine.id());
                if (invite == null || !invite.challengerGuildId.equals(challenger.id()) || invite.expiresAt < System.currentTimeMillis()) {
                    messages.send(actor, "guild.war-invite-expired", "&cПриглашение на войну не найдено или истекло.");
                    return CompletableFuture.completedFuture(null);
                }
                invitesByTargetGuild.remove(mine.id());
                return startWar(challenger.id(), mine.id(), invite.teamSize, invite.arenaId);
            });
        });
    }

    public void acceptByGuildId(Player actor, String challengerGuildId) {
        cleanupExpiredReservations();
        guilds.guildOf(actor.getUniqueId()).thenAccept(optMine -> {
            if (optMine.isEmpty()) {
                return;
            }
            GuildRecord mine = optMine.get();
            WarInvite invite = invitesByTargetGuild.get(mine.id());
            if (invite == null || invite.expiresAt < System.currentTimeMillis() || !invite.challengerGuildId.equals(challengerGuildId)) {
                messages.send(actor, "guild.war-invite-expired", "&cПриглашение на войну не найдено или истекло.");
                return;
            }
            invitesByTargetGuild.remove(mine.id());
            startWar(invite.challengerGuildId, mine.id(), invite.teamSize, invite.arenaId);
        });
    }

    public void deny(Player actor, String challengerName) {
        guilds.guildOf(actor.getUniqueId()).thenCompose(optMine -> {
            if (optMine.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            GuildRecord mine = optMine.get();
            return repository.byName(challengerName).thenAccept(optChallenger -> {
                if (optChallenger.isEmpty()) {
                    return;
                }
                GuildRecord challenger = optChallenger.get();
                WarInvite invite = invitesByTargetGuild.get(mine.id());
                if (invite != null && invite.challengerGuildId.equals(challenger.id())) {
                    invitesByTargetGuild.remove(mine.id());
                    messages.send(actor, "guild.war-invite-denied", "&eВызов отклонен.");
                    notifyGuild(challenger.id(), "&c[GuildWar] Вызов отклонен гильдией " + mine.name());
                }
            });
        });
    }

    public void denyByGuildId(Player actor, String challengerGuildId) {
        cleanupExpiredReservations();
        guilds.guildOf(actor.getUniqueId()).thenAccept(optMine -> {
            if (optMine.isEmpty()) {
                return;
            }
            GuildRecord mine = optMine.get();
            WarInvite invite = invitesByTargetGuild.get(mine.id());
            if (invite != null && invite.challengerGuildId.equals(challengerGuildId)) {
                invitesByTargetGuild.remove(mine.id());
                releaseArena(invite.arenaId);
                messages.send(actor, "guild.war-invite-denied", "&eВызов отклонен.");
                notifyGuild(challengerGuildId, "&c[GuildWar] Вызов отклонен гильдией " + mine.name());
            }
        });
    }

    public void recordKill(Player killer, Player victim) {
        String warId = participantWar.get(killer.getUniqueId());
        if (warId == null || !warId.equals(participantWar.get(victim.getUniqueId()))) {
            return;
        }
        ActiveWar war = activeWars.get(warId);
        if (war == null) {
            return;
        }
        if (war.guildAPlayers.contains(killer.getUniqueId())) {
            war.scoreA++;
        } else if (war.guildBPlayers.contains(killer.getUniqueId())) {
            war.scoreB++;
        }
        updateBar(war);
        int targetScore = war.teamSize * 2;
        if (war.scoreA >= targetScore || war.scoreB >= targetScore) {
            finishWar(warId, war.scoreA > war.scoreB ? war.guildA : war.guildB, "kill_target");
        }
    }

    public void handlePlayerQuit(Player player) {
        String warId = participantWar.get(player.getUniqueId());
        if (warId == null) {
            return;
        }
        ActiveWar war = activeWars.get(warId);
        if (war == null) {
            return;
        }
        if (war.guildAPlayers.remove(player.getUniqueId())) {
            war.scoreB++;
        } else if (war.guildBPlayers.remove(player.getUniqueId())) {
            war.scoreA++;
        }
        updateBar(war);
    }

    public void createArena(String id, String displayName) {
        repository.upsertArena(id, displayName, "world", "world;0;80;0;0;0", "world;10;80;0;180;0", true);
    }

    public void setArenaSpawn(String id, boolean aSide, Location location) {
        repository.updateArenaSpawn(id, aSide, location.getWorld().getName(), serialize(location));
    }

    public void setArenaEnabled(String id, boolean enabled) {
        repository.setArenaEnabled(id, enabled);
    }

    public CompletableFuture<List<GuildRepository.WarArenaRecord>> arenas() {
        return repository.listArenas(false);
    }

    private void tryMatch(int size, String preferredArenaId) {
        cleanupExpiredReservations();
        Set<String> queue = queuesBySize.getOrDefault(size, Set.of());
        if (queue.size() < 2) {
            return;
        }
        List<String> guilds = new ArrayList<>(queue);
        guilds.sort(Comparator.naturalOrder());
        String a = guilds.get(0);
        String b = guilds.get(1);
        queue.remove(a);
        queue.remove(b);
        startWar(a, b, size, preferredArenaId);
    }

    private CompletableFuture<Void> startWar(String guildA, String guildB, int size, String preferredArenaId) {
        cleanupExpiredReservations();
        return availableArenas().thenCompose(arenas -> {
            if (arenas.isEmpty()) {
                notifyGuild(guildA, "&c[GuildWar] Нет доступных арен.");
                notifyGuild(guildB, "&c[GuildWar] Нет доступных арен.");
                return CompletableFuture.completedFuture(null);
            }
            GuildRepository.WarArenaRecord arena = arenas.stream()
                .filter(a -> preferredArenaId != null && !preferredArenaId.isBlank() && a.arenaId().equalsIgnoreCase(preferredArenaId))
                .findFirst()
                .orElse(arenas.get(0));
            return repository.members(guildA).thenCombine(repository.members(guildB), (aMembers, bMembers) ->
                prepareParticipants(aMembers, bMembers, size)).thenAccept(selection -> {
                if (selection == null) {
                    notifyGuild(guildA, "&c[GuildWar] Недостаточно онлайн-участников.");
                    notifyGuild(guildB, "&c[GuildWar] Недостаточно онлайн-участников.");
                    return;
                }
                String warId = UUID.randomUUID().toString();
                ActiveWar war = new ActiveWar(warId, guildA, guildB, size, selection.aPlayers, selection.bPlayers, arena, System.currentTimeMillis(), System.currentTimeMillis() + durationMillis);
                activeWars.put(warId, war);
                arenaReservations.put(arena.arenaId(), new ArenaReservation(arena.arenaId(), guildA, guildB, Long.MAX_VALUE));
                for (UUID uuid : war.guildAPlayers) {
                    participantWar.put(uuid, warId);
                }
                for (UUID uuid : war.guildBPlayers) {
                    participantWar.put(uuid, warId);
                }
                teleportToArena(war);
                createBar(war);
                startTick(war);
                notifyGuild(guildA, "&6[GuildWar] Бой начался на арене " + arena.displayName());
                notifyGuild(guildB, "&6[GuildWar] Бой начался на арене " + arena.displayName());
                repository.appendActivity(guildA, "SYSTEM", "WAR_START", "vs=" + guildB + ",size=" + size + ",arena=" + arena.arenaId());
                repository.appendActivity(guildB, "SYSTEM", "WAR_START", "vs=" + guildA + ",size=" + size + ",arena=" + arena.arenaId());
            });
        });
    }

    private ParticipantSelection prepareParticipants(List<GuildMemberRecord> aMembers, List<GuildMemberRecord> bMembers, int size) {
        List<UUID> a = onlinePlayers(aMembers);
        List<UUID> b = onlinePlayers(bMembers);
        if (a.size() < size || b.size() < size) {
            return null;
        }
        return new ParticipantSelection(new HashSet<>(a.subList(0, size)), new HashSet<>(b.subList(0, size)));
    }

    private List<UUID> onlinePlayers(List<GuildMemberRecord> members) {
        List<UUID> result = new ArrayList<>();
        for (GuildMemberRecord member : members) {
            Player p = Bukkit.getPlayer(member.playerUuid());
            if (p != null && p.isOnline()) {
                result.add(p.getUniqueId());
            }
        }
        return result;
    }

    private void teleportToArena(ActiveWar war) {
        Location a = deserialize(war.arena.spawnA());
        Location b = deserialize(war.arena.spawnB());
        for (UUID uuid : war.guildAPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(a);
            }
        }
        for (UUID uuid : war.guildBPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(b);
            }
        }
    }

    private void createBar(ActiveWar war) {
        war.bar = BossBar.bossBar(FoxariaText.plain("Гильдейская война"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        for (UUID uuid : war.guildAPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showBossBar(war.bar);
        }
        for (UUID uuid : war.guildBPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showBossBar(war.bar);
        }
        updateBar(war);
    }

    private void startTick(ActiveWar war) {
        war.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long left = war.endsAt - System.currentTimeMillis();
            if (left <= 0) {
                String winner = war.scoreA >= war.scoreB ? war.guildA : war.guildB;
                finishWar(war.id, winner, "timer");
                return;
            }
            updateBar(war);
        }, 20L, 20L);
    }

    private void updateBar(ActiveWar war) {
        long total = durationMillis;
        long left = Math.max(0L, war.endsAt - System.currentTimeMillis());
        float progress = Math.max(0.0f, Math.min(1.0f, (float) left / (float) total));
        war.bar.progress(progress);
        String timer = formatDuration(left);
        war.bar.name(FoxariaText.plain("Война " + war.teamSize + "×" + war.teamSize + " | " + timer + " | " + war.scoreA + ":" + war.scoreB));
    }

    private void finishWar(String warId, String winnerGuildId, String reason) {
        ActiveWar war = activeWars.remove(warId);
        if (war == null) {
            return;
        }
        releaseArena(war.arena.arenaId());
        if (war.task != null) {
            war.task.cancel();
        }
        for (UUID uuid : war.guildAPlayers) participantWar.remove(uuid);
        for (UUID uuid : war.guildBPlayers) participantWar.remove(uuid);
        for (UUID uuid : war.guildAPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.hideBossBar(war.bar);
                p.teleport(p.getWorld().getSpawnLocation());
            }
        }
        for (UUID uuid : war.guildBPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.hideBossBar(war.bar);
                p.teleport(p.getWorld().getSpawnLocation());
            }
        }
        repository.addCoinsAndPoints(winnerGuildId, 250, 200);
        notifyGuild(war.guildA, "&6[GuildWar] Бой завершен (" + reason + "). Победитель: " + guildName(winnerGuildId));
        notifyGuild(war.guildB, "&6[GuildWar] Бой завершен (" + reason + "). Победитель: " + guildName(winnerGuildId));
        repository.appendActivity(war.guildA, "SYSTEM", "WAR_END", reason + ":" + winnerGuildId);
        repository.appendActivity(war.guildB, "SYSTEM", "WAR_END", reason + ":" + winnerGuildId);
    }

    private void notifyGuild(String guildId, String message) {
        repository.members(guildId).thenAccept(members -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (GuildMemberRecord member : members) {
                Player p = Bukkit.getPlayer(member.playerUuid());
                if (p != null) {
                    p.sendMessage(message);
                }
            }
        }));
    }

    private boolean isGuildBusy(String guildId) {
        cleanupExpiredReservations();
        for (ActiveWar war : activeWars.values()) {
            if (war.guildA.equals(guildId) || war.guildB.equals(guildId)) {
                return true;
            }
        }
        for (Set<String> queue : queuesBySize.values()) {
            if (queue.contains(guildId)) {
                return true;
            }
        }
        return invitesByTargetGuild.containsKey(guildId) || invitesByTargetGuild.values().stream().anyMatch(inv -> inv.challengerGuildId.equals(guildId));
    }

    public Optional<IncomingInvite> incomingInvite(String targetGuildId) {
        cleanupExpiredReservations();
        WarInvite invite = invitesByTargetGuild.get(targetGuildId);
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(new IncomingInvite(invite.challengerGuildId, invite.teamSize, invite.arenaId));
    }

    public boolean isQueued(String guildId) {
        cleanupExpiredReservations();
        for (Set<String> queue : queuesBySize.values()) {
            if (queue.contains(guildId)) {
                return true;
            }
        }
        return false;
    }

    public CompletableFuture<List<GuildRepository.WarArenaRecord>> availableArenas() {
        cleanupExpiredReservations();
        return repository.listArenas(true).thenApply(arenas -> arenas.stream()
            .filter(a -> !arenaReservations.containsKey(a.arenaId()))
            .toList());
    }

    private String resolveArenaForInvite(String preferredArena) {
        if (preferredArena != null && !preferredArena.isBlank()) {
            return arenaReservations.containsKey(preferredArena) ? null : preferredArena;
        }
        List<GuildRepository.WarArenaRecord> free = availableArenas().join();
        return free.isEmpty() ? null : free.getFirst().arenaId();
    }

    private boolean reserveArena(String arenaId, String guildA, String guildB, long expiresAt) {
        cleanupExpiredReservations();
        return arenaReservations.putIfAbsent(arenaId, new ArenaReservation(arenaId, guildA, guildB, expiresAt)) == null;
    }

    private void releaseArena(String arenaId) {
        if (arenaId != null) {
            arenaReservations.remove(arenaId);
        }
    }

    private void cleanupExpiredReservations() {
        long now = System.currentTimeMillis();
        invitesByTargetGuild.entrySet().removeIf(entry -> {
            WarInvite invite = entry.getValue();
            if (invite.expiresAt < now) {
                releaseArena(invite.arenaId);
                return true;
            }
            return false;
        });
        arenaReservations.entrySet().removeIf(entry -> {
            ArenaReservation reservation = entry.getValue();
            return reservation.expiresAt != Long.MAX_VALUE && reservation.expiresAt < now;
        });
    }

    private String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long min = d.toMinutes();
        long sec = d.minusMinutes(min).toSeconds();
        return String.format("%02d:%02d", min, sec);
    }

    private String serialize(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    private Location deserialize(String encoded) {
        String[] parts = encoded.split(";");
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
            Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }

    private String guildName(String guildId) {
        Optional<GuildRecord> record = repository.byId(guildId).join();
        return record.map(GuildRecord::name).orElse(guildId);
    }

    private static final class WarInvite {
        private final String challengerGuildId;
        private final String targetGuildId;
        private final int teamSize;
        private final String arenaId;
        private final long expiresAt;

        private WarInvite(String challengerGuildId, String targetGuildId, int teamSize, String arenaId, long expiresAt) {
            this.challengerGuildId = challengerGuildId;
            this.targetGuildId = targetGuildId;
            this.teamSize = teamSize;
            this.arenaId = arenaId;
            this.expiresAt = expiresAt;
        }
    }

    private static final class ArenaReservation {
        private final String arenaId;
        private final String guildA;
        private final String guildB;
        private final long expiresAt;

        private ArenaReservation(String arenaId, String guildA, String guildB, long expiresAt) {
            this.arenaId = arenaId;
            this.guildA = guildA;
            this.guildB = guildB;
            this.expiresAt = expiresAt;
        }
    }

    public record IncomingInvite(String challengerGuildId, int teamSize, String arenaId) {}

    private static final class ParticipantSelection {
        private final Set<UUID> aPlayers;
        private final Set<UUID> bPlayers;

        private ParticipantSelection(Set<UUID> aPlayers, Set<UUID> bPlayers) {
            this.aPlayers = aPlayers;
            this.bPlayers = bPlayers;
        }
    }

    private static final class ActiveWar {
        private final String id;
        private final String guildA;
        private final String guildB;
        private final int teamSize;
        private final Set<UUID> guildAPlayers;
        private final Set<UUID> guildBPlayers;
        private final GuildRepository.WarArenaRecord arena;
        private final long startedAt;
        private final long endsAt;
        private int scoreA;
        private int scoreB;
        private BossBar bar;
        private BukkitTask task;

        private ActiveWar(String id, String guildA, String guildB, int teamSize, Set<UUID> guildAPlayers, Set<UUID> guildBPlayers,
                          GuildRepository.WarArenaRecord arena, long startedAt, long endsAt) {
            this.id = id;
            this.guildA = guildA;
            this.guildB = guildB;
            this.teamSize = teamSize;
            this.guildAPlayers = guildAPlayers;
            this.guildBPlayers = guildBPlayers;
            this.arena = arena;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
        }
    }
}
