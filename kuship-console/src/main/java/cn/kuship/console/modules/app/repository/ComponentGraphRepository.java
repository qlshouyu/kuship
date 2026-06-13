package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.ComponentGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComponentGraphRepository extends JpaRepository<ComponentGraph, Integer> {

    /** 对齐 component_graph_repo.list：filter(component_id).order_by("sequence")。 */
    List<ComponentGraph> findByComponentIdOrderBySequence(String componentId);
}
