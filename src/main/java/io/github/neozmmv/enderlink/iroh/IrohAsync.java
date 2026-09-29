package io.github.neozmmv.enderlink.iroh;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.SupervisorKt;
import kotlinx.coroutines.future.FutureKt;

/**
 * Bridges iroh's Kotlin {@code suspend} functions and Java {@link CompletableFuture}s.
 *
 * <p>On the JVM a Kotlin {@code suspend fun f(a): R} compiles to {@code Object f(a, Continuation<? super R>)},
 * which Java cannot call (or implement) directly. Use {@link #future} to call one and {@link #await} to
 * implement one (e.g. {@code ProtocolHandler.accept}).
 */
public final class IrohAsync {
	private static final CoroutineScope SCOPE =
			CoroutineScopeKt.CoroutineScope(SupervisorKt.SupervisorJob(null).plus(Dispatchers.getIO()));

	private IrohAsync() {
	}

	/** A call to a Kotlin suspend function, forwarding the continuation it is given. */
	@FunctionalInterface
	public interface SuspendCall<T> {
		Object invoke(Continuation<? super T> continuation) throws Throwable;
	}

	/**
	 * Runs an iroh suspend function and returns its result as a future.
	 * Cancelling the future cancels the underlying call.
	 *
	 * <p>Java cannot infer {@code T} from the lambda alone: assign the result to a typed variable,
	 * or pass a type witness when chaining.
	 *
	 * <pre>{@code
	 * CompletableFuture<Endpoint> endpoint = IrohAsync.future(c -> Endpoint.Companion.bind(options, c));
	 * IrohAsync.<Unit>future(ep::shutdown).join();
	 * }</pre>
	 */
	public static <T> CompletableFuture<T> future(SuspendCall<T> call) {
		return FutureKt.future(SCOPE, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT, (scope, continuation) -> {
			try {
				return call.invoke(continuation);
			} catch (Throwable t) {
				throw sneakyThrow(t);
			}
		});
	}

	/**
	 * Implements a Kotlin suspend function from Java by suspending until {@code stage} completes.
	 *
	 * <pre>{@code
	 * public Object accept(Connection conn, Continuation<? super Unit> continuation) {
	 *     return IrohAsync.await(handle(conn).thenApply(v -> Unit.INSTANCE), continuation);
	 * }
	 * }</pre>
	 */
	public static <T> Object await(CompletionStage<T> stage, Continuation<? super T> continuation) {
		return FutureKt.await(stage, continuation);
	}

	@SuppressWarnings("unchecked")
	private static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
		throw (E) t;
	}
}
