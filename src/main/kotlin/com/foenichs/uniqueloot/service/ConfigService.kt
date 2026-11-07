package com.foenichs.uniqueloot.service

import org.bukkit.plugin.Plugin

/**
 * Typed access to the values of the config.yml, read live for instant reload
 */
class ConfigService(private val plugin: Plugin) {
    private val config get() = plugin.config

    val autoReplenish get() = config.getBoolean("auto-replenish.enabled", false)
    val maxRefills get() = config.getInt("auto-replenish.max-refills", 2).let { if (it < 0) Int.MAX_VALUE else it }
    val requireEmptied get() = config.getBoolean("auto-replenish.require-emptied", true)

    private val minDelay get() = config.getLong("auto-replenish.minutes-min", 720).coerceAtLeast(0) * 60_000L
    private val maxDelay get() = (config.getLong("auto-replenish.minutes-max", 2880) * 60_000L).coerceAtLeast(minDelay)

    /**
     * Loads config.yml from disk again
     */
    fun reload() = plugin.reloadConfig()

    /**
     * Random time in milliseconds before a container can refill
     */
    fun refillDelay() = (minDelay..maxDelay).random()
}
