package cn.kuship.console.modules.appmarket.upgrade.repository;

import cn.kuship.console.modules.appmarket.upgrade.entity.AppUpgradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUpgradeRecordRepository extends JpaRepository<AppUpgradeRecord, Integer> {

    List<AppUpgradeRecord> findByGroupIdOrderByCreateTimeDesc(Integer groupId);

    List<AppUpgradeRecord> findByParentId(Integer parentId);
}
