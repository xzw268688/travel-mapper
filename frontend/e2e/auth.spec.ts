import { execFileSync } from 'node:child_process'
import { test, expect } from '@playwright/test'

// 注册用户未登录，覆盖 storageState 中的管理员登录态
test.use({ storageState: { cookies: [], origins: [] } })

const MYSQL_CONTAINER = process.env.E2E_MYSQL_CONTAINER ?? 'tm-mysql'
const MYSQL_ROOT_PASSWORD = process.env.E2E_MYSQL_ROOT_PASSWORD ?? 'root'
const MYSQL_DATABASE = process.env.E2E_MYSQL_DATABASE ?? 'travelmapper'

function runSql(sql: string): string {
  return execFileSync(
    'docker',
    [
      'exec',
      '-i',
      '-e',
      `MYSQL_PWD=${MYSQL_ROOT_PASSWORD}`,
      MYSQL_CONTAINER,
      'mysql',
      '-uroot',
      MYSQL_DATABASE,
      '-N',
      '-B',
      '-e',
      sql
    ],
    { encoding: 'utf8' }
  ).trim()
}

test('注册 → 邮箱验证 → 登录', async ({ page }) => {
  const stamp = Date.now()
  const username = `e2e_${stamp}`
  const email = `${username}@example.com`
  const password = 'Password123'

  try {
    await page.goto('/register')
    await expect(page.getByText('注册 TravelMapper')).toBeVisible()
    await page.getByPlaceholder('4-20 位字母、数字或下划线').fill(username)
    await page.getByPlaceholder('用于接收验证令牌').fill(email)
    await page.locator('input[type="password"]').first().fill(password)
    await page.locator('input[type="password"]').nth(1).fill(password)
    await page.getByText('我已阅读并同意用户协议').click()
    await page.getByRole('button', { name: '注册' }).click()

    await expect(page).toHaveURL(/\/verify-email/)
    await expect(page.locator('.login-title').getByText('验证邮箱')).toBeVisible()

    const token = runSql(
      `SELECT token FROM email_token WHERE email = '${email}' AND type = 'VERIFY' ORDER BY id DESC LIMIT 1`
    )
    expect(token.length).toBeGreaterThan(20)

    await page.getByPlaceholder('邮件中的令牌，或数据库 email_token 表中的 token').fill(token)
    await page.getByRole('button', { name: '验证邮箱' }).click()
    await expect(page).toHaveURL(/\/login$/)

    await page.locator('input[autocomplete="username"]').fill(email)
    await page.locator('input[autocomplete="current-password"]').fill(password)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('button', { name: '退出' })).toBeVisible()
  } finally {
    runSql(
      `DELETE et FROM email_token et JOIN user u ON et.user_id = u.id WHERE u.email = '${email}';` +
        `DELETE FROM user WHERE email = '${email}';`
    )
  }
})
