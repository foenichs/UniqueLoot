package com.foenichs.uniqueloot.service

import com.foenichs.uniqueloot.inventory.LootStorage
import com.foenichs.uniqueloot.inventory.PersonalContainer
import com.foenichs.uniqueloot.inventory.Refill
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.RandomizableContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.ContainerEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import java.util.UUID

class LootService(
    private val storage: LootStorage,
    private val compat: CompatService,
    private val config: ConfigService
) {

    /**
     * Whether the container is a block entity or minecart with a loot table
     */
    fun hasLootTable(container: Container) = lootTable(container) != null

    /**
     * Loads the player's saved or migrated items or generates new loot
     */
    fun personalContainer(level: ServerLevel, real: Container, player: ServerPlayer, migrated: () -> List<ItemStack>?): PersonalContainer {
        val container = PersonalContainer(real, player.uuid, storage)
        val saved = storage.load(real, player.uuid) ?: migrated()
        val refill = storage.refill(real, player.uuid)
        if (saved != null && !isRefillDue(real, player.uuid, saved, refill)) {
            container.setContents(saved)
            return container
        }

        generateLoot(level, real, container, player)
        if (config.autoReplenish) scheduleRefill(real, player.uuid, if (saved != null) refill.count + 1 else 0)
        return container
    }

    /**
     * Whether the refill time passed and the saved items allow a refill
     */
    private fun isRefillDue(real: Container, player: UUID, saved: List<ItemStack>, refill: Refill): Boolean {
        if (!config.autoReplenish || refill.count >= config.maxRefills) return false

        // Containers from before refilling was enabled start counting now
        val at = refill.at
        if (at == null) {
            scheduleRefill(real, player, refill.count)
            return false
        }
        return System.currentTimeMillis() >= at && (!config.requireEmptied || saved.all(ItemStack::isEmpty))
    }

    /**
     * Picks the next refill time of a player's container, none once the maximum refills are used
     */
    private fun scheduleRefill(real: Container, player: UUID, count: Int) {
        val at = if (count < config.maxRefills) System.currentTimeMillis() + config.refillDelay() else null
        storage.saveRefill(real, player, Refill(at, count))
    }

    /**
     * Fills a personal inventory from the container's loot table
     */
    private fun generateLoot(level: ServerLevel, real: Container, target: Container, player: ServerPlayer) {
        val key = lootTable(real) ?: return
        val table = level.server.reloadableRegistries().getLootTable(key)
        compat.triggerGenerateLoot(player, key)
        val params = LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, origin(real))
            .withLuck(player.luck)
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.CHEST)
        // Random seed per player
        table.fill(target, params, 0L)
    }

    /**
     * The loot table of a block entity or minecart
     */
    private fun lootTable(container: Container) = when (container) {
        is RandomizableContainer -> container.lootTable
        is ContainerEntity -> container.containerLootTable
        else -> null
    }

    /**
     * The position loot is generated at
     */
    private fun origin(container: Container): Vec3 = when (container) {
        is BlockEntity -> Vec3.atCenterOf(container.blockPos)
        is Entity -> container.position()
        else -> error("Unsupported loot holder")
    }
}
