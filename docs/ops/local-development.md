# 本地开发

## 前置条件

- JDK 17（Maven 必须使用 JDK 17；本机 `java` 可能是 8，但 `mvn -version` 应显示 17）
- Maven 3.8+
- Docker（MySQL 8.0 / Redis 7 / 对象存储）
- 不要使用本机 MySQL 5.7：DDL 使用 `utf8mb4_0900_ai_ci`，仅 MySQL 8.0 支持

## 启动依赖

```bash
cp .env.example .env
# 替换 TRAVELMAPPER_AES_KEY
openssl rand -base64 32

docker compose up -d mysql redis minio minio-init
```

如果当前镜像源无法拉取 MinIO：

```bash
docker compose --profile s3mock up -d s3mock
```

然后在后台把 `storage.endpoint` 配成 `http://127.0.0.1:19090`，并填写对象存储 Key（S3Mock 可用 `test/test`）。

## 时区约定（重要）

全系统固定北京时间：数据库存北京时间墙钟（无时区 `DATETIME`），后端所有“现在/今天”由固定 `Asia/Shanghai` 的 `TimeProvider` 计算，前端只做格式化不转换时区。

本地环境需要三处一致：

- MySQL 容器：`TZ=Asia/Shanghai` 且 `--default-time-zone=+08:00`（见下方启动依赖与测试章节）。
- JDBC：URL 使用 `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true`，强制会话时区，避免容器默认 UTC。
- JVM：建议设置 `TZ=Asia/Shanghai` 或 `-Duser.timezone=Asia/Shanghai`；业务代码本身不依赖该默认值。

如果本地 MySQL 是用旧命令手动创建的（没有时区参数），需要删除容器后按新命令重建。开发库中已有的混时区旧数据不做迁移，可直接清库重跑种子数据。

## 启动后端

```bash
set -a
. ./.env
set +a

cd backend
mvn spring-boot:run
```

Flyway 会自动建表并写入种子数据；`V4__more_cities.sql` 额外预置常用中国地级市与热门外国城市；`V10__all_world_countries.sql` 预置 `world.json` 中全部有名称的国家/地区（含 `X1` 锡亚琴冰川、`X2` 北塞浦路斯两个自定义区域），使地图上每个区域都能打开国家抽屉；`V11__polar_regions.sql` 补充南极洲（`AQ`）与北极地区（`X3`）；前端合并 `antarctica.json` 把底图扩展到 -90°~90°，主图整体缩放，北极地区则以地图顶部的可点击点标记展示。注意：新增国家与极地地区默认没有城市，规划行程与写游记前需要管理员在后台补充城市；记账的城市可选，可直接使用。旅行状态会按日期与游记/消费自动推导（计划中/进行中/已完成），手动标记优先；没有任何内容的旅行会被自动清理。首次启动后：

- 健康检查：`http://localhost:8080/actuator/health`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 预置管理员：`admin` / `Admin@123456`（生产首次部署必须修改）

`.env` 必须包含 `TRAVELMAPPER_JWT_SECRET`（至少 32 字节）。change-02 起认证使用 JWT：

```bash
# 登录（管理员）
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"Admin@123456"}'

# 使用返回的 token
curl -H 'Authorization: Bearer <token>' http://localhost:8080/api/user/profile
```

开发期 `MAIL_ENABLED=false`，注册验证与找回密码的 6 位验证码只打印在后端日志中，不写入数据库：

```
[auth-dev] scene=VERIFY email=you@example.com code=123456 expiresIn=600s
```

前端 `/register` 在邮箱下方直接填 6 位验证码，注册成功自动登录。注册与找回密码使用邮箱验证码；开发期 `MAIL_ENABLED=false` 时邮件不会发送，验证码只打印在后端日志（`[auth-dev] scene=... email=... code=...`），按日志填写即可。管理员后台入口为 `/admin/login`。普通用户访问 `/api/admin/**` 返回 403 `4002`。

## 邮件 SMTP 配置

开发期默认 `MAIL_ENABLED=false`，验证码只打印在后端日志。需要真实发信时可在后台「配置 → 邮件 SMTP」填写主机、端口、账号、密码、发件人并启用，保存后立即热加载，无需重启；密码留空表示保留原值。未保存过 DB 配置时使用环境变量 `MAIL_*` 作为默认值。

