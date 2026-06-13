package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.UserAccessKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAccessKeyRepository extends JpaRepository<UserAccessKey, Integer> {

    /** 某用户的访问令牌。 */
    List<UserAccessKey> findByUserId(Integer userId);
}
