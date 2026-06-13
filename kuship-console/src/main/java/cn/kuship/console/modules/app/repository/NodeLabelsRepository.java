package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.NodeLabels;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeLabelsRepository extends JpaRepository<NodeLabels, Integer> {
    List<NodeLabels> findByRegionId(String regionId);
}
