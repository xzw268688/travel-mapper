## 1. 后端测试能力

- [x] 1.1 新增 `StorageTestResult` 与 `StorageConnectionTestService`：临时 Provider + `HeadBucket` + 上传/HEAD/删除 `healthcheck/` 临时对象
- [x] 1.2 `AdminConfigController` 新增 `POST /config/oss/test`，表单空字段回退已保存配置，不写审计、不记录密钥
- [x] 1.3 错误信息脱敏并限制长度

## 2. 前端

- [x] 2.1 `api/admin.ts` 增加 `testStorageConfig` 与 `StorageTestResult`
- [x] 2.2 对象存储页签增加「测试连接」按钮与结果提示；提示阿里云 OSS 需关闭 Path Style
- [x] 2.3 `npm run build` 通过

## 3. 测试与文档

- [x] 3.1 `StorageIntegrationTest` 增加测试接口成功用例，并验证临时对象被删除
- [x] 3.2 增加未配置/错误凭证的失败用例（可用 Mockito 或本地 HTTP 服务）
- [x] 3.3 更新 `docs/ops/storage-media.md`：阿里云 OSS 的 Endpoint/Region、Path Style、CORS、最小权限与测试步骤
- [x] 3.4 后端全量测试通过，验证方式：`mvn test` 退出码为 0