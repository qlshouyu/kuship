package cn.kuship.console.modules.rbac.repository;

import cn.kuship.console.modules.rbac.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {

    /** 某用户的全部角色关联（user_id 为字符串）。范围过滤在服务层用团队角色 ID 集合完成。 */
    List<UserRole> findByUserId(String userId);

    /** 删除某角色的全部成员关联（删角色时清理；role_id 为字符串）。 */
    void deleteByRoleId(String roleId);

    /** 某用户在一批角色内的关联（成员角色重建/查看用；均为字符串）。 */
    List<UserRole> findByUserIdAndRoleIdIn(String userId, List<String> roleIds);

    /** 删除某用户在一批角色内的关联（成员角色重建/清空用）。 */
    void deleteByUserIdAndRoleIdIn(String userId, List<String> roleIds);

    /** 批量删除一批用户在一批角色内的关联（移除成员时清团队角色关联）。 */
    void deleteByUserIdInAndRoleIdIn(List<String> userIds, List<String> roleIds);
}
