package com.foenichs.uniqueloot.service

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.minecraft.world.entity.vehicle.ContainerEntity
import org.bukkit.GameMode
import org.bukkit.Tag
import org.bukkit.block.Barrel
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.block.data.type.Chest.Type
import org.bukkit.craftbukkit.entity.CraftMinecart
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.StorageMinecart
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.loot.LootTable
import org.bukkit.loot.Lootable
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ProtectionService(
    private val plugin: Plugin,
    private val compat: CompatService
) {
    private val frozen = ConcurrentHashMap<StorageMinecart, ScheduledTask>()
    private val removing = ConcurrentHashMap.newKeySet<UUID>()
    private val horizontal = listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)

    /**
     * Whether the block is a chest or barrel with a loot table
     */
    fun isLootChest(block: Block): Boolean {
        val state = block.getState(false)
        return (state is Chest || state is Barrel) && (state as Lootable).lootTable != null
    }

    /**
     * Whether the entity is a chest minecart with a loot table
     */
    fun isLootMinecart(entity: Entity) = entity is StorageMinecart && entity.lootTable != null

    /**
     * Whether the inventory belongs to a loot chest, barrel or chest minecart
     */
    fun isLootInventory(inventory: Inventory): Boolean {
        val holder = inventory.getHolder(false)
        if (holder is DoubleChest) return isLootHolder(holder.getLeftSide(false)) || isLootHolder(holder.getRightSide(false))
        return isLootHolder(holder)
    }

    /**
     * Whether the holder is a chest, barrel or chest minecart with a loot table
     */
    private fun isLootHolder(holder: InventoryHolder?) =
        (holder is Chest || holder is Barrel || holder is StorageMinecart) && (holder as Lootable).lootTable != null

    /**
     * Whether the player is removing a loot container they confirmed
     */
    fun isRemoving(player: Player) = player.uniqueId in removing

    /**
     * Removes a loot chest for a creative player who confirmed it
     */
    fun removeLootChest(player: Player, block: Block) {
        player.scheduler.run(plugin, {
            if (player.gameMode == GameMode.CREATIVE && isLootChest(block)) confirmed(player) { player.breakBlock(block) }
        }, null)
    }

    /**
     * Removes a loot minecart for a creative player who confirmed it
     */
    fun removeLootMinecart(player: Player, minecart: Entity) {
        player.scheduler.run(plugin, {
            if (player.gameMode == GameMode.CREATIVE && !minecart.isDead && isLootMinecart(minecart)) confirmed(player) { player.attack(minecart) }
        }, null)
    }

    /**
     * Runs the removal with the player's protection bypass active
     */
    private fun confirmed(player: Player, removal: () -> Unit) {
        removing.add(player.uniqueId)
        try {
            removal()
        } finally {
            removing.remove(player.uniqueId)
        }
    }

    /**
     * Whether the block is the rail or the block below the rail of a loot minecart
     */
    fun isLootMinecartBase(block: Block) = hasLootMinecart(block) || hasLootMinecart(block.getRelative(BlockFace.UP))

    /**
     * Whether a loot minecart sits on the rail block
     */
    private fun hasLootMinecart(rail: Block): Boolean {
        if (!Tag.RAILS.isTagged(rail.type)) return false
        return rail.world.getNearbyEntitiesByType(StorageMinecart::class.java, rail.location.add(0.5, 0.5, 0.5), 0.5)
            .any { isLootMinecart(it) && it.location.block == rail }
    }

    /**
     * Locks a loot minecart in place until the plugin is disabled, nothing is saved on the entity
     */
    fun freeze(minecart: StorageMinecart) {
        minecart.maxSpeed = 0.0
        val task = minecart.scheduler.runAtFixedRate(plugin, { tick(minecart, it) }, { frozen.remove(minecart) }, 1L, 1L) ?: return
        frozen.put(minecart, task)?.cancel()
    }

    /**
     * Stops tracking a loot minecart that left the world
     */
    fun release(minecart: StorageMinecart) {
        frozen.remove(minecart)?.cancel()
    }

    /**
     * Gives all locked loot minecarts their vanilla speed limit back
     */
    fun unfreezeAll() {
        // Plugins only unload with the server on Folia
        if (compat.folia) return

        frozen.forEach { (minecart, task) ->
            task.cancel()
            (minecart as CraftMinecart).handle.maxSpeed = null
        }
        frozen.clear()
    }

    /**
     * Removes velocity from locked loot minecarts and unlocks those that lost their loot table
     */
    private fun tick(minecart: StorageMinecart, task: ScheduledTask) {
        val handle = (minecart as CraftMinecart).handle
        // No longer a loot minecart
        if ((handle as ContainerEntity).containerLootTable == null) {
            handle.maxSpeed = null
            frozen.remove(minecart)
            task.cancel()
            return
        }

        val velocity = minecart.velocity
        if (velocity.x * velocity.x + velocity.z * velocity.z < 1.0E-7) return
        minecart.velocity = Vector(0.0, velocity.y, 0.0)
    }

    /**
     * Restores the loot table cleared by Paper (Paper#12998)
     */
    fun restoreLootTable(holder: InventoryHolder, table: LootTable) {
        val live = if (holder is BlockState) holder.block.getState(false) else holder
        if (live is Lootable) live.lootTable = table
    }

    /**
     * Keeps a placed chest single if it would merge with a loot chest
     */
    fun separateFromLootChest(block: Block) {
        val data = block.blockData as? org.bukkit.block.data.type.Chest ?: return
        if (data.type == Type.SINGLE) return
        val partnerFace = if (data.type == Type.LEFT) turn(data.facing, 1) else turn(data.facing, 3)
        if (!isLootChest(block.getRelative(partnerFace))) return
        data.type = Type.SINGLE
        block.setBlockData(data, false)
    }

    /**
     * The horizontal face a number of clockwise quarter turns away, seen from above
     */
    private fun turn(face: BlockFace, quarterTurns: Int) = horizontal[(horizontal.indexOf(face) + quarterTurns) % 4]
}
