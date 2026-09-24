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

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        // Владелец и участники своего привата — не «чужаки».
        // Проверка по кэшу: раньше тут был .join() на SQL, то есть запрос
        // к базе на каждый шаг игрока внутри региона.
        if (region.ownerUuid().equals(intruder.getUniqueId())
            || f.repo().isMemberCached(region.id(), intruder.getUniqueId())) {
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
        // Карта кулдаунов росла бесконечно (ключ на каждую пару регион+игрок).
        // Периодически чистим протухшие записи.
        if (lastNotifyMs.size() > 512) {
            lastNotifyMs.entrySet().removeIf(e -> e.getValue() + NOTIFY_COOLDOWN_MS * 4 < now);
        }

        String regionTitle = region.effectiveDisplayTitle();
        f.repo().memberUuids(region.id()).thenAccept(members -> {
            // ВАЖНО: владелец добавляется к получателям явно.
            // Если его строки не было в fx_region_members (старые приваты,
            // ручные правки БД, случайный /region kick по самому себе),
            // он единственный не получал уведомление о вторжении — при том,
            // что участники его привата получали.
            Set<UUID> recipients = new HashSet<>(members);
            recipients.add(region.ownerUuid());
            recipients.remove(intruder.getUniqueId());

            f.plugin().getServer().getScheduler().runTask(f.plugin(), () -> {
                for (UUID u : recipients) {
                    Player m = Bukkit.getPlayer(u);
                    if (m == null || !m.isOnline()) {
                        continue;
                    }
                    f.messages().send(m, "region.intrusion-enter",
                        "&eВ приват &f<r>&e вошёл чужак: &f<p>",
                        new MessageService.Placeholder("r", regionTitle),
                        new MessageService.Placeholder("p", intruder.getName()));
                }
            });
        });

        if (region.level() >= f.config().intrusionGlowMinLevel) {
            RegionIntruderGlow.start(f.plugin(), intruder);
        }
    }
}
