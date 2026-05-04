package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {

    List<UserRole> findByUserId(String userId);

    List<UserRole> findByRoleId(String roleId);

    List<UserRole> findByUserIdAndRoleIdIn(String userId, List<String> roleIds);

    @Modifying
    @Query("delete from UserRole u where u.userId = :userId and u.roleId in :roleIds")
    int deleteByUserIdAndRoleIds(@Param("userId") String userId, @Param("roleIds") List<String> roleIds);

    @Modifying
    @Query("delete from UserRole u where u.userId = :userId")
    int deleteAllByUserId(@Param("userId") String userId);
}
