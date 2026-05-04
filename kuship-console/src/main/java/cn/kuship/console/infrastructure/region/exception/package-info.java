/**
 * Region API 异常族。
 *
 * <p>统一根：{@link cn.kuship.console.infrastructure.region.exception.RegionApiException}。
 * 子类按 rainbond-console Python 端 {@code _check_status} 的分支一一对应。
 *
 * <p>所有这些异常的对外响应映射由 {@code GlobalExceptionHandler} 完成
 * （change {@code migrate-console-region-client} 在 {@code migrate-console-response-contract} 已落地的 handler 上追加）。
 */
package cn.kuship.console.infrastructure.region.exception;
