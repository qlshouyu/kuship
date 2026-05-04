package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.UserInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserInfoRepository extends JpaRepository<UserInfo, Integer> {

    Optional<UserInfo> findByNickName(String nickName);

    Optional<UserInfo> findByEmail(String email);

    Optional<UserInfo> findByPhone(String phone);

    boolean existsByNickName(String nickName);

    boolean existsByEmail(String email);

    @Query("select u from UserInfo u where lower(u.nickName) like lower(concat('%', :q, '%')) "
            + "or lower(u.email) like lower(concat('%', :q, '%')) "
            + "or u.phone like concat('%', :q, '%')")
    Page<UserInfo> search(@Param("q") String q, Pageable pageable);

    List<UserInfo> findByEnterpriseId(String enterpriseId);
}
