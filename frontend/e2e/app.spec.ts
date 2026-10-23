import fs from 'node:fs'
import { test, expect } from '@playwright/test'

interface E2EState {
  token: string
  itineraryId: number
  itineraryTitle: string
  noteId: number
  noteTitle: string
  shareToken: string
}

const state: E2EState = JSON.parse(fs.readFileSync('e2e/.state.json', 'utf8'))

test.describe.configure({ mode: 'serial' })

test('地图 → 国家抽屉 → 打开行程编辑页', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('TravelMapper').first()).toBeVisible()
  // 本地 world.json 应让 ECharts 正常渲染，不再降级
  await expect(page.locator('.map-canvas canvas')).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('地图底图加载失败，已切换为国家列表')).toHaveCount(0)

  const japanTag = page.locator('.map-legend .el-tag').filter({ hasText: '日本' }).first()
  await expect(japanTag).toBeVisible({ timeout: 20_000 })
  await japanTag.click()

  const drawer = page.locator('.el-drawer')
  await expect(drawer).toBeVisible()
  await drawer.getByText(state.itineraryTitle).click()

  await expect(page).toHaveURL(new RegExp(`/itineraries/${state.itineraryId}$`))
  await expect(page.getByText(`编辑行程 #${state.itineraryId}`)).toBeVisible()
})

