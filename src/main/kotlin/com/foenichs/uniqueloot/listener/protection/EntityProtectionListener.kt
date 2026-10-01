package com.foenichs.uniqueloot.listener.protection

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.foenichs.uniqueloot.service.DialogService
import com.foenichs.uniqueloot.service.ProtectionService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.StorageMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent

class EntityProtectionListener(
    private val protection: ProtectionService,
    private val dialogs: DialogService
) : Listener {

    /**
     * Lock loot minecarts in place when they enter the world
     */
    @EventHandler
    fun onEntityAddToWorld(event: EntityAddToWorldEvent) {
        val minecart = event.entity as? StorageMinecart ?: return
        if (protection.isLootMinecart(minecart)) protection.freeze(minecart)
    }

    /**
     * Stop tracking loot minecarts that leave the world
     */
    @EventHandler
    fun onEntityRemoveFromWorld(event: EntityRemoveFromWorldEvent) {
        (event.entity as? StorageMinecart)?.let { protection.release(it) }
    }

    /**
     * Prevent loot minecarts from being pushed by other entities
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVehicleCollision(event: VehicleEntityCollisionEvent) {
        if (protection.isLootMinecart(event.vehicle)) event.isCancelled = true
    }

    /**
     * Prevent loot minecarts from being damaged and destroyed, creative players have to confirm
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVehicleDamage(event: VehicleDamageEvent) {
        val minecart = event.vehicle
        if (!protection.isLootMinecart(minecart)) return

        val player = event.attacker as? Player
        if (player != null && protection.isRemoving(player)) return

        event.isCancelled = true
        if (player == null) return
        if (player.gameMode != GameMode.CREATIVE) {
            player.sendActionBar(
                Component.text("You can't destroy loot containers.", NamedTextColor.RED)
            )
            return
        }
        dialogs.confirmRemoval(player) { protection.removeLootMinecart(player, minecart) }
    }
}
