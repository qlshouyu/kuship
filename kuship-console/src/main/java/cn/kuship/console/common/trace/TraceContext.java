package cn.kuship.console.common.trace;

/**
 * 基于 ThreadLocal 的 TraceId 上下文。由 {@link TraceIdFilter} 在请求入口写入、出口清理。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String get() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
