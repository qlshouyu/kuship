package cn.kuship.console.modules.rbac.repository;

import cn.kuship.console.modules.rbac.entity.RolePerms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermsRepository extends JpaRepository<RolePerms, Integer> {

    /** 一批角色的权限码关联（含全局 app_id=-1 与应用级）。 */
    List<RolePerms> findByRoleIdIn(List<Integer> roleIds);
}
