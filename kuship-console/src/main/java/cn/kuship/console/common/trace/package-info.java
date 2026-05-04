/**
 * TraceId 透传：{@link cn.kuship.console.common.trace.TraceIdFilter} 给每个请求生成 UUID，
 * 注入 MDC + 响应头 {@code X-Trace-Id}。logback pattern 通过 {@code %X{traceId}} 引用。
 */
package cn.kuship.console.common.trace;
