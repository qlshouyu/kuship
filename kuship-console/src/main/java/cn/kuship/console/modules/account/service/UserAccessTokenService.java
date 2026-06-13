package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.UserAccessKey;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.repository.UserAccessKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户访问令牌列表读（对齐 rainbond UserAccessTokenCLView.get）。 */
@Service
public class UserAccessTokenService {

    private final UserAccessKeyRepository repository;

    public UserAccessTokenService(UserAccessKeyRepository repository) {
        this.repository = repository;
    }

    /** 当前用户令牌列表，每项 {note, expire_time, user_id, ID}。 */
    public List<Map<String, Object>> list(Integer userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserAccessKey k : repository.findByUserId(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("note", k.getNote());
            m.put("expire_time", k.getExpireTime());
            m.put("user_id", k.getUserId());
            m.put("ID", k.getId());
            out.add(m);
        }
        return out;
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成 access_key：sha1(随机 24B) 40 位 hex（对齐 generate_key）。 */
    private static String generateKey() {
        byte[] r = new byte[24];
        RANDOM.nextBytes(r);
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(r);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 创建令牌：note 必填；expire_time=age?(now秒+age):null；返回 to_dict {ID,note,user_id,access_key,expire_time}。 */
    public java.util.Map<String, Object> create(Integer userId, String note, Integer age) {
        if (note == null || note.isEmpty()) {
            throw ServiceHandleException.badRequest("note can't be null", "注释不能为空");
        }
        UserAccessKey k = new UserAccessKey();
        k.setNote(note);
        k.setUserId(userId);
        k.setAccessKey(generateKey());
        k.setExpireTime(age == null ? null : (int) (System.currentTimeMillis() / 1000 + age));
        return toDict(repository.save(k));
    }

    /** 查看令牌（{note,expire_time,user_id,ID}），无→404。 */
    public java.util.Map<String, Object> getById(Integer userId, Integer id) {
        UserAccessKey k = repository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ServiceHandleException(404, "no found access key", "未找到该凭证"));
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("note", k.getNote());
        m.put("expire_time", k.getExpireTime());
        m.put("user_id", k.getUserId());
        m.put("ID", k.getId());
        return m;
    }

    /** 重生成 access_key，返回 to_dict；无→404。 */
    public java.util.Map<String, Object> regenerate(Integer userId, Integer id) {
        UserAccessKey k = repository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ServiceHandleException(404, "no found access key", "未找到该凭证"));
        k.setAccessKey(generateKey());
        return toDict(repository.save(k));
    }

    /** 删除令牌。 */
    @Transactional
    public void delete(Integer userId, Integer id) {
        repository.findByUserIdAndId(userId, id).ifPresent(repository::delete);
    }

    /** to_dict 全列 {ID,note,user_id,access_key,expire_time}。 */
    private static java.util.Map<String, Object> toDict(UserAccessKey k) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("ID", k.getId());
        m.put("note", k.getNote());
        m.put("user_id", k.getUserId());
        m.put("access_key", k.getAccessKey());
        m.put("expire_time", k.getExpireTime());
        return m;
    }
}
