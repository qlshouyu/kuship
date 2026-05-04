package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.PluginShareRecordEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PluginShareRecordEventRepository extends JpaRepository<PluginShareRecordEvent, Integer> {

    List<PluginShareRecordEvent> findByRecordId(Integer recordId);

    void deleteByRecordId(Integer recordId);
}
