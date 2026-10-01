package com.foenichs.uniqueloot.listener.protection

import com.foenichs.uniqueloot.service.DialogService
import com.foenichs.uniqueloot.service.ProtectionService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.block.Barrel
import org.bukkit.block.Chest
import org.bukkit.entity.minecart.StorageMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.world.LootGenerateEvent

class BlockProtectionListener(
    private val protection: ProtectionService,
    private val dialogs: DialogService
) : Listener {

    /**
     * Prevent the loot table from being unpacked
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLoot(event: LootGenerateEvent) {
        val holder = event.inventoryHolder ?: return
        if (holder !is Chest && holder !is Barrel && holder !is StorageMinecart) return
        event.isCancelled = true
        protection.restoreLootTable(holder, event.lootTable)
    }

    /**
     * Prevent players from breaking loot chests, creative players have to confirm
     */
    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        if (!protection.isLootChest(block) || protection.isRemoving(player)) return

        event.isCancelled = true
        if (player.gameMode != GameMode.CREATIVE) {
            player.sendActionBar(
                Component.text("You can't destroy loot containers.", NamedTextColor.RED)
            )
            return
        }
        dialogs.confirmRemoval(player) { protection.removeLootChest(player, block) }
    }

    /**
     * Prevent players from breaking the rail or support of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onMinecartBaseBreak(event: BlockBreakEvent) {
        if (event.player.gameMode == GameMode.CREATIVE || !protection.isLootMinecartBase(event.block)) return
        event.isCancelled = true
        event.player.sendActionBar(
            Component.text("You can't break support blocks of loot containers.", NamedTextColor.RED)
        )
    }

    /**
     * Prevent liquids from washing away the rail of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onBlockFlow(event: BlockFromToEvent) {
        if (protection.isLootMinecartBase(event.toBlock)) event.isCancelled = true
    }

    /**
     * Prevent players from placing liquids on the rail of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (event.player.gameMode == GameMode.CREATIVE) return
        if (protection.isLootMinecartBase(event.block)) event.isCancelled = true
    }

    /**
     * Prevent pistons pushing the rail or support of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { protection.isLootMinecartBase(it) }) event.isCancelled = true
    }

    /**
     * Prevent pistons pulling the rail or support of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { protection.isLootMinecartBase(it) }) event.isCancelled = true
    }

    /**
     * Prevent fire from burning the support of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        if (protection.isLootMinecartBase(event.block)) event.isCancelled = true
    }

    /**
     * Prevent mobs and falling blocks from changing the rail or support of a loot minecart
     */
    @EventHandler(ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (protection.isLootMinecartBase(event.block)) event.isCancelled = true
    }

    /**
     * Prevent placed chests from merging with loot chests
     */
    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        protection.separateFromLootChest(event.block)
    }
}
