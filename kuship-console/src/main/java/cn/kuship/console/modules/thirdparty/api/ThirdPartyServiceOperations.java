package cn.kuship.console.modules.thirdparty.api;

import java.util.Map;

/**
 * 第三方组件运行时管理 region API。落地 change：{@code migrate-console-third-party-runtime}。
 *
 * <p>覆盖 6 个 region method（rainbond 锚点 {@code regionapi.py:1828-1893}）：4 个 endpoint CRUD
 * + 2 个 health probe 配置。POST/PUT/DELETE endpoints 三个 method MUST 在 HTTP header 带
 * {@code Resource-Validation: true}（与 rainbond {@code _set_headers(token, resource_validation="true")} 一致）；
 * GET endpoints / GET health / PUT health 不带该 header。
 *
 * <p>{@code namespace} 取自 {@code Tenants.namespace}，缺失时回退 {@code tenant_name}。
 */
public interface ThirdPartyServiceOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-third-party-runtime";

    /** GET /v2/tenants/{namespace}/services/{alias}/endpoints */
    Map<String, Object> getEndpoints(String regionName, String tenantName, String serviceAlias);

    /** POST /v2/tenants/{namespace}/services/{alias}/endpoints （body 单条或批量；带 Resource-Validation header） */
    Map<String, Object> postEndpoints(String regionName, String tenantName, String serviceAlias,
                                       Map<String, Object> body);

    /** PUT /v2/tenants/{namespace}/services/{alias}/endpoints （带 Resource-Validation header） */
    Map<String, Object> putEndpoints(String regionName, String tenantName, String serviceAlias,
                                      Map<String, Object> body);

    /** DELETE /v2/tenants/{namespace}/services/{alias}/endpoints （带 Resource-Validation header；body=`{"ep_id":"..."}`） */
    Map<String, Object> deleteEndpoints(String regionName, String tenantName, String serviceAlias,
                                         Map<String, Object> body);

    /** GET /v2/tenants/{namespace}/services/{alias}/3rd-party/probe */
    Map<String, Object> getHealth(String regionName, String tenantName, String serviceAlias);

    /** PUT /v2/tenants/{namespace}/services/{alias}/3rd-party/probe */
    Map<String, Object> putHealth(String regionName, String tenantName, String serviceAlias,
                                    Map<String, Object> body);
}
