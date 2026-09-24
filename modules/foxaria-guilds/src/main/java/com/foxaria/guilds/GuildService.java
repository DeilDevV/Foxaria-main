package com.foxaria.guilds;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.guilds.gui.GuildChestHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.logging.Level;

import com.foxaria.guilds.GuildLevelModels.GuildObjectiveDef;
import com.foxaria.guilds.GuildLevelModels.GuildObjectiveType;
import com.foxaria.guilds.GuildLevelModels.LevelRewardBundle;
import com.foxaria.guilds.GuildLevelModels.TemplateRewardEntry;

import static com.foxaria.guilds.GuildModels.GuildChestSnapshot;
import static com.foxaria.guilds.GuildModels.GuildMemberRecord;
import static com.foxaria.guilds.GuildModels.GuildRecord;

public final class GuildService {

    private final JavaPlugin plugin;
    private final GuildRepository repository;
    private final EconomyService economyService;
    private final MenuManager menuManager;
    private final ConfigService configs;
    private final MessageService messages;
    private final AuditService audits;
    private final ItemTemplateService itemTemplates;
    private GuildWarEngine warEngine;

    /** Базовые количества предметов в сундуке при открытии — для учёта SUBMIT_ITEMS. */
    private final Map<UUID, ChestDepositBaseline> chestDepositBaselines = new ConcurrentHashMap<>();

    private record ChestDepositBaseline(String guildId, Map<Material, Integer> materialCounts) {
    }

    private record ObjectiveDelta(int level, int index, long delta) {
    }
    private final Map<UUID, String> playerGuildCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> guildFriendlyFireCache = new ConcurrentHashMap<>();

    /** Pending guild invites: invited player UUID → guild ID (expires after 60 s). */
    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingInviteExpiry = new ConcurrentHashMap<>();
    private static final long INVITE_TIMEOUT_MS = 60_000L;

    private static final List<String> ROLE_HIERARCHY = List.of("MEMBER", "OFFICER", "LEADER", "MASTER");
    private final Map<UUID, Integer> warTeamSizeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> warArenaByPlayer = new ConcurrentHashMap<>();
    private static final String PERM_WAR_INVITE = "WAR_INVITE";
    private static final String PERM_WAR_QUEUE = "WAR_QUEUE";
    private static final String PERM_WAR_ACCEPT = "WAR_ACCEPT";
    private static final String PERM_WAR_ARENA = "WAR_ARENA_SELECT";
    private static final String PERM_INVITE = "INVITE";
    private static final String PERM_KICK = "KICK";
    private static final String PERM_CHEST = "CHEST";
    private static final String PERM_BANK_DEPOSIT = "BANK_DEPOSIT";
    private static final String PERM_BANK_WITHDRAW = "BANK_WITHDRAW";
    private static final String PERM_SHOP_BUY = "SHOP_BUY";
    private static final String PERM_UPGRADE = "UPGRADE";
    private static final String PERM_TAG_COLOR = "TAG_COLOR";
    private static final String PERM_MOTD_EDIT = "MOTD_EDIT";
    private static final String PERM_FF_TOGGLE = "FRIENDLY_FIRE_TOGGLE";
    private static final String PERM_LEVEL_REWARD_CLAIM = "LEVEL_REWARD_CLAIM";

    /** Порядок и подписи для меню прав (OWNER настраивает OFFICER/MEMBER). */
    private static final List<Map.Entry<String, String>> GUILD_ROLE_PERM_ROWS = List.of(
        Map.entry(PERM_INVITE, "&eПриглашать игроков"),
        Map.entry(PERM_KICK, "&eИсключать участников"),
        Map.entry(PERM_CHEST, "&eОбщий сундук"),
        Map.entry(PERM_BANK_DEPOSIT, "&aВклад в казну"),
        Map.entry(PERM_BANK_WITHDRAW, "&cСнятие из казны"),
        Map.entry(PERM_SHOP_BUY, "&6Покупки в гильд-магазине"),
        Map.entry(PERM_UPGRADE, "&dУлучшения гильдии"),
        Map.entry(PERM_TAG_COLOR, "&bЦвет тега"),
        Map.entry(PERM_MOTD_EDIT, "&fСлоган (MOTD)"),
        Map.entry(PERM_FF_TOGGLE, "&cДружественный огонь"),
        Map.entry(PERM_WAR_INVITE, "&4Война: вызов"),
        Map.entry(PERM_WAR_QUEUE, "&4Война: очередь"),
        Map.entry(PERM_WAR_ACCEPT, "&4Война: принять"),
        Map.entry(PERM_WAR_ARENA, "&4Война: арена"),
        Map.entry(PERM_LEVEL_REWARD_CLAIM, "&dНаграды за уровни гильдии")
    );

    /** Лор для справки в GUI (без внутренних имён прав). */
    private static String[] guildHelpLore() {
        return new String[] {
            "&8─── &f&lЭкономика и прогресс &8───",
            "&7Коины гильдии &8— &7покупки в гильд-магазине.",
            "&7Казна &8— &7общие деньги на улучшения (сундук, слоты, цвет тега…).",
            "&7Очки с мобов &8— &7считаются с убийств; нужны для части улучшений.",
            "&7Уровень гильдии (1–5) &8— &7растёт по цепочке квестов; открывает пороги улучшений."
        };
    }

    public GuildService(
        JavaPlugin plugin,
        GuildRepository repository,
        EconomyService economyService,
        MenuManager menuManager,
        ConfigService configs,
        MessageService messages,
        AuditService audits,
        ItemTemplateService itemTemplates
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.economyService = economyService;
        this.menuManager = menuManager;
        this.configs = configs;
        this.messages = messages;
        this.audits = audits;
        this.itemTemplates = itemTemplates;
    }

