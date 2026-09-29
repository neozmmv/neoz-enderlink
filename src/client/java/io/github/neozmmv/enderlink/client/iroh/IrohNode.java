package io.github.neozmmv.enderlink.client.iroh;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import computer.iroh.Accepting;
import computer.iroh.BiStream;
import computer.iroh.Connection;
import computer.iroh.Endpoint;
import computer.iroh.EndpointOptions;
import computer.iroh.Incoming;
import computer.iroh.IrohException;
import computer.iroh.SecretKey;
import io.github.neozmmv.enderlink.Enderlink;
import io.github.neozmmv.enderlink.iroh.IrohAsync;
import io.github.neozmmv.enderlink.iroh.IrohKeys;
import io.github.neozmmv.enderlink.iroh.IrohStreams;
import kotlin.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This game's iroh endpoint. It is bound at startup with a persistent identity (so the player's key
 * never changes) and, while the world is exposed, forwards iroh peers to the LAN world's TCP port.
 */
public final class IrohNode {
	/** Protocol identifier both sides must agree on; bump the version on incompatible changes. */
	public static final byte[] ALPN = "neoz_enderlink/minecraft/1".getBytes(StandardCharsets.UTF_8);

	private static final Logger LOGGER = LoggerFactory.getLogger(Enderlink.MOD_ID);
	private static final byte[] STOPPED_REASON = "world no longer shared".getBytes(StandardCharsets.UTF_8);

	private final CompletableFuture<Endpoint> endpoint;
	private final AtomicInteger exposedPort = new AtomicInteger(-1);
	private final Set<Connection> hostedConnections = ConcurrentHashMap.newKeySet();

	private IrohNode(byte[] secretKey) {
		EndpointOptions options = new EndpointOptions(null, null, secretKey, List.of(ALPN), null, null);
		this.endpoint = IrohAsync.future(c -> Endpoint.Companion.bind(options, c));
		this.endpoint.whenComplete((ep, error) -> {
			if (error != null) {
				LOGGER.error("Failed to bind the iroh endpoint", error);
			} else {
				Thread.ofVirtual().name("enderlink-iroh-accept").start(() -> acceptLoop(ep));
			}
		});
	}

	/** Binds the endpoint, reusing the secret key stored at {@code keyFile} (created on first launch). */
	public static IrohNode start(Path keyFile) {
		return new IrohNode(loadOrCreateSecretKey(keyFile));
	}

	public CompletableFuture<Endpoint> endpoint() {
		return endpoint;
	}

	/** The key other players type into Direct Connection to join this player's world. */
	public CompletableFuture<String> key() {
		return endpoint.thenApply(ep -> IrohKeys.encode(ep.id()));
	}

	public boolean isExposed() {
		return exposedPort.get() >= 0;
	}

	/** Starts forwarding iroh peers to the LAN world on {@code port}. Returns false if it already was. */
	public boolean expose(int port) {
		return exposedPort.getAndSet(port) != port;
	}

	/** Stops accepting iroh peers and disconnects the current ones. Returns whether the world was exposed. */
	public boolean stopExposing() {
		boolean wasExposed = exposedPort.getAndSet(-1) >= 0;
		for (Connection connection : hostedConnections) {
			try {
				connection.close(0, STOPPED_REASON);
			} catch (IrohException e) {
				LOGGER.debug("Failed to close iroh connection", e);
			}
		}
		return wasExposed;
	}

	public void close() {
		stopExposing();
		if (!endpoint.isDone() || endpoint.isCompletedExceptionally()) {
			endpoint.cancel(true);
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

	private void acceptLoop(Endpoint ep) {
		try {
			Incoming incoming;
			while ((incoming = IrohAsync.<Incoming>future(ep::acceptNext).join()) != null) {
				Incoming next = incoming;
				Thread.ofVirtual().start(() -> handleIncoming(next));
			}
		} catch (Exception e) {
			if (!ep.isClosed()) {
				LOGGER.error("iroh accept loop stopped", e);
			}
		}
	}

	private void handleIncoming(Incoming incoming) {
		try (incoming) {
			if (!isExposed()) {
				IrohAsync.<Unit>future(incoming::refuse).join();
				return;
			}

			try (Accepting accepting = IrohAsync.<Accepting>future(incoming::accept).join();
					Connection connection = IrohAsync.<Connection>future(accepting::connect).join()) {
				LOGGER.info("iroh peer {} connected", connection.remoteId().fmtShort());
				hostedConnections.add(connection);
				try {
					// Every Minecraft TCP connection from the peer arrives as its own bi-directional stream
					while (true) {
						BiStream stream = IrohAsync.<BiStream>future(connection::acceptBi).join();
						int port = exposedPort.get();
						if (port < 0) {
							stream.close();
							break;
						}
						Thread.ofVirtual().start(() -> forwardToLan(stream, port));
					}
				} finally {
					hostedConnections.remove(connection);
				}
			}
		} catch (Exception e) {
			LOGGER.debug("iroh connection ended", e);
		}
	}

	private static void forwardToLan(BiStream stream, int port) {
		Socket socket;
		try {
			socket = new Socket(InetAddress.getLoopbackAddress(), port);
		} catch (IOException e) {
			LOGGER.warn("Could not reach the LAN world on port {}", port, e);
			stream.close();
			return;
		}
		IrohStreams.pipe(socket, stream);
	}

	private static byte[] loadOrCreateSecretKey(Path file) {
		try {
			if (Files.exists(file)) {
				byte[] key = Files.readAllBytes(file);
				if (key.length == 32) {
					return key;
				}
				LOGGER.warn("Replacing invalid iroh secret key at {}", file);
			}

			byte[] key = generateSecretKey();
			Files.createDirectories(file.getParent());
			Files.write(file, key);
			try {
				Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
			} catch (UnsupportedOperationException ignored) {
				// Not a POSIX file system (Windows)
			}
			return key;
		} catch (IOException e) {
			LOGGER.error("Could not store the iroh secret key at {}, using a temporary one", file, e);
			return generateSecretKey();
		}
	}

	private static byte[] generateSecretKey() {
		try (SecretKey secretKey = SecretKey.Companion.generate()) {
			return secretKey.toBytes();
		}
	}
}
