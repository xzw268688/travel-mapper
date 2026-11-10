## Context

`sys_config` + `ConfigService` 已支持敏感配置加密、掩码、快照回滚与 `ReloadableProvider` 热更新；`AdminConfigController` 的 GET/PUT 模式与 `AdminSettingsView` 页签可复用。现有 `AlertNotifier` 在构造时通过 `@Value` 读取环境变量，修改后必须重启。

## Goals / Non-Goals

**Goals**

- 机器人配置在管理端可改、可加密、可测试，保存后立即生效。
- 数据库优先、环境变量兜底，数据库故障时告警仍可用。
- 限制目标地址为飞书 HTTPS，降低 SSRF 风险。

**Non-Goals**

- 多机器人、钉钉/企业微信、飞书签名密钥、运行中修改 Cron。

## Decisions

- **新增 `AlertConfigService` 统一解析配置**：每次 `notify` 读取 `ConfigService`，逐字段捕获异常并回退到构造时注入的 `ALERT_*` 环境变量。不引入缓存，保证保存后立即生效；告警频率低，额外查询可接受。数据库不可用时回退环境变量，避免「数据库挂了但告警也发不出」。
- **Webhook 使用 `ConfigEntry.secret`**：数据库存 AES 密文，GET 返回 `AesCryptoService.mask` 掩码，PUT 留空表示保留原值；配置变更沿用 `@Auditable(CONFIG_UPDATE)` 审计。
- **地址白名单**：只接受 `https`、Host 为 `open.feishu.cn` 或 `open.larksuite.com`、路径以 `/open-apis/bot/v2/hook/` 开头。替代方案是允许任意 HTTPS，但会引入 SSRF 与错误消息格式风险。
- **测试发送同步执行**：`AlertNotifier.sendTest()` 绕过去重并同步返回成功/失败；普通告警仍异步。测试只使用 `AlertConfigService.resolve()` 的已保存配置。
- **健康检查 Cron 留在环境变量**：`@Scheduled` 在启动时解析 Cron，运行中修改需要动态调度；本次不引入。
- **前端复用现有配置页**：新增 `alert` 页签，沿用掩码输入与“留空保留”的交互。

## Risks / Trade-offs

- [Webhook 地址掩码后无法完整复制] → 需要修改时重新填写；这是敏感配置的既定交互。
- [数据库不可用时每次告警都尝试读配置] → 逐字段 try/catch，失败后使用环境变量；不缓存，避免恢复后仍用旧值。
- [飞书签名机器人无法使用] → 当前只支持无签名 URL；文档说明限制，后续可扩展 secret。
- [测试消息可能被误用刷屏] → 仅管理员可调用，且只使用已保存配置；不做去重以允许连续测试，属于可接受风险。

## Migration Plan

1. 部署后端与前端：新增配置页签与配置键；现有 `.env` 继续作为兜底，不破坏告警。
2. 管理员进入「配置 → 故障提醒」，保存 Webhook 后点击测试确认。
3. 回滚时移除页面与配置读取即可，`.env` 仍可继续工作。

## Open Questions

无。