    public void openEntry(Player player) {
        repository.byPlayer(player.getUniqueId()).thenAccept(optionalGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (optionalGuild.isPresent()) {
                updateCaches(player.getUniqueId(), optionalGuild.get());
                openMain(player, optionalGuild.get());
            } else {
                menuManager.open(player, new GuildOnboardingMenu(this));
            }
        }));
    }

    public void setWarEngine(GuildWarEngine warEngine) {
        this.warEngine = warEngine;
    }

    public GuildWarEngine war() {
        return warEngine;
    }

    public CompletableFuture<Optional<GuildRecord>> guildOf(UUID playerUuid) {
        return repository.byPlayer(playerUuid).thenApply(opt -> {
            if (opt.isPresent()) {
                updateCaches(playerUuid, opt.get());
            } else {
                playerGuildCache.remove(playerUuid);
            }
            return opt;
        });
    }

    public void create(Player player, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            messages.send(player, "guild.create-usage", "&cИспользование: /guild create <название>");
            return;
        }
        FileConfiguration cfg = moduleConfig();
        int min = cfg.getInt("creation.name-min", 3);
        int max = cfg.getInt("creation.name-max", 16);
        if (trimmed.length() < min || trimmed.length() > max) {
            messages.send(player, "guild.name-invalid", "&cНазвание гильдии должно быть от <min> до <max> символов.",
                new MessageService.Placeholder("min", String.valueOf(min)),
                new MessageService.Placeholder("max", String.valueOf(max)));
            return;
        }
        if (!trimmed.matches("[A-Za-zА-Яа-я0-9_\\- ]+")) {
            messages.send(player, "guild.name-invalid", "&cНедопустимое название гильдии.");
            return;
        }
        repository.byPlayer(player.getUniqueId()).thenCompose(current -> {
            if (current.isPresent()) {
                messages.send(player, "guild.already-in-guild", "&cВы уже состоите в гильдии.");
                return CompletableFuture.completedFuture(false);
            }
            return repository.byName(trimmed).thenCompose(existing -> {
                if (existing.isPresent()) {
                    messages.send(player, "guild.name-taken", "&cГильдия с таким названием уже существует.");
                    return CompletableFuture.completedFuture(false);
                }
                BigDecimal createCost = BigDecimal.valueOf(cfg.getDouble("creation.cost", 100000.0D)).setScale(2, RoundingMode.HALF_UP);
                CompletableFuture<Boolean> creationFlow;
                if (createCost.compareTo(BigDecimal.ZERO) > 0) {
                    creationFlow = economyService.balance(player.getUniqueId()).thenCompose(balance -> {
                        if (balance.balance().compareTo(createCost) < 0) {
                            messages.send(player, "guild.create-not-enough",
                                "&cДля создания гильдии нужно: <required>. У вас: <current>.",
                                new MessageService.Placeholder("required", createCost.toPlainString()),
                                new MessageService.Placeholder("current", balance.balance().setScale(2, RoundingMode.HALF_UP).toPlainString()));
                            return CompletableFuture.completedFuture(false);
                        }
                        return economyService.withdraw(player.getUniqueId(), createCost, "guild_create", player.getUniqueId())
                            .thenCompose(v -> createGuildAfterPayment(player, trimmed, cfg));
                    });
                } else {
                    creationFlow = createGuildAfterPayment(player, trimmed, cfg);
                }
                return creationFlow.exceptionally(error -> {
                    messages.send(player, "guild.create-failed", "&cНе удалось создать гильдию: <error>",
                        new MessageService.Placeholder("error", userError(error)));
                    return false;
                });
            });
        }).thenAccept(created -> {
            if (created) {
                messages.send(player, "guild.created", "&aГильдия <name> успешно создана.",
                    new MessageService.Placeholder("name", trimmed));
                audits.append(new AuditEvent(
                    "GUILD_CREATED",
                    player.getUniqueId(),
                    null,
                    player.getName(),
                    null,
                    "Guild created",
                    Map.of("guildName", trimmed),
                    System.currentTimeMillis()
                ));
                openEntry(player);
            }
        });
    }

    private CompletableFuture<Boolean> createGuildAfterPayment(Player player, String trimmed, FileConfiguration cfg) {
        String id = UUID.randomUUID().toString();
        GuildRecord guild = new GuildRecord(
            id,
            trimmed,
            player.getUniqueId(),
            System.currentTimeMillis(),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            cfg.getLong("defaults.start-coins", 0L),
            cfg.getLong("defaults.start-points", 0L),
            1,
            cfg.getInt("defaults.chest-rows", 3),
            cfg.getInt("defaults.shop-tier", 1),
            0,
            cfg.getString("defaults.motd", "Добро пожаловать в гильдию!"),
            cfg.getBoolean("defaults.friendly-fire", false),
            cfg.getString("defaults.tag-color", "WHITE")
        );
        GuildMemberRecord owner = new GuildMemberRecord(id, player.getUniqueId(), player.getName(), "OWNER", System.currentTimeMillis());
        CompletableFuture<Boolean> createdFuture = repository.createGuild(guild, owner);
        return createdFuture.thenCompose(created -> {
            if (!created) {
                throw new IllegalStateException("Guild create failed");
            }
            return seedDefaultRoles(id).thenApply(v -> true);
        });
    }

    public void disband(Player player) {
        repository.byPlayer(player.getUniqueId()).thenAccept(opt -> {
            if (opt.isEmpty()) {
                messages.send(player, "guild.not-in-guild", "&cВы не состоите в гильдии.");
                return;
            }
            GuildRecord guild = opt.get();
            repository.memberRole(guild.id(), player.getUniqueId()).thenAccept(optRole -> {
                if (optRole.isEmpty() || !"OWNER".equalsIgnoreCase(optRole.get())) {
                    messages.send(player, "general.no-permission", "&cРаспустить гильдию может только владелец (OWNER).");
                    return;
                }
                String guildName = guild.name();
                repository.deleteGuild(guild.id()).thenRun(() -> {
                    playerGuildCache.remove(player.getUniqueId());
                    guildFriendlyFireCache.remove(guild.id());
                    messages.send(player, "guild.disbanded", "&cГильдия &f" + guildName + " &cраспущена.");
                }).exceptionally(e -> {
                    messages.send(player, "guild.error", "&cОшибка при роспуске гильдии.");
                    return null;
                });
            });
        });
    }

    public void invite(Player inviter, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(inviter, "guild.invite.offline", "&cИгрок &f" + targetName + " &cне в сети.");
            return;
        }
        if (target.equals(inviter)) {
            messages.send(inviter, "guild.invite.self", "&cНельзя пригласить самого себя.");
            return;
        }
        repository.byPlayer(inviter.getUniqueId()).thenAccept(optGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (optGuild.isEmpty()) {
                messages.send(inviter, "guild.not-in-guild", "&cВы не состоите в гильдии.");
                return;
            }
            GuildRecord guild = optGuild.get();
            hasRolePermission(guild.id(), inviter.getUniqueId(), PERM_INVITE).thenAccept(can -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!can) {
                    messages.send(inviter, "general.no-permission", "&cУ вас нет прав приглашать игроков.");
                    return;
                }
                repository.byPlayer(target.getUniqueId()).thenAccept(targetGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (targetGuild.isPresent()) {
                        messages.send(inviter, "guild.invite.already-in-guild", "&cИгрок &f" + targetName + " &cуже состоит в гильдии.");
                        return;
                    }
                    pendingInvites.put(target.getUniqueId(), guild.id());
                    pendingInviteExpiry.put(target.getUniqueId(), System.currentTimeMillis() + INVITE_TIMEOUT_MS);
                    messages.send(inviter, "guild.invite.sent", "&aПриглашение отправлено игроку &f" + targetName + "&a.");
                    target.sendMessage(net.kyori.adventure.text.Component.text(
                        "§6[Гильдия] §fВас приглашают в гильдию §e" + guild.name() +
                        "§f. Введите §a/guild accept§f или §c/guild decline§f (60 секунд)."));
                    // Auto-expire
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        pendingInvites.remove(target.getUniqueId()), 1200L);
                }));
            }));
        }));
    }

    public void acceptInvite(Player player) {
        cleanExpiredInvites();
        // Check both in-memory (in-game invite) and DB (site invite)
        String memGuildId = pendingInvites.remove(player.getUniqueId());
        pendingInviteExpiry.remove(player.getUniqueId());
        if (memGuildId != null) {
            doAcceptInvite(player, memGuildId);
            return;
        }
        // Fallback: check DB invite from site
        repository.pendingInvite(player.getUniqueId()).thenAccept(optGuildId ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optGuildId.isEmpty()) {
                    messages.send(player, "guild.invite.none", "&cУ вас нет активного приглашения в гильдию.");
                    return;
                }
                repository.removeInvite(player.getUniqueId());
                doAcceptInvite(player, optGuildId.get());
            })
        );
    }

    private void doAcceptInvite(Player player, String guildId) {
        repository.byPlayer(player.getUniqueId()).thenAccept(existing -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (existing.isPresent()) {
                messages.send(player, "guild.invite.already-in-guild", "&cВы уже состоите в гильдии. Сначала покиньте её.");
                return;
            }
            repository.byId(guildId).thenAccept(optGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optGuild.isEmpty()) {
                    messages.send(player, "guild.invite.expired", "&cГильдия не найдена или была распущена.");
                    return;
                }
                GuildRecord guild = optGuild.get();
                repository.addMember(guildId, player.getUniqueId(), player.getName(), "MEMBER").thenRun(() ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        updateCaches(player.getUniqueId(), guild);
                        messages.send(player, "guild.invite.accepted", "&aВы вступили в гильдию &f" + guild.name() + "&a!");
                        Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                            "§6[Гильдия] §f" + player.getName() + " §7вступил в §e" + guild.name()));
                    })
                ).exceptionally(e -> {
                    messages.send(player, "guild.error", "&cОшибка при вступлении в гильдию.");
                    return null;
                });
            }));
        }));
    }

    public void declineInvite(Player player) {
        cleanExpiredInvites();
        boolean had = pendingInvites.remove(player.getUniqueId()) != null;
        pendingInviteExpiry.remove(player.getUniqueId());
        if (had) {
            messages.send(player, "guild.invite.declined", "&7Приглашение в гильдию отклонено.");
        } else {
            messages.send(player, "guild.invite.none", "&cУ вас нет активного приглашения.");
        }
    }

    public void kick(Player kicker, String targetName) {
        repository.byPlayer(kicker.getUniqueId()).thenAccept(optGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (optGuild.isEmpty()) {
                messages.send(kicker, "guild.not-in-guild", "&cВы не состоите в гильдии.");
                return;
            }
            GuildRecord guild = optGuild.get();
            hasRolePermission(guild.id(), kicker.getUniqueId(), PERM_KICK).thenAccept(can -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!can) {
                    messages.send(kicker, "general.no-permission", "&cУ вас нет прав исключать игроков.");
                    return;
                }
                repository.findMemberByName(targetName).thenAccept(optMember -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (optMember.isEmpty() || !optMember.get().guildId().equals(guild.id())) {
                        messages.send(kicker, "guild.kick.not-member", "&cИгрок &f" + targetName + " &cне является участником вашей гильдии.");
                        return;
                    }
                    GuildModels.GuildMemberRecord target = optMember.get();
                    if ("OWNER".equalsIgnoreCase(target.role())) {
                        messages.send(kicker, "guild.kick.owner", "&cНельзя исключить владельца гильдии.");
                        return;
                    }
                    repository.removeMember(guild.id(), target.playerUuid()).thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            playerGuildCache.remove(target.playerUuid());
                            messages.send(kicker, "guild.kick.success", "&a" + targetName + " исключён из гильдии.");
                            Player online = Bukkit.getPlayer(target.playerUuid());
                            if (online != null) {
                                online.sendMessage("§c[Гильдия] Вы были исключены из гильдии §f" + guild.name() + "§c.");
                            }
                        })
                    );
                }));
            }));
        }));
    }

    public void leave(Player player) {
        repository.byPlayer(player.getUniqueId()).thenAccept(optGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (optGuild.isEmpty()) {
                messages.send(player, "guild.not-in-guild", "&cВы не состоите в гильдии.");
                return;
            }
            GuildRecord guild = optGuild.get();
            repository.memberRole(guild.id(), player.getUniqueId()).thenAccept(optRole -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                String role = optRole.orElse("MEMBER");
                if ("OWNER".equalsIgnoreCase(role)) {
                    messages.send(player, "guild.leave.owner", "&cВладелец не может покинуть гильдию. Сначала распустите её (/guild disband).");
                    return;
                }
                repository.removeMember(guild.id(), player.getUniqueId()).thenRun(() ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        playerGuildCache.remove(player.getUniqueId());
                        messages.send(player, "guild.leave.success", "&7Вы покинули гильдию &f" + guild.name() + "&7.");
                    })
                );
            }));
        }));
    }

    public void promote(Player leader, String targetName) {
        changeRole(leader, targetName, 1, false);
    }

    public void demote(Player leader, String targetName) {
        changeRole(leader, targetName, -1, false);
    }

    /** Promote/demote from the GUI members menu — refreshes the menu on success. */
    void promoteAndRefresh(Player leader, GuildRecord guild, String targetName) {
        changeRole(leader, targetName, 1, true);
    }

    void demoteAndRefresh(Player leader, GuildRecord guild, String targetName) {
        changeRole(leader, targetName, -1, true);
    }

    private void changeRole(Player leader, String targetName, int direction, boolean refreshMenu) {
        repository.byPlayer(leader.getUniqueId()).thenAccept(optGuild -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (optGuild.isEmpty()) {
                messages.send(leader, "guild.not-in-guild", "&cВы не состоите в гильдии.");
                return;
            }
            GuildRecord guild = optGuild.get();
            repository.memberRole(guild.id(), leader.getUniqueId()).thenAccept(optMyRole -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                String myRole = optMyRole.orElse("MEMBER");
                boolean canManage = "OWNER".equalsIgnoreCase(myRole) || "MASTER".equalsIgnoreCase(myRole) || "LEADER".equalsIgnoreCase(myRole);
                if (!canManage) {
                    messages.send(leader, "general.no-permission", "&cНедостаточно прав для изменения ролей.");
                    return;
                }
                repository.findMemberByName(targetName).thenAccept(optMember -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (optMember.isEmpty() || !optMember.get().guildId().equals(guild.id())) {
                        messages.send(leader, "guild.kick.not-member", "&cИгрок &f" + targetName + " &cне является участником вашей гильдии.");
                        return;
                    }
                    GuildModels.GuildMemberRecord target = optMember.get();
                    if ("OWNER".equalsIgnoreCase(target.role())) {
                        messages.send(leader, "guild.promote.owner", "&cНельзя изменить роль владельца.");
                        return;
                    }
                    int idx = ROLE_HIERARCHY.indexOf(target.role().toUpperCase());
                    if (idx < 0) idx = 0;
                    int newIdx = Math.max(0, Math.min(ROLE_HIERARCHY.size() - 1, idx + direction));
                    if (newIdx == idx) {
                        messages.send(leader, "guild.promote.limit", "&cДальнейшее " + (direction > 0 ? "повышение" : "понижение") + " невозможно.");
                        return;
                    }
                    String newRole = ROLE_HIERARCHY.get(newIdx);
                    repository.updateMemberRole(guild.id(), target.playerUuid(), newRole).thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String action = direction > 0 ? "повышен до" : "понижен до";
                            messages.send(leader, "guild.role.changed", "&a" + targetName + " " + action + " &e" + newRole + "&a.");
                            Player online = Bukkit.getPlayer(target.playerUuid());
                            if (online != null) {
                                online.sendMessage("§6[Гильдия] §fВаша роль изменена: §e" + newRole);
                            }
                            if (refreshMenu && leader.isOnline()) {
                                openMembers(leader, guild);
                            }
                        })
                    );
                }));
            }));
        }));
    }

    private void cleanExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInviteExpiry.entrySet().removeIf(e -> {
            if (e.getValue() < now) { pendingInvites.remove(e.getKey()); return true; }
            return false;
        });
    }

    private void updateCaches(UUID playerUuid, GuildRecord guild) {
        playerGuildCache.put(playerUuid, guild.id());
        guildFriendlyFireCache.put(guild.id(), guild.friendlyFire());
    }

    private CompletableFuture<Void> seedDefaultRoles(String guildId) {
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        // Always seed the full hierarchy to avoid permission holes for high roles.
        Set<String> allFlags = Set.of("ALL");
        Set<String> officerFlags = new HashSet<>(Set.of(
            "INVITE","KICK","CHEST","BANK_DEPOSIT","BANK_WITHDRAW","SHOP_BUY",
            "UPGRADE","TAG_COLOR","MOTD_EDIT","FRIENDLY_FIRE_TOGGLE",
            "WAR_INVITE","WAR_QUEUE","WAR_ACCEPT","WAR_ARENA_SELECT","LEVEL_REWARD_CLAIM"
        ));
        Set<String> memberFlags = new HashSet<>(Set.of(
            "CHEST","BANK_DEPOSIT","SHOP_BUY","WAR_QUEUE","LEVEL_REWARD_CLAIM"
        ));
        jobs.add(repository.upsertRole(guildId, "MEMBER",  "MEMBER",  10, memberFlags));
        jobs.add(repository.upsertRole(guildId, "OFFICER", "OFFICER", 70, officerFlags));
        jobs.add(repository.upsertRole(guildId, "LEADER",  "LEADER",  80, allFlags));
        jobs.add(repository.upsertRole(guildId, "MASTER",  "MASTER",  90, allFlags));
        jobs.add(repository.upsertRole(guildId, "OWNER",   "OWNER",  100, allFlags));
        // Also apply any extra flags from config if present.
        ConfigurationSection section = moduleConfig().getConfigurationSection("roles");
        if (section != null) {
            for (String roleId : section.getKeys(false)) {
                ConfigurationSection role = section.getConfigurationSection(roleId);
                if (role == null) continue;
                Set<String> flags = new java.util.HashSet<>(role.getStringList("flags"));
                jobs.add(repository.upsertRole(guildId, roleId, roleId, role.getInt("weight", 0), flags));
            }
        }
        return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Boolean> hasRolePermission(String guildId, UUID playerUuid, String permission) {
        return repository.memberRole(guildId, playerUuid).thenCompose(optRole -> {
            if (optRole.isEmpty()) return CompletableFuture.completedFuture(false);
            String role = optRole.get();
            // High-level roles always have all permissions regardless of DB flags.
            if ("OWNER".equalsIgnoreCase(role) || "MASTER".equalsIgnoreCase(role)) {
                return CompletableFuture.completedFuture(true);
            }
            return repository.roleFlags(guildId, role).thenApply(flags -> {
                if (flags.isEmpty() && "LEADER".equalsIgnoreCase(role)) {
                    // Fallback: LEADER without DB entry gets all permissions.
                    return true;
                }
                return flags.contains("ALL") || flags.contains(permission);
            });
        });
    }

    void openMain(Player player, GuildRecord guild) {
        repository.members(guild.id()).thenAccept(members -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildMainMenu(this, guild, members))));
    }

    void openMembers(Player player, GuildRecord guild) {
        repository.members(guild.id()).thenAccept(members -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildMembersMenu(this, guild, members))));
    }

    void openBank(Player player, GuildRecord guild) {
        menuManager.open(player, new GuildBankMenu(this, guild));
    }

    void openUpgrades(Player player, GuildRecord guild) {
        repository.upgrades(guild.id()).thenCompose(levels ->
            repository.totalKills(guild.id()).thenApply(kills -> Map.entry(levels, kills))
        ).thenAccept(data -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildUpgradesMenu(this, guild, data.getKey(), data.getValue()))));
    }

    void openShop(Player player, GuildRecord guild) {
        menuManager.open(player, new GuildShopMenu(this, guild));
    }

    void openWarMenu(Player player, GuildRecord guild) {
        warPermissions(guild.id(), player.getUniqueId()).thenCompose(perms ->
            warEngine.availableArenas().thenApply(arenas -> new WarUiState(perms, arenas))
        ).thenAccept(state -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildWarMenu(this, guild, state))));
    }

    void openWarGuildList(Player player, GuildRecord guild) {
        openWarGuildList(player, guild, 0);
    }

    void openWarGuildList(Player player, GuildRecord guild, int page) {
        warPermissions(guild.id(), player.getUniqueId()).thenCompose(perms ->
            repository.listGuilds().thenCompose(guilds -> onlineGuildViews(guilds, guild.id()))
                .thenApply(views -> new WarGuildListState(perms.canInvite(), views))
        ).thenAccept(state -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildWarGuildListMenu(this, guild, state, page))));
    }

    void openWarInvites(Player player, GuildRecord guild) {
        Optional<GuildWarEngine.IncomingInvite> invite = warEngine.incomingInvite(guild.id());
        menuManager.open(player, new GuildWarInvitesMenu(this, guild, invite));
    }

    void openWarSettings(Player player, GuildRecord guild) {
        warPermissions(guild.id(), player.getUniqueId()).thenCompose(perms ->
            warEngine.availableArenas().thenApply(arenas -> new WarUiState(perms, arenas))
        ).thenAccept(state -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildWarSettingsMenu(this, guild, state))));
    }

    void openWarHistory(Player player, GuildRecord guild) {
        repository.latestWarActivity(guild.id(), 20).thenAccept(lines -> plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new GuildWarHistoryMenu(this, guild, lines))));
    }

    private CompletableFuture<List<OnlineGuildView>> onlineGuildViews(List<GuildRecord> guilds, String excludeGuildId) {
        List<CompletableFuture<OnlineGuildView>> jobs = new ArrayList<>();
        for (GuildRecord record : guilds) {
            if (record.id().equals(excludeGuildId)) {
                continue;
            }
            jobs.add(repository.members(record.id()).thenApply(members -> {
                int online = 0;
                for (GuildMemberRecord member : members) {
                    Player p = Bukkit.getPlayer(member.playerUuid());
                    if (p != null && p.isOnline()) {
                        online++;
                    }
                }
                return new OnlineGuildView(record.id(), record.name(), online, members.size());
            }));
        }
        return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new))
            .thenApply(v -> jobs.stream().map(CompletableFuture::join).filter(g -> g.online() > 0).toList());
    }

    private CompletableFuture<WarPermissions> warPermissions(String guildId, UUID playerUuid) {
        CompletableFuture<Boolean> invite = hasRolePermission(guildId, playerUuid, PERM_WAR_INVITE);
        CompletableFuture<Boolean> queue = hasRolePermission(guildId, playerUuid, PERM_WAR_QUEUE);
        CompletableFuture<Boolean> accept = hasRolePermission(guildId, playerUuid, PERM_WAR_ACCEPT);
        CompletableFuture<Boolean> arena = hasRolePermission(guildId, playerUuid, PERM_WAR_ARENA);
        return CompletableFuture.allOf(invite, queue, accept, arena).thenApply(done ->
            new WarPermissions(invite.join(), queue.join(), accept.join(), arena.join()));
    }

    void cycleWarTeamSize(Player player) {
        guildOf(player.getUniqueId()).thenCompose(optGuild -> {
            if (optGuild.isEmpty()) return CompletableFuture.completedFuture(false);
            GuildRecord guild = optGuild.get();
            return hasRolePermission(guild.id(), player.getUniqueId(), PERM_WAR_QUEUE).thenApply(allowed -> {
                if (!allowed) {
                    messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                    return false;
                }
                return true;
            });
        }).thenAccept(allowed -> {
            if (!allowed) return;
            int current = warTeamSizeByPlayer.getOrDefault(player.getUniqueId(), 2);
            int next = current >= 10 ? 2 : current + 1;
            warTeamSizeByPlayer.put(player.getUniqueId(), next);
            openEntry(player);
        });
    }

    int warTeamSize(Player player) {
        return warTeamSizeByPlayer.getOrDefault(player.getUniqueId(), 2);
    }

    void cycleWarArena(Player player) {
        guildOf(player.getUniqueId()).thenCompose(optGuild -> {
            if (optGuild.isEmpty()) return CompletableFuture.completedFuture(false);
            return hasRolePermission(optGuild.get().id(), player.getUniqueId(), PERM_WAR_ARENA);
        }).thenAccept(allowed -> {
            if (!allowed) {
                messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                return;
            }
            repository.listArenas(true).thenAccept(arenas -> {
            if (arenas.isEmpty()) {
                messages.send(player, "guild.war-no-arena", "&cНет доступных арен.");
                return;
            }
            String current = warArenaByPlayer.get(player.getUniqueId());
            int idx = -1;
            for (int i = 0; i < arenas.size(); i++) {
                if (arenas.get(i).arenaId().equalsIgnoreCase(current)) {
                    idx = i;
                    break;
                }
            }
            String next = arenas.get((idx + 1) % arenas.size()).arenaId();
            warArenaByPlayer.put(player.getUniqueId(), next);
            openEntry(player);
            });
        });
    }

    String warArena(Player player) {
        return warArenaByPlayer.getOrDefault(player.getUniqueId(), "");
    }

    void joinWarQueue(Player player, GuildRecord guild) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_WAR_QUEUE).thenAccept(allowed -> {
            if (!allowed) {
                messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                return;
            }
            warEngine.queue(player, warTeamSize(player), warArena(player));
        });
    }

    void leaveWarQueue(Player player) {
        warEngine.leaveQueue(player);
    }

    void sendWarInvite(Player player, GuildRecord guild, String targetGuildName) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_WAR_INVITE).thenAccept(allowed -> {
            if (!allowed) {
                messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                return;
            }
            warEngine.invite(player, targetGuildName, warTeamSize(player), warArena(player));
        });
    }

    void acceptWarInvite(Player player, String challengerGuildId) {
        guildOf(player.getUniqueId()).thenAccept(optGuild -> {
            if (optGuild.isEmpty()) return;
            hasRolePermission(optGuild.get().id(), player.getUniqueId(), PERM_WAR_ACCEPT).thenAccept(allowed -> {
                if (!allowed) {
                    messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                    return;
                }
                warEngine.acceptByGuildId(player, challengerGuildId);
            });
        });
    }

    void denyWarInvite(Player player, String challengerGuildId) {
        warEngine.denyByGuildId(player, challengerGuildId);
    }

    void openRoleSettings(Player player, GuildRecord guild) {
        repository.memberRole(guild.id(), player.getUniqueId()).thenAccept(optRole -> {
            if (optRole.isEmpty() || !"OWNER".equalsIgnoreCase(optRole.get())) {
                messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                return;
            }
            repository.allRoleFlags(guild.id()).thenAccept(flags -> plugin.getServer().getScheduler().runTask(plugin, () ->
                menuManager.open(player, new GuildRoleSettingsMenu(this, guild, flags))));
        });
    }

    void toggleRolePermission(Player player, GuildRecord guild, String roleId, String permission) {
        repository.memberRole(guild.id(), player.getUniqueId()).thenCompose(optRole -> {
            if (optRole.isEmpty() || !"OWNER".equalsIgnoreCase(optRole.get())) {
                messages.send(player, "general.no-permission", "&cУ вас нет прав.");
                return CompletableFuture.completedFuture(null);
            }
            return repository.roleFlags(guild.id(), roleId).thenCompose(flags -> {
                java.util.Set<String> next = new java.util.HashSet<>(flags);
                if (next.contains(permission)) next.remove(permission); else next.add(permission);
                return repository.upsertRole(guild.id(), roleId, roleId, "OFFICER".equals(roleId) ? 70 : 10, next);
            });
        }).thenRun(() -> {
            messages.send(player, "guild.role-updated", "&aПрава роли обновлены.");
            openRoleSettings(player, guild);
        });
    }

    public void openChest(Player player) {
        repository.byPlayer(player.getUniqueId()).thenAccept(optionalGuild -> {
            if (optionalGuild.isEmpty()) {
                return;
            }
            GuildRecord guild = optionalGuild.get();
            hasRolePermission(guild.id(), player.getUniqueId(), PERM_CHEST).thenAccept(allowed -> {
                if (!allowed) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        messages.send(player, "general.no-permission", "&cНет права: общий сундук гильдии."));
                    return;
                }
                repository.chest(guild.id(), guild.chestRows()).thenAccept(snapshot -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    int rows = Math.min(6, Math.max(1, snapshot.rows()));
                    Inventory inventory = Bukkit.createInventory(new GuildChestHolder(guild.id()), rows * 9, "Гильдейский сундук: " + guild.name());
                    snapshot.items().forEach(inventory::setItem);
                    registerGuildChestBaseline(player, snapshot);
                    player.openInventory(inventory);
                }));
            });
        });
    }

    public void saveChest(String guildId, Inventory inventory) {
        Map<Integer, ItemStack> items = new HashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.put(slot, item);
            }
        }
        repository.saveChest(guildId, items);
    }

    public void rewardForKill(Player killer, String entityKey) {
        repository.byPlayer(killer.getUniqueId()).thenAccept(optionalGuild -> {
            if (optionalGuild.isEmpty()) {
                return;
            }
            ConfigurationSection section = moduleConfig().getConfigurationSection("mob-rewards.entities." + entityKey);
            if (section == null) {
                return;
            }
            long coins = section.getLong("coins", 0L);
            long points = section.getLong("points", 0L);
            if (coins <= 0 && points <= 0) {
                return;
            }
            GuildRecord guild = optionalGuild.get();
            updateCaches(killer.getUniqueId(), guild);
            repository.addKillAndRewards(guild.id(), coins, points).thenCompose(v -> refreshEffectiveGuildLevel(guild.id()))
                .thenRun(() ->
                messages.send(killer, "guild.reward-kill", "&a+<coins> коинов и +<points> очков гильдии за <entity>.",
                    new MessageService.Placeholder("coins", String.valueOf(coins)),
                    new MessageService.Placeholder("points", String.valueOf(points)),
                    new MessageService.Placeholder("entity", entityKey))
            );
        });
    }

    void bankDeposit(Player player, GuildRecord guild, BigDecimal amount) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_BANK_DEPOSIT).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: вклад в казну гильдии."));
                return;
            }
            economyService.withdraw(player.getUniqueId(), amount, "guild_bank_deposit", player.getUniqueId())
                .thenCompose(ignored -> repository.changeBank(guild.id(), amount))
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    messages.send(player, "guild.bank-deposit", "&aВ банк гильдии внесено <amount>.",
                        new MessageService.Placeholder("amount", amount.toPlainString()));
                    repository.appendActivity(guild.id(), player.getName(), "BANK_DEPOSIT", "+" + amount.toPlainString());
                    openBank(player, guild);
                }))
                .exceptionally(error -> {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        messages.send(player, "guild.bank-failed", "&cОперация банка не выполнена: <error>",
                            new MessageService.Placeholder("error", rootCause(error))));
                    return null;
                });
        });
    }

    void bankWithdraw(Player player, GuildRecord guild, BigDecimal amount) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_BANK_WITHDRAW).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: снятие из казны."));
                return;
            }
            repository.changeBank(guild.id(), amount.negate())
                .thenCompose(ok -> {
                    if (!ok) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            messages.send(player, "guild.bank-failed", "&cНедостаточно средств в банке гильдии."));
                        return CompletableFuture.completedFuture(false);
                    }
                    return economyService.deposit(player.getUniqueId(), amount, "guild_bank_withdraw", player.getUniqueId())
                        .thenApply(ignored -> true);
                })
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        messages.send(player, "guild.bank-withdraw", "&aИз банка гильдии снято <amount>.",
                            new MessageService.Placeholder("amount", amount.toPlainString()));
                        repository.appendActivity(guild.id(), player.getName(), "BANK_WITHDRAW", "-" + amount.toPlainString());
                        openBank(player, guild);
                    }
                }));
        });
    }

    void buyUpgrade(Player player, GuildRecord guild, String key) {
        Map<String, UpgradeDef> defs = upgradeDefs();
        UpgradeDef def = defs.get(key);
        if (def == null) {
            return;
        }
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_UPGRADE).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: улучшения гильдии."));
                return;
            }
        refreshEffectiveGuildLevel(guild.id()).thenCompose(freshOpt -> {
            GuildRecord g0 = freshOpt.orElse(guild);
            return repository.upgrades(guild.id()).thenCompose(levels -> {
            int current = levels.getOrDefault(key, 0);
            if (current >= def.maxLevel()) {
                messages.send(player, "guild.upgrade-max", "&eЭто улучшение уже максимального уровня.");
                return CompletableFuture.completedFuture(false);
            }
            int nextLevel = current + 1;
            long bankCost = def.bankCostByLevel().getOrDefault(nextLevel, 0L);
            long requiredKills = def.requiredKillsByLevel().getOrDefault(nextLevel, 0L);
            int requiredGuildLevel = def.requiredGuildLevelByLevel().getOrDefault(nextLevel, 1);
            int currentGuildLevel = g0.level();
            if (currentGuildLevel < requiredGuildLevel) {
                messages.send(player, "guild.upgrade-failed", "&cНужен уровень гильдии: <level>.",
                    new MessageService.Placeholder("level", String.valueOf(requiredGuildLevel)));
                return CompletableFuture.completedFuture(false);
            }
            return repository.totalKills(guild.id()).thenCompose(kills -> {
                if (kills < requiredKills) {
                    messages.send(player, "guild.upgrade-failed", "&cНужно убийств гильдии: <kills>.",
                        new MessageService.Placeholder("kills", String.valueOf(requiredKills)));
                    return CompletableFuture.completedFuture(false);
                }
                return repository.changeBank(guild.id(), BigDecimal.valueOf(bankCost).negate().setScale(2, RoundingMode.HALF_UP)).thenCompose(spent -> {
                    if (!spent) {
                        messages.send(player, "guild.upgrade-failed", "&cНедостаточно денег в казне гильдии.");
                        return CompletableFuture.completedFuture(false);
                    }
                    return repository.applyUpgrade(guild.id(), key, nextLevel).thenApply(ignored -> true);
                });
            });
        });
        }).thenAccept(success -> {
            if (success) {
                messages.send(player, "guild.upgrade-success", "&aУлучшение куплено.");
                repository.appendActivity(guild.id(), player.getName(), "UPGRADE", key);
                openEntry(player);
            }
        });
        });
    }

    void openTagColorMenu(Player player, GuildRecord guild) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_TAG_COLOR).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: палитра тега."));
                return;
            }
            repository.upgrades(guild.id()).thenAccept(levels -> plugin.getServer().getScheduler().runTask(plugin, () ->
                menuManager.open(player, new GuildTagColorMenu(this, guild, levels.getOrDefault("tag_palette", 0)))));
        });
    }

    void setTagColor(Player player, GuildRecord guild, String colorId) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_TAG_COLOR).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: смена цвета тега."));
                return;
            }
            repository.setTagColor(guild.id(), colorId).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                messages.send(player, "guild.tag-color-updated", "&aЦвет тега гильдии обновлен.");
                openEntry(player);
            }));
        });
    }

    private Material woolByCode(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> Material.BLACK_WOOL;
            case '1' -> Material.BLUE_WOOL;
            case '2' -> Material.GREEN_WOOL;
            case '3' -> Material.CYAN_WOOL;
            case '4' -> Material.RED_WOOL;
            case '5' -> Material.PURPLE_WOOL;
            case '6' -> Material.ORANGE_WOOL;
            case '7' -> Material.LIGHT_GRAY_WOOL;
            case '8' -> Material.GRAY_WOOL;
            case '9' -> Material.LIGHT_BLUE_WOOL;
            case 'a' -> Material.LIME_WOOL;
            case 'b' -> Material.LIGHT_BLUE_WOOL;
            case 'c' -> Material.PINK_WOOL;
            case 'd' -> Material.MAGENTA_WOOL;
            case 'e' -> Material.YELLOW_WOOL;
            case 'f' -> Material.WHITE_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    private List<TagColorDef> tagColorDefs() {
        List<TagColorDef> defs = new ArrayList<>();
        ConfigurationSection section = moduleConfig().getConfigurationSection("tag-colors");
        if (section == null) {
            return defs;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection cfg = section.getConfigurationSection(id);
            if (cfg == null) {
                continue;
            }
            String code = cfg.getString("color-code", "&f");
            String display = cfg.getString("display", id);
            int requiredPalette = cfg.getInt("required-palette-level", 0);
            defs.add(new TagColorDef(id, display, code, requiredPalette));
        }
        defs.sort(java.util.Comparator.comparingInt(TagColorDef::requiredPaletteLevel));
        return defs;
    }

    private String colorCodeFor(String colorId) {
        for (TagColorDef def : tagColorDefs()) {
            if (def.id().equalsIgnoreCase(colorId)) {
                return def.colorCode();
            }
        }
        return "&f";
    }

    /** Цвет тега гильдии (&-коды) для чата/bridge на proxy. */
    public String legacyColorForGuildTag(String colorId) {
        return colorCodeFor(colorId);
    }

    /**
     * Подсказка при наведении на название гильдии в чате (только hover, без клика).
     */
    public Component guildChatHoverText(GuildRecord g) {
        CompletableFuture<Long> fk = repository.totalKills(g.id());
        CompletableFuture<Integer> fm = repository.countMembers(g.id());
        CompletableFuture<Integer> fw = repository.countWarWins(g.id());
        CompletableFuture.allOf(fk, fm, fw).join();
        long kills = fk.join();
        int members = fm.join();
        int warWins = fw.join();
        return Component.empty()
            .append(Component.text("Гильдия: ", NamedTextColor.GRAY))
            .append(Component.text(g.name(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Уровень: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(g.level()), NamedTextColor.GREEN))
            .append(Component.newline())
            .append(Component.text("Участников: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(members), NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("Побед в войнах: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(warWins), NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Учёт убийств: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(kills), NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("Коины: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(g.guildCoins()), NamedTextColor.YELLOW))
            .append(Component.text("  Очки: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(g.guildPoints()), NamedTextColor.LIGHT_PURPLE));
    }

    void buyShop(Player player, GuildRecord guild, ShopOffer offer) {
        if (guild.shopTier() < offer.requiredTier()) {
            messages.send(player, "guild.shop-tier-low", "&cНужен уровень магазина гильдии: <tier>.",
                new MessageService.Placeholder("tier", String.valueOf(offer.requiredTier())));
            return;
        }
        if (offer.usesTemplate()) {
            if (itemTemplates == null || !itemTemplates.exists(offer.itemTemplateId())) {
                messages.send(player, "guild.shop-template-missing", "&cШаблон предмета недоступен: <id>",
                    new MessageService.Placeholder("id", offer.itemTemplateId()));
                return;
            }
        }
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_SHOP_BUY).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: покупки за коины гильдии."));
                return;
            }
        repository.spendCoins(guild.id(), offer.price()).thenAccept(spent -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!spent) {
                messages.send(player, "guild.shop-failed", "&cНедостаточно коинов гильдии.");
                return;
            }
            if (offer.usesTemplate() && itemTemplates != null) {
                ItemStack base = itemTemplates.cloneTemplate(offer.itemTemplateId()).orElse(null);
                if (base == null) {
                    messages.send(player, "guild.shop-template-missing", "&cШаблон предмета недоступен: <id>",
                        new MessageService.Placeholder("id", offer.itemTemplateId()));
                    return;
                }
                int copies = Math.max(1, offer.item().getAmount());
                for (int i = 0; i < copies; i++) {
                    player.getInventory().addItem(base.clone());
                }
            } else {
                player.getInventory().addItem(offer.item().clone());
            }
            messages.send(player, "guild.shop-bought", "&aПокупка выполнена за <price> коинов.",
                new MessageService.Placeholder("price", String.valueOf(offer.price())));
            repository.appendActivity(guild.id(), player.getName(), "SHOP_BUY", offer.key());
        }));
        });
    }

    private ItemStack shopOfferIcon(ShopOffer offer) {
        if (offer.usesTemplate() && itemTemplates != null) {
            return itemTemplates.cloneTemplate(offer.itemTemplateId()).orElse(offer.item());
        }
        return offer.item();
    }

    public boolean isSameGuild(UUID first, UUID second) {
        String g1 = playerGuildCache.get(first);
        String g2 = playerGuildCache.get(second);
        return g1 != null && g1.equals(g2);
    }

    public boolean isFriendlyFireEnabled(UUID anyMember) {
        String guildId = playerGuildCache.get(anyMember);
        if (guildId == null) {
            return false;
        }
        return guildFriendlyFireCache.getOrDefault(guildId, false);
    }

    private void toggleFriendlyFire(Player player, GuildRecord guild) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_FF_TOGGLE).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: дружественный огонь."));
                return;
            }
            boolean next = !guild.friendlyFire();
            repository.setFriendlyFire(guild.id(), next).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                guildFriendlyFireCache.put(guild.id(), next);
                messages.send(player, "guild.friendly-fire-updated", "&eДружественный огонь: &f<state>&e.",
                    new MessageService.Placeholder("state", next ? "включён" : "выключен"));
                repository.appendActivity(guild.id(), player.getName(), "FRIENDLY_FIRE", next ? "включён" : "выключен");
                openEntry(player);
            }));
        });
    }

    private void cycleMotd(Player player, GuildRecord guild) {
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_MOTD_EDIT).thenAccept(allowed -> {
            if (!allowed) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(player, "general.no-permission", "&cНет права: слоган гильдии."));
                return;
            }
            String[] presets = {"Добро пожаловать в гильдию!", "Фармим, строим, побеждаем.", "Только актив и командная игра."};
            int index = 0;
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equalsIgnoreCase(guild.motd())) {
                    index = i;
                    break;
                }
            }
            String next = presets[(index + 1) % presets.length];
            repository.setMotd(guild.id(), next).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                messages.send(player, "guild.motd-updated", "&aСлоган гильдии обновлён.");
                repository.appendActivity(guild.id(), player.getName(), "MOTD", next);
                openEntry(player);
            }));
        });
    }

    private String rootCause(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "unknown" : cursor.getMessage();
    }

    private String userError(Throwable error) {
        String cause = rootCause(error);
        String lower = cause.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("insufficient balance") || lower.contains("not enough balance")) {
            return "Недостаточно денег на балансе для создания гильдии.";
        }
        if (lower.contains("timeout")) {
            return "Таймаут операции. Попробуйте еще раз.";
        }
        return cause;
    }

    FileConfiguration moduleConfig() {
        return configs.module("modules/guilds.yml");
    }

    FileConfiguration guiConfig() {
        return configs.module("modules/guilds-gui.yml");
    }

    FileConfiguration shopConfig() {
        return configs.module("modules/guilds-shop.yml");
    }

    FileConfiguration levelsConfig() {
        return configs.module("modules/guilds-levels.yml");
    }

    private List<Integer> levelSlots() {
        return levelsConfig().getIntegerList("menu.snake-slots");
    }

    CompletableFuture<Optional<GuildRecord>> refreshEffectiveGuildLevel(String guildId) {
        return repository.claimedGuildQuestIds(guildId).thenCompose(claimed -> repository.byId(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            GuildRecord g = opt.get();
            int gl = guildLevelFromClaimCount(claimed.size());
            if (gl != g.level()) {
                return repository.setLevel(guildId, gl).thenApply(v -> Optional.of(
                    new GuildRecord(g.id(), g.name(), g.ownerUuid(), g.createdAt(), g.bankBalance(),
                        g.guildCoins(), g.guildPoints(), gl, g.chestRows(), g.shopTier(), g.memberSlotsBonus(),
                        g.motd(), g.friendlyFire(), g.tagColor())));
            }
            return CompletableFuture.completedFuture(Optional.of(g));
        }));
    }

    /** Уровень гильдии 1–5 для прокачек: по числу забранных наград за квесты (каждые 4 квеста +1). */
    static int guildLevelFromClaimCount(int claimedQuests) {
        return Math.min(5, 1 + claimedQuests / 4);
    }

    static int guildTierFromQuestId(int questId) {
        return Math.min(5, 1 + (Math.max(1, questId) - 1) / 4);
    }

    static String guildNextTierHint(int claimedQuests) {
        if (claimedQuests >= 21) {
            return "&aЦепочка квестов завершена.";
        }
        int gl = guildLevelFromClaimCount(claimedQuests);
        if (gl < 5) {
            int nextAt = (claimedQuests / 4 + 1) * 4;
            int left = nextAt - claimedQuests;
            return "&7До ур. &f" + (gl + 1) + " &7гильдии: &e" + left + " &7квест(а)";
        }
        return "&7До финала цепочки: &e" + (21 - claimedQuests) + " &7квест(а)";
    }

    private boolean objectivesSatisfied(LevelDef def, long[] prog) {
        if (def.trackedObjectives().isEmpty()) {
            return true;
        }
        if (prog == null) {
            return false;
        }
        for (int i = 0; i < def.trackedObjectives().size(); i++) {
            long p = i < prog.length ? prog[i] : 0;
            if (p < def.trackedObjectives().get(i).amount()) {
                return false;
            }
        }
        return true;
    }

    private List<LevelDef> levelDefs() {
        List<LevelDef> defs = new ArrayList<>();
        ConfigurationSection section = levelsConfig().getConfigurationSection("quests");
        if (section == null) {
            section = levelsConfig().getConfigurationSection("levels");
        }
        if (section == null) {
            return defs;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection cfg = section.getConfigurationSection(key);
            if (cfg == null) {
                continue;
            }
            int lvl = Integer.parseInt(key);
            long req = cfg.getLong("required-points", 0L);
            int gTier = cfg.getInt("guild-tier", guildTierFromQuestId(lvl));
            gTier = Math.max(1, Math.min(5, gTier));
            String display = cfg.getString("display", "Квест " + lvl);
            List<String> textLore = new ArrayList<>();
            List<GuildObjectiveDef> tracked = new ArrayList<>();
            Object rawObj = cfg.get("objectives");
            if (rawObj instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) {
                        continue;
                    }
                    GuildObjectiveType t = GuildObjectiveType.parse(String.valueOf(m.get("type")));
                    int amt = 1;
                    Object am = m.get("amount");
                    if (am instanceof Number n) {
                        amt = Math.max(1, n.intValue());
                    } else if (am != null) {
                        try {
                            amt = Math.max(1, Integer.parseInt(am.toString()));
                        } catch (NumberFormatException ignored) {
                            amt = 1;
                        }
                    }
                    String mat = m.get("material") != null ? m.get("material").toString() : "";
                    String ent = m.get("entity") != null ? m.get("entity").toString() : "";
                    tracked.add(new GuildObjectiveDef(t, mat, ent, amt));
                }
                textLore.addAll(cfg.getStringList("objectives-text"));
            } else {
                textLore.addAll(cfg.getStringList("objectives"));
                if (textLore.isEmpty()) {
                    textLore = new ArrayList<>(List.of(
                        "&7Общий прогресс квестов гильдии.",
                        "&7См. mob-rewards в guilds.yml для очков."));
                }
            }
            LevelRewardBundle bundle = parseLevelRewardBundle(cfg);
            ItemStack preview = previewIconForLevel(cfg, bundle);
            defs.add(new LevelDef(lvl, gTier, req, display, List.copyOf(textLore), List.copyOf(tracked), bundle, preview));
        }
        defs.sort(java.util.Comparator.comparingInt(LevelDef::level));
        return defs;
    }

    private LevelRewardBundle parseLevelRewardBundle(ConfigurationSection cfg) {
        ConfigurationSection rewardsRoot = cfg.getConfigurationSection("rewards");
        if (rewardsRoot != null) {
            BigDecimal money = BigDecimal.ZERO;
            try {
                String raw = rewardsRoot.getString("money", "0");
                if (raw != null && !raw.isBlank()) {
                    money = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Exception ignored) {
                money = BigDecimal.ZERO;
            }
            List<ItemStack> plain = new ArrayList<>();
            List<?> itemList = rewardsRoot.getList("items");
            if (itemList != null) {
                for (Object o : itemList) {
                    if (!(o instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Object mat = map.get("material");
                    if (mat == null) {
                        continue;
                    }
                    Material m = Material.matchMaterial(mat.toString());
                    if (m == null) {
                        continue;
                    }
                    int amount = 1;
                    Object amt = map.get("amount");
                    if (amt instanceof Number n) {
                        amount = Math.max(1, n.intValue());
                    } else if (amt != null) {
                        try {
                            amount = Math.max(1, Integer.parseInt(amt.toString()));
                        } catch (NumberFormatException ignored) {
                            amount = 1;
                        }
                    }
                    ItemStack stack = new ItemStack(m, amount);
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        Object name = map.get("name");
                        if (name != null) {
                            meta.displayName(FoxariaText.legacy(name.toString()));
                        }
                        Object loreObj = map.get("lore");
                        if (loreObj instanceof List<?> loreList) {
                            List<String> sl = new ArrayList<>();
                            for (Object line : loreList) {
                                sl.add(String.valueOf(line));
                            }
                            List<net.kyori.adventure.text.Component> lc = FoxariaText.legacyLore(sl);
                            if (!lc.isEmpty()) {
                                meta.lore(lc);
                            }
                        }
                        stack.setItemMeta(meta);
                    }
                    plain.add(stack);
                }
            }
            List<TemplateRewardEntry> tpl = new ArrayList<>();
            List<?> tplList = rewardsRoot.getList("templates");
            if (tplList != null) {
                for (Object o : tplList) {
                    if (!(o instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Object id = map.get("id");
                    if (id == null) {
                        continue;
                    }
                    int amount = 1;
                    Object amt = map.get("amount");
                    if (amt instanceof Number n) {
                        amount = Math.max(1, n.intValue());
                    } else if (amt != null) {
                        try {
                            amount = Math.max(1, Integer.parseInt(amt.toString()));
                        } catch (NumberFormatException ignored) {
                            amount = 1;
                        }
                    }
                    tpl.add(new TemplateRewardEntry(id.toString(), amount));
                }
            }
            return new LevelRewardBundle(money, List.copyOf(plain), List.copyOf(tpl));
        }
        ConfigurationSection reward = cfg.getConfigurationSection("reward");
        List<ItemStack> plain = new ArrayList<>();
        if (reward != null) {
            Material mat = Material.matchMaterial(reward.getString("material", "EMERALD"));
            if (mat == null) {
                mat = Material.EMERALD;
            }
            ItemStack stack = new ItemStack(mat, Math.max(1, reward.getInt("amount", 1)));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(FoxariaText.legacy(reward.getString("name", "&aНаграда")));
                List<net.kyori.adventure.text.Component> lore = FoxariaText.legacyLore(reward.getStringList("lore"));
                if (!lore.isEmpty()) {
                    meta.lore(lore);
                }
                stack.setItemMeta(meta);
            }
            plain.add(stack);
        }
        return new LevelRewardBundle(BigDecimal.ZERO, List.copyOf(plain), List.of());
    }

    private ItemStack previewIconForLevel(ConfigurationSection cfg, LevelRewardBundle bundle) {
        ConfigurationSection rewardsRoot = cfg.getConfigurationSection("rewards");
        if (rewardsRoot != null) {
            List<?> itemList = rewardsRoot.getList("items");
            if (itemList != null && !itemList.isEmpty() && itemList.getFirst() instanceof Map<?, ?> map) {
                Object mat = map.get("material");
                if (mat != null) {
                    Material m = Material.matchMaterial(mat.toString());
                    if (m != null) {
                        return new ItemStack(m, 1);
                    }
                }
            }
        }
        ConfigurationSection reward = cfg.getConfigurationSection("reward");
        if (reward != null) {
            Material mat = Material.matchMaterial(reward.getString("material", "EMERALD"));
            if (mat != null) {
                return new ItemStack(mat, 1);
            }
        }
        if (!bundle.plainItems().isEmpty()) {
            return bundle.plainItems().getFirst().clone();
        }
        return new ItemStack(Material.EMERALD, 1);
    }

    void openLevels(Player player, GuildRecord guild) {
        List<LevelDef> defs = levelDefs();
        if (defs.isEmpty()) {
            messages.send(player, "guild.levels-config-missing",
                "&cКвесты гильдии не настроены: проверьте файл &emodules/guilds-levels.yml &cна сервере.");
            return;
        }
        refreshEffectiveGuildLevel(guild.id()).thenCompose(freshOpt -> {
            GuildRecord g = freshOpt.orElse(guild);
            return repository.loadAllObjectiveProgress(g.id()).thenCompose(progressMap ->
                repository.claimedGuildQuestIds(g.id()).thenApply(claimed -> Map.entry(g, Map.entry(progressMap, claimed))));
        }).whenComplete((triple, ex) -> {
            if (ex != null) {
                plugin.getLogger().log(Level.SEVERE, "openLevels: не удалось загрузить данные квестов гильдии", ex);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage("§cНе удалось открыть квесты гильдии. Попробуйте через несколько секунд.");
                    }
                });
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || triple == null) {
                    return;
                }
                GuildRecord g = triple.getKey();
                Map<Integer, long[]> progressMap = triple.getValue().getKey();
                Set<Integer> claimed = triple.getValue().getValue();
                menuManager.open(player, new GuildLevelsMenu(this, g, g.level(), defs, claimed, progressMap));
            });
        });
    }

    void openStatistics(Player player, GuildRecord guild, List<GuildMemberRecord> members) {
        repository.claimedGuildQuestIds(guild.id()).thenAccept(claimed ->
            plugin.getServer().getScheduler().runTask(plugin, () ->
                menuManager.open(player, new GuildStatisticsMenu(this, guild, members, claimed.size()))));
    }

    void claimLevelReward(Player player, GuildRecord guild, LevelDef def) {
        int qid = def.level();
        hasRolePermission(guild.id(), player.getUniqueId(), PERM_LEVEL_REWARD_CLAIM).thenCompose(allowed -> {
            if (!allowed) {
                messages.send(player, "guild.level-reward-no-permission", "&cНет права забирать награды уровня гильдии.");
                return CompletableFuture.completedFuture(false);
            }
            return repository.claimedGuildQuestIds(guild.id()).thenCompose(claimed -> {
                if (qid > 1 && !claimed.contains(qid - 1)) {
                    messages.send(player, "guild.level-reward-locked", "&cСначала завершите предыдущий квест гильдии.");
                    return CompletableFuture.completedFuture(false);
                }
                if (claimed.contains(qid)) {
                    messages.send(player, "guild.level-reward-claimed", "&eНаграда за этот квест уже получена.");
                    return CompletableFuture.completedFuture(false);
                }
                return repository.loadAllObjectiveProgress(guild.id()).thenCompose(progressMap -> {
                    if (!objectivesSatisfied(def, progressMap.get(qid))) {
                        messages.send(player, "guild.level-reward-locked", "&cЦели квеста ещё не выполнены.");
                        return CompletableFuture.completedFuture(false);
                    }
                    return repository.markLevelRewardClaimed(guild.id(), qid, player.getUniqueId()).thenCompose(v ->
                        grantLevelRewardsToGuild(guild.id(), def.rewards()).thenCompose(ignored ->
                            refreshEffectiveGuildLevel(guild.id()).thenApply(og -> true)));
                });
            });
        }).thenAccept(ok -> {
            if (!ok) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                messages.send(player, "guild.level-reward-success", "&aНаграда за уровень зачислена в казну и общий сундук.");
                openEntry(player);
            });
        });
    }

    private CompletableFuture<Void> grantLevelRewardsToGuild(String guildId, LevelRewardBundle bundle) {
        if (!bundle.hasAny()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        if (bundle.moneyToBank().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal add = bundle.moneyToBank().setScale(2, RoundingMode.HALF_UP);
            chain = chain.thenCompose(v -> repository.changeBank(guildId, add).thenApply(b -> null));
        }
        chain = chain.thenCompose(v -> repository.byId(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            GuildRecord g = opt.get();
            List<ItemStack> stacks = new ArrayList<>(bundle.plainItems());
            if (itemTemplates != null) {
                for (TemplateRewardEntry te : bundle.templateEntries()) {
                    itemTemplates.cloneTemplate(te.templateId()).ifPresent(is -> {
                        ItemStack c = is.clone();
                        c.setAmount(Math.min(c.getMaxStackSize(), Math.max(1, te.amount())));
                        stacks.add(c);
                    });
                }
            }
            if (stacks.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return repository.chest(guildId, g.chestRows()).thenCompose(snapshot ->
                mergeItemsIntoGuildChest(guildId, snapshot, stacks));
        }));
        return chain;
    }

    private CompletableFuture<Void> mergeItemsIntoGuildChest(String guildId, GuildChestSnapshot snapshot, List<ItemStack> toAdd) {
        int maxSlots = Math.max(1, snapshot.rows()) * 9;
        Map<Integer, ItemStack> map = new HashMap<>(snapshot.items());
        for (ItemStack add : toAdd) {
            if (add == null || add.getType() == Material.AIR) {
                continue;
            }
            int remaining = add.getAmount();
            ItemStack prototype = add.clone();
            while (remaining > 0) {
                for (int s = 0; s < maxSlots; s++) {
                    ItemStack slot = map.get(s);
                    if (slot != null && slot.getType() != Material.AIR && slot.isSimilar(prototype) && slot.getAmount() < slot.getMaxStackSize()) {
                        int space = slot.getMaxStackSize() - slot.getAmount();
                        int move = Math.min(space, remaining);
                        slot.setAmount(slot.getAmount() + move);
                        remaining -= move;
                        if (remaining <= 0) {
                            break;
                        }
                    }
                }
                if (remaining <= 0) {
                    break;
                }
                int empty = -1;
                for (int s = 0; s < maxSlots; s++) {
                    ItemStack slot = map.get(s);
                    if (slot == null || slot.getType() == Material.AIR) {
                        empty = s;
                        break;
                    }
                }
                if (empty < 0) {
                    break;
                }
                int chunk = Math.min(remaining, prototype.getMaxStackSize());
                ItemStack stack = prototype.clone();
                stack.setAmount(chunk);
                map.put(empty, stack);
                remaining -= chunk;
            }
        }
        return repository.saveChest(guildId, map);
    }

    void registerGuildChestBaseline(Player player, GuildChestSnapshot snapshot) {
        chestDepositBaselines.put(player.getUniqueId(), new ChestDepositBaseline(snapshot.guildId(), countMaterialsInSlots(snapshot.items())));
    }

    private static Map<Material, Integer> countMaterialsInSlots(Map<Integer, ItemStack> items) {
        Map<Material, Integer> out = new HashMap<>();
        for (ItemStack is : items.values()) {
            if (is != null && is.getType() != Material.AIR) {
                out.merge(is.getType(), is.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    private static Map<Material, Integer> countMaterialsInInventory(Inventory inventory) {
        Map<Material, Integer> out = new HashMap<>();
        for (ItemStack is : inventory.getContents()) {
            if (is != null && is.getType() != Material.AIR) {
                out.merge(is.getType(), is.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    public void onGuildChestClosed(Player player, String guildId, Inventory inventory) {
        ChestDepositBaseline before = chestDepositBaselines.remove(player.getUniqueId());
        if (before == null || !before.guildId.equals(guildId)) {
            return;
        }
        Map<Material, Integer> after = countMaterialsInInventory(inventory);
        Map<Material, Integer> delta = new HashMap<>();
        for (Map.Entry<Material, Integer> e : after.entrySet()) {
            int diff = e.getValue() - before.materialCounts.getOrDefault(e.getKey(), 0);
            if (diff > 0) {
                delta.merge(e.getKey(), diff, Integer::sum);
            }
        }
        if (delta.isEmpty()) {
            return;
        }
        applySubmitItemDeltas(guildId, delta);
    }

    private void applySubmitItemDeltas(String guildId, Map<Material, Integer> materialDeltas) {
        repository.byId(guildId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return repository.claimedGuildQuestIds(guildId).thenCompose(claimed ->
                repository.loadAllObjectiveProgress(guildId).thenCompose(progressMap -> {
                    List<LevelDef> defs = levelDefs();
                    int active = findActiveQuestIdForProgress(claimed, progressMap, defs);
                    if (active <= 0) {
                        return CompletableFuture.completedFuture(null);
                    }
                    LevelDef def = defs.stream().filter(d -> d.level() == active).findFirst().orElse(null);
                    if (def == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Map<Integer, long[]> mut = new HashMap<>();
                    for (Map.Entry<Integer, long[]> e : progressMap.entrySet()) {
                        mut.put(e.getKey(), Arrays.copyOf(e.getValue(), e.getValue().length));
                    }
                    List<ObjectiveDelta> work = new ArrayList<>();
                    for (Map.Entry<Material, Integer> e : materialDeltas.entrySet()) {
                        Material mat = e.getKey();
                        int remaining = e.getValue();
                        for (int i = 0; i < def.trackedObjectives().size() && remaining > 0; i++) {
                            GuildObjectiveDef o = def.trackedObjectives().get(i);
                            if (o.type() != GuildObjectiveType.SUBMIT_ITEMS || !o.matchesSubmitMaterial(mat)) {
                                continue;
                            }
                            long[] arr = mut.computeIfAbsent(def.level(), lv -> new long[def.trackedObjectives().size()]);
                            if (arr.length < def.trackedObjectives().size()) {
                                long[] n = new long[def.trackedObjectives().size()];
                                System.arraycopy(arr, 0, n, 0, arr.length);
                                arr = n;
                                mut.put(def.level(), arr);
                            }
                            long cur = arr[i];
                            long cap = o.amount();
                            if (cur >= cap) {
                                continue;
                            }
                            long add = Math.min(remaining, cap - cur);
                            if (add <= 0) {
                                continue;
                            }
                            work.add(new ObjectiveDelta(def.level(), i, add));
                            arr[i] = cur + add;
                            remaining -= (int) add;
                        }
                    }
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (ObjectiveDelta d : work) {
                        chain = chain.thenCompose(v -> repository.addObjectiveProgressDelta(guildId, d.level, d.index, d.delta));
                    }
                    return chain.thenCompose(v -> refreshEffectiveGuildLevel(guildId).thenApply(x -> null));
                }));
        });
    }

    /**
     * @return номер активного квеста, 0 если нет, -2 если цели выполнены и ждём получение награды
     */
    private int findActiveQuestIdForProgress(Set<Integer> claimed, Map<Integer, long[]> progressMap, List<LevelDef> defs) {
        for (LevelDef def : defs) {
            int q = def.level();
            if (claimed.contains(q)) {
                continue;
            }
            if (q > 1 && !claimed.contains(q - 1)) {
                return 0;
            }
            if (objectivesSatisfied(def, progressMap.get(q))) {
                return -2;
            }
            return q;
        }
        return 0;
    }

    public void onGuildBlockBroken(Player player, Material mat) {
        if (mat == null || mat == Material.AIR) {
            return;
        }
        repository.byPlayer(player.getUniqueId()).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return applySingleEventProgress(opt.get().id(), GuildObjectiveType.BLOCK_BREAK, mat, null, 1L);
        });
    }

    public void onGuildEntityKill(Player player, String entityType) {
        repository.byPlayer(player.getUniqueId()).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return applySingleEventProgress(opt.get().id(), GuildObjectiveType.ENTITY_KILL, null, entityType, 1L);
        });
    }

    private CompletableFuture<Void> applySingleEventProgress(String guildId, GuildObjectiveType type, Material mat, String entityType, long delta) {
        return repository.claimedGuildQuestIds(guildId).thenCompose(claimed ->
            repository.loadAllObjectiveProgress(guildId).thenCompose(progressMap -> {
                List<LevelDef> defs = levelDefs();
                int active = findActiveQuestIdForProgress(claimed, progressMap, defs);
                if (active <= 0) {
                    return CompletableFuture.completedFuture(null);
                }
                LevelDef def = defs.stream().filter(d -> d.level() == active).findFirst().orElse(null);
                if (def == null) {
                    return CompletableFuture.completedFuture(null);
                }
                for (int i = 0; i < def.trackedObjectives().size(); i++) {
                    GuildObjectiveDef o = def.trackedObjectives().get(i);
                    if (o.type() != type) {
                        continue;
                    }
                    if (type == GuildObjectiveType.BLOCK_BREAK && (mat == null || !o.matchesBlockBreak(mat))) {
                        continue;
                    }
                    if (type == GuildObjectiveType.ENTITY_KILL && !o.matchesEntity(entityType)) {
                        continue;
                    }
                    long cur = progAt(progressMap, def.level(), i);
                    if (cur >= o.amount()) {
                        continue;
                    }
                    long add = Math.min(delta, o.amount() - cur);
                    if (add <= 0) {
                        continue;
                    }
                    return repository.addObjectiveProgressDelta(guildId, def.level(), i, add)
                        .thenCompose(v -> refreshEffectiveGuildLevel(guildId).thenApply(x -> null));
                }
                return CompletableFuture.completedFuture(null);
            }));
    }

    private static long progAt(Map<Integer, long[]> progressMap, int level, int index) {
        long[] arr = progressMap.get(level);
        if (arr == null || index < 0 || index >= arr.length) {
            return 0L;
        }
        return arr[index];
    }

    Map<String, UpgradeDef> upgradeDefs() {
        Map<String, UpgradeDef> defs = new LinkedHashMap<>();
        ConfigurationSection section = moduleConfig().getConfigurationSection("upgrades");
        if (section == null) {
            return defs;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Map<Integer, Long> costs = new HashMap<>();
            ConfigurationSection costsSection = entry.getConfigurationSection("costs");
            if (costsSection != null) {
                for (String lvl : costsSection.getKeys(false)) {
                    costs.put(Integer.parseInt(lvl), costsSection.getLong(lvl));
                }
            }
            Map<Integer, Long> bankCosts = new HashMap<>();
            ConfigurationSection bankSection = entry.getConfigurationSection("bank-costs");
            if (bankSection != null) {
                for (String lvl : bankSection.getKeys(false)) {
                    bankCosts.put(Integer.parseInt(lvl), bankSection.getLong(lvl));
                }
            }
            Map<Integer, Long> requiredKills = new HashMap<>();
            ConfigurationSection killsSection = entry.getConfigurationSection("required-kills");
            if (killsSection != null) {
                for (String lvl : killsSection.getKeys(false)) {
                    requiredKills.put(Integer.parseInt(lvl), killsSection.getLong(lvl));
                }
            }
            Map<Integer, Integer> requiredGuildLevel = new HashMap<>();
            ConfigurationSection levelSection = entry.getConfigurationSection("required-guild-level");
            if (levelSection != null) {
                for (String lvl : levelSection.getKeys(false)) {
                    requiredGuildLevel.put(Integer.parseInt(lvl), levelSection.getInt(lvl));
                }
            }
            defs.put(key, new UpgradeDef(key, entry.getString("display", key), entry.getInt("max-level", 3), costs, bankCosts, requiredKills, requiredGuildLevel));
        }
        return defs;
    }

    List<ShopOffer> shopOffers() {
        List<ShopOffer> offers = new ArrayList<>();
        ConfigurationSection section = shopConfig().getConfigurationSection("offers");
        if (section == null) {
            return offers;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String templateId = entry.getString("item-template", "").trim();
            String materialKey = entry.getString("material", templateId.isEmpty() ? "STONE" : "PAPER");
            Material material = Material.matchMaterial(materialKey);
            if (material == null) {
                material = Material.PAPER;
            }
            offers.add(new ShopOffer(
                key,
                entry.getString("display", key),
                entry.getLong("price-coins", 0L),
                entry.getInt("required-tier", 1),
                new ItemStack(material, Math.max(1, entry.getInt("amount", 1))),
                templateId
            ));
        }
        return offers;
    }

    record UpgradeDef(
        String key,
        String display,
        int maxLevel,
        Map<Integer, Long> costByLevel,
        Map<Integer, Long> bankCostByLevel,
        Map<Integer, Long> requiredKillsByLevel,
        Map<Integer, Integer> requiredGuildLevelByLevel
    ) {
    }

    record ShopOffer(String key, String display, long price, int requiredTier, ItemStack item, String itemTemplateId) {
        boolean usesTemplate() {
            return itemTemplateId != null && !itemTemplateId.isBlank();
        }
    }

    record TagColorDef(String id, String display, String colorCode, int requiredPaletteLevel) {
    }

    private static final class GuildOnboardingMenu extends BaseMenu {
        private final GuildService service;

        private GuildOnboardingMenu(GuildService service) {
            super(service.guiConfig().getString("onboarding.title", "&5&lГильдии &8| &7Foxaria"), 27);
            this.service = service;
        }

        @Override
        protected void draw(Player player) {
            for (int i = 0; i < 27; i++) {
                setItem(i, MenuItems.filler(), null);
            }
            String cost = BigDecimal.valueOf(service.moduleConfig().getDouble("creation.cost", 100000.0D))
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
            setItem(11, MenuItems.item(Material.EMERALD, "&a&lСоздать гильдию",
                "&7Команда: &e/guild create <название>",
                "&7Стоимость: &6" + cost), null);
            setItem(13, MenuItems.item(Material.BOOK, "&e&lСправка", guildHelpLore()), null);
            setItem(15, MenuItems.item(Material.NETHER_STAR, "&d&lРазвитие", "&7Качайте гильдию вместе", "&7с друзьями."), null);
        }
    }

    private static final class GuildMainMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final List<GuildMemberRecord> members;

        private GuildMainMenu(GuildService service, GuildRecord guild, List<GuildMemberRecord> members) {
            super(service.guiConfig().getString("main.title", "&5&l⚔ Гильдия &8| &f<name>").replace("<name>", guild.name()), 54);
            this.service = service;
            this.guild = guild;
            this.members = members;
        }

        @Override
        protected void draw(Player player) {
            for (int i = 0; i < 54; i++) {
                setItem(i, MenuItems.filler(), null);
            }
            for (int i = 36; i < 45; i++) {
                setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
            }
            long ageHours = Math.max(1, Duration.ofMillis(System.currentTimeMillis() - guild.createdAt()).toHours());
            String tagColor = service.colorCodeFor(guild.tagColor());
            setItem(4, MenuItems.item(Material.NAME_TAG, "&6&l" + guild.name(),
                "&8───────────────",
                "&7Участников: &e" + members.size(),
                "&7Возраст: &e" + (ageHours / 24) + " дн. &7" + (ageHours % 24) + " ч.",
                "&7Коины: &e" + guild.guildCoins(),
                "&7Очки: &e" + guild.guildPoints(),
                "&7Банк: &a" + guild.bankBalance().toPlainString(),
                "&7Тег: " + tagColor + guild.name(),
                "&7Дружественный огонь: &e" + (guild.friendlyFire() ? "Вкл" : "Выкл"),
                "&7Слоган: &f" + guild.motd()), null);

            setItem(13, MenuItems.item(Material.KNOWLEDGE_BOOK, "&e&lСправка", guildHelpLore()), null);

            setItem(20, MenuItems.item(Material.PLAYER_HEAD, "&b&lУчастники и роли", "&7Состав, ранги и настройка доступов"), e -> service.openMembers(player, guild));
            setItem(21, MenuItems.item(Material.GOLD_INGOT, "&6&lКазна", "&7Общий банк гильдии"), e -> service.openBank(player, guild));
            setItem(22, MenuItems.item(Material.ANVIL, "&d&lУлучшения", "&7Прокачка за казну и киллы"), e -> service.openUpgrades(player, guild));
            setItem(23, MenuItems.item(Material.CHEST, "&a&lМагазин гильдии", "&7Товары за коины гильдии"), e -> service.openShop(player, guild));
            setItem(24, MenuItems.item(Material.BARREL, "&e&lОбщий сундук", "&7Хранилище для всех"), e -> service.openChest(player));
            setItem(19, MenuItems.item(Material.WRITTEN_BOOK, "&f&lСтатистика", "&7Сводка по гильдии"), e -> service.openStatistics(player, guild, members));
            setItem(25, MenuItems.item(Material.EXPERIENCE_BOTTLE, "&a&lУровни и задания", "&7Совместный прогресс, награды"), e -> service.openLevels(player, guild));
            setItem(31, MenuItems.item(Material.IRON_SWORD, "&c&lГильдейские войны", "&7Арены и вызовы"), e -> service.openWarMenu(player, guild));
            setItem(39, MenuItems.item(Material.FLINT_AND_STEEL, "&6&lДружественный огонь",
                "&7Урон своим: вкл/выкл",
                "&7Доступно, если лидер выдал право в ролях"), e -> service.toggleFriendlyFire(player, guild));
            setItem(40, MenuItems.item(Material.WRITABLE_BOOK, "&b&lСлоган гильдии",
                "&7Короткий текст под названием",
                "&7Редактирование — по праву в ролях"), e -> service.cycleMotd(player, guild));
            setItem(41, MenuItems.item(Material.CLOCK, "&e&lЖурнал активности", "&7Последние события"), e -> {
                service.repository.latestActivity(guild.id(), 8).thenAccept(lines -> service.plugin.getServer().getScheduler().runTask(service.plugin, () -> {
                    if (lines.isEmpty()) {
                        player.sendMessage("§7[Гильдия] Записей пока нет.");
                        return;
                    }
                    player.sendMessage("§6--- Активность гильдии ---");
                    for (String line : lines) {
                        player.sendMessage("§7" + line);
                    }
                }));
            });
        }
    }

    private static final class GuildMembersMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final List<GuildMemberRecord> members;

        private GuildMembersMenu(GuildService service, GuildRecord guild, List<GuildMemberRecord> members) {
            super("&6Участники: " + guild.name(), 54);
            this.service = service;
            this.guild = guild;
            this.members = members;
        }

        @Override
        protected void draw(Player player) {
            // Check viewer's role to show appropriate hints
            String viewerRole = members.stream()
                .filter(m -> m.playerUuid().equals(player.getUniqueId()))
                .map(GuildMemberRecord::role)
                .findFirst().orElse("MEMBER");
            boolean canManage = "OWNER".equalsIgnoreCase(viewerRole)
                || "MASTER".equalsIgnoreCase(viewerRole)
                || "LEADER".equalsIgnoreCase(viewerRole);

            int slot = 10;
            for (GuildMemberRecord member : members) {
                if (slot >= 44) break;

                boolean isSelf = member.playerUuid().equals(player.getUniqueId());
                boolean isOwner = "OWNER".equalsIgnoreCase(member.role());

                String roleColor = switch (member.role().toUpperCase()) {
                    case "OWNER"   -> "&4&l";
                    case "MASTER"  -> "&6&l";
                    case "LEADER"  -> "&e";
                    case "OFFICER" -> "&b";
                    case "ADMIN"   -> "&d";
                    default        -> "&7";
                };

                String[] lore;
                if (canManage && !isSelf && !isOwner) {
                    lore = new String[]{
                        "&7Роль: " + roleColor + member.role(),
                        "",
                        "&a▲ ЛКМ &7— Повысить",
                        "&c▼ ПКМ &7— Понизить"
                    };
                } else {
                    lore = new String[]{"&7Роль: " + roleColor + member.role()};
                }

                // Non-blocking head: uses PlayerProfile without Mojang API call
                final GuildMemberRecord m = member;
                setItem(slot, MenuItems.headByProfile(member.playerUuid(), member.playerName(),
                    "&e" + member.playerName(), lore), canManage && !isSelf && !isOwner ? e -> {
                        if (e.isLeftClick()) {
                            service.promoteAndRefresh(player, guild, m.playerName());
                        } else if (e.isRightClick()) {
                            service.demoteAndRefresh(player, guild, m.playerName());
                        }
                    } : null);

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            setItem(46, MenuItems.item(Material.COMPARATOR, "&dПрава ролей", "&7Настроить доступы"), e -> service.openRoleSettings(player, guild));
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openMain(player, guild));
        }
    }

    private static final class GuildRoleSettingsMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final Map<String, Set<String>> roleFlags;

        private GuildRoleSettingsMenu(GuildService service, GuildRecord guild, Map<String, Set<String>> roleFlags) {
            super("&5&lПрава ролей &8| &7" + guild.name(), 54);
            this.service = service;
            this.guild = guild;
            this.roleFlags = roleFlags;
        }

        @Override
        protected void draw(Player player) {
            for (int i = 0; i < 54; i++) {
                setItem(i, MenuItems.filler(), null);
            }
            for (int i = 36; i < 45; i++) {
                setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
            }
            setItem(4, MenuItems.item(Material.WRITTEN_BOOK, "&d&lНастройка доступов",
                "&7Слева колонка — &eофицер&7, справа — &aучастник&7.",
                "&7Флаги можно комбинировать как удобно гильдии.",
                "&8 ",
                "&7OWNER всегда имеет полный доступ."), null);
            for (int idx = 0; idx < GUILD_ROLE_PERM_ROWS.size(); idx++) {
                Map.Entry<String, String> entry = GUILD_ROLE_PERM_ROWS.get(idx);
                int row = idx / 3;
                int col = idx % 3;
                int offBase = 10 + row * 9 + col;
                int memBase = 14 + row * 9 + col;
                drawFlag(service, player, guild, roleFlags, "OFFICER", offBase, entry.getKey(), entry.getValue());
                drawFlag(service, player, guild, roleFlags, "MEMBER", memBase, entry.getKey(), entry.getValue());
            }
            setItem(49, MenuItems.item(Material.ARROW, "&7◄ Назад к участникам"), e -> service.openMembers(player, guild));
        }

        private void drawFlag(GuildService service, Player player, GuildRecord guild, Map<String, Set<String>> roleFlags,
            String roleId, int slot, String perm, String coloredLabel) {
            Set<String> flags = roleFlags.getOrDefault(roleId, Set.of());
            boolean enabled = flags.contains(perm) || flags.contains("ALL");
            String shortRole = "OFFICER".equals(roleId) ? "&6Офицер" : "&aУчастник";
            setItem(slot, MenuItems.item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                shortRole + " &8| " + coloredLabel,
                "&7Состояние: " + (enabled ? "&aВкл" : "&cВыкл"),
                "&7Клик — переключить"),
                e -> service.toggleRolePermission(player, guild, roleId, perm));
        }
    }

    private static final class GuildBankMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;

        private GuildBankMenu(GuildService service, GuildRecord guild) {
            super("&5&lКазна гильдии &8| &7" + guild.name(), 54);
            this.service = service;
            this.guild = guild;
        }

        @Override
        protected void draw(Player player) {
            for (int i = 0; i < 54; i++) {
                setItem(i, MenuItems.filler(), null);
            }
            for (int i = 36; i < 45; i++) {
                setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
            }
            String bal = guild.bankBalance().toPlainString();
            setItem(13, MenuItems.item(Material.GOLD_BLOCK, "&6&lБаланс казны",
                "&8─────────────────",
                "&7Сейчас в казне: &a" + bal,
                "&7Коины гильдии (отдельно): &e" + guild.guildCoins(),
                "&8─────────────────",
                "&7Вклад и снятие — кнопки ниже (зелёные / красные).",
                "&7Доступ настраивает лидер в ролях."), null);

            int[] amounts = {100, 1000, 10000, 100000};
            int[] depSlots = {27, 28, 29, 30};
            int[] witSlots = {32, 33, 34, 35};
            for (int i = 0; i < amounts.length; i++) {
                int a = amounts[i];
                BigDecimal bd = BigDecimal.valueOf(a).setScale(2, RoundingMode.HALF_UP);
                setItem(depSlots[i], MenuItems.item(Material.LIME_CONCRETE, "&a&l+ " + a,
                    "&7Внести из своего баланса", "&7Сумма: &a" + bd.toPlainString()),
                    e -> service.bankDeposit(player, guild, bd));
                setItem(witSlots[i], MenuItems.item(Material.RED_CONCRETE, "&c&l− " + a,
                    "&7Снять на свой баланс", "&7Сумма: &c" + bd.toPlainString()),
                    e -> service.bankWithdraw(player, guild, bd));
            }
            setItem(4, MenuItems.item(Material.PAPER, "&e&lПодсказка",
                "&7Казна — деньги на улучшения гильдии.",
                "&7Коины гильдии — отдельно: магазин гильдии.",
                "&7См. также «Справка» в главном меню гильдии."), null);
            setItem(49, MenuItems.item(Material.ARROW, "&7◄ Назад"), e -> service.openMain(player, guild));
        }
    }

    private static final class GuildUpgradesMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final Map<String, Integer> levels;
        private final long kills;

        private GuildUpgradesMenu(GuildService service, GuildRecord guild, Map<String, Integer> levels, long kills) {
            super("&dУлучшения: " + guild.name(), 54);
            this.service = service;
            this.guild = guild;
            this.levels = levels;
            this.kills = kills;
        }

        @Override
        protected void draw(Player player) {
            int slot = 19;
            for (UpgradeDef def : service.upgradeDefs().values()) {
                int level = levels.getOrDefault(def.key(), 0);
                int next = level + 1;
                long nextCost = def.bankCostByLevel().getOrDefault(next, 0L);
                long reqKills = def.requiredKillsByLevel().getOrDefault(next, 0L);
                int reqGuildLevel = def.requiredGuildLevelByLevel().getOrDefault(next, 1);
                setItem(slot, MenuItems.item(Material.ENCHANTED_BOOK, "&d" + def.display(),
                    "&7Уровень: &e" + level + "/" + def.maxLevel(),
                    "&7Цена след. уровня: &6" + nextCost + " из казны",
                    "&7Треб. уровень гильдии: &a" + reqGuildLevel,
                    "&7Треб. убийств: &c" + reqKills + " &7(сейчас: &f" + kills + "&7)"), e -> service.buyUpgrade(player, guild, def.key()));
                slot += 2;
            }
            setItem(47, MenuItems.item(Material.NAME_TAG, "&bЦвет тега", "&7Открыть доступные цвета"), e -> service.openTagColorMenu(player, guild));
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openMain(player, guild));
        }
    }

    private static final class GuildShopMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;

        private GuildShopMenu(GuildService service, GuildRecord guild) {
            super("&aМагазин: " + guild.name(), 54);
            this.service = service;
            this.guild = guild;
        }

        @Override
        protected void draw(Player player) {
            int slot = 10;
            for (ShopOffer offer : service.shopOffers()) {
                ItemStack icon = service.shopOfferIcon(offer).clone();
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.displayName(FoxariaText.legacy("&a" + offer.display()));
                    List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(FoxariaText.legacy("&8─────────────"));
                    lore.add(FoxariaText.legacy("&7Цена: &e" + offer.price() + " коинов"));
                    lore.add(FoxariaText.legacy("&7Тир: &b" + offer.requiredTier()));
                    if (offer.usesTemplate()) {
                        lore.add(FoxariaText.legacy("&7Шаблон: &f" + offer.itemTemplateId()));
                    }
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                }
                setItem(slot, icon, e -> service.buyShop(player, guild, offer));
                slot++;
                if (slot % 9 == 8) {
                    slot += 2;
                }
                if (slot >= 44) {
                    break;
                }
            }
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openMain(player, guild));
        }
    }

    private static final class GuildTagColorMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final int paletteLevel;

        private GuildTagColorMenu(GuildService service, GuildRecord guild, int paletteLevel) {
            super("&bЦвет тега: " + guild.name(), 54);
            this.service = service;
            this.guild = guild;
            this.paletteLevel = paletteLevel;
        }

        @Override
        protected void draw(Player player) {
            List<TagColorDef> unlocked = service.tagColorDefs().stream()
                .filter(def -> def.requiredPaletteLevel() <= paletteLevel)
                .toList();
            if (unlocked.isEmpty()) {
                setItem(22, MenuItems.item(Material.BARRIER, "&7Нет доступных цветов", "&7Прокачай улучшение &eЦвет тега"), null);
            } else {
                int slot = 10;
                for (TagColorDef def : unlocked) {
                    if (slot >= 44) {
                        break;
                    }
                    char code = def.colorCode().replace("&", "").isEmpty() ? 'f' : def.colorCode().replace("&", "").charAt(0);
                    setItem(slot, MenuItems.item(service.woolByCode(code),
                        def.colorCode() + def.display(),
                        "&7Треб. палитра: &e" + def.requiredPaletteLevel(),
                        guild.tagColor().equalsIgnoreCase(def.id()) ? "&aСейчас выбран" : "&7Нажми для выбора"),
                        e -> service.setTagColor(player, guild, def.id()));
                    slot++;
                    if (slot % 9 == 8) {
                        slot += 2;
                    }
                }
            }
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openUpgrades(player, guild));
        }
    }

    private static final class GuildWarMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final WarUiState state;

        private GuildWarMenu(GuildService service, GuildRecord guild, WarUiState state) {
            super("&cВойны: " + guild.name(), 36);
            this.service = service;
            this.guild = guild;
            this.state = state;
        }

        @Override
        protected void draw(Player player) {
            int size = service.warTeamSize(player);
            String arena = service.warArena(player);
            if (arena.isBlank() && !state.arenas().isEmpty()) {
                arena = state.arenas().getFirst().arenaId();
            }
            setItem(11, MenuItems.item(Material.PLAYER_HEAD, "&eОнлайн гильдии", "&7Список доступных для вызова"), e -> service.openWarGuildList(player, guild));
            setItem(13, MenuItems.item(state.permissions().canQueue() ? Material.IRON_SWORD : Material.GRAY_DYE,
                "&c&lРазмер войны", "&7Текущий: &e" + size + " на " + size,
                "&7Доступ: " + (state.permissions().canQueue() ? "&aВкл" : "&cВыкл"),
                "&7Клик — сменить"),
                state.permissions().canQueue() ? e -> service.cycleWarTeamSize(player) : null);
            setItem(15, MenuItems.item(state.permissions().canSelectArena() ? Material.MAP : Material.GRAY_DYE,
                "&b&lАрена", "&7Текущая: &e" + (arena.isBlank() ? "авто" : arena),
                "&7Доступ: " + (state.permissions().canSelectArena() ? "&aВкл" : "&cВыкл"),
                "&7Клик — сменить"),
                state.permissions().canSelectArena() ? e -> service.cycleWarArena(player) : null);
            setItem(20, MenuItems.item(state.permissions().canQueue() ? Material.LIME_BANNER : Material.GRAY_BANNER,
                "&a&lВстать в очередь",
                "&7Доступ: " + (state.permissions().canQueue() ? "&aВкл" : "&cВыкл")),
                state.permissions().canQueue() ? e -> service.joinWarQueue(player, guild) : null);
            setItem(21, MenuItems.item(Material.RED_BANNER, "&c&lПокинуть очередь"), e -> service.leaveWarQueue(player));
            setItem(23, MenuItems.item(state.permissions().canAccept() ? Material.BOOK : Material.GRAY_DYE,
                "&6&lВходящие вызовы", "&7Доступ: " + (state.permissions().canAccept() ? "&aВкл" : "&cВыкл")),
                state.permissions().canAccept() ? e -> service.openWarInvites(player, guild) : null);
            setItem(24, MenuItems.item(Material.COMPARATOR, "&d&lНастройки войн", "&7Формат и арена"), e -> service.openWarSettings(player, guild));
            setItem(25, MenuItems.item(Material.CLOCK, "&e&lИстория войн", "&7Последние события"), e -> service.openWarHistory(player, guild));
            setItem(31, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openMain(player, guild));
        }
    }

    private static final class GuildWarGuildListMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final WarGuildListState state;
        private final int page;

        private GuildWarGuildListMenu(GuildService service, GuildRecord guild, WarGuildListState state, int page) {
            super("&6Онлайн гильдии", 54);
            this.service = service;
            this.guild = guild;
            this.state = state;
            this.page = Math.max(0, page);
        }

        @Override
        protected void draw(Player player) {
            int perPage = 28;
            int start = page * perPage;
            int end = Math.min(state.onlineGuilds().size(), start + perPage);
            int slot = 10;
            for (int i = start; i < end; i++) {
                OnlineGuildView view = state.onlineGuilds().get(i);
                setItem(slot, MenuItems.item(state.canInvite() ? Material.SHIELD : Material.GRAY_DYE, "&e&l" + view.name(),
                    "&7Онлайн: &a" + view.online(),
                    "&7Всего: &f" + view.total(),
                    "&7Вызов на войну: " + (state.canInvite() ? "&aдоступен" : "&cнедоступен")),
                    state.canInvite() ? e -> service.sendWarInvite(player, guild, view.name()) : null);
                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            if (start > 0) {
                setItem(45, MenuItems.item(Material.ARROW, "&7Предыдущая"), e -> service.openWarGuildList(player, guild, page - 1));
            }
            if (end < state.onlineGuilds().size()) {
                setItem(53, MenuItems.item(Material.ARROW, "&7Следующая"), e -> service.openWarGuildList(player, guild, page + 1));
            }
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openWarMenu(player, guild));
        }
    }

    private static final class GuildWarInvitesMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final Optional<GuildWarEngine.IncomingInvite> invite;

        private GuildWarInvitesMenu(GuildService service, GuildRecord guild, Optional<GuildWarEngine.IncomingInvite> invite) {
            super("&6Входящие вызовы", 27);
            this.service = service;
            this.guild = guild;
            this.invite = invite;
        }

        @Override
        protected void draw(Player player) {
            if (invite.isEmpty()) {
                setItem(13, MenuItems.item(Material.BARRIER, "&7Нет активных вызовов"), null);
            } else {
                GuildWarEngine.IncomingInvite i = invite.get();
                setItem(11, MenuItems.item(Material.LIME_STAINED_GLASS_PANE, "&a&lПринять вызов",
                    "&7Формат: &e" + i.teamSize() + " на " + i.teamSize(),
                    "&7Арена: &b" + (i.arenaId() == null || i.arenaId().isBlank() ? "авто" : i.arenaId())),
                    e -> service.acceptWarInvite(player, i.challengerGuildId()));
                setItem(15, MenuItems.item(Material.RED_STAINED_GLASS_PANE, "&c&lОтклонить"),
                    e -> service.denyWarInvite(player, i.challengerGuildId()));
            }
            setItem(22, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openWarMenu(player, guild));
        }
    }

    private static final class GuildWarSettingsMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final WarUiState state;

        private GuildWarSettingsMenu(GuildService service, GuildRecord guild, WarUiState state) {
            super("&dНастройки войн", 27);
            this.service = service;
            this.guild = guild;
            this.state = state;
        }

        @Override
        protected void draw(Player player) {
            int size = service.warTeamSize(player);
            String arena = service.warArena(player);
            if (arena.isBlank() && !state.arenas().isEmpty()) arena = state.arenas().getFirst().arenaId();

            setItem(11, MenuItems.item(state.permissions().canQueue() ? Material.IRON_SWORD : Material.GRAY_DYE,
                "&c&lФормат войны", "&7Текущий: &e" + size + " на " + size,
                "&7Доступ: " + (state.permissions().canQueue() ? "&aВкл" : "&cВыкл")),
                state.permissions().canQueue() ? e -> service.cycleWarTeamSize(player) : null);
            setItem(13, MenuItems.item(state.permissions().canSelectArena() ? Material.MAP : Material.GRAY_DYE,
                "&b&lВыбор арены", "&7Текущая: &e" + (arena.isBlank() ? "авто" : arena),
                "&7Доступ: " + (state.permissions().canSelectArena() ? "&aВкл" : "&cВыкл")),
                state.permissions().canSelectArena() ? e -> service.cycleWarArena(player) : null);
            setItem(15, MenuItems.item(Material.BOOK, "&6Права доступа", "&7Управляется в меню ролей"), e -> service.openRoleSettings(player, guild));
            setItem(22, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openWarMenu(player, guild));
        }
    }

    private static final class GuildWarHistoryMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final List<String> lines;

        private GuildWarHistoryMenu(GuildService service, GuildRecord guild, List<String> lines) {
            super("&eИстория войн", 54);
            this.service = service;
            this.guild = guild;
            this.lines = lines;
        }

        @Override
        protected void draw(Player player) {
            if (lines.isEmpty()) {
                setItem(22, MenuItems.item(Material.BARRIER, "&7Нет данных"), null);
            } else {
                int slot = 10;
                for (String line : lines) {
                    if (slot >= 44) break;
                    setItem(slot, MenuItems.item(Material.PAPER, "&eСобытие", "&7" + line), null);
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            }
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openWarMenu(player, guild));
        }
    }

    private static final class GuildStatisticsMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final List<GuildMemberRecord> members;
        private final int claimedGuildQuests;

        private GuildStatisticsMenu(GuildService service, GuildRecord guild, List<GuildMemberRecord> members, int claimedGuildQuests) {
            super("&bСтатистика: " + guild.name(), 54);
            this.service = service;
            this.guild = guild;
            this.members = members;
            this.claimedGuildQuests = claimedGuildQuests;
        }

        @Override
        protected void draw(Player player) {
            long ageHours = Math.max(1, Duration.ofMillis(System.currentTimeMillis() - guild.createdAt()).toHours());
            String hint = GuildService.guildNextTierHint(claimedGuildQuests);
            setItem(10, MenuItems.item(Material.NAME_TAG, "&6&lОсновное",
                "&7Название: &e" + guild.name(),
                "&7Возраст: &e" + (ageHours / 24) + " дн. &7" + (ageHours % 24) + " ч.",
                "&7Уровень гильдии (прокачки): &a" + guild.level()), null);
            setItem(12, MenuItems.item(Material.GOLD_INGOT, "&6Экономика",
                "&7Коины: &e" + guild.guildCoins() + " &8— &7гильд-магазин",
                "&7Казна: &a" + guild.bankBalance().toPlainString() + " &8— &7улучшения",
                "&7Очки мобов: &e" + guild.guildPoints() + " &8— &7часть требований к прокачкам"), null);
            setItem(14, MenuItems.item(Material.PLAYER_HEAD, "&bСостав",
                "&7Участников: &e" + members.size(),
                "&7Слоты бонус: &a+" + guild.memberSlotsBonus()), null);
            setItem(16, MenuItems.item(Material.CHEST, "&e&lИнфраструктура",
                "&7Размер сундука: &e" + guild.chestRows() + " рядов",
                "&7Тир магазина: &e" + guild.shopTier(),
                "&7Друж. огонь: &e" + (guild.friendlyFire() ? "Вкл" : "Выкл")), null);
            setItem(31, MenuItems.item(Material.EXPERIENCE_BOTTLE, "&aЦепочка квестов гильдии",
                "&7Завершено квестов: &e" + claimedGuildQuests + "&7/&f21",
                "&7" + hint), null);
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openMain(player, guild));
        }
    }

    private static final class GuildLevelsMenu extends BaseMenu {
        private final GuildService service;
        private final GuildRecord guild;
        private final int guildLevel;
        private final List<LevelDef> defs;
        private final Set<Integer> claimedQuests;
        private final Map<Integer, long[]> progressMap;

        private GuildLevelsMenu(GuildService service, GuildRecord guild, int guildLevel, List<LevelDef> defs, Set<Integer> claimedQuests, Map<Integer, long[]> progressMap) {
            super(service.levelsConfig().getString("menu.title", "&5&lКвесты гильдии &8| &f<name>").replace("<name>", guild.name()),
                service.levelsConfig().getInt("menu.size", 54));
            this.service = service;
            this.guild = guild;
            this.guildLevel = guildLevel;
            this.defs = defs;
            this.claimedQuests = claimedQuests;
            this.progressMap = progressMap;
        }

        private boolean questUnlocked(int qid) {
            return qid <= 1 || claimedQuests.contains(qid - 1);
        }

        @Override
        protected void draw(Player player) {
            for (int i = 0; i < 54; i++) {
                setItem(i, MenuItems.filler(), null);
            }
            for (int i = 36; i < 45; i++) {
                setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
            }
            List<Integer> slots = service.levelSlots();
            AtomicInteger idx = new AtomicInteger(0);
            int claimedCount = claimedQuests.size();
            String hint = GuildService.guildNextTierHint(claimedCount);
            for (LevelDef def : defs) {
                int i = idx.getAndIncrement();
                if (i >= slots.size()) {
                    break;
                }
                int slot = slots.get(i);
                int qid = def.level();
                boolean unlocked = questUnlocked(qid);
                boolean claimed = claimedQuests.contains(qid);
                boolean doneObjectives = service.objectivesSatisfied(def, progressMap.get(qid));
                if (!unlocked) {
                    setItem(slot, MenuItems.item(Material.BLACK_STAINED_GLASS_PANE,
                        "&8Квест &7" + qid,
                        "&7Сначала завершите квест &f" + (qid - 1)), null);
                    continue;
                }
                Material mat;
                String status;
                if (claimed) {
                    mat = Material.GREEN_TERRACOTTA;
                    status = "&2Награда получена";
                } else if (doneObjectives) {
                    mat = Material.GOLD_BLOCK;
                    status = "&6&lМожно забрать награду";
                } else {
                    mat = Material.LIME_STAINED_GLASS_PANE;
                    status = "&aВ процессе";
                }
                List<String> lore = new ArrayList<>();
                lore.add("&8── &7Ступень гильдии &f" + def.guildTier() + "&8/&f5 &8──");
                lore.add("&8────────── &7Цели &8──────────");
                for (String line : def.objectiveTextLore()) {
                    lore.add(line);
                }
                long[] prog = progressMap.getOrDefault(qid, new long[0]);
                for (int ti = 0; ti < def.trackedObjectives().size(); ti++) {
                    GuildObjectiveDef o = def.trackedObjectives().get(ti);
                    long cur = ti < prog.length ? prog[ti] : 0L;
                    lore.add(o.progressLine(cur));
                }
                lore.add("&8────────────────────────");
                lore.add("&7Очки гильдии (мобы): &e" + guild.guildPoints());
                if (def.rewards().moneyToBank().compareTo(BigDecimal.ZERO) > 0) {
                    lore.add("&7Награда в казну: &a" + def.rewards().moneyToBank().toPlainString());
                }
                int nItems = def.rewards().plainItems().size() + def.rewards().templateEntries().size();
                if (nItems > 0) {
                    lore.add("&7Предметы в общий сундук: &e" + nItems + " &7тип(ов)");
                }
                lore.add("&7Статус: " + status);
                lore.add(!claimed && doneObjectives ? "&e▶ &7Нажми, чтобы забрать награду" : "&7");
                setItem(slot, MenuItems.item(mat, "&f#" + qid + " &r" + def.display(), lore.toArray(String[]::new)), e -> {
                    if (unlocked && !claimed && doneObjectives) {
                        service.claimLevelReward(player, guild, def);
                    }
                });
            }
            setItem(4, MenuItems.item(Material.EXPERIENCE_BOTTLE, "&aПрогресс гильдии",
                "&7Уровень для прокачек: &a" + guildLevel,
                "&7Квестов завершено: &e" + claimedCount + "&7/&f21",
                "&7" + hint,
                "&7Очки с мобов: &e" + guild.guildPoints()), null);
            setItem(49, MenuItems.item(Material.ARROW, "&7Назад"), e -> service.openMain(player, guild));
        }
    }

    record OnlineGuildView(String id, String name, int online, int total) {
    }

    record WarPermissions(boolean canInvite, boolean canQueue, boolean canAccept, boolean canSelectArena) {}

    record WarUiState(WarPermissions permissions, List<GuildRepository.WarArenaRecord> arenas) {}

    record WarGuildListState(boolean canInvite, List<OnlineGuildView> onlineGuilds) {}

    record LevelDef(
        int level,
        int guildTier,
        long requiredPoints,
        String display,
        List<String> objectiveTextLore,
        List<GuildObjectiveDef> trackedObjectives,
        LevelRewardBundle rewards,
        ItemStack previewIcon
    ) {
    }
}
