package cn.kuship.console.modules.appmarket.market.repository;

import cn.kuship.console.modules.appmarket.market.entity.RainbondCenterAppVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RainbondCenterAppVersionRepository extends JpaRepository<RainbondCenterAppVersion, Integer> {

    List<RainbondCenterAppVersion> findByAppId(String appId);

    Optional<RainbondCenterAppVersion> findByAppIdAndVersion(String appId, String version);

    void deleteByAppId(String appId);

    void deleteByAppIdAndVersion(String appId, String version);
}
