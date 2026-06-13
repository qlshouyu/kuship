## 1. 服务与接口
- [x] 1.1 `EnterpriseUserReadService.listUsers(eid, query, page, pageSize)`：findByEnterpriseId→user_id 升序→query 模糊→分页；项 {email,nick_name,real_name(回退),user_id,phone,create_time,default_favorite_*:null}
- [x] 1.2 `EnterpriseUserController GET /console/enterprise/{enterprise_id}/users`（list+顶层 page/page_size/total）
- [x] 1.3 单测：real_name 回退、query 过滤、分页、default_favorite null
## 2. 实跑校准
- [x] 2.1 deep-diff 8000 vs 7070（interop/viewer；含 create_time 精度、分页 page/page_size/total、query）
- [x] 2.2 全量构建 + 单测
- [x] 2.3 docs 记结论（含 create_time 格式）；openspec 校验归档
