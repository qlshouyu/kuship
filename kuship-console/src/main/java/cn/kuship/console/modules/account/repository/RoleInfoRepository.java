package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.RoleInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleInfoRepository extends JpaRepository<RoleInfo, Integer> {

    List<RoleInfo> findByKindAndKindId(String kind, String kindId);

    Optional<RoleInfo> findByNameAndKindAndKindId(String name, String kind, String kindId);
}
