package cn.kuship.console.infrastructure.region.api;

import org.springframework.stereotype.Service;

/**
 * 默认占位 bean：所有 method 走接口 default 实现（抛 {@code UnsupportedOperationException}）。
 * {@code migrate-console-app-create} 落地时新增 @Primary 实现 bean override 此默认实现。
 */
@Service
public class ServiceOperationsDefaultImpl implements ServiceOperations {
}
