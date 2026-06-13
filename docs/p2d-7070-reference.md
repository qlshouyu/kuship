# P2-d（企业 admin 角色列表）7070 校准基线
- `GET /console/enterprise/{eid}/admin/roles` → `{code:200,msg:success,msg_show:null,data:{bean:{},list:["admin","app_store"]}}`。
- 纯计算：AdminRolesView 遍历 perms.py ENTERPRISE 键。kuship 复用 PermsCatalog.ENTERPRISE.keySet()(LinkedHashMap 保序 admin,app_store)。
- 校准：8000 与 7070 完全一致 ✓。JWTAuthApiView 仅登录。
