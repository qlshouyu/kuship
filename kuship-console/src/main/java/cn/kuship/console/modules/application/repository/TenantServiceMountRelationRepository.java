package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.TenantServiceMountRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组件挂载依赖存储关系仓库（{@code tenant_service_mnt_relation}）。
 */
public interface TenantServiceMountRelationRepository extends JpaRepository<TenantServiceMountRelation, Integer> {

    /** 查询某组件已挂载的所有依赖存储关系。 */
    List<TenantServiceMountRelation> findByTenantIdAndServiceId(String tenantId, String serviceId);

    /** 查询挂载了当前组件存储的所有关系（被依赖方）。 */
    List<TenantServiceMountRelation> findByTenantIdAndDepServiceId(String tenantId, String depServiceId);

    /** 精确查找一条挂载关系（用于删除前校验）。 */
    Optional<TenantServiceMountRelation> findByServiceIdAndDepServiceIdAndMntName(
            String serviceId, String depServiceId, String mntName);

    /** 删除挂载关系（取消挂载时使用）。 */
    void deleteByServiceIdAndDepServiceIdAndMntName(String serviceId, String depServiceId, String mntName);

    /** 查询指定 volume（dep_service_id + mnt_name）有哪些组件挂载了它，用于返回 dep_services 信息。 */
    List<TenantServiceMountRelation> findByTenantIdAndDepServiceIdAndMntName(
            String tenantId, String depServiceId, String mntName);
}
