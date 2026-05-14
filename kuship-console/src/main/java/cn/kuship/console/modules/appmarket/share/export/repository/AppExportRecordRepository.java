package cn.kuship.console.modules.appmarket.share.export.repository;

import cn.kuship.console.modules.appmarket.share.export.entity.AppExportRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppExportRecordRepository extends JpaRepository<AppExportRecord, Integer> {
    Optional<AppExportRecord> findByEventId(String eventId);
}
