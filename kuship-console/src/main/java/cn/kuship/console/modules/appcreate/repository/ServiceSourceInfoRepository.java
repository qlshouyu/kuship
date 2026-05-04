package cn.kuship.console.modules.appcreate.repository;

import cn.kuship.console.modules.appcreate.entity.ServiceSourceInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ServiceSourceInfoRepository extends JpaRepository<ServiceSourceInfo, Integer> {
    Optional<ServiceSourceInfo> findByServiceId(String serviceId);

    @Modifying
    @Query("delete from ServiceSourceInfo s where s.serviceId = :serviceId")
    int deleteByServiceId(@Param("serviceId") String serviceId);
}
