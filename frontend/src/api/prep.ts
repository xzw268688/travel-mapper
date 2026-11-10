import { apiDelete, apiGet, apiPost, apiPut } from './http'

export interface ChecklistItem {
  id: number | null
  itemKey: string
  label: string
  title: string
  description: string | null
  daysBefore: number
  checked: boolean
  custom: boolean
  sortOrder: number
}

export interface ChecklistResponse {
  itineraryId: number | null
  countryCode: string | null
  items: ChecklistItem[]
}

export interface OfficialLink {
  title: string
  url: string
}

export interface OfficialLinkResponse {
  countryCode: string
  fallback: boolean
  links: OfficialLink[]
  searchUrl: string | null
}

/** 网络失败时新建规划使用的兜底模板。 */
export const DEFAULT_PREP_ITEMS: ChecklistItem[] = [
  ['VISA', '签证', '确认签证类型、所需材料和办理时长；确保护照有效期覆盖整个行程。', 60, 10],
  ['FLIGHT', '机票', '对比直飞/转机、行李额和退改签；廉航注意托运行李费用。', 45, 20],
  ['HOTEL', '住宿', '确认位置、交通、取消政策、独立卫浴和入住时间。', 30, 30],
  ['INSURANCE', '保险', '覆盖医疗、意外、行李延误/丢失；保额满足目的地要求。', 30, 40],
  ['SIM', '电话卡 / eSIM', '对比本地 SIM 卡与 eSIM，确认流量、覆盖范围和激活方式。', 14, 50],
  ['CURRENCY', '换汇与银行卡', '准备少量现金，带银联/Visa/MasterCard，确认境外取现手续费。', 14, 60],
  ['PLUG', '转换头 + 插排', '确认目的地插头标准与电压，带万能转换头和插排。', 7, 70],
  ['APPS', 'App 准备', '地图、翻译、公共交通、支付和当地生活类 App。', 7, 80],
  ['PACKING', '必备物品', '充电宝、雨具、舒适步行鞋、常用药品、防晒等。', 7, 90],
  ['CUSTOMS', '入境卡与海关', '了解入境卡/电子申报、现金申报限额和禁带物品。', 1, 100]
].map(([itemKey, title, description, daysBefore, sortOrder]) => ({
  id: null,
  itemKey: String(itemKey),
  label: String(title),
  title: String(title),
  description: String(description),
  daysBefore: Number(daysBefore),
  checked: false,
  custom: false,
  sortOrder: Number(sortOrder)
}))

export function getChecklistTemplate(): Promise<ChecklistResponse> {
  return apiGet<ChecklistResponse>('/prep/checklist/template')
}

export function getChecklist(itineraryId: number): Promise<ChecklistResponse> {
  return apiGet<ChecklistResponse>('/prep/checklist', { itineraryId })
}

export function updateChecklist(itineraryId: number, itemKey: string, checked: boolean): Promise<unknown> {
  return apiPut('/prep/checklist', { itineraryId, itemKey, checked })
}

export function addChecklistItem(body: {
  itineraryId: number
  title: string
  description?: string | null
  daysBefore: number
}): Promise<ChecklistItem> {
  return apiPost<ChecklistItem>('/prep/checklist/items', body)
}

export function deleteChecklistItem(itemId: number): Promise<{ id: number }> {
  return apiDelete<{ id: number }>(`/prep/checklist/items/${itemId}`)
}

export function batchSaveChecklist(
  itineraryId: number,
  items: ChecklistItem[]
): Promise<ChecklistResponse> {
  return apiPut<ChecklistResponse>('/prep/checklist/batch', {
    itineraryId,
    items: items.map((item) => ({
      itemKey: item.itemKey,
      title: item.title,
      description: item.description,
      daysBefore: item.daysBefore,
      checked: item.checked,
      custom: item.custom
    }))
  })
}

export function getOfficialLinks(countryCode: string): Promise<OfficialLinkResponse> {
  return apiGet<OfficialLinkResponse>('/prep/official-links', { countryCode })
}