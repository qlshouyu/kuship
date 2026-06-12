package cn.kuship.console.modules.rbac.repository;

import cn.kuship.console.modules.rbac.entity.RolePerms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermsRepository extends JpaRepository<RolePerms, Integer> {

    /** 一批角色的权限码关联（含全局 app_id=-1 与应用级）。 */
    List<RolePerms> findByRoleIdIn(List<Integer> roleIds);

    /** 单角色的权限码关联。 */
    List<RolePerms> findByRoleId(Integer roleId);

    /** 删除某角色的全部权限码关联（更新权限树时整体重建、删角色时清理）。 */
    void deleteByRoleId(Integer roleId);
}
