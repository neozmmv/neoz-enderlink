package io.github.neozmmv.enderlink.client.mixin;

import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Optional;

import io.github.neozmmv.enderlink.client.EnderlinkClient;
import io.github.neozmmv.enderlink.iroh.IrohKeys;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {
	// An iroh key isn't a DNS name: resolve it to a local tunnel to that peer instead (used by both joining and server list pings)
	@Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
	private void enderlink$resolveIrohKey(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
		String host = address.getHost();
		if (IrohKeys.parse(host).isEmpty()) {
			return;
		}

		try {
			cir.setReturnValue(Optional.of(ResolvedServerAddress.from(EnderlinkClient.joinProxy().localAddressFor(host.toLowerCase(Locale.ROOT)))));
		} catch (UncheckedIOException e) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
