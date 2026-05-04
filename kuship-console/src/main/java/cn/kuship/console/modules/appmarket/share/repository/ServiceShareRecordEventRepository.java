package cn.kuship.console.modules.appmarket.share.repository;

import cn.kuship.console.modules.appmarket.share.entity.ServiceShareRecordEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceShareRecordEventRepository extends JpaRepository<ServiceShareRecordEvent, Integer> {

    List<ServiceShareRecordEvent> findByRecordId(Integer recordId);

    void deleteByRecordId(Integer recordId);
}
