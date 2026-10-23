# 性能基准

覆盖 change-05 的 `7.7 / 11.6`；最近一次结果与优化记录见 `docs/ops/performance.md`。

| 场景 | 接口 |
|---|---|
| 图片直传签名 | `POST /api/upload/presign` |
| 游记列表（地图抽屉） | `GET /api/map/country/JP` |
| 消费列表 | `GET /api/expenses?page=1&size=20` |
| 消费汇总（按国家） | `GET /api/expenses/summary?groupBy=country` |
| 消费汇总（按旅行） | `GET /api/expenses/summary?groupBy=trip` |

> API 未定义独立的 `GET /notes` 列表接口，因此“游记列表”使用地图国家抽屉响应，其中包含该国的游记列表。

## 前置条件

1. 后端已启动，并连接到 Docker 中的 MySQL（默认容器 `tm-mysql`）。
2. 已通过 `/api/admin/config/oss` 配置对象存储，否则图片直传签名场景会预检失败；也可以 `SKIP_PRESIGN=1` 跳过。
3. 种子管理员 `admin` 与 V1/V2 迁移存在（`seed_benchmark.sql` 复用 admin 的 BCrypt 密码创建 `perfuser`）。

## 运行

```bash
# 从仓库根目录
bash scripts/perf/benchmark.sh

# 跑完清理 perfuser 及其数据，避免污染测试库
CLEANUP=1 bash scripts/perf/benchmark.sh
```

默认写入 100 个旅行、500 篇游记、30000 条消费，全部属于 `perfuser`。脚本可重复执行，每次先清理该用户数据。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `API_BASE` | `http://localhost:8080` | 后端地址 |
| `ITERATIONS` | `200` | 计时样本数 |
| `WARMUP` | `20` | 预热请求数 |
| `THRESHOLD_MS` | `500` | P95 阈值 |
| `SKIP_SEED` | `0` | `1` 跳过数据写入 |
| `SKIP_PRESIGN` | `0` | `1` 跳过图片签名场景 |
| `CLEANUP` | `0` | `1` 结束时清理 perfuser 数据 |
| `PERF_ACCOUNT` / `PERF_PASSWORD` | `perfuser` / `Admin@123456` | 基准账号；密码需与 admin 当前密码一致 |
| `MYSQL_CONTAINER` | `tm-mysql` | MySQL 容器名 |
| `MYSQL_ROOT_PASSWORD` | `root` | MySQL root 密码 |
| `MYSQL_DATABASE` | `travelmapper` | 数据库名 |

## 说明

- 脚本串行发送请求，结果是单连接基线；并发压测请用 k6/JMeter 复用同一套种子数据。
- P95 超过 `THRESHOLD_MS`、出现非 2xx 响应或预检失败时，脚本以非 0 退出。
- 签名图片不参与本次基准（`POST /upload/presign` 只验证签名生成，不实际上传）。
- `html2canvas` 海报导出属于浏览器端性能，不在本脚本范围。
