package com.foenichs.uniqueloot.inventory

import com.foenichs.uniqueloot.service.CompatService
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * When a player's container refills next, null if it won't, and how often it refilled already
 */
data class Refill(val at: Long?, val count: Int)

class LootStorage(
    private val plugin: Plugin,
    private val compat: CompatService
) {

    /**
     * Storage key of a player's items on the chest
     */
    private fun key(player: UUID) = NamespacedKey(plugin, player.toString())

    /**
     * Storage key of a player's refill data on the chest
     */
    private fun refillKey(player: UUID, name: String) = NamespacedKey(plugin, "$player/$name")

    /**
     * Persistent data of the block entity or entity holding the loot
     */
    private fun data(holder: Container): PersistentDataContainer = when (holder) {
        is BlockEntity -> holder.persistentDataContainer
        is Entity -> holder.bukkitEntity.persistentDataContainer
        else -> error("Unsupported loot holder")
    }

    /**
     * Items a player left in the chest, null if never opened
     */
    fun load(holder: Container, player: UUID): List<ItemStack>? {
        val bytes = data(holder).get(key(player), PersistentDataType.BYTE_ARRAY) ?: return null
        return org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(bytes).map(CraftItemStack::asNMSCopy)
    }

    /**
     * Saves a player's items on the chest
     */
    fun save(holder: Container, player: UUID, items: List<ItemStack>) {
        val bytes = org.bukkit.inventory.ItemStack.serializeItemsAsBytes(items.map(compat::toBukkit))
        data(holder).set(key(player), PersistentDataType.BYTE_ARRAY, bytes)
        holder.setChanged()
    }

    /**
     * Refill data of a player on the chest
     */
    fun refill(holder: Container, player: UUID): Refill {
        val data = data(holder)
        return Refill(
            data.get(refillKey(player, "refill-at"), PersistentDataType.LONG),
            data.get(refillKey(player, "refills"), PersistentDataType.INTEGER) ?: 0
        )
    }

    /**
     * Saves refill data of a player on the chest
     */
    fun saveRefill(holder: Container, player: UUID, refill: Refill) {
        val data = data(holder)
        if (refill.at != null) data.set(refillKey(player, "refill-at"), PersistentDataType.LONG, refill.at)
        else data.remove(refillKey(player, "refill-at"))
        data.set(refillKey(player, "refills"), PersistentDataType.INTEGER, refill.count)
        holder.setChanged()
    }
}
