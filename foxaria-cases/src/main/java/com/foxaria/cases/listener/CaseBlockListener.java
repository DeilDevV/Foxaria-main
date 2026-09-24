package com.foxaria.cases.listener;

import com.foxaria.api.service.MessageService;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.service.CaseItemService;
import com.foxaria.cases.service.CaseService;
import com.foxaria.cases.service.PlacedCaseService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class CaseBlockListener implements Listener {

    private final CaseService caseService;
    private final CaseItemService items;
    private final PlacedCaseService placedCases;
    private final MessageService messages;

    public CaseBlockListener(
        CaseService caseService,
        CaseItemService items,
        PlacedCaseService placedCases,
        MessageService messages
    ) {
        this.caseService = caseService;
        this.items = items;
        this.placedCases = placedCases;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        String caseId = items.readBlockItemCaseId(stack);
        if (caseId == null) {
            return;
        }
        if (caseService.config().definition(caseId).isEmpty()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "cases.missing", "&cТакой кейс не найден в конфиге.");
            return;
        }
        placedCases.place(event.getBlockPlaced(), caseId);
        messages.send(event.getPlayer(), "cases.placed", "&aКейс &f<case>&a установлен.",
            new MessageService.Placeholder("case", caseId)
        );
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String caseId = items.readPlacedBlockCaseId(block);
        if (caseId == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("foxaria.cases.admin")) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "cases.break-denied", "&cЭтот кейс может снять только администратор.");
            return;
        }
        placedCases.remove(CaseLocation.from(block, caseService.config().serverId()), true);
        messages.send(event.getPlayer(), "cases.removed", "&aКейс удалён.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (items.readPlacedBlockCaseId(block) == null) {
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("foxaria.cases.use") && !event.getPlayer().hasPermission("foxaria.cases.admin")) {
            messages.send(event.getPlayer(), "general.no-permission", "&cНет прав.");
            return;
        }
        caseService.openMenu(event.getPlayer(), block);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        caseService.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk()) {
            return;
        }
        // PlacedCaseService already tracks active cases; chunk load restores visuals lazily via restoreAll on startup.
    }
}
