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

## 启动后端

```bash
set -a
. ./.env
set +a

cd backend
mvn spring-boot:run
```

Flyway 会自动建表并写入种子数据；`V4__more_cities.sql` 额外预置常用中国地级市与热门外国城市。首次启动后：

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

开发期 `MAIL_ENABLED=false`，验证/找回密码令牌会写入 `email_token` 表，不打印在日志中：

```sql
SELECT token, type, expire_at FROM email_token
WHERE email = 'you@example.com' ORDER BY id DESC;
```

前端 `/register` 可直接注册，注册后跳转 `/verify-email`。开发期 `MAIL_ENABLED=false` 时邮件不会发送，验证令牌写入 `email_token` 表，可查询后粘贴到验证页；验证成功后登录。普通用户访问 `/api/admin/**` 返回 403 `4002`。

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

开发服务器 `http://localhost:5173`，`/api` 自动代理到 `http://localhost:8080`。

- `/` 世界地图：三态着色、悬浮计数、点击国家打开旅行/城市抽屉。
- `/plan` AI 行程规划：生成、草稿微调（天/景点增删改排、跨天移动）、保存、行前准备/清单/官方链接。
- `/itineraries/:id` 已保存行程编辑：基本信息、景点/天 CRUD、重新生成与合并提示；地图抽屉可进入，保存/删除/重新生成后地图统计自动刷新。
- `/polish` AI 流式润色与取消。
- `/notes` 游记：TipTap 图文编辑器（粘贴/拖拽上传、压缩、直传）、保存并跳转详情。
- `/notes/:id` 游记详情/编辑：可见性、关联旅行、封面、分享管理、下架状态；也可在 `/notes` 按 ID 打开。
- `/poster/:id?` 游记海报：确保分享令牌、地图足迹 Canvas 连线、统计、二维码与 PNG 下载。
- `/expenses` 记账：新增/编辑/删除、筛选、分页、按国家/城市/旅行/日期汇总与图表。
- `/profile` 个人中心：昵称/头像、额度、卡密兑换、注销；`/forgot-password`、`/reset-password` 找回密码。
- 导航“管理”菜单（ADMIN）：用户、卡密、城市、官方链接、汇率、内容管理、AI 配置。
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

生产 profile（`SPRING_PROFILES_ACTIVE=prod`）默认关闭该身份头，必须等 change-02 的 JWT 才能访问后台。

## 对象存储配置

种子数据中的 `storage.access-key` / `storage.secret-key` 为空，存储 Provider 会保持“未配置”状态。通过后台配置写入真实值后即时热加载：

```bash
curl -X PUT http://localhost:8080/api/admin/config/oss \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' -H 'X-User-Role: ADMIN' \
  -d '{"provider":"minio","endpoint":"http://127.0.0.1:9000","bucket":"travelmapper","accessKey":"minioadmin","secretKey":"minioadmin","pathStyle":true}'
```

## 测试

单元测试不依赖 Docker：

```bash
cd backend
mvn -q -Dtest='AesCryptoServiceTest,SensitiveDataMaskerTest,SensitiveLogConverterTest,PageAndSortTest,ObjectKeyGeneratorTest,GuideLinkSanitizerTest,TimeoutSearchProviderTest,ChecklistItemTest,ErrorCodeTest' test
```

集成测试需要 MySQL 8 / Redis / S3 兼容服务。当前环境 Testcontainers 与 Docker 29 命名管道不兼容，使用外部容器模式：

```bash
docker run -d --name tm-mysql -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=travelmapper -e MYSQL_USER=travelmapper \
  -e MYSQL_PASSWORD=travelmapper -p 13306:3306 mysql:8.0
docker run -d --name tm-redis -p 16379:6379 redis:7-alpine redis-server --appendonly yes
docker run -d --name tm-s3mock -p 19090:9090 \
  -e COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS=travelmapper,travelmapper2 \
  adobe/s3mock:latest

mvn -q \
  -Dtest.containers.enabled=false \
  -Dtest.mysql.url='jdbc:mysql://127.0.0.1:13306/travelmapper?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
  -Dtest.mysql.username=travelmapper -Dtest.mysql.password=travelmapper \
  -Dtest.mysql.admin-username=root -Dtest.mysql.admin-password=root \
  -Dtest.redis.host=127.0.0.1 -Dtest.redis.port=16379 \
  -Dtest.object-storage.endpoint=http://127.0.0.1:19090 \
  -Dtest.object-storage.access-key=test -Dtest.object-storage.secret-key=test \
  test
```

S3Mock 不强制私有访问，私有桶用例会自动跳过。MinIO 镜像/Windows 二进制不可用时，可用 `scripts/storage/` 中的 SeaweedFS S3 网关完成私有桶验证（匿名 GET 返回 403），启动与测试命令见 `scripts/storage/README.md`。

浏览器 E2E（Playwright + 本机 Chrome，需要后端已启动）：

```bash
cd frontend
npm run test:e2e
```

详见 `frontend/e2e/README.md`。

性能基准（不计入测试套件，需要后端已启动）：

```bash
CLEANUP=1 bash scripts/perf/benchmark.sh
```

说明与最近一次结果见 `scripts/perf/README.md`、`docs/ops/performance.md`。
