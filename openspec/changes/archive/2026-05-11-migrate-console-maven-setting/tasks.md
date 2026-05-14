# Tasks — migrate-console-maven-setting

> **路线图**：母 [`migrate-region-coverage-roadmap`](../migrate-region-coverage-roadmap/) §4.3（P2 #3，5 method 实际，路线图估计 8）

## 1. 接口与 Impl

- [x] 1.1 新建 `cn.kuship.console.modules.region.maven.api.MavenSettingOperations`，5 method 签名
- [x] 1.2 新建 `MavenSettingOperationsImpl @Primary`，落地 5 method
- [x] 1.3 `listMavenSettings` 实现（GET + onlyName 投影）
- [x] 1.4 `addMavenSetting` / `updateMavenSetting` 实现（POST / PUT + body）
- [x] 1.5 `getMavenSetting` / `deleteMavenSetting` 实现（GET / DELETE + path param）

## 2. Controller

- [x] 2.1 新建 `MavenSettingController`，注入 `MavenSettingOperations`
- [x] 2.2 5 个 HTTP method 落地，URL 与 rainbond 一致
- [x] 2.3 全部 endpoint 加 `@RequireEnterpriseAdmin`
- [x] 2.4 错误兼容：POST 400 重名 / GET PUT DELETE 404 不存在

## 3. 单元测试

- [x] 3.1 `MavenSettingOperationsImplTest`：5 method × 2 用例 = 10 用例
- [x] 3.2 测试 onlyName 投影逻辑（list 投影为 `[{name, is_default}]`）

## 4. 集成测试

- [x] 4.1 `MavenSettingIntegrationTest @SpringBootTest`
- [x] 4.2 5 endpoint × happy/error = ~10 用例
- [x] 4.3 权限测试：非 enterprise admin 401/403

## 5. 验证与归档

- [x] 5.1 `mvn -DskipTests package` 编译通过
- [x] 5.2 `mvn test -Dtest=MavenSettingOperationsImplTest,MavenSettingIntegrationTest` 全过
- [x] 5.3 既有 416+ 用例零回归
- [x] 5.4 联动验证：5 个 region URL 真实可调
- [x] 5.5 实施期探测结果回填到 design.md §9
- [x] 5.6 母路线图 §4.3 标 [x] + 加 4.3.3 实施落地条目 + 偏差原因摘要
- [x] 5.7 `kuship-console/CLAUDE.md` 表格 P2 #3 状态从 ⏳ 改为 ✅
- [x] 5.8 `openspec archive migrate-console-maven-setting --skip-specs`
