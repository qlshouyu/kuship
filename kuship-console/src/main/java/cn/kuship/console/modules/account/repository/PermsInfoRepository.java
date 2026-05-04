package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.PermsInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermsInfoRepository extends JpaRepository<PermsInfo, Integer> {

    Optional<PermsInfo> findByCode(Integer code);

    List<PermsInfo> findByKind(String kind);

    Optional<PermsInfo> findByName(String name);
}
