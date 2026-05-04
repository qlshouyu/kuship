package cn.kuship.console.modules.misc.webhook.repository;

import cn.kuship.console.modules.misc.webhook.entity.ServiceWebhooks;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceWebhooksRepository extends JpaRepository<ServiceWebhooks, Integer> {

    List<ServiceWebhooks> findByServiceId(String serviceId);

    Optional<ServiceWebhooks> findByServiceIdAndWebhooksType(String serviceId, String webhooksType);

    void deleteByServiceId(String serviceId);
}
