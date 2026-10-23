import fs from 'node:fs'
import path from 'node:path'
import { request } from '@playwright/test'

const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8080'
const STATE_FILE = path.join(process.cwd(), 'e2e', '.state.json')
const AUTH_FILE = path.join(process.cwd(), 'e2e', '.auth.json')

export default async function globalTeardown(): Promise<void> {
  if (!fs.existsSync(STATE_FILE)) {
    return
  }
  const state = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'))
  const ctx = await request.newContext({ baseURL: API_BASE })
  try {
    const headers = { Authorization: `Bearer ${state.token}` }
    if (state.noteId) {
      await ctx.delete(`/api/notes/${state.noteId}`, { headers }).catch(() => undefined)
    }
    if (state.itineraryId) {
      await ctx.delete(`/api/itinerary/${state.itineraryId}`, { headers }).catch(() => undefined)
    }
  } finally {
    await ctx.dispose()
    fs.rmSync(STATE_FILE, { force: true })
    fs.rmSync(AUTH_FILE, { force: true })
  }
}
