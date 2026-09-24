package com.foxaria.auction;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.model.AuctionListing;
import com.foxaria.api.model.AuctionMailboxItem;
import com.foxaria.api.service.AuctionService;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.auction.gui.AuctionBrowserMenu;
import com.foxaria.auction.gui.AuctionMailboxMenu;
import com.foxaria.core.gui.MenuHolder;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcAuctionService implements AuctionService {

    private final JavaPlugin plugin;
    private final AuctionRepository repository;
    private final EconomyService economyService;
    private final MessageService messages;
    private final AuditService audits;
    private final long listingDurationMillis;
    private final BigDecimal feePercent;

    public JdbcAuctionService(
        JavaPlugin plugin,
        AuctionRepository repository,
        EconomyService economyService,
        MessageService messages,
        AuditService audits,
        long listingDurationSeconds,
        BigDecimal feePercent
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.economyService = economyService;
        this.messages = messages;
        this.audits = audits;
        this.listingDurationMillis = listingDurationSeconds * 1000L;
        this.feePercent = feePercent;
    }

    @Override
    public CompletableFuture<List<AuctionListing>> activeListings() {
        return repository.active();
    }

    @Override
    public CompletableFuture<Void> listItem(Player seller, ItemStack item, BigDecimal price) {
        if (item == null || item.getType() == Material.AIR) {
            messages.send(seller, "auction.no-item", "&cHold an item to list it.");
            return CompletableFuture.completedFuture(null);
        }

        ItemStack listedItem = item.clone();
        seller.getInventory().setItemInMainHand(null);
        BigDecimal fee = price.multiply(feePercent).divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        AuctionListing listing = new AuctionListing(
            UUID.randomUUID().toString(),
            seller.getUniqueId(),
            null,
            listedItem,
            ItemStackSerializer.fingerprint(listedItem),
            price.setScale(2, RoundingMode.HALF_UP),
            fee,
            "ACTIVE",
            System.currentTimeMillis() + listingDurationMillis,
            System.currentTimeMillis()
        );
        return repository.create(listing)
            .thenRun(() -> {
                messages.send(seller, "auction.listed", "&aListed item for <price>.", new MessageService.Placeholder("price", price.toPlainString()));
                audits.append(new AuditEvent(
                    "AUCTION_LIST_CREATED",
                    seller.getUniqueId(),
                    null,
                    seller.getName(),
                    null,
                    "Auction listing created",
                    Map.of("listingId", listing.id(), "price", price.toPlainString()),
                    System.currentTimeMillis()
                ));
                plugin.getServer().getScheduler().runTask(plugin, JdbcAuctionService.this::refreshOpenAuctionGUIs);
            })
            .exceptionally(throwable -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> seller.getInventory().addItem(listedItem));
                messages.send(seller, "auction.list-failed", "&cFailed to create listing.");
                return null;
            });
    }

    @Override
    public CompletableFuture<Void> buy(Player buyer, String listingId) {
        return repository.reserve(listingId, buyer.getUniqueId()).thenCompose(optionalListing -> {
            if (optionalListing.isEmpty()) {
                messages.send(buyer, "auction.unavailable", "&cListing is no longer available.");
                return CompletableFuture.completedFuture(null);
            }
            AuctionListing listing = optionalListing.get();
            return economyService.withdraw(buyer.getUniqueId(), listing.price(), "auction_buy:" + listing.id(), buyer.getUniqueId())
                .thenCompose(ignored -> economyService.deposit(listing.sellerUuid(), listing.price().subtract(listing.fee()), "auction_sale:" + listing.id(), buyer.getUniqueId()))
                .thenCompose(ignored -> repository.markSold(listing.id()))
                .thenCompose(ignored -> deliver(buyer, listing))
                .thenRun(() -> {
                    messages.send(buyer, "auction.bought", "&aBought listing for <price>.", new MessageService.Placeholder("price", listing.price().toPlainString()));
                    audits.append(new AuditEvent(
                        "AUCTION_ITEM_PURCHASED",
                        buyer.getUniqueId(),
                        listing.sellerUuid(),
                        buyer.getName(),
                        listing.sellerUuid().toString(),
                        "Auction purchase completed",
                        Map.of("listingId", listing.id(), "price", listing.price().toPlainString()),
                        System.currentTimeMillis()
                    ));
                    plugin.getServer().getScheduler().runTask(plugin, JdbcAuctionService.this::refreshOpenAuctionGUIs);
                })
                .exceptionallyCompose(throwable -> repository.release(listing.id()).thenRun(() -> {
                    messages.send(buyer, "auction.buy-failed", "&cAuction purchase failed.");
                    plugin.getServer().getScheduler().runTask(plugin, JdbcAuctionService.this::refreshOpenAuctionGUIs);
                }));
        });
    }

    @Override
    public CompletableFuture<Void> openMailbox(Player player) {
        return repository.mailbox(player.getUniqueId()).thenAccept(deliveries -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (AuctionRepository.MailboxDelivery delivery : deliveries) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(delivery.itemStack());
                if (overflow.isEmpty()) {
                    repository.markMailboxClaimed(delivery.id());
                }
            }
            messages.send(player, "auction.mailbox-opened", "&aЗабрано из почты всё, что поместилось в инвентарь.");
            refreshOpenAuctionGUIs();
        }));
    }

    @Override
    public CompletableFuture<List<AuctionMailboxItem>> pendingMailboxItems(UUID playerUuid) {
        return repository.mailbox(playerUuid).thenApply(list ->
            list.stream().map(d -> new AuctionMailboxItem(d.id(), d.itemStack().clone())).toList()
        );
    }

    @Override
    public CompletableFuture<Boolean> claimMailboxDelivery(Player player, String deliveryId) {
        return repository.mailbox(player.getUniqueId()).thenCompose(deliveries -> {
            Optional<AuctionRepository.MailboxDelivery> found = deliveries.stream()
                .filter(d -> d.id().equals(deliveryId))
                .findFirst();
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            AuctionRepository.MailboxDelivery delivery = found.get();
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    done.complete(false);
                    return;
                }
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(delivery.itemStack().clone());
                if (!overflow.isEmpty()) {
                    messages.send(player, "auction.mailbox-inv-full", "&cОсвободи место в инвентаре, чтобы забрать предмет.");
                    done.complete(false);
                    return;
                }
                repository.markMailboxClaimed(deliveryId).whenComplete((v, err) -> {
                    if (err != null) {
                        plugin.getLogger().warning("Mailbox claim failed: " + err.getMessage());
                        done.complete(false);
                    } else {
                        done.complete(true);
                        plugin.getServer().getScheduler().runTask(plugin, JdbcAuctionService.this::refreshOpenAuctionGUIs);
                    }
                });
            });
            return done;
        });
    }

    private void refreshOpenAuctionGUIs() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder mh)) {
                continue;
            }
            if (mh.menu() instanceof AuctionBrowserMenu ab) {
                ab.render(p);
            } else if (mh.menu() instanceof AuctionMailboxMenu am) {
                am.render(p);
            }
        }
    }

    private CompletableFuture<Void> deliver(Player buyer, AuctionListing listing) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(listing.item().clone());
            if (overflow.isEmpty()) {
                future.complete(null);
                return;
            }
            repository.addMailboxDelivery(buyer.getUniqueId(), listing.item()).thenRun(() -> future.complete(null));
        });
        return future;
    }
}
