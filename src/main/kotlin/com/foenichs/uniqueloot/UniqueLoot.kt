package com.foenichs.uniqueloot

import com.foenichs.uniqueloot.inventory.LootStorage
import com.foenichs.uniqueloot.listener.ChestListener
import com.foenichs.uniqueloot.listener.protection.BlockProtectionListener
import com.foenichs.uniqueloot.listener.protection.EntityProtectionListener
import com.foenichs.uniqueloot.listener.protection.ExplosionProtectionListener
import com.foenichs.uniqueloot.service.CompatService
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
        // Initialize Compatibility
        val compatService = try {
            CompatService()
        } catch (ex: Exception) {
            return disableUnsupported(ex)
        } catch (ex: LinkageError) {
            return disableUnsupported(ex)
        }

        // Initialize Storage
        val storage = LootStorage(this, compatService)

        // Initialize Services
        val lootService = LootService(storage, compatService)
        migrationService = MigrationService(this)
        containerService = ContainerService(lootService, migrationService)
        protectionService = ProtectionService(this, compatService)
        val dialogService = DialogService()

        // Register Event Listeners
        val pluginManager = server.pluginManager
        pluginManager.registerEvents(ChestListener(containerService), this)

        // Rule Enforcement
        pluginManager.registerEvents(BlockProtectionListener(protectionService, dialogService), this)
        pluginManager.registerEvents(EntityProtectionListener(protectionService, dialogService), this)
        pluginManager.registerEvents(ExplosionProtectionListener(protectionService), this)

        // bStats
        Metrics(this, 27274)
    }

    /**
     * Logs error message and disables the plugin
     */
    private fun disableUnsupported(ex: Throwable) {
        logger.severe("This Minecraft version (${server.minecraftVersion}) isn't supported: ${ex.message}")
        server.pluginManager.disablePlugin(this)
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
