package com.foenichs.uniqueloot.listener.protection

import com.foenichs.uniqueloot.service.ProtectionService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent

class ExplosionProtectionListener(
    private val protection: ProtectionService
) : Listener {

    /**
     * Prevent loot chests and loot minecart rails being destroyed by block explosions
     */
    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { protection.isLootChest(it) || protection.isLootMinecartBase(it) }
    }

    /**
     * Prevent loot chests and loot minecart rails being destroyed by entity explosions
     */
    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { protection.isLootChest(it) || protection.isLootMinecartBase(it) }
    }
}
