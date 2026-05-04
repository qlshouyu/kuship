package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsoleConfigRepository extends JpaRepository<ConsoleConfig, Integer> {

    List<ConsoleConfig> findByUserNickName(String userNickName);

    @Query("select c from ConsoleConfig c where c.key = :key")
    Optional<ConsoleConfig> findByKey(@Param("key") String key);

    @Query("select c from ConsoleConfig c where c.key like :prefix")
    List<ConsoleConfig> findByKeyStartingWith(@Param("prefix") String keyPrefix);

    @Modifying
    @Query("delete from ConsoleConfig c where c.key = :key")
    int deleteByKey(@Param("key") String key);

    @Modifying
    @Query("delete from ConsoleConfig c where c.userNickName = :nick and c.key in :keys")
    int deleteByUserNickNameAndKeyIn(@Param("nick") String userNickName, @Param("keys") List<String> keys);
}
