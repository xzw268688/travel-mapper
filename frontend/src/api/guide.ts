import { apiGet } from './http'
import { getStoredToken } from '../utils/authStorage'

export interface CountryGuideAttraction {
  name: string
  description: string
}

export interface CountryGuideNotice {
  visa: string
  currency: string
  powerPlug: string
  bestSeason: string
  notes: string[]
}

export interface CountryGuide {
  countryCode: string
  countryName: string
  intro: string
  attractions: CountryGuideAttraction[]
  notice: CountryGuideNotice
  cached: boolean
}

export interface CountryGuideCache {
  available: boolean
  guide: CountryGuide | null
}

export function getCachedGuide(countryCode: string): Promise<CountryGuideCache> {
  return apiGet<CountryGuideCache>(`/country-guide/${encodeURIComponent(countryCode)}`)
}

export interface GuideStreamCallbacks {
  onMeta?: (quotaSource: string, cached: boolean) => void
  onDelta: (delta: string) => void
  onDone: (guide: CountryGuide, cached: boolean) => void
  onError: (message: string) => void
}

export async function countryGuideStream(
  requestId: string,
  countryCode: string,
  callbacks: GuideStreamCallbacks,
  signal: AbortSignal
): Promise<void> {
  const token = getStoredToken()
  const response = await fetch('/api/country-guide/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ requestId, countryCode }),
    signal
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || '生成国家攻略失败')
  }
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('浏览器不支持流式读取')
  }
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      let event = 'message'
      let data = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) data += line.slice(5).trim()
      }
      if (!data) continue
      let payload: any = {}
      try {
        payload = JSON.parse(data)
      } catch {
        continue
      }
      if (event === 'meta') callbacks.onMeta?.(payload.quotaSource || '', Boolean(payload.cached))
      else if (event === 'delta') callbacks.onDelta(payload.delta || '')
      else if (event === 'done') callbacks.onDone(payload.guide, Boolean(payload.cached))
      else if (event === 'error') callbacks.onError(payload.message || 'AI 服务不可用')
    }
  }
}