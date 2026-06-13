package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.ConsoleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsoleConfigRepository extends JpaRepository<ConsoleConfig, Integer> {

    /** 某用户（nick_name）的自定义配置。 */
    List<ConsoleConfig> findByUserNickName(String userNickName);

    /** 按 key 集合全局删除（对齐 rainbond bulk_create_or_update 的 delete(keys)）。 */
    void deleteByKeyIn(java.util.List<String> keys);
}
