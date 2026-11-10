## Context

见 `proposal.md`。当前约束：

- 后端为 Java 17 + Spring Boot 3.3.5 + Maven，测试类共约 61 个、268 个用例；`*IntegrationTest` 依赖 MySQL/Redis/S3，优先 Testcontainers，本地 Windows 因 Docker 29 命名管道问题使用外部容器模式（`docs/ops/local-development.md:177-197`）。
- `pom.xml` 未配置 Surefire 排除，`mvn test` 会同时跑集成测试；`ItineraryPerformanceIT` 通过 `-Dperf.enabled=true` 才执行。
- 前端为 Vue 3 + Vite 6，只有 Playwright E2E；`playwright.config.ts` 的 `webServer` 固定跑 `npm run dev`，`reuseExistingServer: true`。
- 仓库托管在 GitHub（`xzw268688/travel-mapper`），当前无任何 CI 配置；`deploy/release.sh` 本地构建并 `-DskipTests package`。
- 当前构建产物：入口 JS 2.86MB raw / 925KB gzip，CSS 54KB gzip，`world.json` 285KB gzip。

## Goals / Non-Goals

**Goals:**

- PR 上自动跑后端单元 + 集成测试、前端单元测试、类型检查、生产构建与体积预算。
- 用一个可复现的流程在 CI 中起服务并跑生产构建的 E2E（夜间 + 发布前）。
- 让“测试过没过”成为发布前的硬条件，而不是人工记忆。
- 为后续时区与性能 change 提供稳定的测试与预算基座。

**Non-Goals:**

- 不改变任何 API、数据库 schema 或用户可见行为。
- 不追求覆盖率百分比目标；只让覆盖率可见并逐步棘轮。
- 不引入浏览器矩阵（Firefox/WebKit）或移动端真机测试。
- 不在本 change 收紧体积预算到性能目标值；那属于性能 change。
- 不自动配置 GitHub 分支保护（需要仓库管理员权限），只提供工作流与文档。

## Decisions

### 1. CI 平台使用 GitHub Actions

仓库已在 GitHub，托管 runner 自带 Docker，可直接跑 Testcontainers。替代方案（GitLab CI、Jenkins、本地 Git hooks）都需要额外基础设施，收益不足。

工作流拆为三个：

- `ci.yml`：`pull_request` / `push` 到 `main`、`release/**`。Job A 后端单测；Job B 后端集成测试（Testcontainers）；Job C 前端单测、`vue-tsc`、`vite build`、体积预算。三者并行，`concurrency` 取消同分支旧运行。
- `e2e-nightly.yml`：每日定时 + `workflow_dispatch`。用 `docker compose` 起 MySQL/Redis/MinIO，构建并启动后端 jar（`SPRING_PROFILES_ACTIVE=local`，设置种子管理员密码、JWT secret、AES key），构建前端后 `vite preview` 启动生产构建，运行 Playwright。
- 发布前由 `release.sh` 查询 `ci.yml` 对当前 commit 的结果，而不是复制一套发布专用流程。

### 2. Maven 测试拆分为 Surefire 单元 / Failsafe 集成

- Surefire 排除 `**/*IntegrationTest.java`，`mvn test` 只跑单元测试（本地也更快）。
- Failsafe 在 `mvn verify` 阶段包含 `**/*IntegrationTest.java`；`ItineraryPerformanceIT` 显式排除，保持 `-Dperf.enabled=true` 时手工执行。
- CI 分别运行 `mvn -DskipITs=false test` 与 `mvn verify`（或直接 `mvn verify`，但拆 job 便于定位）。本地文档同步改成“单测 `mvn test`，集成 `mvn verify`；外部容器模式命令不变”。

替代方案：保持 `mvn test` 跑全部，用 `-Dtest`/标签筛选。缺点是 CI 命令脆弱、开发者无法快速只跑单测，因此不选。

### 3. 前端单测：Vitest + Vue Test Utils + happy-dom

- 新增 `vitest.config.ts`（复用 `@vitejs/plugin-vue`，`environment: 'happy-dom'`），避免再引入 Jest 的 ESM 转译配置。
- 脚本：`test:unit`、`test:unit:coverage`、`typecheck`；`build` 改为 `npm run typecheck && vite build`。
- 覆盖率用 `@vitest/coverage-v8`，只输出报告、不设阈值。
- 首批测试目标（均为现有代码）：`src/utils/authStorage.ts`、`email.ts`、`share.ts`、`noteContent.ts`，`src/stores/auth.ts`，`src/router/index.ts` 的守卫逻辑，`src/api/http.ts` 拦截器。日期工具测试在时区 change 创建 `utils/datetime.ts` 时一并补齐。
- 对难以直接测试的路由守卫，允许把纯逻辑抽成导出函数（不改变运行行为）。

