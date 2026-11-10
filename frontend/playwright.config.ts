import { defineConfig } from '@playwright/test'

const isCI = Boolean(process.env.CI)
const usePreview = process.env.E2E_PREVIEW === '1'
const frontendOrigin = process.env.E2E_FRONTEND_ORIGIN ?? 'http://localhost:5173'
const serverCommand = usePreview
  ? 'npm run preview -- --host 127.0.0.1 --port 5173'
  : 'npm run dev -- --host 127.0.0.1 --port 5173'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: isCI,
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  use: {
    baseURL: frontendOrigin,
    // 本地使用本机 Chrome，避免额外下载；CI 使用 Playwright 安装的 Chromium。
    channel: isCI ? undefined : 'chrome',
    headless: true,
    storageState: 'e2e/.auth.json',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: serverCommand,
    url: frontendOrigin,
    reuseExistingServer: !isCI && !usePreview,
    timeout: 120_000
  }
})