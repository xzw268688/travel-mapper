# 生产部署

目标机器：2 vCPU / 1.8 GiB / 40G（剩余 28G），单机 Docker。

## 架构

```
Internet
  └─ Cloudflare Tunnel（cloudflared 容器，TLS 由 Cloudflare 终止）
       └─ nginx:80（宿主 8080:80）
            ├─ /                → nginx-html/（Vue 构建产物，容器内 /usr/share/nginx/html）
            ├─ /api/            → java-app:8080
            ├─ /s/<token>       → java-app:8080（分享 API）
            └─ SSE              → /api/ai/polish/stream、/api/country-guide/stream

web-net 内网：
  java-app（Spring Boot 3.3 / JDK 17）
  mysql:8.0
  redis:7

外部服务：
  阿里云 OSS（图片直传，S3 兼容）
  Resend SMTP（验证码邮件）
  LLM / 搜索 API
```

公网 TLS 由 Cloudflare 完成，**不需要 certbot、不需要 443 端口**。Nginx 只监听 80，宿主机映射 8080，由 cloudflared 回源。java-app / mysql / redis 仅在内网。

参考文件在仓库 `deploy/`：

| 文件 | 用途 |
|---|---|
| `backend.Dockerfile` | 放到服务器 `java-app/Dockerfile` |
| `docker-compose.example.yml` | 与你现有 compose 对照合并 |
| `nginx/default.conf` | 放到服务器 `nginx-conf/default.conf` |
| `.env.prod.example` | 复制为服务器 `my-docker-apps/.env` |
| `release.sh` | 本地构建 + 上传 + 重启 |

> 服务器上已有 nginx / java-app / web-net 结构时，**对照合并，不要直接覆盖** compose 和 nginx 配置。

## 内存预算

| 组件 | 限制 | 关键配置 |
|---|---|---|
| cloudflared | 64m | — |
| nginx | 64m | — |
| java-app | 640m | `-Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC` |
| mysql | 384m | `performance_schema=OFF`、buffer pool 128M、max_connections 30 |
| redis | 96m | `maxmemory 64mb`、关闭 RDB、`noeviction` |
| 系统 + Docker daemon + 阿里云 agent | 约 350m | — |

当前机器实测已用约 490 MiB（含 nginx、cloudflared、系统），新增三件套上限 1120 MiB，
合计约 1.6 GiB，余量约 260 MiB，另有 4G swap 兜底。上线第一周观察：

```bash
free -m
docker stats --no-stream
```

若频繁 OOM 或 Java 被 `-XX:+ExitOnOutOfMemoryError` 重启，优先把 MySQL 迁到云 RDS（可释放约 350MB），而不是继续压缩 JVM 堆。

## 首次部署

### 1. 合并 compose

在服务器 `/root/my-docker-apps/`：

```bash
cp docker-compose.yml docker-compose.yml.bak
```

对照 `deploy/docker-compose.example.yml` 加入或核对：

- 保留现有 `cloudflared` 与 `nginx`；把 cloudflared 的 tunnel token 从 `command` 移到 `.env` 的 `CLOUDFLARE_TUNNEL_TOKEN`，避免明文写在 compose
- `java-app`：`env_file: .env`、`depends_on` mysql/redis 健康、`mem_limit: 640m`、healthcheck
- `mysql` / `redis`：本机没有就新增；已有则保留现有服务名，并确保 java-app 的 `DB_URL` 指向该服务名
- 所有服务都在 `web-net`；java-app **不要映射端口**

启动：

```bash
docker compose up -d
```

### 2. 构建与上传产物

在本地仓库根目录：

```bash
# 后端
cd backend && MAVEN_SKIP_RC=1 mvn -DskipTests package
# 产物：backend/target/travelmapper-backend-0.1.0-SNAPSHOT.jar

# 前端
cd frontend && npm ci && npm run build
# 产物：frontend/dist/
```

上传：

```bash
scp backend/target/travelmapper-backend-*.jar root@<server>:/root/my-docker-apps/java-app/app.jar
tar -C frontend/dist -czf - . | ssh root@<server> \
  "find /root/my-docker-apps/nginx-html -mindepth 1 -delete && tar -C /root/my-docker-apps/nginx-html -xzf -"
```

