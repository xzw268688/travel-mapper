Triage: ready-for-agent

## Why

故障提醒的飞书 Webhook 目前只存在于 `.env`，修改后必须重启后端，也不在管理员熟悉的「配置」页面里。现有配置中心已经支持敏感项加密、掩码与变更审计，应该把机器人配置也纳入同一套能力。

## What Changes

- 在「配置」菜单新增「故障提醒」页签，包含：启用开关、飞书机器人 Webhook（掩码显示，留空保存表示保留原值）、同类提醒最小间隔、发送测试消息按钮。
- 新增配置键：`alert.enabled`、`alert.webhook-url`（AES 加密）、`alert.min-interval-seconds`，存入 `sys_config`。
- 读取优先级：数据库配置优先，缺失时回退到 `ALERT_*` 环境变量；保存后立即生效，无需重启。
- Webhook 地址仅允许飞书 HTTPS：`open.feishu.cn` 或 `open.larksuite.com` 的 `/open-apis/bot/v2/hook/...`，防止被指向内网。
- 测试按钮使用已保存的配置发送一条测试消息；未保存或未启用时返回明确错误。
- 健康检查 Cron 继续通过 `ALERT_HEALTH_CRON` 配置，不进入页面。
- 数据库不可用时，告警配置解析回退到 `.env`，保证数据库故障本身仍能发送提醒。

非目标：钉钉/企业微信多机器人、飞书签名密钥、运行中修改健康检查 Cron、多个 Webhook。

## Capabilities

### Modified Capabilities

- `platform/alerting`: 告警配置从仅环境变量扩展为「管理端配置中心 + 环境变量兜底」，并新增测试发送能力。
- `ui/branding`: 「配置」菜单新增「故障提醒」页签。

## Impact

- 后端：新增 `AlertConfigService`、`AlertConfigDto`；`AlertNotifier` 改为按次解析配置；`AdminConfigController` 增加 GET/PUT/测试接口；`ConfigKeys` 增加告警键。
- 前端：`AdminSettingsView` 增加故障提醒页签、`api/admin.ts` 增加类型与接口、`App.vue` 配置菜单增加入口。
- 数据库：无迁移；配置写入 `sys_config`。
- 文档：`docs/ops/alerting.md` 补充配置中心优先与环境变量兜底。
- 测试：配置解析、地址校验、掩码/保留原值、测试发送错误路径。