package com.foenichs.uniqueloot.service

import org.bukkit.plugin.Plugin

/**
 * Typed access to the values of the config.yml
 */
class ConfigService(plugin: Plugin) {
    private val config = plugin.config

    val autoReplenish = config.getBoolean("auto-replenish.enabled", false)
    val maxRefills = config.getInt("auto-replenish.max-refills", 2).let { if (it < 0) Int.MAX_VALUE else it }
    val requireEmptied = config.getBoolean("auto-replenish.require-emptied", true)

    private val minDelay = config.getLong("auto-replenish.minutes-min", 720).coerceAtLeast(0) * 60_000L
    private val maxDelay = (config.getLong("auto-replenish.minutes-max", 2880) * 60_000L).coerceAtLeast(minDelay)

    /**
     * Random time in milliseconds before a container can refill
     */
    fun refillDelay() = (minDelay..maxDelay).random()
}
