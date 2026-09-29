package io.github.neozmmv.enderlink.client;

import io.github.neozmmv.enderlink.Enderlink;
import io.github.neozmmv.enderlink.client.iroh.IrohJoinProxy;
import io.github.neozmmv.enderlink.client.iroh.IrohNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderlinkClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger(Enderlink.MOD_ID);

	private static IrohNode iroh;
	private static IrohJoinProxy joinProxy;

	@Override
	public void onInitializeClient() {
		// Kept out of the config folder so modpacks don't hand every player the same identity
		iroh = IrohNode.start(FabricLoader.getInstance().getGameDir().resolve(Enderlink.MOD_ID).resolve("iroh_secret_key"));
		joinProxy = new IrohJoinProxy(iroh);
		iroh.key().thenAccept(key -> LOGGER.info("iroh key: {}", key));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			joinProxy.close();
			iroh.close();
		});
	}

	public static IrohNode iroh() {
		return iroh;
	}

	public static IrohJoinProxy joinProxy() {
		return joinProxy;
	}
}
