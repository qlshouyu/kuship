package cn.kuship.console.common.exception;

/**
 * 无操作权限，对齐 rainbond-console 的 {@code NoPermissionsError}：
 * HTTP 403、业务信封 {@code code=10402}、{@code msg="no permissions "}、{@code msg_show="没有操作权限"}。
 */
public class NoPermissionsException extends ServiceHandleException {

    public NoPermissionsException() {
        super(403, 10402, "no permissions ", "没有操作权限");
    }
}
