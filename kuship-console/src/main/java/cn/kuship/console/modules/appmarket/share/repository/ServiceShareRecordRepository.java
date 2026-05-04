package cn.kuship.console.modules.appmarket.share.repository;

import cn.kuship.console.modules.appmarket.share.entity.ServiceShareRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceShareRecordRepository extends JpaRepository<ServiceShareRecord, Integer> {

    Optional<ServiceShareRecord> findByGroupShareId(String groupShareId);

    List<ServiceShareRecord> findByGroupId(String groupId);

    List<ServiceShareRecord> findByTeamName(String teamName);
}
