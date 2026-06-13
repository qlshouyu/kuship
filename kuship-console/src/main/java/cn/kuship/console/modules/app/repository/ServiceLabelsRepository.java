package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ServiceLabels;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceLabelsRepository extends JpaRepository<ServiceLabels, Integer> {
    List<ServiceLabels> findByServiceId(String serviceId);
}
