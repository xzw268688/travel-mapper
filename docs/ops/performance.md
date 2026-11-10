# 性能基准记录

对应 change-05 的 `7.7 / 11.6`。复现方式见 `scripts/perf/README.md`。

## 环境

- 日期：2026-10-22
- 本机：Windows 11，JDK 17，Spring Boot 单实例（`mvn spring-boot:run`）
- 外部容器：MySQL 8.0（`tm-mysql:13306`）、Redis 7（`tm-redis:16379`）、S3Mock（`tm-s3mock:19090`）
- 数据：`perfuser` 名下 100 个旅行、500 篇游记、30000 条消费
- 请求：串行，200 个计时样本，20 次预热，`THRESHOLD_MS=500`

## 结果

| 场景 | 接口 | P50 | P95 | Max | 结论 |
|---|---|---|---|---|---|
| 图片直传签名 | `POST /api/upload/presign` | 6.0ms | 19.2ms | 49.9ms | OK |
| 游记列表（地图抽屉） | `GET /api/map/country/JP` | 106.5ms | 146.5ms | 260.5ms | OK |
| 消费列表 | `GET /api/expenses?page=1&size=20` | 74.5ms | 103.6ms | 209.2ms | OK |
| 消费汇总（按国家） | `GET /api/expenses/summary?groupBy=country` | 63.2ms | 75.7ms | 99.8ms | OK |
| 消费汇总（按旅行） | `GET /api/expenses/summary?groupBy=trip` | 66.1ms | 83.2ms | 95.7ms | OK |

全部场景 P95 < 500ms。

## 本轮优化

首次实测发现按国家汇总需要 **28.5 秒**：`ExpenseService.summary` 把 3 万条消费全部加载到应用层，并逐行查询国家/城市名称（N+1）。

改为：

- `ExpenseMapper.selectSummaryGroups`：按“分组维度 + 分类 + 币种”做 SQL 聚合，只返回少量汇总行；维度列来自服务端白名单，避免拼接注入。
- `ExpenseMapper.selectUnconfiguredCurrencies`：单独用 `SELECT DISTINCT` 找未配置汇率的币种。
- 国家/城市名称按分组缓存，避免逐行查目录。

优化后同一接口 P95 为 **75.7ms**。

## change-04：行程接口（MockMvc 层）

日期：2026-10-23。使用 `ItineraryPerformanceIT` + FakeLlmProvider，100 个样本、10 次预热，不包含真实网络与 LLM 延迟。

```bash
cd backend
mvn -Dtest=ItineraryPerformanceIT -Dperf.enabled=true test \
  -Dtest.containers.enabled=false \
  -Dtest.mysql.url='jdbc:mysql://127.0.0.1:13306/travelmapper?...' \
  -Dtest.mysql.username=travelmapper -Dtest.mysql.password=travelmapper \
  -Dtest.mysql.admin-username=root -Dtest.mysql.admin-password=root \
  -Dtest.redis.host=127.0.0.1 -Dtest.redis.port=16379
```

| 场景 | P50 | P95 | Max | 阈值 |
|---|---|---|---|---|
| 生成 | 34.0ms | 49.6ms | 56.6ms | 500ms |
| 保存 | 25.4ms | 35.6ms | 45.7ms | 500ms |
| 手动景点-新增 | 25.7ms | 30.7ms | 39.9ms | 300ms |
| 手动景点-编辑 | 36.1ms | 49.4ms | 79.5ms | 300ms |
| 手动景点-移动 | 36.6ms | 46.8ms | 58.0ms | 300ms |
| 天-新增 | 31.8ms | 37.9ms | 48.4ms | 500ms |
| 天-修改 | 35.7ms | 44.2ms | 53.3ms | 500ms |
| 天-删除 | 31.4ms | 39.6ms | 71.7ms | 500ms |
| 清单-读取 | 3.0ms | 3.8ms | 5.3ms | 500ms |
| 清单-保存 | 7.7ms | 10.7ms | 17.2ms | 500ms |

全部达标。该测试默认不执行（类名以 `IT` 结尾且需 `-Dperf.enabled=true`），不会污染常规测试套件。

## 前端体积基线（2026-11-10）

构建命令：`cd frontend && npm run build && npm run budget`。体积为 gzip（level 9），由 `scripts/check-bundle-budget.mjs` 统计。

| 指标 | 实测 | 预算 |
|---|---|---|
| 入口 JS 合计 | 159.1 KB | ≤ 400 KB |
| 首屏 JS+CSS | 377.4 KB | — |
| 首屏合计（含地图数据） | 652.1 KB | ≤ 800 KB |
| 最大 JS chunk | 180.5 KB（ECharts） | ≤ 600 KB |

- 入口未包含 ECharts / TipTap：ECharts 仅在 `/` 地图路由加载，RichTextEditor（106.3 KB）仅在游记编辑路由加载。
- html2canvas 46.7 KB 仅在海报路由加载；`axios` 19.0 KB、`vue` 62.0 KB 为入口共享依赖。
- 世界地图 GeoJSON 274.7 KB gzip，与页面 API 并发加载，失败时降级为国家列表。

## 说明

- 这是单机串行基线，不代表生产并发能力；上线前建议用 k6/JMeter 基于同一套种子数据做并发压测。
- API 未定义独立的 `GET /notes` 列表接口，因此“游记列表”使用地图国家抽屉响应（其中包含该国游记）。
- 图片场景只验证预签名生成，不包含实际上传与 CDN 回源。
- 基准数据在脚本结束时用 `CLEANUP=1` 清理，不会污染开发库。
- 行程生成基准使用 FakeLlmProvider，只衡量后端编排开销；真实模型首字与总耗时取决于供应商，需在生产前单独联调。
