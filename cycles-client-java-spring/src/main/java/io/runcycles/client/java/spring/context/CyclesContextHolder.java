package io.runcycles.client.java.spring.context;

public final class CyclesContextHolder {

    private static final ThreadLocal<CyclesReservationContext> HOLDER = new ThreadLocal<>();

    public static void set(CyclesReservationContext ctx) { HOLDER.set(ctx); }
    public static CyclesReservationContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
