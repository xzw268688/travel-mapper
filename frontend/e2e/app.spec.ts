import fs from 'node:fs'
import { test, expect } from '@playwright/test'

interface E2EState {
  token: string
  itineraryId: number
  itineraryTitle: string
  tripId: number
  tripName: string
  noteId: number
  noteTitle: string
  shareToken: string
}

const state: E2EState = JSON.parse(fs.readFileSync('e2e/.state.json', 'utf8'))

test.describe.configure({ mode: 'serial' })

test('地图 → 国家抽屉 → 打开行程编辑页', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('旅迹地图').first()).toBeVisible()
  // 本地 world.json 应让 ECharts 正常渲染，不再降级
  await expect(page.locator('.map-canvas canvas')).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('地图底图加载失败，已切换为国家列表')).toHaveCount(0)

  const japanTag = page.locator('.map-legend .el-tag').filter({ hasText: '日本' }).first()
  await expect(japanTag).toBeVisible({ timeout: 20_000 })
  await japanTag.click()

  const drawer = page.locator('.el-drawer')
  await expect(drawer).toBeVisible()
  // 空未归组不再渲染占位文案
  await expect(drawer.getByText('暂无未归组数据')).toHaveCount(0)

  // 国家攻略：无缓存时显示生成入口，有缓存时直接展示内容
  const guideSection = drawer.locator('.guide-section')
  await expect(guideSection.getByText('AI 国家攻略')).toBeVisible()
  await expect(
    guideSection.getByRole('button', { name: '生成攻略' }).or(guideSection.locator('.guide-content'))
  ).toBeVisible()

  await drawer.locator('.archive-card').first().click()
  await expect(page).toHaveURL(/\/trips\/\d+$/)

  await page.locator('.trip-hub-row').filter({ hasText: state.itineraryTitle }).first().click()
  await expect(page).toHaveURL(new RegExp(`/itineraries/${state.itineraryId}$`))
  await expect(page.getByText(`编辑行程 #${state.itineraryId}`)).toBeVisible()
})

test('旅行详情聚合与录入联动', async ({ page }) => {
  await page.goto(`/trips/${state.tripId}`)
  await expect(page.locator('.trip-title').first()).toContainText(state.tripName)
  await expect(page.getByText(state.itineraryTitle).first()).toBeVisible()

  await page
    .locator('.trip-hub-row')
    .filter({ hasText: state.itineraryTitle })
    .first()
    .click()
  await expect(page).toHaveURL(new RegExp(`/itineraries/${state.itineraryId}$`))

  // 记账页自动按旅行筛选
  const expenseRequest = page.waitForRequest(
    (request) =>
      request.url().includes('/api/expenses?') &&
      request.url().includes(`tripId=${state.tripId}`)
  )
  await page.goto(`/expenses?tripId=${state.tripId}`)
  await expenseRequest

  // 游记页不再要求手动选择旅行
  await page.goto('/notes')
  await expect(page.getByText('发布游记')).toBeVisible()
  await expect(page.getByText('未归组', { exact: true })).toHaveCount(0)
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

test('编辑器内选中文本流式润色并替换', async ({ page }) => {
  await page.goto('/notes')
  const editor = page.locator('.rich-editor-content').first()
  await editor.click()
  await page.keyboard.type('这是需要润色的原文')
  await page.keyboard.press('Control+A')

  let requestBody = ''
  await page.route('**/api/ai/polish/stream', async (route) => {
    requestBody = route.request().postData() || ''
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body:
        'event: meta\ndata: {"requestId":"e2e","quotaSource":"FREE"}\n\n' +
        'event: delta\ndata: {"delta":"这是润色后的"}\n\n' +
        'event: delta\ndata: {"delta":"文本"}\n\n' +
        'event: done\ndata: {"requestId":"e2e","totalChars":8}\n\n'
    })
  })

  await page.locator('.rich-editor-toolbar').getByRole('button', { name: 'AI 润色' }).click()
  await expect(editor).toContainText('这是润色后的文本')
  await expect(page.getByText('润色完成，已替换选中文本')).toBeVisible()
  expect(JSON.parse(requestBody).target).toBe('selected')
})

test('编辑器润色取消不修改正文', async ({ page }) => {
  await page.goto('/notes')
  const editor = page.locator('.rich-editor-content').first()
  await editor.click()
  await page.keyboard.type('取消测试的原文')
  await page.keyboard.press('Control+A')

  await page.route('**/api/ai/polish/stream', () => {
    // 保持请求挂起，等待用户取消
  })
  await page.route('**/api/ai/cancel', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '{"code":0,"message":"ok","data":null}'
    })
  )

  await page.locator('.rich-editor-toolbar').getByRole('button', { name: 'AI 润色' }).click()
  await page.getByRole('button', { name: '取消润色' }).click()
  await expect(editor).toContainText('取消测试的原文')
  await expect(page.getByText('已取消（不退回额度）')).toBeVisible()
})

