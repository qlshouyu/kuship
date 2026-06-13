package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户模糊查询（对齐 rainbond UserFuzSerView.get）：nick_name/email 不区分大小写包含，全局（无企业作用域）。
 */
@Service
public class UserSearchService {

    private final UserInfoRepository userInfoRepository;

    public UserSearchService(UserInfoRepository userInfoRepository) {
        this.userInfoRepository = userInfoRepository;
    }

    /** query_key 为空返回空列表；否则按 nick_name/email icontains 过滤，user_id 升序，每项 {nick_name,email,user_id}。 */
    public List<Map<String, Object>> search(String queryKey) {
        if (queryKey == null || queryKey.isEmpty()) {
            return new ArrayList<>();
        }
        String q = queryKey.toLowerCase();
        return userInfoRepository.findAll().stream()
                .filter(u -> contains(u.getNickName(), q) || contains(u.getEmail(), q))
                .sorted(Comparator.comparing(UserInfo::getUserId))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nick_name", u.getNickName());
                    m.put("email", u.getEmail());
                    m.put("user_id", u.getUserId());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private static boolean contains(String s, String lowerQ) {
        return s != null && s.toLowerCase().contains(lowerQ);
    }
}
