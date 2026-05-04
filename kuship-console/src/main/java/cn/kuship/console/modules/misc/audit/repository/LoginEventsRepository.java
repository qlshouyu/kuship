package cn.kuship.console.modules.misc.audit.repository;

import cn.kuship.console.modules.misc.audit.entity.LoginEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventsRepository extends JpaRepository<LoginEvents, Integer> {

    Page<LoginEvents> findByEnterpriseIdOrderByLoginTimeDesc(String enterpriseId, Pageable pageable);
}
