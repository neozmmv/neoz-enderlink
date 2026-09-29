package io.github.neozmmv.enderlink.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import computer.iroh.Endpoint;
import computer.iroh.EndpointOptions;
import io.github.neozmmv.enderlink.Enderlink;
import io.github.neozmmv.enderlink.iroh.IrohAsync;
import kotlin.Unit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderlinkClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(Enderlink.MOD_ID);

	// Bound lazily on the first button click (default options use iroh's n0 preset: public relays + discovery)
	private CompletableFuture<Endpoint> endpoint;

	@Override
	public void onInitializeClient() {
		// Add a button to the title screen every time it is initialized
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				Button button = Button.builder(Component.literal("Enderlink"), b -> onEnderlinkClicked())
						.bounds(10, 10, 100, 20) // x, y, width, height
						.build();

				Screens.getWidgets(screen).add(button);
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeEndpoint());
	}

	private void onEnderlinkClicked() {
		LOGGER.info("Enderlink button clicked");

		if (endpoint == null) {
			EndpointOptions options = new EndpointOptions();
			endpoint = IrohAsync.future(c -> Endpoint.Companion.bind(options, c));
		}

		endpoint.whenComplete((ep, error) -> {
			if (error != null) {
				LOGGER.error("Failed to bind iroh endpoint", error);
			} else {
				LOGGER.info("iroh endpoint id: {}", ep.id());
			}
		});
	}

	private void closeEndpoint() {
		if (endpoint == null || !endpoint.isDone() || endpoint.isCompletedExceptionally()) {
			return;
		}

		Endpoint ep = endpoint.join();
		try {
			IrohAsync.<Unit>future(ep::shutdown).get(2, TimeUnit.SECONDS);
		} catch (Exception e) {
			LOGGER.warn("iroh endpoint did not shut down cleanly", e);
		} finally {
			ep.close();
		}
	}
}
