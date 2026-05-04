package cn.kuship.console.modules.appmarket.backup.repository;

import cn.kuship.console.modules.appmarket.backup.entity.ServiceGroupBackup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceGroupBackupRepository extends JpaRepository<ServiceGroupBackup, Integer> {

    List<ServiceGroupBackup> findByGroupIdOrderByCreateTimeDesc(Integer groupId);

    Optional<ServiceGroupBackup> findByBackupId(String backupId);

    List<ServiceGroupBackup> findByTeamId(String teamId);
}
