package com.foenichs.uniqueloot.service

import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootTable
import org.bukkit.craftbukkit.inventory.CraftItemStack
import java.lang.reflect.Method

/**
 * Support for older versions by handling everything that differs
 */
class CompatService {
    private val lootTrigger: Any
    private val lootTriggerMethod: Method
    private val asBukkitCopy: Method

    /**
     * Whether the server runs threading
     */
    val folia = runCatching { Class.forName("io.papermc.paper.threadedregions.RegionizedServer") }.isSuccess

    init {
        // CriteriaTriggers moved to its own package in 26.x
        val triggers = listOf("net.minecraft.advancements.triggers.CriteriaTriggers", "net.minecraft.advancements.CriteriaTriggers")
            .firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: error("CriteriaTriggers not found")
        lootTrigger = triggers.getField("GENERATE_LOOT").get(null)
        lootTriggerMethod = lootTrigger.javaClass.getMethod("trigger", ServerPlayer::class.java, ResourceKey::class.java)

        // The item parameter became a supertype of ItemStack in 26.3
        asBukkitCopy = CraftItemStack::class.java.methods.firstOrNull {
            it.name == "asBukkitCopy" && it.parameterTypes.singleOrNull()?.isAssignableFrom(ItemStack::class.java) == true
        } ?: error("CraftItemStack.asBukkitCopy not found")
    }

    /**
     * Triggers the advancement criterion for generated loot
     */
    fun triggerGenerateLoot(player: ServerPlayer, key: ResourceKey<LootTable>) {
        lootTriggerMethod.invoke(lootTrigger, player, key)
    }

    /**
     * Copies an item to the Bukkit item type
     */
    fun toBukkit(item: ItemStack): org.bukkit.inventory.ItemStack = asBukkitCopy.invoke(null, item) as org.bukkit.inventory.ItemStack
}
