package com.foenichs.uniqueloot.inventory

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

class LootStorage(private val plugin: Plugin) {

    /**
     * Storage key of a player's items on the chest
     */
    private fun key(player: UUID) = NamespacedKey(plugin, player.toString())

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
        val bytes = org.bukkit.inventory.ItemStack.serializeItemsAsBytes(items.map(CraftItemStack::asBukkitCopy))
        data(holder).set(key(player), PersistentDataType.BYTE_ARRAY, bytes)
        holder.setChanged()
    }
}
