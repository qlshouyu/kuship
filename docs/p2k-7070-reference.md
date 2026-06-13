# P2-k（用户自定义配置 users/custom_configs GET）7070 校准基线
- `GET /console/users/custom_configs` → 当前用户(nick_name)的 console_config 行(.values() 全列)，项 {ID,key,value,description,update_time(ISO|null),user_nick_name}，msg_show=操作成功。
- 校准：interop 空 + DB 直插临时行(key=p2k_theme,user_nick_name=interop)非空，deep-diff 全 0(含 update_time ISO)，测毕删。单测 104/104。仅 GET；POST 写 defer。
