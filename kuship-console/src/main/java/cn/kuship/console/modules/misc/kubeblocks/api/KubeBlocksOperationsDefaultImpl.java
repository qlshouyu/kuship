package cn.kuship.console.modules.misc.kubeblocks.api;

import java.util.List;
import java.util.Map;

/**
 * {@link KubeBlocksOperations} 默认占位实现，所有 method 抛 {@link UnsupportedOperationException}。
 *
 * <p>不加 {@code @Service} / {@code @Primary}：仅用于测试场景或接口签名变化时的兼容降级，
 * 实际生产由 {@link KubeBlocksOperationsImpl}（@Primary @Service）覆盖。
 */
public class KubeBlocksOperationsDefaultImpl implements KubeBlocksOperations {

    private static final String NOT_IMPLEMENTED =
            "not yet implemented; will be filled in by migrate-console-kubeblocks";

    @Override
    public Map<String, Object> listSupportedDatabases(String regionName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> listStorageClasses(String regionName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> listBackupRepos(String regionName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> getClusterDetail(String regionName, String serviceId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> listClusterParameters(String regionName, String serviceId,
                                                       int page, int pageSize, String keyword) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> listClusterBackups(String regionName, String serviceId,
                                                    int page, int pageSize) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> getClusterPodDetail(String regionName, String serviceId, String podName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> createCluster(String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> expansionCluster(String regionName, String serviceId, Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> deleteCluster(String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> deleteClusterBackups(String regionName, String serviceId, List<String> backups) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> updateBackupConfig(String regionName, String serviceId, Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> createManualBackup(String regionName, String serviceId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, Object> updateClusterParameters(String regionName, String serviceId, Map<String, Object> body) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
