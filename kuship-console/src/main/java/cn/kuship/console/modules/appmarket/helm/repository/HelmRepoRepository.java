package cn.kuship.console.modules.appmarket.helm.repository;

import cn.kuship.console.modules.appmarket.helm.entity.HelmRepo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HelmRepoRepository extends JpaRepository<HelmRepo, Integer> {

    Optional<HelmRepo> findByRepoName(String repoName);

    Optional<HelmRepo> findByRepoId(String repoId);
}
