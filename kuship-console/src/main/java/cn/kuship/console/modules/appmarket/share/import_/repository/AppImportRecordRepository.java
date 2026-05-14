package cn.kuship.console.modules.appmarket.share.import_.repository;

import cn.kuship.console.modules.appmarket.share.import_.entity.AppImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppImportRecordRepository extends JpaRepository<AppImportRecord, Integer> {
    Optional<AppImportRecord> findByEventId(String eventId);
}
