# P2-i（用户模糊查询 users/query）7070 校准基线
- `GET /console/users/query?query_key=` → query_key 非空：nick_name/email icontains（**全局，无企业作用域**），user_id 升序，项 {nick_name,email,user_id}，msg_show=查询用户成功。
- 空 query_key → list:[]，msg_show=你没有查询任何用户。
- 校准：query_key=inter([interop])/kuship([interop,viewer])/空，三案 deep-diff 8000 vs 7070 全 0。单测 100/100。
