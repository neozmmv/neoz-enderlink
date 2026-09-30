package io.github.neozmmv.enderlink.client.mixin;

import io.github.neozmmv.enderlink.client.EnderlinkClient;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
	// 26.1 can't unpublish a world, so the only way it stops being published is the server shutting down
	@Inject(method = "stopServer", at = @At("HEAD"))
	private void enderlink$stopIrohExposure(CallbackInfo ci) {
		EnderlinkClient.iroh().stopExposing();
	}
}
