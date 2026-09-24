package com.foxaria.shop;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ShopService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.shop.gui.ProgressionShopCategoryMenu;
import com.foxaria.shop.gui.ProgressionShopRootMenu;
import com.foxaria.shop.progression.ProgressionRepository;
import com.foxaria.shop.progression.ProgressionService;
import com.foxaria.shop.progression.ProgressionShopOffer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ProgressionShopService implements ShopService {

    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final MessageService messages;
    private final AuditService audits;
    private final MenuManager menuManager;
    private final ItemTemplateService itemTemplates;
    private final ProgressionRepository shopRepository;
    private final ProgressionService progression;
    private final FileConfiguration guiConfig;

    public ProgressionShopService(
        JavaPlugin plugin,
        EconomyService economy,
        MessageService messages,
        AuditService audits,
        MenuManager menuManager,
        ItemTemplateService itemTemplates,
        ProgressionRepository shopRepository,
        ProgressionService progression,
        FileConfiguration guiConfig
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.messages = messages;
        this.audits = audits;
        this.menuManager = menuManager;
        this.itemTemplates = itemTemplates;
        this.shopRepository = shopRepository;
        this.progression = progression;
        this.guiConfig = guiConfig;
    }

    @Override
    public void open(Player player) {
        menuManager.open(player, new ProgressionShopRootMenu(this, progression, guiConfig));
    }

    public void openCategory(Player player, com.foxaria.shop.progression.ProgressionCategory category) {
        menuManager.open(player, new ProgressionShopCategoryMenu(this, progression, category, guiConfig));
    }

    public ProgressionRepository shopRepository() {
        return shopRepository;
    }

    public ProgressionService progression() {
        return progression;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public ItemTemplateService itemTemplates() {
        return itemTemplates;
    }

    public CompletableFuture<Void> buy(Player player, ProgressionShopOffer offer, int amount) {
        final int qty = Math.max(1, amount);
        return progression.knowledgeLevel(player.getUniqueId()).thenCompose(level -> {
            if (level < offer.requiredKnowledge()) {
                messages.send(player, "shop.knowledge-locked", "&cНедостаточно знаний. Нужен уровень &f<need>&c, у вас &f<have>&c.",
                    new MessageService.Placeholder("need", String.valueOf(offer.requiredKnowledge())),
                    new MessageService.Placeholder("have", String.valueOf(level)));
                return CompletableFuture.completedFuture(null);
            }
            BigDecimal total = offer.price().multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            return economy.withdraw(player.getUniqueId(), total, "progression_shop:" + offer.id(), null)
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> giveItems(player, offer, qty, total)))
                .exceptionally(ex -> {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    String m = c.getMessage() == null ? "" : c.getMessage();
                    if (m.toLowerCase().contains("insufficient")) {
                        messages.send(player, "shop.buy-failed", "&cНе хватает монет. Нужно: &f<need>",
                            new MessageService.Placeholder("need", total.toPlainString()));
                    } else {
                        messages.send(player, "shop.buy-failed", "&cПокупка не удалась: <error>",
                            new MessageService.Placeholder("error", m.isEmpty() ? "ошибка" : m));
                    }
                    return null;
                });
        });
    }

    private void giveItems(Player player, ProgressionShopOffer offer, int amount, BigDecimal total) {
        Map<Integer, ItemStack> overflow = new HashMap<>();
        if (offer.usesTemplate() && itemTemplates != null && itemTemplates.exists(offer.itemTemplate())) {
            for (int i = 0; i < amount; i++) {
                ItemStack one = itemTemplates.cloneTemplate(offer.itemTemplate()).orElse(null);
                if (one != null) {
                    overflow.putAll(player.getInventory().addItem(one));
                }
            }
        } else {
            ItemStack base = ProgressionRepository.deserializeItem(offer);
            if (base == null || base.getType().isAir()) {
                messages.send(player, "shop.offer-missing", "&cПредмет товара недоступен.");
                return;
            }
            ItemStack give = base.clone();
            give.setAmount(give.getAmount() * amount);
            overflow.putAll(player.getInventory().addItem(give));
        }
        overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        messages.send(player, "shop.bought", "&aКуплено за &e<price> &aмон.: &f<item>",
            new MessageService.Placeholder("price", total.toPlainString()),
            new MessageService.Placeholder("item", offer.id()));
        audits.append(new AuditEvent(
            "PROGRESSION_SHOP_BUY",
            player.getUniqueId(),
            null,
            player.getName(),
            null,
            "Progression shop",
            Map.of("offer", offer.id(), "amount", String.valueOf(amount)),
            System.currentTimeMillis()
        ));
    }

    @Override
    public CompletableFuture<Void> buy(Player player, String offerId, int amount) {
        return shopRepository.findShopOffer(offerId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                messages.send(player, "shop.offer-missing", "&cТовар не найден.");
                return CompletableFuture.completedFuture(null);
            }
            return buy(player, opt.get(), amount);
        });
    }

    @Override
    public CompletableFuture<Void> sell(Player player, ItemStack itemStack, BigDecimal price) {
        messages.send(player, "shop.sell-disabled", "&cВ этом магазине нельзя продавать предметы.");
        return CompletableFuture.completedFuture(null);
    }
}
