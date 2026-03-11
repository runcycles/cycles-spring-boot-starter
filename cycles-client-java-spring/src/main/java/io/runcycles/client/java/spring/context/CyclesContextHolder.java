package io.runcycles.client.java.spring.context;

/**
 * Thread-local holder for the active {@link CyclesReservationContext}.
 *
 * <p>Set by {@link CyclesLifecycleService} before the guarded method executes and
 * cleared in its {@code finally} block. Application code inside a
 * {@code @Cycles}-annotated method can call {@link #get()} to inspect reservation
 * details, attach metrics, or add commit metadata.
 *
 * @see CyclesReservationContext
 */
public final class CyclesContextHolder {

    private CyclesContextHolder() {}

    private static final ThreadLocal<CyclesReservationContext> HOLDER = new ThreadLocal<>();

    /**
     * Binds the given context to the current thread.
     *
     * @param ctx the reservation context to bind
     */
    public static void set(CyclesReservationContext ctx) { HOLDER.set(ctx); }

    /**
     * Returns the active reservation context for the current thread, or {@code null}
     * if no reservation is in progress.
     *
     * @return the current reservation context, or {@code null}
     */
    public static CyclesReservationContext get() { return HOLDER.get(); }

    /** Removes the context from the current thread. */
    public static void clear() { HOLDER.remove(); }
}
