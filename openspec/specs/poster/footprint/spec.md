# Poster Footprint Specification

## Purpose

定义由用户主动触发的足迹海报生成：在前端组合精选游记摘要、点亮国家集合/连线示意与分享页二维码，不暴露消费金额、私密全文或未公开照片。

## Requirements

### Requirement: Poster generation entry
海报 MUST 由用户主动点击“生成海报”触发，不得在发布游记时自动生成；用户 MUST 能从自己的游记中选择一篇（默认最近一篇）作为海报内容。

#### Scenario: User triggers poster generation
- **WHEN** 用户点击“生成海报”
- **THEN** 系统进入海报生成流程，默认选中最近一篇本人游记

#### Scenario: Publishing does not auto-generate
- **WHEN** 用户发布游记
- **THEN** 系统不自动生成海报

### Requirement: Poster data sources
生成海报前 MUST 确保所选游记存在有效分享令牌；MUST 调用地图统计获取 `GREEN`/`YELLOW` 国家集合；二维码 MUST 指向该游记的分享页 URL；海报 MUST 包含精选摘要（正文纯文本前 100 字）、点亮国家集合/连线示意和二维码。

#### Scenario: Poster contains required elements
- **WHEN** 海报生成完成
- **THEN** 图片包含游记摘要、点亮国家集合或连线示意、以及分享页二维码

#### Scenario: QR points to share page
- **WHEN** 扫描海报二维码
- **THEN** 打开所选游记的只读分享页

#### Scenario: Share token is ensured
- **WHEN** 所选游记尚无有效分享令牌
- **THEN** 系统先创建分享令牌再生成海报

### Requirement: Poster content exclusions
海报 MUST NOT 包含消费金额、私密游记全文、未公开照片、用户 ID、邮箱或其他游记内容；不做复杂排版。

#### Scenario: Expenses are absent
- **WHEN** 检查海报内容
- **THEN** 不出现任何消费金额或旅行小计

#### Scenario: Private content is not exposed
- **WHEN** 游记为私密且未分享
- **THEN** 海报只使用允许展示的摘要，不包含全文或未公开照片

### Requirement: Poster and share page separation
海报是静态图片，分享页是独立只读 URL；用户 MUST 能只分享海报图片或只分享分享页链接；二维码固定指向分享页，不由海报图片替代。

#### Scenario: Share image only
- **WHEN** 用户只保存并分享海报图片
- **THEN** 分享页仍独立存在，扫码可访问

#### Scenario: Share link only
- **WHEN** 用户只复制分享页链接
- **THEN** 不生成海报也能正常访问分享页

### Requirement: Connection visualization
海报中的“轨迹” MUST 为 `GREEN`/`YELLOW` 国家集合及按旅行时间顺序的连线示意，MUST NOT 表示 GPS 真实轨迹或地理位置追踪。

#### Scenario: Connections are schematic
- **WHEN** 海报绘制连线
- **THEN** 连线按旅行时间顺序连接已点亮国家，不展示 GPS 坐标
