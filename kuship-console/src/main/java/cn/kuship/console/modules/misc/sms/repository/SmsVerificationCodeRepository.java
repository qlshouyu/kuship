package cn.kuship.console.modules.misc.sms.repository;

import cn.kuship.console.modules.misc.sms.entity.SmsVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, Integer> {

    List<SmsVerificationCode> findByPhoneAndPurposeAndExpiresAtAfterOrderByCreatedAtDesc(
            String phone, String purpose, LocalDateTime now);
}
