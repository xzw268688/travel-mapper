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
  await expect(page.getByText('汇率维护')).toBeVisible()
  await expect(page.locator('.el-table').getByText('CNY').first()).toBeVisible()

  await page.goto('/admin/notes')
  await expect(page.getByText('内容下架与恢复')).toBeVisible()
  await expect(page.getByPlaceholder('游记 ID')).toBeVisible()
})
