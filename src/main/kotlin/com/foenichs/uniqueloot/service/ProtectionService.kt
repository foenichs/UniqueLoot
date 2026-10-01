package com.foenichs.uniqueloot.service

import net.minecraft.world.entity.vehicle.ContainerEntity
import net.minecraft.world.level.block.ChestBlock
import org.bukkit.GameMode
import org.bukkit.Tag
import org.bukkit.block.Barrel
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.Chest
import org.bukkit.block.data.type.Chest.Type
import org.bukkit.craftbukkit.block.CraftBlock
import org.bukkit.craftbukkit.block.data.CraftBlockData
import org.bukkit.craftbukkit.entity.CraftMinecart
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.StorageMinecart
import org.bukkit.inventory.InventoryHolder
import org.bukkit.loot.LootTable
import org.bukkit.loot.Lootable
import java.util.UUID

class ProtectionService {
    private val frozen = mutableSetOf<StorageMinecart>()
    private val removing = mutableSetOf<UUID>()

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
     * Whether the player is removing a loot container they confirmed
     */
    fun isRemoving(player: Player) = player.uniqueId in removing

    /**
     * Removes a loot chest for a creative player who confirmed it
     */
    fun removeLootChest(player: Player, block: Block) {
        if (!player.isOnline || player.gameMode != GameMode.CREATIVE || !isLootChest(block)) return
        confirmed(player) { player.breakBlock(block) }
    }

    /**
     * Removes a loot minecart for a creative player who confirmed it
     */
    fun removeLootMinecart(player: Player, minecart: Entity) {
        if (!player.isOnline || player.gameMode != GameMode.CREATIVE || minecart.isDead || !isLootMinecart(minecart)) return
        confirmed(player) { player.attack(minecart) }
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
        (minecart as CraftMinecart).handle.maxSpeed = 0.0
        frozen.add(minecart)
    }

    /**
     * Stops tracking a loot minecart that left the world
     */
    fun release(minecart: StorageMinecart) {
        frozen.remove(minecart)
    }

    /**
     * Gives all locked loot minecarts their vanilla speed limit back
     */
    fun unfreezeAll() {
        frozen.forEach { (it as CraftMinecart).handle.maxSpeed = null }
        frozen.clear()
    }

    /**
     * Removes velocity from locked loot minecarts and unlocks those that lost their loot table
     */
    fun tick() {
        val minecarts = frozen.iterator()
        while (minecarts.hasNext()) {
            val handle = (minecarts.next() as CraftMinecart).handle
            // No longer a loot minecart
            if ((handle as ContainerEntity).containerLootTable == null) {
                handle.maxSpeed = null
                minecarts.remove()
                continue
            }

            val velocity = handle.deltaMovement
            if (velocity.horizontalDistanceSqr() < 1.0E-7) continue
            handle.setDeltaMovement(0.0, velocity.y, 0.0)
            handle.hurtMarked = true
        }
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
        val partnerFace = CraftBlock.notchToBlockFace(ChestBlock.getConnectedDirection((data as CraftBlockData).state))
        if (!isLootChest(block.getRelative(partnerFace))) return
        data.type = Type.SINGLE
        block.setBlockData(data, false)
    }
}
