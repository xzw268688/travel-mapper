import { test } from '@playwright/test'

/**
 * 视觉验收截图：只生成图片，不做断言。
 * 运行：npx playwright test e2e/screens.spec.ts
 * 产物：test-results/screens/<页面>-<宽度>.png
 */
const PAGES: Array<{ name: string; path: string; ready: string }> = [
  { name: 'map', path: '/', ready: '.map-canvas canvas' },
  { name: 'plan', path: '/plan', ready: 'text=AI 行程规划' },
  { name: 'notes', path: '/notes', ready: 'text=发布游记' },
  { name: 'expenses', path: '/expenses', ready: 'text=记账' },
  { name: 'profile', path: '/profile', ready: 'text=个人资料' },
  { name: 'admin-users', path: '/admin/users', ready: 'text=用户管理' }
]

for (const width of [1440, 390]) {
  test.describe(`screens-${width}`, () => {
    test.use({ viewport: { width, height: width === 390 ? 844 : 900 } })

    for (const item of PAGES) {
      test(`截图 ${item.name} @${width}`, async ({ page }) => {
        await page.goto(item.path)
        await page.locator(item.ready).first().waitFor({ state: 'visible', timeout: 20_000 })
        await page.waitForTimeout(600)
        await page.screenshot({
          path: `test-results/screens/${item.name}-${width}.png`,
          fullPage: false
        })
      })
    }

    test(`截图 plan-manual @${width}`, async ({ page }) => {
      await page.goto('/plan')
      await page.locator('.el-radio-button').filter({ hasText: '手动规划' }).click()
      await page.getByRole('button', { name: '创建手动行程' }).click()
      await page.locator('.city-block').first().waitFor({ state: 'visible', timeout: 20_000 })
      await page.waitForTimeout(600)
      await page.screenshot({
        path: `test-results/screens/plan-manual-${width}.png`,
        fullPage: false
      })
    })

    test(`截图 drawer-guide @${width}`, async ({ page }) => {
      await page.goto('/')
      await page.locator('.map-canvas canvas').waitFor({ state: 'visible', timeout: 20_000 })
      await page.locator('.map-legend .el-tag').filter({ hasText: '日本' }).first().click()
      await page.locator('.guide-section').waitFor({ state: 'visible', timeout: 20_000 })
      await page.waitForTimeout(600)
      await page.screenshot({
        path: `test-results/screens/drawer-guide-${width}.png`,
        fullPage: false
      })
    })
  })
}

test.describe('screens-auth-1440', () => {
  test.use({ viewport: { width: 1440, height: 900 }, storageState: { cookies: [], origins: [] } })

  for (const item of [
    { name: 'login', path: '/login', ready: '.auth-title' },
    { name: 'register', path: '/register', ready: '.auth-title' },
    { name: 'admin-login', path: '/admin/login', ready: '.auth-title' }
  ]) {
    test(`截图 ${item.name}`, async ({ page }) => {
      await page.goto(item.path)
      await page.locator(item.ready).first().waitFor({ state: 'visible', timeout: 20_000 })
      await page.waitForTimeout(400)
      await page.screenshot({ path: `test-results/screens/${item.name}-1440.png`, fullPage: false })
    })
  }
})