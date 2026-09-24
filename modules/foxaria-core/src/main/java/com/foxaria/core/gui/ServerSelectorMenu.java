package com.foxaria.core.gui;

import com.foxaria.core.service.PlayerFlowService;
import com.foxaria.core.service.PlayerFlowService.ServerDestination;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class ServerSelectorMenu extends BaseMenu {

    private final PlayerFlowService playerFlowService;

    public ServerSelectorMenu(PlayerFlowService playerFlowService) {
        super(playerFlowService.selectorMenuTitle(), 27);
        this.playerFlowService = playerFlowService;
    }

    @Override
    protected void draw(Player viewer) {
        for (int slot = 0; slot < inventory().getSize(); slot++) {
            setItem(slot, MenuItems.filler(), null);
        }

        List<ServerDestination> destinations = playerFlowService.destinations();
        if (destinations.isEmpty()) {
            setItem(13, MenuItems.item(
                Material.BARRIER,
                "&cСервера недоступны",
                "&7Список серверов пока пуст."
            ), click -> viewer.closeInventory());
        } else {
            for (ServerDestination destination : destinations) {
                setItem(destination.slot(), buildDestinationItem(destination), click -> {
                    viewer.closeInventory();
                    playerFlowService.sendToMain(viewer, destination.id());
                });
            }
        }

        setItem(22, MenuItems.item(
            Material.WRITABLE_BOOK,
            "&eКак это работает",
            "&7Сначала вы авторизуетесь,",
            "&7затем выбираете сервер",
            "&7и уже после этого входите в мир."
        ), null);
        setItem(26, MenuItems.item(
            Material.BARRIER,
            "&cЗакрыть",
            "&7Закрыть меню выбора сервера"
        ), click -> viewer.closeInventory());
    }

    private ItemStack buildDestinationItem(ServerDestination destination) {
        List<String> lore = destination.description().isEmpty()
            ? List.of("&7Нажмите, чтобы войти на сервер.")
            : destination.description();
        return MenuItems.item(
            destination.material() == null ? Material.NETHER_STAR : destination.material(),
            destination.displayName(),
            lore.toArray(String[]::new)
        );
    }
}
