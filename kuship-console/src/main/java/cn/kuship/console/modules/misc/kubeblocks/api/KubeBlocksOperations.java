package cn.kuship.console.modules.misc.kubeblocks.api;

import java.util.List;
import java.util.Map;

/**
 * KubeBlocks 数据库托管 region API 接口（13 method）。
 *
 * <p>业务自治接口，非 14 核心 region 骨架；归属 {@code modules/misc/kubeblocks/}
 * 业务域。承接 rainbond-console {@code regionapi.py} 中 14 个 KubeBlocks 段
 * region 调用的 13 个核心方法（剩余 1 个 {@code get_kubeblocks_connect_info}
 * 推迟到 hardening change {@code add-kubeblocks-connect-info} 实现）。
 *
 * <p>调用方式与 {@code GatewayOperations}/{@code ThirdPartyServiceOperations}
 * 一致，使用 {@code RegionClientFactory.getClient(regionName, "")} 拿 mTLS 客户端，
 * 走 {@code RegionApiResponseProcessor.extractBean / checkStatus} 解析响应。
 */
public interface KubeBlocksOperations {

    Map<String, Object> listSupportedDatabases(String regionName);

    Map<String, Object> listStorageClasses(String regionName);

    Map<String, Object> listBackupRepos(String regionName);

    Map<String, Object> getClusterDetail(String regionName, String serviceId);

    Map<String, Object> listClusterParameters(String regionName, String serviceId,
                                                int page, int pageSize, String keyword);

    Map<String, Object> listClusterBackups(String regionName, String serviceId,
                                            int page, int pageSize);

    Map<String, Object> getClusterPodDetail(String regionName, String serviceId, String podName);

    Map<String, Object> createCluster(String regionName, Map<String, Object> body);

    Map<String, Object> expansionCluster(String regionName, String serviceId, Map<String, Object> body);

    Map<String, Object> deleteCluster(String regionName, Map<String, Object> body);

    Map<String, Object> deleteClusterBackups(String regionName, String serviceId, List<String> backups);

    Map<String, Object> updateBackupConfig(String regionName, String serviceId, Map<String, Object> body);

    Map<String, Object> createManualBackup(String regionName, String serviceId);

    Map<String, Object> updateClusterParameters(String regionName, String serviceId, Map<String, Object> body);
}
