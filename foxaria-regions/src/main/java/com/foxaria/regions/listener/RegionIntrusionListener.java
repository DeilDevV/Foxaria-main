package com.foxaria.regions.listener;

import com.foxaria.api.service.MessageService;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionFlags;
import com.foxaria.regions.RegionRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionIntrusionListener implements Listener {

    private static final long NOTIFY_COOLDOWN_MS = 45_000L;

    private final RegionFacade f;
    private final Map<String, Long> lastNotifyMs = new ConcurrentHashMap<>();

    public RegionIntrusionListener(RegionFacade f) {
        this.f = f;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player intruder = event.getPlayer();

        Optional<RegionRecord> fromR = f.manager().findContaining(from);
        Optional<RegionRecord> toR = f.manager().findContaining(to);
        if (toR.isEmpty()) {
            return;
        }
        RegionRecord region = toR.get();
        if (f.repo().isMember(region.id(), intruder.getUniqueId()).join()) {
            return;
        }
        if (fromR.isPresent() && fromR.get().id() == region.id()) {
            return;
        }

        f.messages().send(intruder, "region.intrusion-spotted",
            "&eВы были замечены на территории чужого региона.");

        if (region.level() < f.config().intrusionAlertMinLevel) {
            return;
        }
        if (!RegionFlags.intrusionAlert(region.flags())) {
            return;
        }

        long now = System.currentTimeMillis();
        String key = region.id() + ":" + intruder.getUniqueId();
        if (lastNotifyMs.getOrDefault(key, 0L) + NOTIFY_COOLDOWN_MS > now) {
            return;
        }
        lastNotifyMs.put(key, now);

        String regionTitle = region.effectiveDisplayTitle();
        for (UUID u : f.repo().memberUuids(region.id()).join()) {
            Player m = Bukkit.getPlayer(u);
            if (m != null && m.isOnline()) {
                f.messages().send(m, "region.intrusion-enter",
                    "&eВ приват &f<r>&e вошёл чужак: &f<p>",
                    new MessageService.Placeholder("r", regionTitle),
                    new MessageService.Placeholder("p", intruder.getName()));
            }
        }

        if (region.level() >= f.config().intrusionGlowMinLevel) {
            RegionIntruderGlow.start(f.plugin(), intruder);
        }
    }
}
