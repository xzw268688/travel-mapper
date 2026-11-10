import { apiPost } from './http'

export function cancelPolish(requestId: string): Promise<void> {
  return apiPost<void>('/ai/cancel', { requestId })
}

export interface PolishCallbacks {
  onMeta?: (quotaSource: string) => void
  onDelta: (delta: string) => void
  onDone: (totalChars: number) => void
  onCancelled: () => void
  onError: (message: string) => void
}

export async function polishStream(
  requestId: string,
  text: string,
  style: string,
  target: string,
  callbacks: PolishCallbacks,
  signal: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('tm_token')
  const response = await fetch('/api/ai/polish/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ requestId, text, style, target }),
    signal
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || 'AI 润色失败')
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
      if (event === 'meta') callbacks.onMeta?.(payload.quotaSource)
      else if (event === 'delta') callbacks.onDelta(payload.delta || '')
      else if (event === 'done') callbacks.onDone(payload.totalChars || 0)
      else if (event === 'cancelled') callbacks.onCancelled()
      else if (event === 'error') callbacks.onError(payload.message || 'AI 服务不可用')
    }
  }
}
