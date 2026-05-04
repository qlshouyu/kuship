package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.RolePerms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermsRepository extends JpaRepository<RolePerms, Integer> {

    List<RolePerms> findByRoleId(Integer roleId);

    List<RolePerms> findByRoleIdIn(List<Integer> roleIds);

    @Modifying
    @Query("delete from RolePerms p where p.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Integer roleId);
}
