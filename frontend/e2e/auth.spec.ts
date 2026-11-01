import fs from 'node:fs'
import { execFileSync } from 'node:child_process'
import { test, expect } from '@playwright/test'

// 注册与登录相关用例从登出状态开始
test.use({ storageState: { cookies: [], origins: [] } })

const ADMIN_ACCOUNT = process.env.E2E_ADMIN_ACCOUNT ?? 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Admin@123456'
const BACKEND_LOG = process.env.E2E_BACKEND_LOG

function lastCodeFor(email: string): string {
  if (!BACKEND_LOG || !fs.existsSync(BACKEND_LOG)) {
    throw new Error(`E2E_BACKEND_LOG 未设置或不存在：${BACKEND_LOG ?? '(unset)'}`)
  }
  const content = fs.readFileSync(BACKEND_LOG, 'utf8')
  const pattern = new RegExp(
    `\\[auth-dev\\][^\\n]*email=${email.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[^\\n]*code=(\\d{6})`,
    'g'
  )
  let match: RegExpExecArray | null
  let last: string | null = null
  while ((match = pattern.exec(content)) !== null) {
    last = match[1]
  }
  if (!last) {
    throw new Error(`后端日志中找不到 ${email} 的验证码`)
  }
  return last
}

/** 清掉该邮箱的发码限流键，避免同一用例内注册发码后立刻重发被 1005 拦截。 */
function clearSendLimits(email: string): void {
  const container = process.env.E2E_REDIS_CONTAINER ?? 'tm-redis'
  const now = new Date()
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' })
    .format(now)
    .replace(/-/g, '')
  const hour = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    hour12: false
  })
    .format(now)
    .replace(/[^0-9]/g, '')
    .slice(0, 10)
  execFileSync('docker', [
    'exec',
    container,
    'redis-cli',
    'DEL',
    `auth:code:gap:${email}`,
    `auth:code:daily:${email}:${day}`,
    `auth:code:ip:127.0.0.1:${hour}`,
    `auth:code:ip:0:0:0:0:0:0:0:1:${hour}`
  ])
}

async function logout(page: import('@playwright/test').Page): Promise<void> {
  await page.locator('.user-menu').click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
}

test('管理员入口：管理员登录后进入用户管理页', async ({ page }) => {
  await page.goto('/admin/login')
  await expect(page.locator('.auth-title').getByText('管理员登录')).toBeVisible()
  await page.locator('input[autocomplete="username"]').fill(ADMIN_ACCOUNT)
  await page.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录后台' }).click()
  await expect(page).toHaveURL(/\/admin\/users$/)
  await expect(page.locator('.trip-title').getByText('用户管理')).toBeVisible()
})

test('普通登录页不暴露管理员入口', async ({ page }) => {
  await page.goto('/login')
  await expect(page.locator('.auth-title').getByText('登录旅迹地图')).toBeVisible()
  await expect(page.locator('a[href="/admin/login"]')).toHaveCount(0)
  await expect(page.getByText('管理员登录')).toHaveCount(0)
})

test('不勾选保持登录时 token 存在 sessionStorage', async ({ page }) => {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill(ADMIN_ACCOUNT)
  await page.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD)
  await page.getByText('保持登录').click() // 取消勾选
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)

  const storage = await page.evaluate(() => ({
    local: window.localStorage.getItem('tm_token'),
    session: window.sessionStorage.getItem('tm_token')
  }))
  expect(storage.session).toBeTruthy()
  expect(storage.local).toBeNull()

  await page.reload()
  await expect(page.locator('.user-menu')).toBeVisible()
})

test('勾选保持登录时 token 存在 localStorage', async ({ page }) => {
  await page.goto('/login')
  await page.locator('input[autocomplete="username"]').fill(ADMIN_ACCOUNT)
  await page.locator('input[autocomplete="current-password"]').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)

  const storage = await page.evaluate(() => ({
    local: window.localStorage.getItem('tm_token'),
    session: window.sessionStorage.getItem('tm_token')
  }))
  expect(storage.local).toBeTruthy()
  expect(storage.session).toBeNull()
})

