package com.foenichs.uniqueloot.service

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player

@Suppress("UnstableApiUsage")
class DialogService {

    /**
     * Asks the player to confirm removing a loot container
     */
    fun confirmRemoval(viewer: Player, onConfirm: () -> Unit) {
        val dialog = Dialog.create { b ->
            b.empty().base(
                DialogBase.builder(Component.text("Unique Loot"))
                    .body(listOf(DialogBody.plainMessage(
                        Component.text("Are you sure you want to remove this loot container?"), 160
                    )))
                    .build()
            ).type(
                DialogType.multiAction(listOf(
                    ActionButton.create(Component.text("Cancel"), null, 75, DialogAction.customClick({ _, _ -> }, ClickCallback.Options.builder().uses(1).build())),
                    ActionButton.create(Component.text("Confirm"), null, 75, DialogAction.customClick({ _, _ ->
                        onConfirm()
                    }, ClickCallback.Options.builder().uses(1).build()))
                )).build()
            )
        }
        viewer.showDialog(dialog)
    }
}
