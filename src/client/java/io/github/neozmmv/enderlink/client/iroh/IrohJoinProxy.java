package io.github.neozmmv.enderlink.client.iroh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import computer.iroh.BiStream;
import computer.iroh.Connection;
import computer.iroh.EndpointAddr;
import io.github.neozmmv.enderlink.Enderlink;
import io.github.neozmmv.enderlink.iroh.IrohAsync;
import io.github.neozmmv.enderlink.iroh.IrohKeys;
import io.github.neozmmv.enderlink.iroh.IrohStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets Minecraft join worlds by iroh key: each key gets a loopback TCP port, and every connection
 * Minecraft makes to it is tunneled to that peer over iroh.
 */
public final class IrohJoinProxy {
	private static final Logger LOGGER = LoggerFactory.getLogger(Enderlink.MOD_ID);
	private static final long CONNECT_TIMEOUT_SECONDS = 30;

	private final IrohNode node;
	private final Map<String, ServerSocket> listeners = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<Connection>> connections = new ConcurrentHashMap<>();

	public IrohJoinProxy(IrohNode node) {
		this.node = node;
	}

	/** The local address Minecraft should connect to in order to reach the peer with this key. */
	public InetSocketAddress localAddressFor(String key) {
		ServerSocket listener = listeners.computeIfAbsent(key, k -> {
			try {
				ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
				Thread.ofVirtual().name("enderlink-iroh-join").start(() -> acceptLoop(socket, k));
				return socket;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		return (InetSocketAddress) listener.getLocalSocketAddress();
	}

	public void close() {
		listeners.values().forEach(listener -> {
			try {
				listener.close();
			} catch (IOException ignored) {
			}
		});
		listeners.clear();
		connections.values().forEach(future -> future.thenAccept(Connection::close));
		connections.clear();
	}

	private void acceptLoop(ServerSocket listener, String key) {
		try (listener) {
			while (true) {
				Socket socket = listener.accept();
				Thread.ofVirtual().start(() -> tunnel(socket, key));
			}
		} catch (IOException e) {
			if (listeners.remove(key, listener)) {
				LOGGER.warn("Local proxy for iroh key {} stopped", key, e);
			}
		}
	}

	private void tunnel(Socket socket, String key) {
		try {
			CompletableFuture<Connection> pending = connectionTo(key);
			Connection connection;
			try {
				connection = pending.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				pending.cancel(true);
				throw e;
			}

			BiStream stream = IrohAsync.<BiStream>future(connection::openBi).get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			IrohStreams.pipe(socket, stream);
		} catch (Exception e) {
			LOGGER.warn("Could not reach iroh peer {}", key, e);
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}

	// One iroh connection per peer is shared by all tunnels (server list ping, then the actual join)
	private CompletableFuture<Connection> connectionTo(String key) {
		return connections.compute(key, (k, existing) -> {
			if (existing != null && !(existing.isDone() && (existing.isCompletedExceptionally() || existing.join().closeReason() != null))) {
				return existing;
			}

			return node.endpoint().thenCompose(ep -> {
				EndpointAddr addr = new EndpointAddr(IrohKeys.parse(k).orElseThrow(), null, List.of());
				return IrohAsync.<Connection>future(c -> ep.connect(addr, IrohNode.ALPN, c));
			});
		});
	}
}