### Resend 示例

| 字段 | 值 |
|---|---|
| 主机 | `smtp.resend.com` |
| 端口 | `587`（STARTTLS） |
| 用户名 | `resend` |
| 密码 | Resend API Key（`re_` 开头，不是登录密码） |
| 发件人 | 已在 Resend 验证的域名下的地址，如 `noreply@example.com` |
| 认证 / STARTTLS | 均开启 |

> 当前实现只支持 STARTTLS（587/25），未开启 `mail.smtp.ssl.enable`，**不支持 465 隐式 TLS**。
> Resend 要求发件域名在后台完成 SPF/DKIM 验证；未验证时 SMTP 认证能通过，但发信会被服务端拒绝（通常 550）。

### 发信超时与排查

- 每次发信单独建连：连接、读、写各 10 秒超时，验证码/重置密码接口最多同步等待 20 秒。超时返回 `emailSent=false`，但邮件可能已实际投递，重试前先确认收件箱。
- 后端显式把 `mail.smtp.localhost` 设为发件人域名作为 EHLO 域名。未显式设置时 JavaMail 会调用 `InetAddress.getLocalHost().getCanonicalHostName()` 做反向 DNS，部分网络下这一步会卡 10 秒以上，直接把正常发送拖成超时。
- 排查入口：后端日志搜 `mail sender reloaded host=... port=...`（确认热加载生效）和 `verification email send failed`（含 SMTP 服务端返回码与完整异常栈）。
- 前端注册 / 找回密码会在校验与提交前对邮箱做 NFKC 归一化并去除空白，中文输入法下的全角 `＠`、`．` 或误输入的空格不会触发「请输入正确的邮箱」。

## LLM / 搜索配置

种子配置中 `llm.provider=qwen`、`search.provider=tavily`，API Key 为空。填写后即时热加载：

```bash
curl -X PUT http://localhost:8080/api/admin/config/llm \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H 'X-User-Role: ADMIN' \
  -d '{"provider":"qwen","baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1","model":"qwen-max","apiKey":"<key>","timeoutSeconds":30,"maxRetry":1}'

curl -X PUT http://localhost:8080/api/admin/config/search \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H 'X-User-Role: ADMIN' \
  -d '{"provider":"tavily","apiKey":"<key>","dailyLimit":50,"timeoutSeconds":5,"cacheHours":24}'
```

SSE 的 Nginx 配置见 `docs/ops/nginx-sse.conf`（关闭缓冲、读超时 120 秒）。测试环境使用假 LLM/搜索 Provider，不会访问外部服务。

## 前端

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

开发服务器 `http://localhost:5173`，`/api`、`/swagger-ui`、`/v3/api-docs` 默认代理到 `http://localhost:8080`；可用 `VITE_API_TARGET=http://localhost:8090` 指向另一个后端实例。后台「监控 → 接口文档」的 Swagger UI 外链因此可直接在 5173 同源打开；修改 `vite.config.ts` 的代理后需要重启 dev server。

