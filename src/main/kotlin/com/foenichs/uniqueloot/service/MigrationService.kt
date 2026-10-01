@file:Suppress("DEPRECATION")

package com.foenichs.uniqueloot.service

import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.io.BukkitObjectInputStream
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.UUID

class MigrationService(private val plugin: Plugin) : AutoCloseable {
    private val connection: Connection? = open()

    /**
     * Opens the legacy database if it exists
     */
    private fun open(): Connection? {
        val file = File(plugin.dataFolder, "uniqueLoot.db")
        if (!file.exists()) return null

        plugin.logger.info("Found legacy database, loot containers will be migrated on interact.")
        return try {
            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        } catch (ex: Exception) {
            plugin.logger.warning("Could not open the legacy database: ${ex.message}")
            null
        }
    }

    /**
     * Items a player saved in a legacy version for each half of the container, null if they never opened it
     */
    fun load(player: UUID, world: UUID, halves: List<Container>): List<List<ItemStack>>? {
        val connection = connection ?: return null
        val positions = halves.map { (it as? BlockEntity)?.blockPos ?: return null }
        val containerId = "$world:${positions.minOf { it.x }},${positions.minOf { it.y }},${positions.minOf { it.z }}"

        val items = MutableList(halves.sumOf { it.containerSize }) { ItemStack.EMPTY }
        var found = false
        try {
            connection.prepareStatement("SELECT slot, item_data FROM player_chest WHERE player_uuid = ? AND chest_id = ?").use { stmt ->
                stmt.setString(1, player.toString())
                stmt.setString(2, containerId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        found = true
                        // Slot -1 marks an emptied container
                        val slot = rs.getInt("slot")
                        if (slot in items.indices) decode(rs.getString("item_data"))?.let { items[slot] = it }
                    }
                }
            }
        } catch (ex: Exception) {
            plugin.logger.warning("Could not read the legacy database: ${ex.message}")
            return null
        }
        if (!found) return null

        // Each half takes its share of the slots
        var start = 0
        return halves.map { half -> items.subList(start, start + half.containerSize).also { start += half.containerSize } }
    }

    /**
     * Reads an item serialized by a legacy version
     */
    private fun decode(data: String): ItemStack? = try {
        BukkitObjectInputStream(Base64.getDecoder().decode(data).inputStream()).use { stream ->
            (stream.readObject() as? org.bukkit.inventory.ItemStack)?.let(CraftItemStack::asNMSCopy)
        }
    } catch (ex: Exception) {
        plugin.logger.warning("Could not read an legacy database item: ${ex.message}")
        null
    }

    /**
     * Closes the database
     */
    override fun close() {
        connection?.close()
    }
}
