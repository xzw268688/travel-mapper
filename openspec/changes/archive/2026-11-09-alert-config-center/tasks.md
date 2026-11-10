## 1. 后端配置

- [x] 1.1 `ConfigKeys` 增加 `alert.enabled` / `alert.webhook-url` / `alert.min-interval-seconds`
- [x] 1.2 新增 `AlertConfigService`：数据库优先、环境变量兜底、数据库异常时回退
- [x] 1.3 `AlertNotifier` 改为按次解析配置；新增同步 `sendTest()` 并绕过去重
- [x] 1.4 `AdminConfigController` 增加 GET/PUT `/config/alert` 与 POST `/config/alert/test`，Webhook 使用 AES 加密、掩码与飞书 HTTPS 白名单校验

## 2. 前端配置页

- [x] 2.1 `api/admin.ts` 增加告警配置类型与 GET/PUT/测试接口
- [x] 2.2 `AdminSettingsView` 增加「故障提醒」页签：启用开关、Webhook 掩码输入、最小间隔、保存与测试按钮
- [x] 2.3 `App.vue` 配置菜单增加「故障提醒」入口

## 3. 测试与文档

- [x] 3.1 新增配置解析测试：数据库优先、环境变量兜底、数据库异常回退
- [x] 3.2 新增接口测试：掩码、留空保留、非法地址拒绝、未配置测试返回错误
- [x] 3.3 更新 `docs/ops/alerting.md`：配置中心优先、环境变量兜底、测试按钮与地址限制
- [x] 3.4 后端全量测试与前端构建通过