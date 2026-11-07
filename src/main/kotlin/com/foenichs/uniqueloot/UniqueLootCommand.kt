package com.foenichs.uniqueloot

import com.foenichs.uniqueloot.service.ConfigService
import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class UniqueLootCommand(
    private val config: ConfigService
) {

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("uniqueloot")
            .requires { it.sender.isOp }
            .then(Commands.literal("reloadConfig").executes { reload(it.source) })
            .build()

    private fun reload(source: CommandSourceStack): Int {
        config.reload()
        source.sender.sendMessage(Component.text("Successfully reloaded UniqueLoot's config!", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }
}
