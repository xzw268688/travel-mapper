## 1. 前端单测基座

- [x] 1.1 在 `frontend/package.json` 增加 `vitest`、`@vue/test-utils`、`happy-dom`、`@vitest/coverage-v8`，新增 `test:unit`、`test:unit:coverage`、`typecheck` 脚本并更新 lockfile；验证 `npm ci` 与 `npm run test:unit` 可执行
- [x] 1.2 新建 `frontend/vitest.config.ts`（Vue 插件 + happy-dom + `src/**/*.test.ts`）；验证一个样例测试能通过且不会误收集 `e2e/**`
- [x] 1.3 为 `src/utils/authStorage.ts`、`email.ts`、`share.ts`、`noteContent.ts` 补测试；验证 `npm run test:unit` 全绿
- [x] 1.4 为 `src/stores/auth.ts`、`src/api/http.ts` 拦截器补测试（token 注入、401 清理与跳转、业务 message 优先）；必要时抽出可测纯函数；验证测试覆盖上述分支
- [x] 1.5 为 `src/router/index.ts` 守卫补测试（未登录跳登录、管理员权限、二次验证引导）；必要时抽出守卫函数；验证各分支断言通过
- [x] 1.6 把 `build` 改为 `npm run typecheck && vite build`；验证 `npm run build` 成功且类型错误会让构建失败

## 2. 体积预算

- [x] 2.1 在 `frontend/vite.config.ts` 开启 `build.manifest`；验证 `npm run build` 后存在 `dist/.vite/manifest.json`
- [x] 2.2 新增 `frontend/scripts/check-bundle-budget.mjs`：解析 manifest 的入口与静态 imports，gzip 统计入口 JS、单 chunk、首屏（入口 JS+CSS+`world.json`），失败非零退出并打印明细；验证脚本可输出当前基线报告
- [x] 2.3 写入基线预算（入口 JS ≤ 950KB gzip、单 JS chunk ≤ 950KB、首屏 ≤ 1.4MB gzip）并新增 `budget` 脚本；验证临时调低阈值时脚本失败、恢复正常后通过

## 3. 后端测试拆分

- [x] 3.1 在 `backend/pom.xml` 配置 Surefire 排除 `**/*IntegrationTest.java`、Failsafe 在 `verify` 阶段包含集成测试并排除 `ItineraryPerformanceIT`；验证 `mvn test` 不再启动容器、`mvn verify` 会执行集成测试
- [x] 3.2 用外部容器模式（`-Dtest.containers.enabled=false`）跑一轮 `mvn verify`，确认拆分后命令与现有文档一致；验证集成测试全绿
- [x] 3.3 更新 `docs/ops/local-development.md` 的测试章节（单测/集成/外部容器/性能）、`docs/architecture/doc-code-map.md` 的验证命令；验证命令与实际执行一致

## 4. E2E 生产构建模式

- [x] 4.1 在 `frontend/vite.config.ts` 增加 `preview.proxy`，复用 `server.proxy` 的 `/api`、`/s/`、Swagger 代理；验证 `npm run build && npm run preview` 下接口可用
- [x] 4.2 改造 `frontend/playwright.config.ts`：`webServer.command` 由环境变量切换 preview/dev，CI 下 `reuseExistingServer=false`，CI 使用 Playwright 自带 Chromium；验证本地 dev 与 `E2E_PREVIEW=1` 两种模式都能跑通现有用例
- [x] 4.3 新增 `.github/workflows/e2e-nightly.yml`：定时 + 手动触发，`docker compose` 起 MySQL/Redis/MinIO，构建并启动后端（设置种子管理员、JWT、AES 环境变量），构建前端后以 preview 启动并运行 Playwright；验证一次 `workflow_dispatch` 全绿并归档失败截图

## 5. GitHub Actions PR 门禁

- [x] 5.1 新增 `.github/workflows/ci.yml`：后端单测、后端集成测试（Testcontainers）、前端单测 + 类型检查 + 构建 + 体积预算三个并行 job，触发条件为 PR 与 `main`/`release/**` push，启用缓存与 concurrency；验证 PR 上三个 job 全绿
- [ ] 5.2 在真实 PR 中故意制造一个单测失败与一次预算超限，确认对应 job 正确失败并给出可读错误；验证后恢复（暂缓：留待后续 PR 验证）
- [x] 5.3 在 `docs/ops/local-development.md` 记录 CI 触发、必需检查与夜间任务说明，并给出分支保护配置步骤；验证文档与工作流名称一致

## 6. 发布门禁

- [x] 6.1 在 `deploy/release.sh` 增加 `gh` 可用性检查与当前 commit 的 `ci.yml` 结果校验，失败时终止并提示；保留 `SKIP_CI_CHECK=1` 逃生开关并打印警告；验证无运行/失败/成功三种情况下的行为
- [x] 6.2 更新 `docs/ops/local-development.md` 或部署文档，说明发布前必须 CI 绿灯及逃生开关；验证文档命令可直接复制执行

## 7. 整体验证

- [x] 7.1 运行 `openspec validate establish-test-and-ci-baseline --strict` 通过
- [x] 7.2 运行 `cd frontend && npm run test:unit && npm run build && node scripts/check-bundle-budget.mjs` 全绿
- [x] 7.3 运行 `cd backend && mvn test` 与外部容器模式 `mvn verify` 全绿
- [ ] 7.4 在 PR 上确认 `ci.yml` 全绿，手动触发 `e2e-nightly.yml` 全绿，并记录首轮体积基线数据（`ci.yml` 已在 commit `0b05610` 全绿；夜间 E2E 待手动触发）