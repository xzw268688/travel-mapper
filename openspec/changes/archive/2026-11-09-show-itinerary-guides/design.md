## Context

见 `proposal.md`。后端 `GenerateItineraryResponse.guides` 与 `ItineraryDetail.guides` 已返回 `GuideLink{title,url,source}`，搜索失败时降级为空数组；保存接口最多接收 3 条合法链接。前端此前只在保存时透传 `guides`，没有任何展示。

## Goals / Non-Goals

**Goals**
- 用最小改动把已有数据展示出来，不新增接口。
- 与现有「官方信息入口」卡片风格一致。

**Non-Goals**
- 不改变搜索供应商、超时、缓存或降级策略。
- 不新增攻略内容页或站内聚合。

## Decisions

- 复用 `el-card + el-link`，标题「参考攻略」，`target="_blank" rel="noopener"`。
- `guides` 为空或缺失时不渲染卡片，避免空状态干扰。
- 规划页绑定 `draft.guides`，详情页绑定 `detail.guides`；均为现有响应字段。

## Risks / Trade-offs

- [搜索未配置或失败] → 卡片不出现，行为与之前一致，不产生误导性空区块。

## Migration Plan

纯前端发布，无数据迁移；回滚只需回退前端版本。