# Catalog Country-city Specification

## Purpose

提供权威的国家与城市字典，统一城市选择、展示命名和停用规则，确保行程、游记与记账都引用同一套地理数据，历史数据在停用后仍可正确展示。

## Requirements

### Requirement: Country listing
系统 MUST 提供启用国家的列表，按 `sort_order` 排序，返回 `code`、`nameZh`、`nameEn`、`continent`；停用国家 MUST NOT 出现在列表中。其他接口的 `countryName` MUST 由服务端从国家字典读取，不得依赖前端传参。

#### Scenario: Only enabled countries are returned
- **WHEN** 前端请求国家列表
- **THEN** 响应只包含 `status=ENABLED` 的国家，并按 `sort_order` 排序

#### Scenario: Country name is server-resolved
- **WHEN** 任意接口需要返回国家名
- **THEN** 名称来自 `country` 字典，而不是客户端提交的文本

### Requirement: City listing by country
系统 MUST 按国家返回 `status=ENABLED` 的城市，返回 `cityCode`、`name`、`level` 和 `displayName`；`displayName` MUST 为“城市名（国家名）”；某国没有启用城市时 MUST 返回空数组，而不是错误。

#### Scenario: Enabled cities are listed
- **WHEN** 用户请求日本的城市列表
- **THEN** 只返回日本已启用城市，`displayName` 形如“东京（日本）”

#### Scenario: Empty city list is a valid response
- **WHEN** 某国没有任何启用城市
- **THEN** 接口返回空数组，前端可提示“暂无可选城市，请联系管理员补充”

### Requirement: City reference validation
行程和游记 MUST 同时选择国家和城市；记账 MUST 选择国家，城市可选；新建或编辑数据时，城市 MUST 存在且为 `ENABLED`；城市所属国家 MUST 与数据国家一致。

#### Scenario: Missing city is rejected
- **WHEN** 创建行程或游记时没有选择城市
- **THEN** 系统返回 `2001`，不保存数据

#### Scenario: Cross-country city is rejected
- **WHEN** 提交的国家是日本、城市却属于泰国
- **THEN** 系统返回 `2002`，不保存数据

#### Scenario: Disabled city cannot be newly selected
- **WHEN** 用户尝试用已停用城市创建新行程、游记或消费
- **THEN** 系统返回 `2002`，城市选择列表也不返回该城市

### Requirement: Historical display of disabled cities
城市停用 MUST NOT 修改或隐藏已有行程、游记和消费中的城市名称；查看历史数据时 MUST 仍展示该城市，编辑旧数据时 MAY 保留原城市或改选其他启用城市。

#### Scenario: Old note still shows disabled city
- **WHEN** 管理员停用某城市后，用户查看引用该城市的历史游记
- **THEN** 游记详情仍正常显示“城市名（国家名）”

#### Scenario: New selection excludes disabled city
- **WHEN** 用户在城市选择器中查找已停用城市
- **THEN** 该城市不出现，但历史数据中的展示不受影响

### Requirement: Admin city maintenance
管理员 MUST 能查询、新增、修改和删除城市；`(countryCode, cityCode)` MUST 唯一；城市被任何行程/游记/消费引用后 MUST NOT 物理删除，只能停用，删除请求返回 `2025`；后台城市接口 MUST 仅管理员可访问。

#### Scenario: Referenced city cannot be deleted
- **WHEN** 管理员删除一个已被行程引用的城市
- **THEN** 系统返回 `2025`，城市保持存在，可改为 `DISABLED`

#### Scenario: Unreferenced city can be deleted
- **WHEN** 管理员删除一个未被任何业务数据引用的城市
- **THEN** 城市被物理删除，后续选择列表不再出现

#### Scenario: Duplicate city code is rejected
- **WHEN** 管理员在同一国家下新增已存在的 `cityCode`
- **THEN** 系统返回 `2001` 或唯一键冲突错误，不创建重复城市

#### Scenario: Normal user cannot maintain cities
- **WHEN** 普通用户调用后台城市接口
- **THEN** 系统返回 `4002`

### Requirement: Polar region coverage
国家目录 MUST 包含南极洲与北极地区两个启用地区，使其可以出现在地图、国家抽屉、国家下拉、时间线、记账与 AI 国家攻略中：

- 南极洲 MUST 使用 ISO 代码 `AQ`，中文名“南极洲”，英文名 `Antarctica`，大洲 `ANTARCTICA`。
- 北极地区 MUST 使用保留码 `X3`，中文名“北极地区”，英文名 `Arctic`，大洲为空。

两者 MUST NOT 预置城市。规划行程与游记仍要求城市，管理员补充城市后方可使用；记账的城市可选，因此可直接使用这两个地区。新增极地地区 MUST NOT 修改任何现有国家、城市或业务数据。

#### Scenario: Polar regions are listed
- **WHEN** 前端请求国家列表
- **THEN** 响应包含 `AQ` 南极洲与 `X3` 北极地区，且均为启用状态

#### Scenario: Antarctica opens an empty drawer
- **WHEN** 用户打开南极洲抽屉且没有任何数据
- **THEN** 返回灰色状态、空汇总、空分组与 AI 国家攻略入口

#### Scenario: Arctic opens an empty drawer
- **WHEN** 用户打开北极地区抽屉且没有任何数据
- **THEN** 返回灰色状态、空汇总、空分组与 AI 国家攻略入口

#### Scenario: No cities are preconfigured
- **WHEN** 用户请求南极洲或北极地区的城市列表
- **THEN** 接口返回空数组，规划/游记页提示需要管理员补充城市

#### Scenario: Existing data stays unchanged
- **WHEN** 执行极地地区迁移
- **THEN** 现有国家、城市、行程、游记与消费不受影响
