## Why

当前世界底图裁掉了南极洲（纬度只到 -55.8°），北极地区也只是一片海洋，用户无法在地图上看到或点亮极地足迹。用户希望南极洲与北极地区作为完整区域出现在世界地图上，交互方式与其他国家一致。

## What Changes

- **底图扩展**：`world.json` 保持原样；前端初始化时并行加载并合并 `world.json`、`antarctica.json`、`arctic.json`，再注册 ECharts 底图。底图范围扩展为 -90°~90°，主图整体缩放以容纳南极洲。
- **南极洲区域**：现有 `frontend/dist/antarctica.json` 使用南极方位投影，不是经纬度；通过一次性脚本逆投影为经纬度，输出 `frontend/public/antarctica.json`，作为 `Antarctica` feature 参与地图渲染。
- **北极地区**：新增 `frontend/public/arctic.json`，绘制 84°N–90°N 的完整极冠条带（上方无陆地，不遮挡格陵兰/俄罗斯/加拿大），作为 `Arctic` feature。
- **国家目录**：新增 `AQ`（南极洲，ISO 代码，大洲 `ANTARCTICA`）与 `X3`（北极地区，保留码，大洲 `NULL`），`ENABLED`，新增核心迁移 `V11__polar_regions.sql`。V10 已应用，不修改。
- **业务覆盖**：两个地区出现在地图、抽屉、国家下拉、时间线与记账；AI 国家攻略可用。不预置城市，规划/游记仍需管理员补充城市。
- **地图交互**：极地区域与普通国家一致，支持三态着色、悬浮计数、点击打开抽屉；无数据时显示灰色空状态。
- **质量保障**：极地数据独立为 `scripts/data/polar-regions.json`，由脚本生成 V11；扩展校验脚本检查 GeoJSON 覆盖、代码唯一与迁移同步；新增抽屉与目录集成测试。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `catalog/country-city`: 新增“极地地区覆盖”要求，规定南极洲与北极地区作为启用地区进入目录、代码与大洲取值、以及无城市时的行为。
- `map/overview`: 新增“极地区域地图展示”要求，规定底图包含南极洲与北极地区、纬度范围 -90°~90°、极地区域可点击并打开抽屉。

## Impact

- 前端：`MapView.vue` 加载并合并三份 GeoJSON；新增 `frontend/public/antarctica.json`、`frontend/public/arctic.json`；主图布局随纬度范围变化。
- 数据库：新增 `V11__polar_regions.sql`，插入 `AQ`、`X3`；无表结构变更。
- 脚本：新增 `scripts/data/polar-regions.json` 与极地迁移生成器；扩展 `scripts/check-world-names.mjs`。
- 测试：集成测试覆盖国家列表、极地抽屉与现有国家不受影响；手工验证地图渲染与点击。
- 文档：更新 README、本地开发说明与 OpenSpec 规格。