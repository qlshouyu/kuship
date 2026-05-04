package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.UserAccessKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccessKeyRepository extends JpaRepository<UserAccessKey, Integer> {

    List<UserAccessKey> findByUserId(Integer userId);

    Optional<UserAccessKey> findByAccessKey(String accessKey);

    Optional<UserAccessKey> findByUserIdAndNote(Integer userId, String note);
}
