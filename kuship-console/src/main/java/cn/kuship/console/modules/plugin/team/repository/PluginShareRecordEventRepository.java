package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.PluginShareRecordEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PluginShareRecordEventRepository extends JpaRepository<PluginShareRecordEvent, Integer> {

    List<PluginShareRecordEvent> findByRecordId(Integer recordId);

    Optional<PluginShareRecordEvent> findByRecordIdAndEventId(Integer recordId, String eventId);

    void deleteByRecordId(Integer recordId);
}
