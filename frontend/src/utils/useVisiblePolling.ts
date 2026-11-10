import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 页面可见时按固定间隔执行，切到其他页签或最小化时暂停。
 */
export function useVisiblePolling(
  callback: () => void | Promise<void>,
  intervalMs = 15000
): void {
  let timer: number | undefined

  const tick = (): void => {
    if (document.visibilityState === 'visible') {
      void callback()
    }
  }

  const onVisibility = (): void => {
    if (document.visibilityState === 'visible') {
      tick()
    }
  }

  onMounted(() => {
    void callback()
    timer = window.setInterval(tick, intervalMs)
    document.addEventListener('visibilitychange', onVisibility)
  })

  onBeforeUnmount(() => {
    if (timer) {
      window.clearInterval(timer)
    }
    document.removeEventListener('visibilitychange', onVisibility)
  })
}