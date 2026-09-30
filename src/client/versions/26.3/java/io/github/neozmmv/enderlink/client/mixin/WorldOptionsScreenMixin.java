package io.github.neozmmv.enderlink.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.neozmmv.enderlink.client.EnderlinkClient;
import io.github.neozmmv.enderlink.client.iroh.IrohNode;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer.MultiplayerScope;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the "Share via Iroh" switch to the Multiplayer section of World Options (26.x's "Open to LAN"). */
@Mixin(WorldOptionsScreen.class)
public abstract class WorldOptionsScreenMixin extends Screen {
	@Unique
	private static final Component IROH_LABEL = Component.translatable("neoz_enderlink.options.iroh");
	@Unique
	private static final Tooltip IROH_TOOLTIP = Tooltip.create(Component.translatable("neoz_enderlink.options.iroh.tooltip"));
	@Unique
	private static final Tooltip IROH_DISABLED_TOOLTIP = Tooltip.create(Component.translatable("neoz_enderlink.options.iroh.disabled.tooltip"));

	@Shadow
	private @Nullable MultiplayerScope wantedMultiplayerScope;

	@Unique
	private @Nullable CycleButton<Boolean> enderlink$irohButton;
	@Unique
	private boolean enderlink$initialIroh;
	@Unique
	private boolean enderlink$wantedIroh;

	protected WorldOptionsScreenMixin(Component title) {
		super(title);
	}

	@Shadow
	private void updateApplyChangesActiveState() {
		throw new AssertionError();
	}

	@Shadow
	private void sendPublishMessage(Component message) {
		throw new AssertionError();
	}

	@Inject(method = "multiplayerOptions", at = @At("TAIL"))
	private void enderlink$addIrohToggle(LinearLayout content, IntegratedServer server, CallbackInfo ci, @Local GridLayout.RowHelper helper) {
		enderlink$initialIroh = EnderlinkClient.iroh().isExposed();
		enderlink$wantedIroh = enderlink$initialIroh;
		enderlink$irohButton = helper.addChild(
			CycleButton.onOffBuilder(enderlink$wantedIroh).create(0, 0, 308, 20, IROH_LABEL, (button, value) -> {
				enderlink$wantedIroh = value;
				updateApplyChangesActiveState();
			}),
			2
		);
		enderlink$updateIrohButton();
	}

	// Runs whenever the LAN switch changes; iroh forwards to the LAN port, so it needs LAN on
	@Inject(method = "updatePortControlsState", at = @At("TAIL"))
	private void enderlink$onLanToggled(CallbackInfo ci) {
		enderlink$updateIrohButton();
	}

	@Inject(method = "hasSettingsChanges", at = @At("RETURN"), cancellable = true)
	private void enderlink$irohChanged(CallbackInfoReturnable<Boolean> cir) {
		if (enderlink$wantedIroh != enderlink$initialIroh) {
			cir.setReturnValue(true);
		}
	}

	// Runs after vanilla (un)published the world, so our chat message comes after its "Local game hosted on port" one
	@Inject(method = "applyChanges", at = @At("TAIL"))
	private void enderlink$applyIroh(@Nullable IntegratedServer server, CallbackInfo ci) {
		if (server == null) {
			return;
		}

		IrohNode iroh = EnderlinkClient.iroh();
		if (enderlink$wantedIroh && server.isPublished()) {
			if (iroh.expose(server.getPort())) {
				iroh.key().whenCompleteAsync((key, error) -> {
					if (error == null) {
						sendPublishMessage(Component.translatable("neoz_enderlink.iroh.started", ComponentUtils.copyOnClickText(key)));
					} else {
						iroh.stopExposing();
						sendPublishMessage(Component.translatable("neoz_enderlink.iroh.failed"));
					}
				}, minecraft);
			}
		} else if (iroh.stopExposing() || enderlink$initialIroh) {
			sendPublishMessage(Component.translatable("neoz_enderlink.iroh.stopped"));
		}
	}

	@Unique
	private void enderlink$updateIrohButton() {
		if (enderlink$irohButton == null) {
			return;
		}

		boolean lan = wantedMultiplayerScope == MultiplayerScope.LAN;
		enderlink$wantedIroh = lan && enderlink$initialIroh;
		enderlink$irohButton.setValue(enderlink$wantedIroh);
		enderlink$irohButton.active = lan;
		enderlink$irohButton.setTooltip(lan ? IROH_TOOLTIP : IROH_DISABLED_TOOLTIP);
	}
}
