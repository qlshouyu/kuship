package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserInfoRepository extends JpaRepository<UserInfo, Integer> {

    /** 对齐 is_exist：username 可匹配 phone / email / nick_name 任一。 */
    @Query("select u from UserInfo u where u.phone = :username or u.email = :username or u.nickName = :username")
    Optional<UserInfo> findByLoginName(@Param("username") String username);

    long countByEnterpriseId(String enterpriseId);
}
