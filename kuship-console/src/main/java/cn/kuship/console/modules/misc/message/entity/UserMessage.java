package cn.kuship.console.modules.misc.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** rainbond `user_message` —— 用户消息（11 列含 announcement_id + level）。 */
@Entity
@Table(name = "user_message")
@Getter
@Setter
@NoArgsConstructor
public class UserMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;

    @Column(name = "message_id", length = 32, nullable = false)
    private String messageId;

    @Column(name = "receiver_id", nullable = false)
    private Integer receiverId;

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "msg_type", length = 32, nullable = false)
    private String msgType;

    @Column(name = "announcement_id", length = 32)
    private String announcementId;

    @Column(name = "title", length = 64, nullable = false)
    private String title;

    @Column(name = "level", length = 32, nullable = false)
    private String level;
}
