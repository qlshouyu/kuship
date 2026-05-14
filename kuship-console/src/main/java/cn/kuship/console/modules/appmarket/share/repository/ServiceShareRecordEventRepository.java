package cn.kuship.console.modules.appmarket.share.repository;

import cn.kuship.console.modules.appmarket.share.entity.ServiceShareRecordEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceShareRecordEventRepository extends JpaRepository<ServiceShareRecordEvent, Integer> {

    List<ServiceShareRecordEvent> findByRecordId(Integer recordId);

    Optional<ServiceShareRecordEvent> findByRecordIdAndEventId(Integer recordId, String eventId);

    void deleteByRecordId(Integer recordId);
}
