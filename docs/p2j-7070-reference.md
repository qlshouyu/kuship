# P2-j（用户访问令牌 users/access-token GET）7070 校准基线
- `GET /console/users/access-token` → 当前用户 user_access_key，项 {note, expire_time(int|null), user_id, ID}。
- 校准：interop 空 + DB 直插临时令牌(note=p2j-ci,access_key 唯一,expire_time int)非空，deep-diff 8000 vs 7070 全 0，测毕删。单测 102/102。
- 仅 GET 列表；create/delete(POST/DELETE) defer。
