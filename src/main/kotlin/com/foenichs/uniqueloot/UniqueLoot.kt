package com.foenichs.uniqueloot

import com.foenichs.uniqueloot.inventory.LootStorage
import com.foenichs.uniqueloot.listener.ChestListener
import com.foenichs.uniqueloot.listener.protection.BlockProtectionListener
import com.foenichs.uniqueloot.listener.protection.EntityProtectionListener
import com.foenichs.uniqueloot.listener.protection.ExplosionProtectionListener
import com.foenichs.uniqueloot.service.ContainerService
import com.foenichs.uniqueloot.service.DialogService
import com.foenichs.uniqueloot.service.LootService
import com.foenichs.uniqueloot.service.MigrationService
import com.foenichs.uniqueloot.service.ProtectionService
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.java.JavaPlugin

class UniqueLoot : JavaPlugin() {
    private lateinit var migrationService: MigrationService
    private lateinit var containerService: ContainerService
    private lateinit var protectionService: ProtectionService

    /**
     * Creates the services and registers the listeners
     */
    override fun onEnable() {
        // Initialize Storage
        val storage = LootStorage(this)

        // Initialize Services
        val lootService = LootService(storage)
        migrationService = MigrationService(this)
        containerService = ContainerService(lootService, migrationService)
        protectionService = ProtectionService()
        val dialogService = DialogService()

        // Register Event Listeners
        val pluginManager = server.pluginManager
        pluginManager.registerEvents(ChestListener(containerService), this)

        // Rule Enforcement
        pluginManager.registerEvents(BlockProtectionListener(protectionService, dialogService), this)
        pluginManager.registerEvents(EntityProtectionListener(protectionService, dialogService), this)
        pluginManager.registerEvents(ExplosionProtectionListener(protectionService), this)

        // Keep loot minecarts at rest
        server.scheduler.runTaskTimer(this, Runnable { protectionService.tick() }, 1L, 1L)

        // bStats
        Metrics(this, 27274)
    }

    /**
     * Saves open personal inventories, closes the old database and unlocks loot minecarts
     */
    override fun onDisable() {
        if (::containerService.isInitialized) containerService.closeAll()
        if (::migrationService.isInitialized) migrationService.close()

        // Leave no state on entities
        if (::protectionService.isInitialized) protectionService.unfreezeAll()
    }
}
