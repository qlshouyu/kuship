package cn.kuship.console.modules.misc.message.repository;

import cn.kuship.console.modules.misc.message.entity.UserMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMessageRepository extends JpaRepository<UserMessage, Integer> {

    List<UserMessage> findByReceiverIdAndIsReadOrderByCreateTimeDesc(Integer receiverId, Boolean isRead);

    List<UserMessage> findByReceiverIdOrderByCreateTimeDesc(Integer receiverId);

    List<UserMessage> findByMessageIdInAndReceiverId(List<String> messageIds, Integer receiverId);
}
