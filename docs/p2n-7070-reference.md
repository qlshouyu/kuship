# P2-n（自定义配置写 users/custom_configs PUT）7070 校准基线
- **PUT**(非 POST) `/console/users/custom_configs`，body 为 list（否则 400「请求参数必须为列表」The request parameter must be a list）。
- bulk_create_or_update：当前用户已存在该 key 且值为真→更新(该 key 全局删后重建)，否则新建；无 key 跳过；bulk insert(user_nick_name=当前用户,update_time=null)。响应**回显入参 list**、msg_show=操作成功。
- 校准(interop)：PUT [{key:p2n_a,value:1},{key:p2n_b,value:2}]→回显两端逐字节一致；GET 读回 MATCH(项 {ID,key,value,description:null,update_time:null,user_nick_name})；非 list→400 两端一致。测毕清理(interop 配置 0)。单测 117/117。
- delete 按 key 全局(ConsoleConfig.filter(key__in).delete()，不限用户，对齐 rainbond)；校准用唯一 key 安全。
