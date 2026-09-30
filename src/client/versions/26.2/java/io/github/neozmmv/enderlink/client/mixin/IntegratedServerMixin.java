package io.github.neozmmv.enderlink.client.mixin;

import io.github.neozmmv.enderlink.client.EnderlinkClient;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
	// Called whenever the world stops being published: LAN turned off, /unpublish, or the server shutting down
	@Inject(method = "teardownPublishedState", at = @At("HEAD"))
	private void enderlink$stopIrohExposure(CallbackInfo ci) {
		EnderlinkClient.iroh().stopExposing();
	}
}
