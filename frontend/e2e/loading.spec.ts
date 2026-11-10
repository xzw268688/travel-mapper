import fs from 'node:fs'
import path from 'node:path'
import { test, expect } from '@playwright/test'

interface ManifestChunk {
  file: string
}

const manifest = JSON.parse(
  fs.readFileSync(path.join(process.cwd(), 'dist', '.vite', 'manifest.json'), 'utf8')
) as Record<string, ManifestChunk>

function fileFor(sourceSuffix: string): string {
  const key =
    Object.keys(manifest).find((item) => item.endsWith(sourceSuffix)) ??
    Object.keys(manifest).find((item) =>
      item.includes(sourceSuffix.split('/').pop()?.replace('.vue', '') ?? sourceSuffix)
    )
  if (!key) {
    throw new Error(`manifest 中找不到 ${sourceSuffix}`)
  }
  return manifest[key].file
}

function requestedFile(requests: string[], file: string): boolean {
  return requests.some((url) => url.includes(file))
}

test.describe('首屏加载边界', () => {
  test('登录页不请求地图、编辑器、后台或海报代码', async ({ page }) => {
    const requests: string[] = []
    page.on('request', (request) => requests.push(request.url()))
    await page.addInitScript(() => {
      window.localStorage.clear()
      window.sessionStorage.clear()
    })

    await page.goto('/login')
    await expect(page.locator('.auth-title')).toBeVisible()

    for (const source of [
      'src/views/MapView.vue',
      'src/components/RichTextEditor.vue',
      'src/views/AdminUsersView.vue',
      'src/views/PosterView.vue'
    ]) {
      expect(requestedFile(requests, fileFor(source)), `${source} 不应在登录页加载`).toBe(false)
    }
  })

  test('地图页按需加载地图与 ECharts，不加载编辑器', async ({ page }) => {
    const requests: string[] = []
    page.on('request', (request) => requests.push(request.url()))

    await page.goto('/')
    await expect(page.locator('.map-canvas canvas')).toBeVisible({ timeout: 20_000 })

    expect(requestedFile(requests, fileFor('src/views/MapView.vue'))).toBe(true)
    expect(requestedFile(requests, fileFor('src/views/NotesView.vue'))).toBe(false)
    expect(requests.some((url) => url.includes('/assets/echarts-'))).toBe(true)
  })
})