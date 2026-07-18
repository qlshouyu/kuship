package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.AppMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppMarketRepository extends JpaRepository<AppMarket, Integer> {

    /** 企业默认市场（对齐 get_app_markets(eid).first()，按主键序取第一个）。 */
    Optional<AppMarket> findFirstByEnterpriseIdOrderByIdAsc(String enterpriseId);
}
