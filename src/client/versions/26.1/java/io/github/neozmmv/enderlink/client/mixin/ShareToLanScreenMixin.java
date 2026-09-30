package io.github.neozmmv.enderlink.client.mixin;

import io.github.neozmmv.enderlink.client.EnderlinkClient;
import io.github.neozmmv.enderlink.client.iroh.IrohNode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the "Share via Iroh" switch below the port field of 26.1's "Open to LAN" screen. */
@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin extends Screen {
	@Unique
	private static final Component IROH_LABEL = Component.translatable("neoz_enderlink.options.iroh");
	@Unique
	private static final Tooltip IROH_TOOLTIP = Tooltip.create(Component.translatable("neoz_enderlink.options.iroh.tooltip"));

	@Unique
	private boolean enderlink$wantedIroh;

	protected ShareToLanScreenMixin(Component title) {
		super(title);
	}

	// init also runs on every resize, so the switch keeps its value in a field
	@Inject(method = "init", at = @At("TAIL"))
	private void enderlink$addIrohToggle(CallbackInfo ci) {
		CycleButton<Boolean> button = CycleButton.onOffBuilder(enderlink$wantedIroh).create(width / 2 - 155, 190, 310, 20, IROH_LABEL, (b, value) -> enderlink$wantedIroh = value);
		button.setTooltip(IROH_TOOLTIP);
		addRenderableWidget(button);
	}

	// The "Start LAN World" button handler; runs after vanilla published the world,
	// so our chat message comes after its "Local game hosted on port" one
	@Inject(method = "lambda$init$2", at = @At("TAIL"))
	private void enderlink$applyIroh(IntegratedServer server, Button startButton, CallbackInfo ci) {
		if (!enderlink$wantedIroh || !server.isPublished()) {
			return;
		}

		IrohNode iroh = EnderlinkClient.iroh();
		if (iroh.expose(server.getPort())) {
			iroh.key().whenCompleteAsync((key, error) -> {
				if (error == null) {
					enderlink$sendMessage(Component.translatable("neoz_enderlink.iroh.started", ComponentUtils.copyOnClickText(key)));
				} else {
					iroh.stopExposing();
					enderlink$sendMessage(Component.translatable("neoz_enderlink.iroh.failed"));
				}
			}, minecraft);
		}
	}

	@Unique
	private void enderlink$sendMessage(Component message) {
		minecraft.gui.getChat().addClientSystemMessage(message);
		minecraft.getNarrator().saySystemQueued(message);
	}
}
