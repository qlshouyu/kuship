package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.TeamHelmReleaseSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamHelmReleaseSourceRepository extends JpaRepository<TeamHelmReleaseSource, Integer> {

    Optional<TeamHelmReleaseSource> findByRegionNameAndNamespaceAndReleaseName(
            String regionName, String namespace, String releaseName);

    List<TeamHelmReleaseSource> findByRegionNameAndNamespaceAndReleaseNameIn(
            String regionName, String namespace, Collection<String> releaseNames);

    @Modifying
    @Transactional
    long deleteByRegionNameAndNamespaceAndReleaseName(
            String regionName, String namespace, String releaseName);
}