或者直接用 `./deploy/release.sh`（见下文「日常发布」）。

### 3. 配置环境变量

```bash
cp deploy/.env.prod.example /root/my-docker-apps/.env
chmod 600 /root/my-docker-apps/.env
```

必须填写：

```bash
openssl rand -base64 32   # TRAVELMAPPER_AES_KEY
openssl rand -hex 32      # TRAVELMAPPER_JWT_SECRET
```

以及 `TRAVELMAPPER_ADMIN_PASSWORD`、`DB_ROOT_PASSWORD`、`DB_PASSWORD`。

管理员 2FA 强制由 `REQUIRE_ADMIN_2FA` 控制，默认 `false`：关闭时管理员密码登录即可；开启后未绑定 TOTP 的管理员访问后台会返回 `1009`，需要先在个人中心绑定。若开启后无法绑定，可临时改回 `false` 并重启。

> 缺少 `TRAVELMAPPER_AES_KEY` 时应用启动直接失败（`AesCryptoService` 构造校验）。
> 修改 `TRAVELMAPPER_AES_KEY` 会使已有 TOTP 绑定无法解密，用户需重新绑定；轮换前请先通知用户或使用后台重置。
> `SPRING_PROFILES_ACTIVE` 必须是 `prod`，否则会启用开发身份桩。

### 4. 启动并验证

```bash
cd /root/my-docker-apps
docker compose up -d --build
docker compose ps
docker logs --tail 100 java-app
```

启动日志里应看到：

```
Started TravelMapperApplication in ... seconds
mail sender reloaded host=... port=...
```

数据库由 Flyway 在启动时自动迁移，不需要手工执行 SQL。

### 5. Nginx 与 Cloudflare Tunnel

```bash
cp deploy/nginx/default.conf /root/my-docker-apps/nginx-conf/default.conf
docker exec nginx nginx -t
docker exec nginx nginx -s reload
```

公网 TLS 由 Cloudflare 终止，**不需要 certbot 和 443**：

- Cloudflare Tunnel 的公共主机名 `example.com` 回源到 `http://nginx:80`（compose 内网）或 `http://127.0.0.1:8080`
- Nginx 继续用宿主 `8080:80` 映射，保持不变
- 安全组只需放行 SSH（22）；Cloudflare Tunnel 是出站连接，不需要对外开放 80/443
- 如果 Cloudflare 开启了「Authenticated Origin Pull」或 WAF，确认放行 API 与 SSE 路径

## 后台配置（首次登录后）

登录 `https://example.com/admin/login`，在「配置」里完成：

1. **对象存储**：填阿里云 OSS 的 S3 兼容 endpoint、region、bucket、AccessKey/SecretKey。
   OSS Bucket 需配置 CORS：允许站点域名 `PUT`、`GET`、`HEAD`，否则前端直传会被浏览器拦截。
2. **邮件 SMTP**：Resend，见 `docs/ops/local-development.md`。发件域名必须完成 SPF/DKIM 验证。
3. **LLM / 搜索**：填对应 API Key。
4. **故障提醒**：飞书机器人 Webhook，然后点「发送测试消息」。

配置存 DB 并热加载，不需要重启。

## 日常发布

**配置只存一次，发布只替换运行产物**，不需要每次重新配置：

| 配置 | 存放位置 | 发布时是否变化 |
|---|---|---|
| 密钥、DB、Tunnel token | 服务器 `/root/my-docker-apps/.env` | 不变（只在密钥轮换时手动改） |
| OSS / SMTP / LLM / 搜索 / 告警 | MySQL `sys_config`（数据卷） | 不变 |
| 业务数据 | MySQL / Redis 数据卷 | 不变 |
| 容器编排、Nginx | `docker-compose.yml`、`nginx-conf/` | 不变（只有基础设施调整才改） |
| 后端 jar、前端 dist | `java-app/app.jar`、`nginx-html/` | **每次发布替换** |

一条命令发布：

```bash
REMOTE=root@<server> ./deploy/release.sh
```

脚本流程：

