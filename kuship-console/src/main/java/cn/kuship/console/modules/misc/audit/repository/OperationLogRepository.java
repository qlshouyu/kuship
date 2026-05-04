package cn.kuship.console.modules.misc.audit.repository;

import cn.kuship.console.modules.misc.audit.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Integer> {

    @Query("SELECT new cn.kuship.console.modules.misc.audit.repository.OperationLogSummary("
            + "o.id, o.createTime, o.username, o.operationType, o.enterpriseId, o.teamName, "
            + "o.appId, o.serviceAlias, o.comment, o.isOpenapi, o.serviceCname, o.appName, o.informationType) "
            + "FROM OperationLog o WHERE o.enterpriseId = :eid "
            + "ORDER BY o.createTime DESC")
    Page<OperationLogSummary> findEnterprisePage(@Param("eid") String enterpriseId, Pageable pageable);

    @Query("SELECT new cn.kuship.console.modules.misc.audit.repository.OperationLogSummary("
            + "o.id, o.createTime, o.username, o.operationType, o.enterpriseId, o.teamName, "
            + "o.appId, o.serviceAlias, o.comment, o.isOpenapi, o.serviceCname, o.appName, o.informationType) "
            + "FROM OperationLog o WHERE o.teamName = :team "
            + "ORDER BY o.createTime DESC")
    Page<OperationLogSummary> findTeamPage(@Param("team") String teamName, Pageable pageable);

    @Query("SELECT new cn.kuship.console.modules.misc.audit.repository.OperationLogSummary("
            + "o.id, o.createTime, o.username, o.operationType, o.enterpriseId, o.teamName, "
            + "o.appId, o.serviceAlias, o.comment, o.isOpenapi, o.serviceCname, o.appName, o.informationType) "
            + "FROM OperationLog o WHERE o.teamName = :team AND o.appId = :appId "
            + "ORDER BY o.createTime DESC")
    Page<OperationLogSummary> findAppPage(@Param("team") String teamName,
                                              @Param("appId") Integer appId,
                                              Pageable pageable);
}
