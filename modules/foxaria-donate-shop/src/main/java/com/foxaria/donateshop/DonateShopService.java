package com.foxaria.donateshop;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.donateshop.gui.DonateShopCategoryMenu;
import com.foxaria.donateshop.gui.DonateShopRootMenu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DonateShopService {

    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final MessageService messages;
    private final AuditService audits;
    private final MenuManager menuManager;
    private final DonateShopRepository repository;

    public DonateShopService(
        JavaPlugin plugin,
        EconomyService economy,
        MessageService messages,
        AuditService audits,
        MenuManager menuManager,
        DonateShopRepository repository
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        this.audits = audits;
        this.menuManager = menuManager;
        this.repository = repository;
    }

    public void openRoot(Player player) {
        menuManager.open(player, new DonateShopRootMenu(this));
    }

    public void openCategory(Player player, DonateCategory category) {
        menuManager.open(player, new DonateShopCategoryMenu(this, category));
    }

    public CompletableFuture<List<DonateOffer>> offers(DonateCategory category) {
        return repository.listByCategory(category);
    }

    public CompletableFuture<Void> purchase(Player player, DonateOffer offer) {
        return economy.withdrawTokens(player.getUniqueId(), offer.priceTokens(), "donateshop:" + offer.id(), null)
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack give = offer.displayItem().clone();
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(give);
                overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                messages.send(player, "donateshop.bought", "&aКуплено за &b<tokens> &aток.: &f<item>",
                    new MessageService.Placeholder("tokens", String.valueOf(offer.priceTokens())),
                    new MessageService.Placeholder("item", plainName(give)));
                audits.append(new AuditEvent(
                    "DONATE_SHOP_BUY",
                    player.getUniqueId(),
                    null,
                    player.getName(),
                    null,
                    "Donate shop purchase",
                    Map.of("offer", offer.id(), "tokens", String.valueOf(offer.priceTokens())),
                    System.currentTimeMillis()
                ));
            }))
            .exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                String msg = cause.getMessage() == null ? "" : cause.getMessage();
                if (msg.toLowerCase().contains("insufficient")) {
                    economy.balance(player.getUniqueId()).thenAccept(bal ->
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            messages.send(player, "donateshop.insufficient-tokens",
                                "&cНужно &f<need> &cтокенов. У вас: &f<have>&c.",
                                new MessageService.Placeholder("need", String.valueOf(offer.priceTokens())),
                                new MessageService.Placeholder("have", String.valueOf(bal.tokens())))));
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        messages.send(player, "donateshop.error", "&cПокупка не удалась: <msg>",
                            new MessageService.Placeholder("msg", msg.isEmpty() ? "ошибка" : msg)));
                }
                return null;
            });
    }

    private static String plainName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
        }
        return stack.getType().name();
    }

    public DonateShopRepository repository() {
        return repository;
    }

    public MessageService messages() {
        return messages;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public EconomyService economy() {
        return economy;
    }
}
