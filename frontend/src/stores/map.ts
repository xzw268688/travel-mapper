import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 行程/游记变更后通知地图刷新统计，无需整页重载。 */
export const useMapStore = defineStore('map', () => {
  const version = ref(0)

  function requestRefresh(): void {
    version.value += 1
  }

  return { version, requestRefresh }
})
