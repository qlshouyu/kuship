package cn.kuship.console.modules.appmarket.market.repository;

import cn.kuship.console.modules.appmarket.market.entity.AppMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppMarketRepository extends JpaRepository<AppMarket, Integer> {

    List<AppMarket> findByEnterpriseId(String enterpriseId);

    Optional<AppMarket> findByEnterpriseIdAndName(String enterpriseId, String name);

    void deleteByEnterpriseIdAndName(String enterpriseId, String name);
}
