package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.Labels;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LabelsRepository extends JpaRepository<Labels, Integer> {
    List<Labels> findByLabelIdInOrderById(Collection<String> labelIds);
}
