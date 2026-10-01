package com.foenichs.uniqueloot.service

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.CompoundContainer
import net.minecraft.world.Container
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.monster.piglin.PiglinAi
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.minecart.MinecartChest
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuConstructor
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.TrappedChestBlock
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.gameevent.GameEvent

class ContainerService(
    private val loot: LootService,
    private val migration: MigrationService
) {
    private val viewing = mutableSetOf<Player>()

    /**
     * Opens a loot container with a personal inventory, false if there is nothing to open
     */
    fun open(player: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean {
        // Left to vanilla
        if (player.isSpectator) return false
        if (player.isSecondaryUseActive && (!player.mainHandItem.isEmpty || !player.offhandItem.isEmpty)) return false

        val state = level.getBlockState(pos)
        val block = state.block
        val stat = when (block) {
            is TrappedChestBlock -> Stats.TRIGGER_TRAPPED_CHEST
            is ChestBlock -> Stats.OPEN_CHEST
            is BarrelBlock -> Stats.OPEN_BARREL
            else -> return false
        }

        // Blocked containers have no provider
        state.getMenuProvider(level, pos) ?: return false
        val real = when (block) {
            is ChestBlock -> ChestBlock.getContainer(block, state, level, pos, false)
            else -> level.getBlockEntity(pos) as? Container
        } ?: return false
        val halves = if (real is CompoundContainer) listOf(real.container1, real.container2) else listOf(real)
        if (halves.none { loot.hasLootTable(it) }) return false

        // Loot Chest or Loot Barrel
        val title = title(if (block is BarrelBlock) "container.barrel" else "container.chest")

        if (openMenu(player, level, halves, real, title)) {
            player.awardStat(stat)
            PiglinAi.angerNearbyPiglins(level, player, true)
        }
        return true
    }

    /**
     * Opens a loot chest minecart with a personal inventory, false if there is nothing to open
     */
    fun open(player: ServerPlayer, minecart: MinecartChest): Boolean {
        // Left to vanilla
        if (player.isSpectator) return false
        if (!loot.hasLootTable(minecart)) return false

        val level = player.level()
        if (openMenu(player, level, listOf(minecart), minecart, title("container.chest"))) {
            minecart.gameEvent(GameEvent.CONTAINER_OPEN, player)
            PiglinAi.angerNearbyPiglins(level, player, true)
        }
        return true
    }

    /**
     * Title of a personal loot inventory
     */
    private fun title(key: String): Component = Component.literal("Loot ").append(Component.translatable(key))

    /**
     * Opens the personal menu, false if the open was cancelled
     */
    private fun openMenu(player: ServerPlayer, level: ServerLevel, halves: List<Container>, real: Container, title: Component): Boolean {
        var shown: Container? = null
        val opened = player.openMenu(personalMenuProvider(level, halves, real, title) { shown = it }).isPresent
        // Cancelled open
        if (!opened) shown?.stopOpen(player)
        else viewing.add(player)
        return opened
    }

    /**
     * Closes all personal inventories so their items are saved
     */
    fun closeAll() {
        viewing.toList().forEach { it.closeContainer() }
        viewing.clear()
    }

    /**
     * Builds the menu provider showing the player's own inventory
     */
    private fun personalMenuProvider(
        level: ServerLevel,
        halves: List<Container>,
        real: Container,
        title: Component,
        onBuilt: (Container) -> Unit,
    ) = SimpleMenuProvider(MenuConstructor { id, inventory, p ->
        // Locked containers
        if (halves.any { it is BaseContainerBlockEntity && !it.canOpen(p) }) return@MenuConstructor null
        val player = p as ServerPlayer

        // Looked up once, and only for containers without new data
        val migrated by lazy { migration.load(player.uuid, level.world.uid, halves) }

        // Halves without a loot table stay shared
        val parts = halves.mapIndexed { i, half ->
            if (loot.hasLootTable(half)) loot.personalContainer(level, half, player) { migrated?.get(i) } else half
        }
        val shown = if (parts.size == 2) CompoundContainer(parts[0], parts[1]) else parts[0]
        onBuilt(shown)
        object : ChestMenu(
            if (shown.containerSize > 27) MenuType.GENERIC_9x6 else MenuType.GENERIC_9x3,
            id, inventory, shown, shown.containerSize / 9,
        ) {

            /**
             * Reports the real chest so vanilla counts the player as an opener
             */
            override fun getContainer(): Container = real

            /**
             * Stops tracking the player and saves the items
             */
            override fun removed(player: Player) {
                viewing.remove(player)
                super.removed(player)
            }
        }
    }, title)
}
