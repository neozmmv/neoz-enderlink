package io.github.neozmmv.enderlink.iroh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import computer.iroh.BiStream;
import computer.iroh.RecvStream;
import computer.iroh.SendStream;
import io.github.neozmmv.enderlink.Enderlink;
import kotlin.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stream helpers for iroh, including the calls Java cannot make directly. */
public final class IrohStreams {
	private static final Logger LOGGER = LoggerFactory.getLogger(Enderlink.MOD_ID);
	private static final int BUFFER_SIZE = 64 * 1024;

	// RecvStream.read takes a Kotlin UInt, so its JVM name is mangled (e.g. "read-qim9Vi0") and can't be written in Java source
	private static final MethodHandle READ = findMangled(RecvStream.class, "read");

	private IrohStreams() {
	}

	/** Reads up to {@code maxBytes}; completes with an empty array at end of stream. */
	public static CompletableFuture<byte[]> read(RecvStream stream, int maxBytes) {
		return IrohAsync.future(c -> READ.invoke(stream, maxBytes, c));
	}

	/**
	 * Copies bytes both ways between a TCP socket and an iroh stream until both directions finish,
	 * then closes the socket and the stream. Blocks the calling thread, so run it on a virtual thread.
	 */
	public static void pipe(Socket socket, BiStream stream) {
		SendStream send = stream.send();
		RecvStream recv = stream.recv();
		AtomicBoolean aborted = new AtomicBoolean();
		AtomicReference<CompletableFuture<byte[]>> pendingRead = new AtomicReference<>();
		Runnable abort = () -> {
			aborted.set(true);
			closeQuietly(socket);
			CompletableFuture<byte[]> read = pendingRead.get();
			if (read != null) {
				read.cancel(true);
			}
		};

		try {
			socket.setTcpNoDelay(true);
		} catch (IOException ignored) {
		}

		Thread upload = Thread.ofVirtual().start(() -> {
			try {
				InputStream in = socket.getInputStream();
				byte[] buffer = new byte[BUFFER_SIZE];
				int n;
				while ((n = in.read(buffer)) != -1) {
					byte[] chunk = Arrays.copyOf(buffer, n);
					IrohAsync.<Unit>future(c -> send.writeAll(chunk, c)).join();
				}
				IrohAsync.<Unit>future(send::finish).join();
			} catch (Exception e) {
				if (!aborted.get()) {
					LOGGER.debug("iroh tunnel upload ended", e);
					abort.run();
				}
			}
		});

		try {
			OutputStream out = socket.getOutputStream();
			while (true) {
				CompletableFuture<byte[]> read = read(recv, BUFFER_SIZE);
				pendingRead.set(read);
				if (aborted.get()) {
					read.cancel(true);
				}

				byte[] chunk = read.join();
				if (chunk.length == 0) {
					break;
				}
				out.write(chunk);
			}
			socket.shutdownOutput();
		} catch (Exception e) {
			if (!aborted.get()) {
				LOGGER.debug("iroh tunnel download ended", e);
				abort.run();
			}
		}

		try {
			upload.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		closeQuietly(socket);
		send.close();
		recv.close();
		stream.close();
	}

	private static void closeQuietly(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}

	private static MethodHandle findMangled(Class<?> owner, String name) {
		for (Method method : owner.getMethods()) {
			if (method.getName().equals(name) || method.getName().startsWith(name + "-")) {
				try {
					return MethodHandles.publicLookup().unreflect(method);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		throw new IllegalStateException("No method " + name + " on " + owner.getName());
	}
}
