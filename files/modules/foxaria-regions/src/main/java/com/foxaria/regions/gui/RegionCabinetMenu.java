package com.foxaria.regions.gui;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.regions.RegionConfig;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionEconomyFailures;
import com.foxaria.regions.RegionFlags;
import com.foxaria.regions.RegionInvUtil;
import com.foxaria.regions.RegionRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RegionCabinetMenu extends BaseMenu {

    private final RegionFacade f;
    private final int regionId;

    public RegionCabinetMenu(RegionFacade f, int regionId) {
        super("&8⟨ &a&lПриват &8│ &fядро &8⟩", 54);
        this.f = f;
        this.regionId = regionId;
    }

    @Override
    protected void draw(Player player) {
        RegionRecord rec = f.manager().byId(regionId).orElse(null);
        if (rec == null) {
            setItem(22, MenuItems.item(Material.BARRIER, "&cРегион не найден"), e -> player.closeInventory());
            return;
        }
        boolean owner = rec.ownerUuid().equals(player.getUniqueId());
        boolean member = f.repo().isMember(regionId, player.getUniqueId()).join();

        for (int i = 0; i < 54; i++) {
            if (i != 2 && i != 4 && i != 13 && i != 19 && i != 20 && i != 21 && i != 22 && i != 24 && i != 25 && i != 26 && i != 27
                && i != 28 && i != 29 && i != 30 && i != 31) {
                setItem(i, MenuItems.filler(), null);
            }
        }

        int half = rec.halfSize();
        int memCount = f.repo().memberUuids(regionId).join().size();
        String ownerName = Optional.ofNullable(Bukkit.getOfflinePlayer(rec.ownerUuid()).getName()).orElse("—");
        World rw = Bukkit.getWorld(rec.world());
        int bDown = f.manager().blocksDown();
        int bUp = f.manager().blocksUp();
        String yRange;
        if (bDown < 0 && rw != null) {
            yRange = "Y: " + rw.getMinHeight() + " … " + (rec.cabinetY() + bUp) + " &7(низ — дно мира)";
        } else {
            yRange = "Y: " + (rec.cabinetY() - Math.max(0, bDown)) + " … " + (rec.cabinetY() + bUp);
        }
        String titleName = rec.displayName() != null && !rec.displayName().isBlank()
            ? rec.displayName().trim()
            : ("№" + rec.id());
        List<String> statLines = new ArrayList<>();
        statLines.add("&7Имя: &f" + titleName);
        statLines.add("&7Мир: &f" + rec.world());
        statLines.add("&7Уровень: &f" + rec.level());
        statLines.add("&7Радиус: &f" + half + " &7блоков &8(&7в одну сторону от ядра&8)");
        statLines.add("&7Высота: &f" + yRange);
        statLines.add("&7Ядро &8(XYZ)&7: &f" + rec.cabinetX() + " " + rec.cabinetY() + " " + rec.cabinetZ());
        statLines.add("&7HP ядра: &f" + rec.coreHp() + "&7/&f" + rec.coreMaxHp());
        statLines.add("&7Владелец: &f" + ownerName);
        statLines.add("&7Участников (всего): &f" + memCount);
        statLines.add("&7Склад: &f" + rec.depositedWood() + " &7дер. &7| &f" + rec.depositedIron() + " &7жел.");
        setItem(2, MenuItems.item(Material.FILLED_MAP, "&6Статистика привата", statLines.toArray(new String[0])), null);

        List<String> dmgLines = new ArrayList<>();
        dmgLines.add("&7Координаты:");
        var damaged = f.repo().loadDamagedBlocks(regionId).join();
        if (damaged.isEmpty()) {
            dmgLines.add("&8Нет повреждённых блоков");
        } else {
            int n = 0;
            for (var row : damaged) {
                if (n++ >= 12) {
                    dmgLines.add("&8...");
                    break;
                }
                dmgLines.add("&f" + row.x() + " " + row.y() + " " + row.z() + " &7(" + row.currentHp() + "/" + row.maxHp() + ")");
            }
        }
        setItem(4, MenuItems.item(Material.BOOK, "&7Повреждённые блоки", dmgLines.toArray(new String[0])), null);

        RegionConfig.LevelDef curLev = f.config().level(rec.level());
        Optional<RegionConfig.UpgradeCost> up = curLev.upgradeToNext();
        if (up.isEmpty()) {
            setItem(13, MenuItems.item(Material.NETHER_STAR, "&aМаксимальный уровень", "&7Расширять дальше нельзя."), null);
        } else {
            var cost = up.get();
            int wood = rec.depositedWood();
            int iron = rec.depositedIron();
            RegionConfig.LevelDef nextTier = f.config().levelsByNumber.get(rec.level() + 1);
            int newHalf = nextTier != null ? nextTier.halfSizeBlocks() : rec.halfSize();
            boolean neighborBlocks = f.manager().wouldBlockUpgrade(rec, newHalf);

            if (neighborBlocks) {
                setItem(13, MenuItems.item(Material.BARRIER, "&cУлучшение невозможно!",
                    "&cРядом другой приват.",
                    "&7Расширение привело бы к пересечению с соседом."), null);
            } else {
                setItem(13, MenuItems.item(Material.ANVIL, "&eУлучшить регион", "&7До уровня: &f" + (rec.level() + 1),
                    "&7Дерево (склад ядра): &f" + wood + "&7/&f" + cost.wood(),
                    "&7Железо: &f" + iron + "&7/&f" + cost.iron(),
                    "&7Монеты при оплате: &f" + cost.coins().toPlainString(),
                    "&7Сначала склад, затем оплата."), null);
            }

            boolean canDep = owner || RegionFlags.allowMemberUpgrade(rec.flags());
            if (canDep && member) {
                setItem(19, MenuItems.item(Material.OAK_LOG, "&aПоложить дерево", "&7Из инвентаря на склад ядра"), e -> {
                    depositWood(player, rec, cost.wood());
                    reopen(player);
                });
                setItem(20, MenuItems.item(Material.IRON_INGOT, "&aПоложить железо", "&7Из инвентаря на склад"), e -> {
                    depositIron(player, rec, cost.iron());
                    reopen(player);
                });
            }

            boolean canPay = owner || RegionFlags.allowMemberUpgrade(rec.flags());
            if (canPay && member && !neighborBlocks && wood >= cost.wood() && iron >= cost.iron()) {
                setItem(21, MenuItems.item(Material.GOLD_INGOT, "&6Оплатить и улучшить", "&7Списание с вашего счёта"), e -> {
                    payUpgrade(player, rec);
                    reopen(player);
                });
            }
        }

        if (member && RegionFlags.boundaryParticles(rec.flags())) {
            boolean canPersonal = owner || RegionFlags.allowMemberBoundaryToggle(rec.flags());
            boolean hide = f.repo().hideBoundaryParticles(regionId, player.getUniqueId()).join();
            if (canPersonal) {
                setItem(24, MenuItems.item(
                    hide ? Material.GRAY_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    hide ? "&7Граница: скрыта" : "&aГраница: видна",
                    "&7Частицы границы только для участников.",
                    "&7ЛКМ — переключить для себя"
                ), e -> {
                    f.repo().setHideBoundaryParticles(regionId, player.getUniqueId(), !hide).join();
                    reopen(player);
                });
            } else if (!owner) {
                setItem(24, MenuItems.item(Material.BARRIER, "&cГраница привата",
                    "&7Владелец запретил отключать частицы."), null);
            }
        } else if (member) {
            setItem(24, MenuItems.filler(), null);
        }

        if (member && (owner || RegionFlags.allowMemberInvite(rec.flags()))) {
            setItem(22, MenuItems.item(Material.WRITABLE_BOOK, "&fДобавить в приват", "&7ЛКМ — подсказка команды в чат"), e -> {
                player.closeInventory();
                player.sendMessage(
                    Component.text("Команда: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(
                            Component.text("/region add ", NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.suggestCommand("/region add "))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    Component.text("Нажмите, чтобы подставить команду", NamedTextColor.YELLOW)
                                        .decoration(TextDecoration.ITALIC, false)))
                                .decoration(TextDecoration.ITALIC, false)
                        )
                        .append(Component.text("(ник)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                );
            });
        }

        if (owner) {
            long cdMs = f.damage().dismantleCooldownRemainingMs(player);
            List<String> dismantleLore = new ArrayList<>();
            dismantleLore.add("&7Предмет ядра окажется у вас в инвентаре.");
            dismantleLore.add("&7Уровень на предмете сохранится; участников — заново.");
            dismantleLore.add("&7После удаления зона привата исчезнет.");
            if (cdMs > 0) {
                dismantleLore.add("&cКулдаун сноса: &f" + ((cdMs + 59_999) / 60_000) + "&c мин.");
            }
            setItem(23, MenuItems.item(Material.RED_NETHER_BRICKS, "&cСнести ядро привата",
                dismantleLore.toArray(new String[0])), e -> {
                if (f.damage().dismantleCooldownRemainingMs(player) > 0) {
                    f.messages().send(player, "region.dismantle-cooldown", "&cСнос ядра на кулдауне. Подождите.");
                    return;
                }
                player.closeInventory();
                f.damage().dismantleCore(player, rec);
                f.messages().send(player, "region.dismantled", "&aЯдро привата забрано, регион удалён.");
            });
            setItem(25, MenuItems.item(
                RegionFlags.boundaryParticles(rec.flags()) ? Material.GLOWSTONE_DUST : Material.SUGAR,
                RegionFlags.boundaryParticles(rec.flags()) ? "&aЧастицы границы: вкл" : "&7Частицы границы: выкл",
                "&7Видят только участники привата.",
                "&7ЛКМ — переключить для региона"
            ), e -> {
                int nf = RegionFlags.with(rec.flags(), RegionFlags.BOUNDARY_PARTICLES, !RegionFlags.boundaryParticles(rec.flags()));
                f.repo().setFlags(regionId, nf).join();
                f.manager().patchFlags(regionId, nf);
                reopen(player);
            });
            boolean allowMemberHide = RegionFlags.allowMemberBoundaryToggle(rec.flags());
            setItem(26, MenuItems.item(
                allowMemberHide ? Material.LIME_DYE : Material.GRAY_DYE,
                allowMemberHide ? "&aУчастники могут скрыть границу" : "&cУчастники не могут скрыть границу",
                "&7ЛКМ — запретить или разрешить участникам скрывать частицы."
            ), e -> {
                boolean forbidNow = !RegionFlags.allowMemberBoundaryToggle(rec.flags());
                int nf = RegionFlags.with(rec.flags(), RegionFlags.FORBID_MEMBER_BOUNDARY_TOGGLE, !forbidNow);
                f.repo().setFlags(regionId, nf).join();
                f.manager().patchFlags(regionId, nf);
                reopen(player);
            });
            if (rec.level() >= f.config().intrusionAlertMinLevel) {
                setItem(27, MenuItems.item(
                    RegionFlags.intrusionAlert(rec.flags()) ? Material.BELL : Material.GLASS_BOTTLE,
                    RegionFlags.intrusionAlert(rec.flags()) ? "&eУведомления о входе: вкл" : "&7Уведомления о входе: выкл",
                    "&7Только с уровня &f" + f.config().intrusionAlertMinLevel + "&7.",
                    "&7Онлайн-участники узнают о чужаке в регионе."
                ), e -> {
                    int nf = RegionFlags.with(rec.flags(), RegionFlags.INTRUSION_ALERT, !RegionFlags.intrusionAlert(rec.flags()));
                    f.repo().setFlags(regionId, nf).join();
                    f.manager().patchFlags(regionId, nf);
                    reopen(player);
                });
            } else {
                setItem(27, MenuItems.item(Material.BARRIER, "&cУведомления о вторжении",
                    "&7Нужен уровень привата &f" + f.config().intrusionAlertMinLevel + "&7."), null);
            }
            setItem(30, MenuItems.item(Material.NAME_TAG, "&6Переименовать приват",
                "&7Стоимость: &f" + f.config().renameCostCoins.toPlainString() + " &7монет",
                "&7Имя уникально на сервере (без дублей).",
                "&7ЛКМ — подставить команду в чат"), e -> {
                player.closeInventory();
                player.sendMessage(
                    Component.text("Команда: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(
                            Component.text("/region rename ", NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.suggestCommand("/region rename "))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    Component.text("Нажмите, чтобы подставить команду", NamedTextColor.YELLOW)
                                        .decoration(TextDecoration.ITALIC, false)))
                                .decoration(TextDecoration.ITALIC, false)
                        )
                        .append(Component.text("НовоеИмя", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                );
            });
            setItem(28, MenuItems.item(
                RegionFlags.allowMemberInvite(rec.flags()) ? Material.LIME_DYE : Material.GRAY_DYE,
                "&7Участники могут приглашать",
                RegionFlags.allowMemberInvite(rec.flags()) ? ("&7Статус: &aВкл") : ("&7Статус: &cВыкл")
            ), e -> {
                int nf = RegionFlags.with(rec.flags(), RegionFlags.MEMBER_INVITE, !RegionFlags.allowMemberInvite(rec.flags()));
                f.repo().setFlags(regionId, nf).join();
                f.manager().patchFlags(regionId, nf);
                reopen(player);
            });
            setItem(29, MenuItems.item(
                RegionFlags.allowMemberUpgrade(rec.flags()) ? Material.LIME_DYE : Material.GRAY_DYE,
                "&7Участники могут копить / улучшать",
                RegionFlags.allowMemberUpgrade(rec.flags()) ? ("&7Статус: &aВкл") : ("&7Статус: &cВыкл")
            ), e -> {
                int nf = RegionFlags.with(rec.flags(), RegionFlags.MEMBER_UPGRADE, !RegionFlags.allowMemberUpgrade(rec.flags()));
                f.repo().setFlags(regionId, nf).join();
                f.manager().patchFlags(regionId, nf);
                reopen(player);
            });
        }

        if (!owner) {
            setItem(23, MenuItems.filler(), null);
            setItem(25, MenuItems.filler(), null);
            setItem(26, MenuItems.filler(), null);
            setItem(27, MenuItems.filler(), null);
            setItem(30, MenuItems.filler(), null);
        }

        setItem(31, MenuItems.item(Material.ARROW, "&7Закрыть"), e -> player.closeInventory());
    }

    private void reopen(Player player) {
        f.menus().open(player, new RegionCabinetMenu(f, regionId));
    }

    private void depositWood(Player player, RegionRecord rec, int need) {
        int space = Math.max(0, need - rec.depositedWood());
        if (space <= 0) {
            return;
        }
        int took = RegionInvUtil.takeLogs(player, space);
        if (took <= 0) {
            f.messages().send(player, "region.deposit-none", "&cНет подходящего дерева в инвентаре.");
            return;
        }
        int nw = rec.depositedWood() + took;
        f.repo().updateDeposits(rec.id(), nw, rec.depositedIron()).join();
        f.manager().patchDeposits(rec.id(), nw, rec.depositedIron());
        f.messages().send(player, "region.deposit-wood", "&aПоложено дерева: &f<t>", new MessageService.Placeholder("t", String.valueOf(took)));
    }

    private void depositIron(Player player, RegionRecord rec, int need) {
        int space = Math.max(0, need - rec.depositedIron());
        if (space <= 0) {
            return;
        }
        int took = RegionInvUtil.takeIron(player, space);
        if (took <= 0) {
            f.messages().send(player, "region.deposit-none", "&cНет железа в инвентаре.");
            return;
        }
        int ni = rec.depositedIron() + took;
        f.repo().updateDeposits(rec.id(), rec.depositedWood(), ni).join();
        f.manager().patchDeposits(rec.id(), rec.depositedWood(), ni);
        f.messages().send(player, "region.deposit-iron", "&aПоложено железа: &f<t>", new MessageService.Placeholder("t", String.valueOf(took)));
    }

    private void payUpgrade(Player player, RegionRecord rec) {
        RegionConfig.LevelDef curLev = f.config().level(rec.level());
        Optional<RegionConfig.UpgradeCost> up = curLev.upgradeToNext();
        if (up.isEmpty()) {
            return;
        }
        var cost = up.get();
        if (rec.depositedWood() < cost.wood() || rec.depositedIron() < cost.iron()) {
            f.messages().send(player, "region.upgrade-not-ready", "&cСначала заполните склад.");
            return;
        }
        RegionConfig.LevelDef next = f.config().levelsByNumber.get(rec.level() + 1);
        if (next == null) {
            f.messages().send(player, "region.upgrade-max", "&cНет следующего уровня в конфиге.");
            return;
        }
        int newHalf = next.halfSizeBlocks();
        if (f.manager().wouldBlockUpgrade(rec, newHalf)) {
            f.messages().send(player, "region.upgrade-blocked-neighbor", "&cУлучшение невозможно: рядом другой приват.");
            return;
        }
        EconomyService eco = f.economy();
        int rid = rec.id();
        int newLevel = rec.level() + 1;
        try {
            var snap = eco.balance(player.getUniqueId()).join();
            if (snap.balance().compareTo(cost.coins()) < 0) {
                f.messages().send(player, "region.upgrade-pay-fail", "&c<e>",
                    new MessageService.Placeholder("e", RegionEconomyFailures.insufficientCoins(cost.coins(), snap.balance())));
                return;
            }
        } catch (Exception ex) {
            f.messages().send(player, "region.upgrade-pay-fail", "&c<e>",
                new MessageService.Placeholder("e", RegionEconomyFailures.describe(ex)));
            return;
        }
        eco.withdraw(player.getUniqueId(), cost.coins(), "region_upgrade", player.getUniqueId())
            .thenRun(() -> Bukkit.getScheduler().runTask(f.plugin(), () -> {
                f.repo().upgradeRegion(rid, newHalf, newLevel).join();
                f.repo().findById(rid).join().ifPresent(f.manager()::replace);
                f.messages().send(player, "region.upgraded", "&aРегион улучшен до уровня &f<n>.", new MessageService.Placeholder("n", String.valueOf(newLevel)));
            }))
            .exceptionally(t -> {
                Bukkit.getScheduler().runTask(f.plugin(), () ->
                    f.messages().send(player, "region.upgrade-pay-fail", "&c<e>",
                        new MessageService.Placeholder("e", RegionEconomyFailures.describe(t))));
                return null;
            });
    }
}
