package cn.kuship.console.modules.rbac.repository;

import cn.kuship.console.modules.rbac.entity.RoleInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleInfoRepository extends JpaRepository<RoleInfo, Integer> {

    /** 某范围（如 kind=team, kindId=tenant_id）下的角色定义。 */
    List<RoleInfo> findByKindAndKindId(String kind, String kindId);
}
