package com.foenichs.uniqueloot.service

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.Plugin

/**
 * Typed access to the values of the config.yml, read once and again on reload
 */
class ConfigService(private val plugin: Plugin) {
    @Volatile
    private var values = Values(plugin.config)

    val restricted get() = values.restricted
    val confirmation get() = values.confirmation
    val autoReplenish get() = values.autoReplenish
    val maxRefills get() = values.maxRefills
    val requireEmptied get() = values.requireEmptied

    /**
     * Loads config.yml from disk again
     */
    fun reload() {
        plugin.reloadConfig()
        values = Values(plugin.config)
    }

    /**
     * Random time in milliseconds before a container can refill
     */
    fun refillDelay() = values.let { (it.minDelay..it.maxDelay).random() }

    /**
     * Values of one config.yml read
     */
    private class Values(config: FileConfiguration) {
        val restricted = config.getBoolean("destroying.restricted", true)
        val confirmation = config.getBoolean("destroying.confirmation", true)
        val autoReplenish = config.getBoolean("auto-replenish.enabled", false)
        val maxRefills = config.getInt("auto-replenish.max-refills", 2).let { if (it < 0) Int.MAX_VALUE else it }
        val requireEmptied = config.getBoolean("auto-replenish.require-emptied", true)

        val minDelay = config.getLong("auto-replenish.minutes-min", 720).coerceAtLeast(0) * 60_000L
        val maxDelay = (config.getLong("auto-replenish.minutes-max", 2880) * 60_000L).coerceAtLeast(minDelay)
    }
}
