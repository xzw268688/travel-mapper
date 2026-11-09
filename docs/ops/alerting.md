# 故障提醒

系统通过飞书群机器人 Webhook 发送故障提醒，覆盖两类事件：

1. **接口 5xx**：未捕获异常、数据库异常与 5xx 业务错误；4xx 参数错误不告警。
2. **依赖健康状态变化**：每 5 分钟检查 MySQL、Redis、对象存储；只在「正常 → 异常」与「异常 → 正常」时告警，持续异常不重复。

## 配置飞书机器人

1. 在飞书中建一个群（建议只拉运维/开发）。
2. 群设置 → 群机器人 → 添加机器人 → **自定义机器人**。
3. 复制 Webhook 地址，形如：

   ```text
   https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx
   ```

4. 写入服务器上的 `.env`（不要提交到 Git）：

   ```bash
   ALERT_ENABLED=true
   ALERT_WEBHOOK_URL="https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxx"
   ALERT_MIN_INTERVAL_SECONDS=300
   ALERT_HEALTH_CRON="0 */5 * * * *"
   ```

5. 重启后端。

## 行为说明

- 同一类告警默认 300 秒内只发一次（`ALERT_MIN_INTERVAL_SECONDS`）。
- 健康检查默认每 5 分钟一次（`ALERT_HEALTH_CRON`）。
- 对象存储未配置时跳过，不产生告警。
- Webhook 未配置时只写后端日志，不发送外部请求。
- Webhook 发送失败或超时只记录 `warn`，不影响业务请求。
- Webhook 地址只从环境变量读取，不写入代码、`application.yml` 或日志。

## 关闭告警

```bash
ALERT_ENABLED=false
```

重启后端后不再发送任何 Webhook 请求。

## 测试告警通道

可以用 `curl` 直接向机器人发一条测试消息（把地址替换成你的 Webhook）：

```bash
curl -X POST '<webhook-url>' \
  -H 'Content-Type: application/json' \
  -d '{"msg_type":"text","content":{"text":"【旅迹地图】告警通道测试"}}'
```

如果飞书机器人开启了「签名校验」，当前实现只支持 URL、不支持 secret 签名；需要在机器人设置里关闭签名校验，或后续扩展 `ALERT_WEBHOOK_SECRET` 支持。