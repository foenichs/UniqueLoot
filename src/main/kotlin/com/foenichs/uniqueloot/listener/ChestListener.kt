package com.foenichs.uniqueloot.listener

import com.foenichs.uniqueloot.service.ContainerService
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.vehicle.minecart.MinecartChest
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class ChestListener(
    private val containers: ContainerService
) : Listener {

    /**
     * Opens a loot container with a personal inventory
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val clicked = event.clickedBlock ?: return
        val player = (event.player as CraftPlayer).handle
        val level = (clicked.world as CraftWorld).handle

        if (containers.open(player, level, BlockPos(clicked.x, clicked.y, clicked.z))) {
            event.isCancelled = true
        }
    }

    /**
     * Opens a loot chest minecart with a personal inventory
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val minecart = (event.rightClicked as CraftEntity).handle as? MinecartChest ?: return
        val player = (event.player as CraftPlayer).handle

        if (containers.open(player, minecart)) {
            event.isCancelled = true
        }
    }
}