- `/` 世界地图：三态着色、悬浮计数、点击国家打开旅行/城市抽屉。
- `/plan` 行程规划：AI 生成 / 手动规划切换、草稿微调（天/景点增删改排、跨天移动）、保存、清单/官方链接。
- `/itineraries/:id` 已保存行程编辑：基本信息、景点/天 CRUD、重新生成与合并提示；地图抽屉可进入，保存/删除/重新生成后地图统计自动刷新。
- `/notes` 游记：TipTap 图文/视频编辑器（粘贴/拖拽上传、压缩、直传）、编辑器内选中文本 AI 流式润色、保存并跳转详情。
- `/notes/:id` 游记详情/编辑：可见性、关联旅行、封面、分享管理、下架状态；也可在 `/notes` 按 ID 打开。
- `/poster/:id?` 游记海报：确保分享令牌、地图足迹 Canvas 连线、统计、二维码与 PNG 下载。
- `/expenses` 记账：新增/编辑/删除、筛选、分页、快捷录入与金额表达式、「明细 / 按日期」切换、月度合计、按国家/城市/旅行/日期汇总与图表。
- `/profile` 个人中心：昵称/头像、额度、卡密兑换、注销；`/forgot-password`、`/reset-password` 找回密码。
- 导航“管理”菜单（ADMIN）：用户、卡密、城市、内容管理、反馈、举报、汇率、通知公告与日志管理（操作日志 / 登录日志）；另有「配置」与「监控」菜单（在线用户、服务/数据库/缓存/存储监控、接口文档）。地图首页会展示当前生效公告的横幅。
- `/admin/settings` 管理员 LLM/搜索配置：填写 OpenAI 兼容 Base URL、模型与 API Key；Key 留空会保留原 Key。生成行程提示 `2012` 或 HTTP 503 时先检查这里。
- `/share/:token` 公开只读分享页；分享链接与海报二维码均指向该路由。后端 API 返回的 `share.url` 仍是 `/s/{token}`（契约字段），前端展示与二维码统一使用 `/share/{token}`。
- 开发环境预置管理员 `admin / Admin@123456`；管理员登录后导航栏显示“汇率”“内容管理”。
- 游记正文中的图片以相对路径 `/objects/{objectKey}` 存储，后端清洗会保留相对链接；展示与编辑时替换为 600 秒签名 URL，保存前再还原为对象 Key。
- 海报由 html2canvas 抓取包括签名图片在内的 DOM，对象存储必须允许前端域名跨域 GET（`Access-Control-Allow-Origin`），否则图片会导致截图失败。
- 地图默认加载本地 `frontend/public/world.json`，可由 `VITE_WORLD_GEO_URL` 覆盖；上线前必须按 SRS 7.3 完成底图合规确认。加载失败时降级为国家列表，列出全部国家（灰色国家也可点击查看详情）。
- 生产构建：`npm run build`（已通过 `vue-tsc --noEmit`）。移动端手势已启用 ECharts roam，仍需真机验收。

## 配置中心（开发期身份头）

change-01 尚未包含 JWT，后台接口通过临时开发头识别管理员；change-02 会替换为 JWT。

```bash
curl -H 'X-User-Id: 1' -H 'X-User-Role: ADMIN' \
  http://localhost:8080/api/admin/config/llm
```

普通用户：

```bash
curl -H 'X-User-Id: 2' -H 'X-User-Role: USER' \
  http://localhost:8080/api/admin/config/llm
# => HTTP 403, code 4002
```

生产 profile（`SPRING_PROFILES_ACTIVE=prod`）默认关闭该身份头，必须使用 JWT 访问后台。
如果开启 `admin-stub-enabled` 却没有同时设置 `allow-insecure-dev-stub=true`，应用会拒绝启动。
生产环境必须设置 `TRAVELMAPPER_ADMIN_PASSWORD`，否则种子管理员默认密码会被启动校验拒绝。

## 对象存储配置

种子数据中的 `storage.access-key` / `storage.secret-key` 为空，存储 Provider 会保持“未配置”状态。通过后台配置写入真实值后即时热加载：

```bash
curl -X PUT http://localhost:8080/api/admin/config/oss \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H 'X-User-Role: ADMIN' \
  -d '{"provider":"minio","endpoint":"http://127.0.0.1:9000","bucket":"travelmapper","accessKey":"minioadmin","secretKey":"minioadmin","pathStyle":true}'
```

## 测试

后端测试按 JUnit 标签拆分：

- `mvn test`：只跑单元测试，不依赖 Docker。所有集成测试继承 `AbstractIntegrationTest`，统一带 `@Tag("integration")`，不会被执行。
- `mvn verify`：先跑单元测试，再由 Failsafe 执行集成测试。默认使用 Testcontainers；Windows 下 Testcontainers 与 Docker 29 命名管道不兼容时，使用下方外部容器模式。

```bash
cd backend
mvn test
```

集成测试需要 MySQL 8 / Redis / S3 兼容服务。外部容器模式：

