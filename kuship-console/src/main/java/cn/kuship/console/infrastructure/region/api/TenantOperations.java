package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.api.dto.CreateTenantReq;
import cn.kuship.console.infrastructure.region.api.dto.RegionLabelsResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionPublickeyResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantResourcesResp;

/**
 * Tenant 域 region API 操作。本接口是 {@code migrate-console-region-client} 的示范实现，
 * 5 个 method 已完整落地。后续如需补充其他 tenant 相关 method（如 list、update），由
 * {@code migrate-console-account-team} 在此接口上扩展。
 *
 * <p>对应 Python {@code regionapi.py} 中的方法：
 * <ul>
 *   <li>{@link #createTenant} ←→ {@code create_tenant}</li>
 *   <li>{@link #deleteTenant} ←→ {@code delete_tenant}</li>
 *   <li>{@link #getTenantResources} ←→ {@code get_tenant_resources}</li>
 *   <li>{@link #getRegionPublickey} ←→ {@code get_region_publickey}</li>
 *   <li>{@link #getRegionLabels} ←→ {@code get_region_labels}</li>
 * </ul>
 */
public interface TenantOperations {

    /** {@code POST /v2/tenants} —— 在指定 region 创建 tenant。 */
    TenantResourcesResp createTenant(String regionName, String enterpriseId, CreateTenantReq req);

    /** {@code DELETE /v2/tenants/{tenant_name}} —— 删除 tenant。 */
    void deleteTenant(String regionName, String enterpriseId, String tenantName);

    /** {@code GET /v2/tenants/{tenant_name}/res?enterprise_id=...} —— tenant 资源使用情况。 */
    TenantResourcesResp getTenantResources(String regionName, String enterpriseId, String tenantName);

    /** {@code GET /v2/tenants/{tenant_name}/region-key?enterprise_id=...&tenant_id=...} —— region 公钥。 */
    RegionPublickeyResp getRegionPublickey(String regionName, String enterpriseId,
                                            String tenantName, String tenantId);

    /** {@code GET /v2/tenants/{tenant_name}/labels} —— 节点标签列表。 */
    RegionLabelsResp getRegionLabels(String regionName, String enterpriseId, String tenantName);
}
