import fs from 'node:fs'
import path from 'node:path'
import { request, type APIRequestContext } from '@playwright/test'

const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8080'
const ADMIN_ACCOUNT = process.env.E2E_ADMIN_ACCOUNT ?? 'admin'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Admin@123456'
const FRONTEND_ORIGIN = process.env.E2E_FRONTEND_ORIGIN ?? 'http://localhost:5173'
const STATE_FILE = path.join(process.cwd(), 'e2e', '.state.json')
const AUTH_FILE = path.join(process.cwd(), 'e2e', '.auth.json')

async function unwrap(response: Awaited<ReturnType<APIRequestContext['get']>>, label: string) {
  const body = await response.json().catch(() => ({}))
  if (!response.ok() || body.code !== 0) {
    throw new Error(`${label} 失败：HTTP ${response.status()} ${body.message ?? ''}`)
  }
  return body.data
}

export default async function globalSetup(): Promise<void> {
  const ctx = await request.newContext({ baseURL: API_BASE })
  try {
    const login = await unwrap(
      await ctx.post('/api/auth/login', {
        data: { account: ADMIN_ACCOUNT, password: ADMIN_PASSWORD }
      }),
      '管理员登录（请确认后端已启动且密码正确）'
    )
    const token: string = login.token
    const headers = { Authorization: `Bearer ${token}` }
    const stamp = Date.now()
    const startDate = '2028-05-10'

    const itineraryTitle = `E2E 行程 ${stamp}`
    const saved = await unwrap(
      await ctx.post('/api/itinerary/save', {
        headers,
        data: {
          countryCode: 'JP',
          title: itineraryTitle,
          startDate,
          endDate: '2028-05-12',
          guides: [],
          preparations: {
            visa: 'e2e',
            currency: 'e2e',
            powerPlug: 'e2e',
            bestSeason: 'e2e',
            notes: []
          },
          days: [1, 2, 3].map((dayIndex) => ({
            dayIndex,
            date: `2028-05-${9 + dayIndex}`,
            cityCode: 'TYO',
            spots: [
              {
                name: `E2E 景点 ${dayIndex}`,
                description: 'e2e',
                suggestedDuration: '1h',
                source: 'AI'
              }
            ]
          }))
        }
      }),
      '创建 E2E 行程'
    )

    const noteTitle = `E2E 分享游记 ${stamp}`
    const note = await unwrap(
      await ctx.post('/api/notes', {
        headers,
        data: {
          title: noteTitle,
          content: `<p>E2E seeded note body ${stamp}</p>`,
          images: [],
          coverImage: null,
          countryCode: 'JP',
          cityCode: 'TYO',
          visibility: 'SHARED'
        }
      }),
      '创建 E2E 游记'
    )

    const share = await unwrap(
      await ctx.post(`/api/notes/${note.id}/share`, {
        headers,
        data: { expireDays: 7 }
      }),
      '创建 E2E 分享'
    )

    fs.writeFileSync(
      STATE_FILE,
      JSON.stringify(
        {
          token,
          itineraryId: saved.itineraryId,
          itineraryTitle,
          noteId: note.id,
          noteTitle,
          shareToken: share.token
        },
        null,
        2
      ),
      'utf8'
    )
    fs.writeFileSync(
      AUTH_FILE,
      JSON.stringify({
        cookies: [],
        origins: [
          {
            origin: FRONTEND_ORIGIN,
            localStorage: [{ name: 'tm_token', value: token }]
          }
        ]
      }),
      'utf8'
    )
    console.log(`[e2e] seeded itinerary #${saved.itineraryId}, note #${note.id}`)
  } finally {
    await ctx.dispose()
  }
}