1. 校验当前 commit 的 CI 必须为绿（`SKIP_CI_CHECK=1` 可跳过，仅限紧急情况）
2. 本地 `mvn package` + `npm run build`（服务器不构建）
3. 服务器端备份：当前 jar、前端 dist、数据库 dump 各存一份到 `releases/`，各保留 3 份
4. 上传 jar 和 dist，只重建 `java-app`
5. 等待 healthcheck 变 healthy

数据库迁移由 Flyway 在应用启动时自动执行，不需要手工跑 SQL。

### 回滚

```bash
ssh root@<server> "cd /root/my-docker-apps && ls releases/"
ssh root@<server> "cd /root/my-docker-apps && bash rollback.sh <timestamp>"
```

`rollback.sh` 会恢复对应版本的 jar 和前端并重建 `java-app`。数据库默认不自动回滚；需要时按脚本末尾提示单独恢复。

### 什么时候才需要登录服务器

- 修改 `.env`（密钥、数据库密码、Cloudflare token）
- 修改 `docker-compose.yml` 或 Nginx 配置
- 数据库迁移失败、容器 OOM、磁盘清理
- 首次配置或更换云服务

### 不要做的事

- 不要执行 `docker compose down`：会停掉 MySQL/Redis/Cloudflare Tunnel（数据不丢，但站点会中断）
- 不要删除 `mysql-data` / `redis-data` 卷
- 不要在服务器上跑 `mvn` / `npm`：2GB 内存会被构建过程打满

## 备份与恢复

沿用 `docs/ops/backup-restore.md`。生产环境：

- `DB_IN_CONTAINER=true`、`DB_CONTAINER=mysql-db`
- `BACKUP_ENCRYPTION=required`，配置 age 或 gpg 收件人
- 备份上传到 OSS，`S3_PREFIX=backups`
- cron 每日执行，本地保留 7 天

```cron
30 3 * * * cd /root/my-docker-apps && ./scripts/backup.sh >> /var/log/travelmapper-backup.log 2>&1
```

上线前和每次升级前各做一次恢复演练。

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 页面正常但所有接口 404 | Nginx `proxy_pass` 带了结尾斜杠，`/api` 前缀被剥掉 | `proxy_pass http://java-app:8080;`（不带 `/`） |
| AI 输出不是流式 / 中途断开 | 没关 `proxy_buffering` 或读超时太短 | 使用 `default.conf` 里的两个 SSE location |
| 应用启动报 `TRAVELMAPPER_AES_KEY is required` | `.env` 缺 AES key | `openssl rand -base64 32` 后填入并重建容器 |
| `mail sender reloaded` 后发码仍失败 | Resend 域名未验证（550） | Resend 后台完成 SPF/DKIM 验证 |
| 容器被重启且日志有 OOM | 内存超额 | `docker stats` 定位；优先迁 MySQL 到 RDS |
| 磁盘持续增长 | Docker json-file 日志 | compose 已设 `max-size=10m, max-file=3`；`docker system prune` |
| 上传图片 403 | OSS CORS 未配置 | OSS 控制台加站点域名的 PUT/GET 规则 |

## 验收清单

- [ ] `https://example.com` 打开前端，刷新任意路由不 404
- [ ] 注册能收到验证码，SMTP 日志无 `verification email send failed`
- [ ] 登录 / 后台配置页可访问，`/api/admin/**` 未登录返回 403
- [ ] AI 规划或文案润色有流式输出（SSE 正常）
- [ ] 上传图片成功后能在游记中显示（OSS 直传 + CORS）
- [ ] 分享页面 `/share/<token>` 可打开；`/s/<token>` 返回 JSON（分享 API）
- [ ] `docker compose ps` 全部 healthy
- [ ] `/actuator/*`、`/swagger-ui/*`、`/v3/api-docs/*` 对外 404（生产 profile 已禁用 springdoc，不要配置反代）
- [ ] 备份 cron 已执行且 OSS 上有加密文件
- [ ] 飞书告警「发送测试消息」成功

## 升级路径

1. 流量或数据量上来：MySQL 迁云 RDS，删除本机 mysql 容器，释放约 350MB 内存。
2. 仍紧张：服务器升级到 2C4G；java-app 堆可提到 1G。
3. 如果以后要上多实例：对象存储已解耦、Redis 可换云实例，但需要把会话/限流数据迁到共享 Redis，并给 Nginx 加上游负载均衡。