test('行程编辑：景点与天的增删改', async ({ page }) => {
  await page.goto(`/itineraries/${state.itineraryId}`)
  await expect(page.getByText(`编辑行程 #${state.itineraryId}`)).toBeVisible()

  const firstDay = page.locator('.city-block').first()
  await firstDay.getByRole('button', { name: '+ 添加景点' }).click()
  let dialog = page.getByRole('dialog').filter({ hasText: '添加景点' })
  await dialog.locator('input').first().fill('E2E 新景点')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('E2E 新景点', { exact: true }).first()).toBeVisible()

  let row = page.locator('.fallback-row').filter({ hasText: 'E2E 新景点' }).first()
  await row.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog').filter({ hasText: '编辑景点' })
  await dialog.locator('input').first().fill('E2E 编辑后景点')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('E2E 编辑后景点', { exact: true }).first()).toBeVisible()

  row = page.locator('.fallback-row').filter({ hasText: 'E2E 编辑后景点' }).first()
  await row.getByRole('button', { name: '移到…' }).click()
  dialog = page.getByRole('dialog').filter({ hasText: '移动景点到某天' })
  await dialog.getByRole('button', { name: '移动' }).click()
  await expect(page.getByText('E2E 编辑后景点', { exact: true }).first()).toBeVisible()

  await firstDay.getByRole('button', { name: '修改本天' }).click()
  dialog = page.getByRole('dialog').filter({ hasText: '修改本天' })
  await dialog.locator('.el-select').click()
  await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '大阪' }).first().click()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.city-block').first().getByText('大阪').first()).toBeVisible()

  await page.getByRole('button', { name: '+ 增加一天' }).click()
  dialog = page.getByRole('dialog').filter({ hasText: '增加一天' })
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.city-block')).toHaveCount(4)

  await page.locator('.city-block').nth(3).getByRole('button', { name: '删除本天' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click()
  await expect(page.locator('.city-block')).toHaveCount(3)

  row = page.locator('.fallback-row').filter({ hasText: 'E2E 编辑后景点' }).first()
  await row.getByRole('button', { name: '删除' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('E2E 编辑后景点', { exact: true })).toHaveCount(0)
})

test('游记创建、分享与海报下载', async ({ page }) => {
  const title = `E2E UI 游记 ${Date.now()}`
  await page.goto('/notes')
  await page.getByPlaceholder('标题').fill(title)

  await page.locator('.selector-row .el-select').nth(1).click()
  await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '东京' }).first().click()

  await page.locator('.rich-editor-content').click()
  await page.keyboard.type('E2E 游记正文，用于分享与海报。')
  await page.getByRole('button', { name: '保存并查看详情' }).click()

  await expect(page).toHaveURL(/\/notes\/\d+$/)
  await expect(page.getByText(title).first()).toBeVisible()

  await page.getByRole('button', { name: '创建分享' }).click()
  const shareLine = page.locator('.meta').filter({ hasText: '分享链接：' }).first()
  await expect(shareLine).toBeVisible()
  expect(await shareLine.innerText()).toContain('/share/')

  await page.getByRole('button', { name: '生成海报' }).click()
  await expect(page).toHaveURL(/\/poster\/\d+$/)
  await expect(page.locator('.poster-title')).toHaveText(title)
  await expect(page.locator('.poster-qr')).toBeVisible()

  const downloadPromise = page.waitForEvent('download', { timeout: 30_000 })
  await page.getByRole('button', { name: '下载海报' }).click()
  const download = await downloadPromise
  expect(await download.failure()).toBeNull()

  const noteId = Number(page.url().match(/\/poster\/(\d+)$/)?.[1])
  await page.goto(`/notes/${noteId}`)
  await page.getByRole('button', { name: '删除' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click()
  await expect(page).toHaveURL(/\/notes$/)
})

test('公开分享页无需登录即可访问', async ({ browser }) => {
  const context = await browser.newContext({ baseURL: 'http://localhost:5173' })
  const page = await context.newPage()
  try {
    await page.goto(`/share/${state.shareToken}`)
    await expect(page.locator('.trip-title')).toHaveText(state.noteTitle)
    await expect(page.getByText('分享已失效')).toHaveCount(0)
  } finally {
    await context.close()
  }
})

test('后台汇率与内容管理页面可访问', async ({ page }) => {
  await page.goto('/admin/rates')
  await expect(page.locator('.trip-title').getByText('汇率维护')).toBeVisible()
  await expect(page.locator('.el-table').getByText('CNY').first()).toBeVisible()

  await page.goto('/admin/notes')
  await expect(page.locator('.trip-title').getByText('内容下架与恢复')).toBeVisible()
  await expect(page.getByPlaceholder('游记 ID')).toBeVisible()

  await page.goto('/admin/settings')
  await expect(page.locator('.trip-title').getByText('AI 行程模型（LLM）')).toBeVisible()
  await expect(page.getByRole('button', { name: '保存 LLM 配置' })).toBeVisible()

  await page.goto('/admin/users')
  await expect(page.locator('.trip-title').getByText('用户管理')).toBeVisible()
  await page.goto('/admin/cards')
  await expect(page.locator('.trip-title').getByText('生成卡密')).toBeVisible()
  await page.goto('/admin/cities')
  await expect(page.locator('.trip-title').getByText('城市维护')).toBeVisible()
  await page.goto('/admin/official-links')
  await expect(page.locator('.trip-title').getByText('官方链接维护')).toBeVisible()
})

test('个人中心、卡密兑换与找回密码页面', async ({ page }) => {
  await page.goto('/profile')
  await expect(page.getByText('个人资料')).toBeVisible()
  await expect(page.getByText('我的额度')).toBeVisible()
  await expect(page.getByText('卡密兑换')).toBeVisible()

  await page.goto('/forgot-password')
  await expect(page.locator('.login-title').getByText('找回密码')).toBeVisible()
  await page.goto('/reset-password')
  await expect(page.locator('.login-title').getByText('重置密码')).toBeVisible()
})

test('记账新增、编辑、删除与汇总图表', async ({ page }) => {
  const amount = String(900 + (Date.now() % 90)) + '.55'
  const remark = `E2E 记账 ${Date.now()}`
  await page.goto('/expenses')
  await expect(page.getByText('记账').first()).toBeVisible()

  await page.getByRole('button', { name: '记一笔' }).click()
  const dialog = page.getByRole('dialog').filter({ hasText: '记一笔' })
  await dialog.getByPlaceholder('如 125.00，可为负数').fill(amount)
  await dialog.getByPlaceholder('备注（可选）').fill(remark)
  await dialog.getByRole('button', { name: '保存消费' }).click()
  await expect(page.getByText(remark)).toBeVisible()

  const row = page.locator('.el-table__row').filter({ hasText: remark }).first()
  await row.getByRole('button', { name: '编辑' }).click()
  const editDialog = page.getByRole('dialog').filter({ hasText: '编辑消费' })
  await editDialog.getByPlaceholder('如 125.00，可为负数').fill('999.99')
  await editDialog.getByRole('button', { name: '保存消费' }).click()
  const updatedRow = page.locator('.el-table__row').filter({ hasText: remark }).first()
  await expect(updatedRow.getByText('999.99').first()).toBeVisible()

  await expect(page.locator('.expense-chart canvas')).toBeVisible()

  await updatedRow.getByRole('button', { name: '删除' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click()
  await expect(page.getByText(remark)).toHaveCount(0)
})