```bash
docker run -d --name tm-mysql -e TZ=Asia/Shanghai \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=travelmapper -e MYSQL_USER=travelmapper \
  -e MYSQL_PASSWORD=travelmapper -p 13306:3306 mysql:8.0 \
  --default-time-zone=+08:00
docker run -d --name tm-redis -p 16379:6379 redis:7-alpine redis-server --appendonly yes
docker run -d --name tm-s3mock -p 19090:9090 \
  -e COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS=travelmapper,travelmapper2 \
  adobe/s3mock:latest

mvn -q \
  -Dtest.containers.enabled=false \
  -Dtest.mysql.url='jdbc:mysql://127.0.0.1:13306/travelmapper?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true' \
  -Dtest.mysql.username=travelmapper -Dtest.mysql.password=travelmapper \
  -Dtest.mysql.admin-username=root -Dtest.mysql.admin-password=root \
  -Dtest.redis.host=127.0.0.1 -Dtest.redis.port=16379 \
  -Dtest.object-storage.endpoint=http://127.0.0.1:19090 \
  -Dtest.object-storage.access-key=test -Dtest.object-storage.secret-key=test \
  verify
```

S3Mock 不强制私有访问，私有桶用例会自动跳过。MinIO 镜像/Windows 二进制不可用时，可用 `scripts/storage/` 中的 SeaweedFS S3 网关完成私有桶验证（匿名 GET 返回 403），启动与测试命令见 `scripts/storage/README.md`。

性能测试默认不执行，需要显式开启：

```bash
cd backend
mvn -Dperf.enabled=true -Dtest=ItineraryPerformanceIT test
```

前端单元测试与体积预算：

```bash
cd frontend
npm run test:unit
npm run test:unit:coverage
npm run build
npm run budget
```

浏览器 E2E（Playwright，需要后端已启动）：

```bash
cd frontend
E2E_BACKEND_LOG='D:/path/to/backend.log' npm run test:e2e        # 开发服务器
E2E_BACKEND_LOG='D:/path/to/backend.log' E2E_PREVIEW=1 npm run test:e2e  # 生产构建（需先 npm run build）
```

`E2E_BACKEND_LOG` 指向后端日志文件时，注册/找回密码用例会从中读取 `[auth-dev] ... code=...`；未设置时跳过这些用例。`E2E_PREVIEW=1` 时 Playwright 使用 `vite preview` 托管的 `dist`，不会启动 dev server。

详见 `frontend/e2e/README.md`。

## CI 与分支保护

仓库使用 GitHub Actions，工作流位于 `.github/workflows/`：

- `ci.yml`：在 PR 与推送 `main`、`release/**` 时运行。
  - `Backend unit tests`：`mvn test`（只跑单元测试）。
  - `Backend integration tests`：`mvn verify`（Testcontainers；GitHub 托管 runner 自带 Docker）。
  - `Timezone matrix (UTC host and DB)`：`TZ=UTC` + `-Duser.timezone=UTC` + MySQL 默认 UTC，运行时区核心集成测试。
  - `Frontend checks`：`npm ci`、Vitest 覆盖率、`vue-tsc` + 生产构建、体积预算。
  - `CI gate`：汇总以上三个 job，任一失败则整体失败；建议作为分支保护的必需检查。
- `e2e-nightly.yml`：每日 02:17（北京时间）与手动触发。启动 MySQL/Redis/MinIO，构建后端 jar 与前端生产包，以 `vite preview` 运行 Playwright；失败时上传 `playwright-report`、`test-results` 与后端日志。

PR 合并前应确保 `CI gate` 绿灯。首次配置分支保护：GitHub 仓库 → Settings → Branches → Add branch protection rule，对 `main` 与 `release/*` 勾选 “Require status checks to pass before merging”，并选择 `CI gate`。

## 发布

`deploy/release.sh` 在构建前会检查当前 commit 的 `ci.yml` 结果：

```bash
REMOTE=root@your-server ./deploy/release.sh
```

- 只有当前 commit 存在 `ci.yml` 运行且结论为 success 时才继续。
- 需要本机安装并登录 GitHub CLI（`gh auth login`）。
- GitHub 故障或紧急回滚时可显式跳过校验（会打印警告）：

```bash
SKIP_CI_CHECK=1 REMOTE=root@your-server ./deploy/release.sh
```

旅行归组重算（一次性维护，会先备份数据库）：

```bash
API_BASE=http://localhost:8080 bash scripts/maintenance/recluster-trips.sh
```

性能基准（不计入测试套件，需要后端已启动）：

```bash
CLEANUP=1 bash scripts/perf/benchmark.sh
```

说明与最近一次结果见 `scripts/perf/README.md`、`docs/ops/performance.md`。
