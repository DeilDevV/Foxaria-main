package com.foxaria.ranks;

import com.foxaria.api.service.RankService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsRankService implements RankService {

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;

    public LuckPermsRankService(JavaPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        this.luckPerms = provider == null ? null : provider.getProvider();
    }

    @Override
    public CompletableFuture<Void> setPrimaryGroup(UUID playerUuid, String group) {
        if (luckPerms == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is not installed."));
        }
        return CompletableFuture.runAsync(() -> {
            User user = luckPerms.getUserManager().loadUser(playerUuid).join();
            luckPerms.getUserManager().modifyUser(playerUuid, loadedUser -> loadedUser.setPrimaryGroup(group));
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> grantTemporaryGroup(UUID playerUuid, String group, long durationSeconds) {
        if (luckPerms == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is not installed."));
        }
        return CompletableFuture.runAsync(() -> {
            User user = luckPerms.getUserManager().loadUser(playerUuid).join();
            user.data().add(InheritanceNode.builder(group).expiry(Duration.ofSeconds(durationSeconds)).build());
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> removeGroup(UUID playerUuid, String group) {
        if (luckPerms == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is not installed."));
        }
        return CompletableFuture.runAsync(() -> {
            User user = luckPerms.getUserManager().loadUser(playerUuid).join();
            user.data().remove(InheritanceNode.builder(group).build());
            if (group.equalsIgnoreCase(user.getPrimaryGroup())) {
                user.setPrimaryGroup("default");
            }
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<Void> grantTemporaryPermission(UUID playerUuid, String permission, long durationSeconds, String reason) {
        if (luckPerms == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is not installed."));
        }
        return CompletableFuture.runAsync(() -> {
            User user = luckPerms.getUserManager().loadUser(playerUuid).join();
            user.data().add(Node.builder(permission).expiry(Duration.ofSeconds(durationSeconds)).build());
            luckPerms.getUserManager().saveUser(user);
        });
    }

    @Override
    public CompletableFuture<String> primaryGroup(UUID playerUuid) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture("default");
        }
        return CompletableFuture.supplyAsync(() -> luckPerms.getUserManager().loadUser(playerUuid).join().getPrimaryGroup());
    }
}
