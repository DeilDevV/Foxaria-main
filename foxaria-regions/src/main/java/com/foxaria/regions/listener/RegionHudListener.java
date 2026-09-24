package com.foxaria.regions.listener;

import com.foxaria.regions.RegionBlockMarkers;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionSession;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.regions.RegionRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionHudListener implements Listener {

    private static final int REACH = 8;

    private final RegionFacade f;
    private final Map<UUID, Long> lastBar = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> privatBossBars = new ConcurrentHashMap<>();

    public RegionHudListener(RegionFacade f) {
        this.f = f;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastBar.remove(event.getPlayer().getUniqueId());
        RegionSession.forget(event.getPlayer());
        BossBar bb = privatBossBars.remove(event.getPlayer().getUniqueId());
        if (bb != null) {
            bb.removeAll();
        }
    }

    public void tick(Player player) {
        tickPrivatBossBar(player);

        Block b = player.getTargetBlockExact(REACH, FluidCollisionMode.NEVER);
        if (b == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastBar.getOrDefault(player.getUniqueId(), 0L) + 200 > now) {
            return;
        }
        var rid = RegionBlockMarkers.regionIdOf(f.plugin(), b);
        if (rid.isPresent()) {
            RegionRecord rec = f.manager().byId(rid.get()).orElse(null);
            if (rec != null) {
                lastBar.put(player.getUniqueId(), now);
                player.sendActionBar(FoxariaText.noItalic(
                    Component.text("Ядро привата: ", NamedTextColor.GRAY)
                        .append(Component.text(rec.coreHp() + "/" + rec.coreMaxHp(), NamedTextColor.GREEN))
                        .append(Component.text(" \u2692", NamedTextColor.GOLD))
                ));
            }
            return;
        }
        f.manager().findContaining(b.getLocation()).ifPresent(reg ->
            f.repo().findDamaged(reg.id(), b.getX(), b.getY(), b.getZ()).join().ifPresent(row -> {
                lastBar.put(player.getUniqueId(), now);
                player.sendActionBar(FoxariaText.noItalic(
                    Component.text("Блок привата: ", NamedTextColor.GRAY)
                        .append(Component.text(row.currentHp() + "/" + row.maxHp(), NamedTextColor.YELLOW))
                ));
            }));
    }

    private void tickPrivatBossBar(Player player) {
        Optional<RegionRecord> here = f.manager().findContaining(player.getLocation());
        boolean inOwn = here.filter(r -> f.repo().isMember(r.id(), player.getUniqueId()).join()).isPresent();
        RegionRecord rec = here.orElse(null);

        BarColor color;
        String title;
        if (inOwn && rec != null) {
            color = BarColor.GREEN;
            title = rec.effectiveDisplayTitle();
        } else if (rec != null) {
            color = BarColor.RED;
            title = "Чужой: " + rec.effectiveDisplayTitle();
        } else {
            color = BarColor.RED;
            title = "Вне привата";
        }

        UUID uid = player.getUniqueId();
        BossBar bar = privatBossBars.computeIfAbsent(uid, u -> Bukkit.createBossBar(title, color, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setTitle(title);
        bar.setColor(color);
        bar.setProgress(1.0);
        bar.setVisible(true);
    }
}