test.describe('验证码流程', () => {
  test.skip(!BACKEND_LOG, '未设置 E2E_BACKEND_LOG，跳过需要读取验证码的用例')

  test('注册（邮箱下填验证码）→ 自动登录，普通用户被管理员入口拒绝', async ({ page }) => {
    const stamp = Date.now()
    const username = `e2e_${stamp}`
    const email = `${username}@example.com`
    const password = 'Password123'

    clearSendLimits(email)
    await page.goto('/register')
    await expect(page.locator('.auth-title').getByText('注册旅迹地图')).toBeVisible()
    await page.getByPlaceholder('4-20 位字母、数字或下划线').fill(username)
    await page.getByPlaceholder('用于接收验证码').fill(email)
    await page.getByRole('button', { name: '发送验证码' }).click()
    await expect(page.getByText('验证码已发送', { exact: false })).toBeVisible()

    await page.getByPlaceholder('6 位数字').fill(lastCodeFor(email))
    await page.locator('input[type="password"]').first().fill(password)
    await page.locator('input[type="password"]').nth(1).fill(password)
    await page.getByText('我已阅读并同意用户协议').click()
    await page.getByRole('button', { name: '注册' }).click()

    // 注册成功自动登录并进入地图
    await expect(page).toHaveURL(/\/$/)
    await expect(page.locator('.user-menu')).toBeVisible()

    // 普通用户从管理员入口登录应被拒绝并清除登录态
    await logout(page)
    await page.goto('/admin/login')
    await page.locator('input[autocomplete="username"]').fill(email)
    await page.locator('input[autocomplete="current-password"]').fill(password)
    await page.getByRole('button', { name: '登录后台' }).click()
    await expect(page.getByText('该账号没有管理员权限')).toBeVisible()
    const token = await page.evaluate(() => window.localStorage.getItem('tm_token'))
    expect(token).toBeNull()
  })

  test('注册页不再出现昵称与独立验证页', async ({ page }) => {
    await page.goto('/register')
    await expect(page.getByText('昵称', { exact: false })).toHaveCount(0)
    await page.goto('/verify-email')
    // 路由已删除：未登录会被守卫送回登录页
    await expect(page).toHaveURL(/\/login$/)
  })

  test('找回密码：验证码重置后旧密码失效', async ({ page }) => {
    const stamp = Date.now()
    const username = `e2e_reset_${stamp}`
    const email = `${username}@example.com`
    const oldPassword = 'Password123'
    const newPassword = 'Password456'

    // 注册并自动登录
    clearSendLimits(email)
    await page.goto('/register')
    await page.getByPlaceholder('4-20 位字母、数字或下划线').fill(username)
    await page.getByPlaceholder('用于接收验证码').fill(email)
    await page.getByRole('button', { name: '发送验证码' }).click()
    await expect(page.getByText('验证码已发送', { exact: false })).toBeVisible()
    await page.getByPlaceholder('6 位数字').fill(lastCodeFor(email))
    await page.locator('input[type="password"]').first().fill(oldPassword)
    await page.locator('input[type="password"]').nth(1).fill(oldPassword)
    await page.getByText('我已阅读并同意用户协议').click()
    await page.getByRole('button', { name: '注册' }).click()
    await expect(page).toHaveURL(/\/$/)
    await logout(page)

    // 找回密码单页三步（清掉 60 秒间隔限制）
    clearSendLimits(email)
    await page.goto('/reset-password')
    await page.locator('input[autocomplete="email"]').fill(email)
    await page.getByRole('button', { name: '发送验证码' }).click()
    await expect(page.getByText('如果该邮箱已注册', { exact: false })).toBeVisible()
    await page.getByPlaceholder('6 位数字').fill(lastCodeFor(email))
    await page.locator('input[type="password"]').first().fill(newPassword)
    await page.locator('input[type="password"]').nth(1).fill(newPassword)
    await page.getByRole('button', { name: '重置密码' }).click()
    await expect(page).toHaveURL(/\/login/)

    await page.locator('input[autocomplete="username"]').fill(email)
    await page.locator('input[autocomplete="current-password"]').fill(oldPassword)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.getByText('用户名或密码错误')).toBeVisible()

    await page.locator('input[autocomplete="current-password"]').fill(newPassword)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/$/)
  })
})