### 4. 体积预算用 Vite manifest + 自定义脚本

- `vite.config.ts` 开启 `build.manifest: true`（输出 `.vite/manifest.json`）。
- 新增 `frontend/scripts/check-bundle-budget.mjs`：
  - 从 manifest 解析 `index.html` 入口 chunk，沿 `imports`（静态依赖）求和；
  - 首屏 = 入口 JS + 入口 CSS + `world.json`（gzip）；性能 change 后首屏还需沿 `src/views/MapView.vue` 的静态 imports 计入地图路由 chunk；
  - 用 `zlib.gzipSync`（level 9）测量；输出每个 chunk 的 raw/gzip 明细与失败项，预算不通过时以非零码退出。
- 基线预算（当前实测 + 余量）：入口 JS gzip ≤ 950KB、任意单 JS chunk gzip ≤ 950KB、首屏合计 gzip ≤ 1.4MB。
- 不选 `size-limit`：它按 glob 匹配文件，无法表达“入口 + 指定路由的静态依赖”，在路由懒加载后语义会失真。

### 5. E2E 在 CI 中测生产构建

- `vite.config.ts` 增加 `preview.proxy`，与 `server.proxy` 使用同一组 `/api`、`/s/` 代理配置；否则 `vite preview` 无法访问后端。
- `playwright.config.ts` 的 `webServer.command` 改为由环境变量选择：CI 用 `npm run preview -- --host 127.0.0.1 --port 5173`（构建步骤在前），本地默认仍用 `npm run dev`。`reuseExistingServer` 在 CI 为 `false`。
- CI 中 `channel` 不指定 `chrome`，改用 `npx playwright install --with-deps chromium`；本地保持 `channel: 'chrome'`，避免额外下载。
- 夜间任务使用现有 `global-setup.ts` 的 API 种子逻辑；CI 提供 `E2E_ADMIN_ACCOUNT/PASSWORD`、`E2E_BACKEND_LOG`，让验证码用例从后端日志读取验证码，而不是跳过。

### 6. 发布门禁：`release.sh` 查询 CI 结果

- 新增函数：`gh run list --workflow=ci.yml --commit "$(git rev-parse HEAD)" --limit 1 --json status,conclusion`。
- 仅当存在且 `status=completed`、`conclusion=success` 时继续；否则打印提示并退出。
- 提供显式逃生开关 `SKIP_CI_CHECK=1`，会打印醒目警告（用于 GitHub 故障或紧急回滚），但不作为默认路径。
- 前置检查 `gh` 是否安装并已登录；不满足时直接失败，避免静默跳过。

## Risks / Trade-offs

- [Testcontainers 在 GitHub runner 上首次拉镜像慢或偶发失败] → 固定镜像版本、缓存 Maven/npm、集成 job 独立超时；保留外部容器模式作为本地替代路径。
- [`mvn test` 语义从“全部测试”变为“仅单元测试”] → 更新 `docs/ops/local-development.md` 与 `doc-code-map.md` 的验证命令，CI 明确运行两段。
- [E2E 夜间任务依赖后端启动、种子账号与验证码日志，链路长] → 增加健康检查轮询与失败日志归档；E2E 不阻塞 PR，先观察稳定性，再考虑提升为 PR 门禁。
- [体积预算脚本依赖 manifest 结构，路由拆分后首屏定义可能失真] → 脚本输出明细并支持 `--report`；性能 change 中同步更新首屏解析逻辑与预算，用实测校准。
- [发布门禁依赖 GitHub 可用性与本地 `gh` 登录] → 失败时给出明确提示与 `SKIP_CI_CHECK=1` 逃生口；不静默放行。
- [happy-dom 与真实浏览器存在差异] → 只把 DOM 依赖重的行为留给 E2E，单测聚焦纯逻辑与状态管理。

## Migration Plan

1. 合并本 change 后，首次在 PR 上观察三个 job 的结果，按需调整超时与缓存。
2. 在 GitHub 分支保护中把 `ci.yml` 设为 `main`、`release/*` 的必需检查（人工操作，文档说明）。
3. 发布流程改为：推送 → CI 绿灯 → `REMOTE=... ./deploy/release.sh`。回滚时可用 `SKIP_CI_CHECK=1` 紧急发布。
4. 夜间 E2E 连续稳定运行若干天后再评估是否纳入 PR 门禁。

## Open Questions

无。体积基线会由首次 CI 实跑确认；若与本地测量有偏差，只调整阈值数值，不改变预算维度。