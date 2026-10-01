package com.foenichs.uniqueloot.inventory

import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.bukkit.Location
import java.util.UUID

class PersonalContainer(
    private val real: Container,
    private val owner: UUID,
    private val storage: LootStorage,
) : SimpleContainer(real.containerSize) {

    /**
     * Fills the inventory with saved items
     */
    fun setContents(items: List<ItemStack>) {
        for (slot in 0 until minOf(containerSize, items.size)) setItem(slot, items[slot])
    }

    /**
     * Opens the real chest
     */
    override fun startOpen(user: ContainerUser) = real.startOpen(user)

    /**
     * Saves the items and closes the real chest
     */
    override fun stopOpen(user: ContainerUser) {
        storage.save(real, owner, (0 until containerSize).map(::getItem))
        real.stopOpen(user)
    }

    /**
     * Distance check against the real chest
     */
    override fun stillValid(player: Player) = real.stillValid(player)

    /**
     * Location of the real chest
     */
    override fun getLocation(): Location? = real.location
}
