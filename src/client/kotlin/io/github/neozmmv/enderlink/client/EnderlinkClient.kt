package io.github.neozmmv.enderlink.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

object EnderlinkClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("neoz_enderlink")

    override fun onInitializeClient() {
        // Add a button to the title screen every time it is initialized
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is TitleScreen) {
                val button = Button.builder(Component.literal("Enderlink")) {
                    // Runs when the button is clicked
                    logger.info("Enderlink button clicked")
                }
                    .bounds(10, 10, 100, 20) // x, y, width, height
                    .build()

                Screens.getWidgets(screen).add(button)
            }
        }
    }
}