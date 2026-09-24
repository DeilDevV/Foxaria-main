package com.foxaria.playershops;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.model.PlayerShop;
import com.foxaria.api.model.PlayerShopOffer;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.PlayerShopService;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcPlayerShopService implements PlayerShopService {

    private final JavaPlugin plugin;
    private final PlayerShopRepository repository;
    private final EconomyService economyService;
    private final MessageService messages;
    private final AuditService audits;
    private final int maxOffers;
    private final double defaultTaxPercent;

    public JdbcPlayerShopService(
        JavaPlugin plugin,
        PlayerShopRepository repository,
        EconomyService economyService,
        MessageService messages,
        AuditService audits,
        int maxOffers,
        double defaultTaxPercent
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.economyService = economyService;
        this.messages = messages;
        this.audits = audits;
        this.maxOffers = maxOffers;
        this.defaultTaxPercent = defaultTaxPercent;
    }

    @Override
    public CompletableFuture<PlayerShop> ensureShop(Player owner) {
        return repository.findByOwner(owner.getUniqueId()).thenCompose(optional -> {
            if (optional.isPresent()) {
                return CompletableFuture.completedFuture(optional.get());
            }
            PlayerShop shop = new PlayerShop(
                UUID.randomUUID().toString(),
                owner.getUniqueId(),
                owner.getName() + "'s Shop",
                "Private market storefront",
                true,
                defaultTaxPercent,
                System.currentTimeMillis()
            );
            return repository.createShop(shop).thenApply(ignored -> shop);
        });
    }

    @Override
    public CompletableFuture<List<PlayerShop>> listShops() {
        return repository.listOpenShops();
    }

    @Override
    public CompletableFuture<List<PlayerShopOffer>> offers(String shopId) {
        return repository.offers(shopId, false);
    }

    public CompletableFuture<List<PlayerShopOffer>> inspectOffers(String shopId) {
        return repository.offers(shopId, true);
    }

    @Override
    public CompletableFuture<Void> listOffer(Player owner, ItemStack itemStack, BigDecimal price) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            messages.send(owner, "playershops.no-item", "&cHold an item to list it in your shop.");
            return CompletableFuture.completedFuture(null);
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            messages.send(owner, "playershops.invalid-price", "&cPrice must be positive.");
            return CompletableFuture.completedFuture(null);
        }
        ItemStack listing = itemStack.clone();
        owner.getInventory().setItemInMainHand(null);
        return ensureShop(owner).thenCompose(shop ->
            repository.countOffers(shop.id()).thenCompose(count -> {
                if (count >= maxOffers && !owner.hasPermission("foxaria.playershops.unlimited")) {
                    owner.getInventory().setItemInMainHand(listing);
                    messages.send(owner, "playershops.offer-limit", "&cYou reached your active offer limit.");
                    return CompletableFuture.completedFuture(null);
                }
                PlayerShopOffer createdOffer = new PlayerShopOffer(
                    UUID.randomUUID().toString(),
                    shop.id(),
                    listing,
                    ItemStackSerializer.fingerprint(listing),
                    price.setScale(2, RoundingMode.HALF_UP),
                    listing.getAmount(),
                    true,
                    System.currentTimeMillis()
                );
                createdOffer = new PlayerShopOffer(createdOffer.id(), createdOffer.shopId(), createdOffer.item().asOne(), createdOffer.fingerprint(), createdOffer.price(), listing.getAmount(), true, createdOffer.createdAt());
                final PlayerShopOffer finalOffer = createdOffer;
                return repository.createOffer(finalOffer).thenRun(() -> {
                    messages.send(owner, "playershops.offer-created", "&aListed <amount>x item in your shop for <price> each.",
                        new MessageService.Placeholder("amount", String.valueOf(listing.getAmount())),
                        new MessageService.Placeholder("price", price.toPlainString()));
                    audits.append(new AuditEvent(
                        "PLAYER_SHOP_OFFER_CREATED",
                        owner.getUniqueId(),
                        null,
                        owner.getName(),
                        null,
                        "Player shop offer created",
                        Map.of("shopId", shop.id(), "offerId", finalOffer.id(), "stock", String.valueOf(finalOffer.stock()), "price", finalOffer.price().toPlainString()),
                        System.currentTimeMillis()
                    ));
                });
            })
        ).exceptionally(throwable -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> owner.getInventory().setItemInMainHand(listing));
            messages.send(owner, "playershops.offer-failed", "&cFailed to create shop offer.");
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> buy(Player buyer, String offerId) {
        return repository.reserveOffer(offerId).thenCompose(optional -> {
            if (optional.isEmpty()) {
                messages.send(buyer, "playershops.offer-missing", "&cOffer is no longer available.");
                return CompletableFuture.completedFuture(null);
            }
            PlayerShopRepository.ReservedPurchase reserved = optional.get();
            PlayerShop shop = reserved.shop();
            PlayerShopOffer offer = reserved.offer();
            if (shop.ownerUuid().equals(buyer.getUniqueId())) {
                messages.send(buyer, "playershops.self-buy", "&cYou cannot buy your own offer.");
                return CompletableFuture.completedFuture(null);
            }
            return hasInventorySpaceSync(buyer, offer.item()).thenCompose(hasSpace -> {
                if (!hasSpace) {
                    messages.send(buyer, "playershops.inventory-full", "&cMake room in your inventory before buying.");
                    return CompletableFuture.completedFuture(null);
                }
                BigDecimal tax = offer.price().multiply(BigDecimal.valueOf(shop.taxPercent())).divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
                BigDecimal payout = offer.price().subtract(tax);
                return economyService.withdraw(buyer.getUniqueId(), offer.price(), "playershop_buy:" + offer.id(), null)
                    .thenCompose(ignored -> economyService.deposit(shop.ownerUuid(), payout, "playershop_sale:" + offer.id(), buyer.getUniqueId()))
                    .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> buyer.getInventory().addItem(offer.item().clone())))
                    .thenRun(() -> {
                        messages.send(buyer, "playershops.purchase-success", "&aBought item for <price>.", new MessageService.Placeholder("price", offer.price().toPlainString()));
                        audits.append(new AuditEvent(
                            "PLAYER_SHOP_PURCHASE",
                            buyer.getUniqueId(),
                            shop.ownerUuid(),
                            buyer.getName(),
                            shop.ownerUuid().toString(),
                            "Player shop purchase completed",
                            Map.of("shopId", shop.id(), "offerId", offer.id(), "price", offer.price().toPlainString(), "tax", tax.toPlainString()),
                            System.currentTimeMillis()
                        ));
                    });
            });
        });
    }

    private CompletableFuture<Boolean> hasInventorySpaceSync(Player player, ItemStack item) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int remaining = item.getAmount();
            for (ItemStack content : player.getInventory().getStorageContents()) {
                if (content == null || content.getType() == Material.AIR) {
                    future.complete(true);
                    return;
                }
                if (!content.isSimilar(item)) {
                    continue;
                }
                remaining -= Math.max(0, content.getMaxStackSize() - content.getAmount());
                if (remaining <= 0) {
                    future.complete(true);
                    return;
                }
            }
            future.complete(false);
        });
        return future;
    }
}
