package com.foxaria.shop;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ShopService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.shop.gui.ShopCategoryMenu;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ConfigShopService implements ShopService {

    private final FileConfiguration config;
    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final MessageService messages;
    private final AuditService audits;
    private final MenuManager menuManager;
    private final ItemTemplateService itemTemplates;

    public ConfigShopService(
        JavaPlugin plugin,
        FileConfiguration config,
        EconomyService economyService,
        MessageService messages,
        AuditService audits,
        MenuManager menuManager,
        ItemTemplateService itemTemplates
    ) {
        this.plugin = plugin;
        this.config = config;
        this.economyService = economyService;
        this.messages = messages;
        this.audits = audits;
        this.menuManager = menuManager;
        this.itemTemplates = itemTemplates;
    }

    @Override
    public void open(Player player) {
        menuManager.open(player, new ShopCategoryMenu(this));
    }

    @Override
    public CompletableFuture<Void> buy(Player player, String offerId, int amount) {
        ShopOffer offer = offersById().get(offerId);
        if (offer == null) {
            messages.send(player, "shop.offer-missing", "&cShop offer not found.");
            return CompletableFuture.completedFuture(null);
        }
        if (offer.usesTemplate()) {
            if (itemTemplates == null || !itemTemplates.exists(offer.itemTemplateId())) {
                messages.send(player, "shop.template-missing", "&cШаблон предмета недоступен: <id>",
                    new MessageService.Placeholder("id", offer.itemTemplateId()));
                return CompletableFuture.completedFuture(null);
            }
        }
        BigDecimal total = offer.buyPrice().multiply(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        return economyService.withdraw(player.getUniqueId(), total, "shop_buy:" + offer.id(), null)
            .thenRun(() -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<Integer, ItemStack> overflow;
                    if (offer.usesTemplate() && itemTemplates != null) {
                        overflow = new HashMap<>();
                        for (int p = 0; p < amount; p++) {
                            for (int c = 0; c < offer.amount(); c++) {
                                ItemStack one = itemTemplates.cloneTemplate(offer.itemTemplateId()).orElse(null);
                                if (one == null) {
                                    continue;
                                }
                                overflow.putAll(player.getInventory().addItem(one.clone()));
                            }
                        }
                    } else {
                        overflow = player.getInventory().addItem(new ItemStack(offer.material(), offer.amount() * amount));
                    }
                    overflow.values().forEach(itemStack -> player.getWorld().dropItemNaturally(player.getLocation(), itemStack));
                    messages.send(player, "shop.bought", "&aBought <amount>x <item> for <price>.", new MessageService.Placeholder("amount", String.valueOf(amount)), new MessageService.Placeholder("item", offer.displayName()), new MessageService.Placeholder("price", total.toPlainString()));
                    audits.append(new AuditEvent(
                        "SHOP_BUY",
                        player.getUniqueId(),
                        null,
                        player.getName(),
                        null,
                        "Server shop purchase",
                        Map.of("offer", offer.id(), "amount", String.valueOf(amount), "price", total.toPlainString()),
                        System.currentTimeMillis()
                    ));
                });
            })
            .exceptionally(throwable -> {
                messages.send(player, "shop.buy-failed", "&cPurchase failed: <error>", new MessageService.Placeholder("error", throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage()));
                return null;
            });
    }

    @Override
    public CompletableFuture<Void> sell(Player player, ItemStack itemStack, BigDecimal price) {
        int removed = removeMatching(player, itemStack.getType(), itemStack.getAmount());
        if (removed <= 0) {
            messages.send(player, "shop.sell-none", "&cYou do not have enough items to sell.");
            return CompletableFuture.completedFuture(null);
        }
        BigDecimal total = price.multiply(BigDecimal.valueOf(removed)).setScale(2, RoundingMode.HALF_UP);
        return economyService.deposit(player.getUniqueId(), total, "shop_sell:" + itemStack.getType().name(), null)
            .thenRun(() -> messages.send(player, "shop.sold", "&aSold <amount>x <item> for <price>.", new MessageService.Placeholder("amount", String.valueOf(removed)), new MessageService.Placeholder("item", itemStack.getType().name()), new MessageService.Placeholder("price", total.toPlainString())));
    }

    public Map<String, String> categories() {
        ConfigurationSection section = config.getConfigurationSection("categories");
        Map<String, String> categories = new LinkedHashMap<>();
        if (section == null) {
            return categories;
        }
        for (String key : section.getKeys(false)) {
            categories.put(key, section.getString(key + ".display-name", key));
        }
        return categories;
    }

    public List<ShopOffer> offers(String category) {
        ConfigurationSection section = config.getConfigurationSection("categories." + category + ".offers");
        List<ShopOffer> offers = new ArrayList<>();
        if (section == null) {
            return offers;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection offer = section.getConfigurationSection(key);
            if (offer == null) {
                continue;
            }
            String templateId = offer.getString("item-template", "").trim();
            String materialKey = offer.getString("material", templateId.isEmpty() ? "STONE" : "PAPER");
            Material material = Material.matchMaterial(materialKey);
            if (material == null) {
                material = Material.PAPER;
            }
            offers.add(new ShopOffer(
                category + ":" + key,
                category,
                offer.getString("display-name", key),
                material,
                offer.getInt("amount", 1),
                BigDecimal.valueOf(offer.getDouble("buy-price", 0.0D)).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(offer.getDouble("sell-price", 0.0D)).setScale(2, RoundingMode.HALF_UP),
                templateId
            ));
        }
        return offers;
    }

    public Map<String, ShopOffer> offersById() {
        Map<String, ShopOffer> offers = new LinkedHashMap<>();
        for (String category : categories().keySet()) {
            for (ShopOffer offer : offers(category)) {
                offers.put(offer.id(), offer);
            }
        }
        return offers;
    }

    public ItemStack offerIcon(ShopOffer offer) {
        if (offer.usesTemplate() && itemTemplates != null) {
            return itemTemplates.cloneTemplate(offer.itemTemplateId()).orElseGet(offer::icon);
        }
        return offer.icon();
    }

    private int removeMatching(Player player, Material material, int requestedAmount) {
        int remaining = requestedAmount;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack == null || itemStack.getType() != material) {
                continue;
            }
            int taken = Math.min(itemStack.getAmount(), remaining);
            itemStack.setAmount(itemStack.getAmount() - taken);
            remaining -= taken;
            if (remaining <= 0) {
                break;
            }
        }
        return requestedAmount - remaining;
    }
}