test('游记视频上传、保存与播放', async ({ page }) => {
  const title = `E2E 视频游记 ${Date.now()}`
  await page.goto('/notes')
  await page.getByPlaceholder('标题').fill(title)
  await page.locator('.selector-row .el-select').nth(1).click()
  await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '东京' }).first().click()

  const editor = page.locator('.rich-editor-content').first()
  await editor.click()
  await page.keyboard.type('带视频的游记正文')

  await page.locator('input[type="file"][accept*="video"]').setInputFiles({
    name: 'e2e.mp4',
    mimeType: 'video/mp4',
    buffer: Buffer.from([0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109])
  })
  await expect(editor.locator('video')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByText('视频 1/3')).toBeVisible()

  await page.getByRole('button', { name: '保存并查看详情' }).click()
  await expect(page).toHaveURL(/\/notes\/\d+$/)
  await expect(page.locator('.note-video')).toBeVisible({ timeout: 20_000 })

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
  await expect(page.getByText('我的额度', { exact: true })).toBeVisible()
  await expect(page.getByText('卡密兑换')).toBeVisible()

  // /forgot-password 已重定向到合并后的单页流程
  await page.goto('/forgot-password')
  await expect(page).toHaveURL(/\/reset-password$/)
  await expect(page.locator('.auth-title').getByText('找回密码')).toBeVisible()
  await expect(page.getByRole('button', { name: '发送验证码' })).toBeVisible()
})

test('手动规划：创建草稿、添加景点并保存', async ({ page }) => {
  let generateCalled = false
  page.on('request', (request) => {
    if (request.url().includes('/api/itinerary/generate')) {
      generateCalled = true
    }
  })

  const quota = () =>
    page.evaluate(async () => {
      const token = localStorage.getItem('tm_token')
      const response = await fetch('/api/quota/me', {
        headers: { Authorization: `Bearer ${token}` }
      })
      return (await response.json()).data
    })
  await page.goto('/plan')
  const quotaBefore = await quota()
  await expect(page.getByText('AI 行程规划')).toBeVisible()
  await page.locator('.el-radio-button').filter({ hasText: '手动规划' }).click()
  await expect(
    page.getByText('手动规划不调用 AI、不消耗额度；创建草稿后逐天添加景点。')
  ).toBeVisible()

  const dayInput = page.locator('.selector-row .el-input-number input').first()
  await dayInput.fill('2')
  await dayInput.press('Enter')
  await dayInput.blur()
  await page.getByRole('button', { name: '创建手动行程' }).click()
  await expect(page.locator('.city-block')).toHaveCount(2)
  await expect(page.getByText('手动规划 · 未消耗额度')).toBeVisible()

  // 未保存草稿切换模式需要确认；取消保留草稿，确认后清空
  await page.locator('.el-radio-button').filter({ hasText: 'AI 生成' }).click()
  const confirmBox = page.locator('.el-message-box')
  await expect(confirmBox.getByText('切换模式会清空当前未保存的草稿')).toBeVisible()
  await confirmBox.getByRole('button', { name: '取消' }).click()
  await expect(page.locator('.city-block')).toHaveCount(2)

  await page.locator('.el-radio-button').filter({ hasText: 'AI 生成' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '切换并清空' }).click()
  await expect(page.locator('.city-block')).toHaveCount(0)

  // 切回手动模式重新创建
  await page.locator('.el-radio-button').filter({ hasText: '手动规划' }).click()
  await page.getByRole('button', { name: '创建手动行程' }).click()
  await expect(page.locator('.city-block')).toHaveCount(2)

  const firstDay = page.locator('.city-block').first()
  await firstDay.getByRole('button', { name: '+ 添加手动景点' }).click()
  const dialog = page.getByRole('dialog').filter({ hasText: '添加手动景点' })
  await dialog.locator('input').first().fill('E2E 手动景点')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(firstDay.getByText('E2E 手动景点')).toBeVisible()

  await page.getByRole('button', { name: '保存行程', exact: true }).click()
  const savedAlert = page.locator('.el-alert').filter({ hasText: '已保存行程' })
  await expect(savedAlert).toBeVisible()
  const itineraryId = Number((await savedAlert.innerText()).match(/#(\d+)/)?.[1])

  const quotaAfter = await quota()
  expect(generateCalled).toBe(false)
  expect(quotaAfter.freeUsedToday).toBe(quotaBefore.freeUsedToday)
  expect(quotaAfter.packBalance).toBe(quotaBefore.packBalance)
  expect(quotaAfter.vipDailyUsed).toBe(quotaBefore.vipDailyUsed)

  // 保存结果进入归档详情
  const trip = await page.evaluate(async (id) => {
    const token = localStorage.getItem('tm_token')
    const response = await fetch(`/api/itinerary/${id}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    return (await response.json()).data
  }, itineraryId)
  await page.goto(`/trips/${trip.tripId}`)
  await expect(page.locator('.trip-title').first()).toContainText(trip.tripName)

  // 清理：删除行程并重算归档，避免空归档堆积
  await page.evaluate(async (id) => {
    const token = localStorage.getItem('tm_token')
    const headers = { Authorization: `Bearer ${token}` }
    await fetch(`/api/itinerary/${id}`, { method: 'DELETE', headers })
    await fetch('/api/admin/trips/recluster', { method: 'POST', headers })
  }, itineraryId)
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
