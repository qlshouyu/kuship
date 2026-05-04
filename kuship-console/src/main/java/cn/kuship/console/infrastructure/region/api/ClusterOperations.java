package cn.kuship.console.infrastructure.region.api;

import cn.kuship.console.infrastructure.region.api.dto.ClusterIdResp;
import cn.kuship.console.infrastructure.region.api.dto.LicenseStatusResp;
import cn.kuship.console.infrastructure.region.api.dto.NamespaceListResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionFeaturesResp;
import cn.kuship.console.infrastructure.region.api.dto.RegionResourceResp;
import cn.kuship.console.infrastructure.region.api.dto.TenantLimitReq;

import java.util.List;
import java.util.Map;

import static cn.kuship.console.infrastructure.region.api.UnsupportedRegionOperations.unsupported;

/**
 * Cluster 元信息 / 集群级管理域。<b>实现 change：{@code migrate-console-region-cluster}</b>。
 *
 * <p>本 change 完整实现 8 个 method：getClusterId / activateLicense / getLicenseStatus / getRegionFeatures /
 * getRegionNamespaces / getRegionResources / setTenantLimit / listTenantsInRegion；其余沿用 default 占位。
 */
public interface ClusterOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-region-cluster";

    // ---- 8 method 由本 change 落地（ClusterOperationsImpl @Primary 覆盖） ----

    default ClusterIdResp getClusterId(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default void activateLicense(String regionName, String enterpriseId, Map<String, Object> body) { unsupported(IMPLEMENTING_CHANGE); }

    default LicenseStatusResp getLicenseStatus(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default RegionFeaturesResp getRegionFeatures(String regionName, String tenantName) { return unsupported(IMPLEMENTING_CHANGE); }

    default NamespaceListResp getRegionNamespaces(String regionName, String enterpriseId, String content) { return unsupported(IMPLEMENTING_CHANGE); }

    default RegionResourceResp getRegionResources(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default void setTenantLimit(String regionName, String enterpriseId, String tenantName, TenantLimitReq req) { unsupported(IMPLEMENTING_CHANGE); }

    default List<Map<String, Object>> listTenantsInRegion(String regionName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    // ---- 后续 change 占位 ----

    default Map<String, Object> getResources(String regionName, String tenantName, String enterpriseId) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getClusterInfo(String regionName) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getClusterEvents(String regionName, Map<String, Object> body) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getNodes(String regionName) { return unsupported(IMPLEMENTING_CHANGE); }

    default Map<String, Object> getNodeDetail(String regionName, String nodeName) { return unsupported(IMPLEMENTING_CHANGE); }
}
