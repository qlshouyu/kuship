package cn.kuship.console.modules.enterprise.service;

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
 * 企业用户列表读（对齐 rainbond EnterPriseUsersCLView.get + get_user_by_eid）。
 * default_favorite_* 当前恒 null（无收藏域）。
 */
@Service
public class EnterpriseUserReadService {

    private final UserInfoRepository userInfoRepository;

    public EnterpriseUserReadService(UserInfoRepository userInfoRepository) {
        this.userInfoRepository = userInfoRepository;
    }

    /** 企业用户分页列表，返回 {list, page, page_size, total}。 */
    public Map<String, Object> listUsers(String enterpriseId, String query, int page, int pageSize) {
        List<UserInfo> users = userInfoRepository.findByEnterpriseId(enterpriseId).stream()
                .sorted(Comparator.comparing(UserInfo::getUserId))
                .filter(u -> query == null || query.isBlank() || contains(u, query))
                .collect(Collectors.toList());
        int total = users.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(users.size(), from + pageSize);
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserInfo u : (from >= users.size() ? List.<UserInfo>of() : users.subList(from, to))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("email", u.getEmail());
            m.put("nick_name", u.getNickName());
            m.put("real_name", u.getRealName() == null ? u.getNickName() : u.getRealName());
            m.put("user_id", u.getUserId());
            m.put("phone", u.getPhone());
            m.put("create_time", cn.kuship.console.common.util.PyIsoDateTime.iso(u.getCreateTime())); // ISO 微秒不截尾零，对齐 DRF
            m.put("default_favorite_name", null);
            m.put("default_favorite_url", null);
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("page", page);
        out.put("page_size", pageSize);
        out.put("total", total);
        return out;
    }

    private static boolean contains(UserInfo u, String q) {
        return (u.getNickName() != null && u.getNickName().contains(q))
                || (u.getRealName() != null && u.getRealName().contains(q))
                || (u.getPhone() != null && u.getPhone().contains(q))
                || (u.getEmail() != null && u.getEmail().contains(q));
    }
}
