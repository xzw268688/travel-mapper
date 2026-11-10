<script setup lang="ts">
import { formatDateTime } from '../utils/datetime'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getCountries } from '../api/catalog'
import {
  getActiveAnnouncements,
  markAnnouncementRead,
  type AnnouncementView
} from '../api/announcements'
import { countryGuideStream, getCachedGuide, type CountryGuide } from '../api/guide'
import { getCountryDrawer, getMapStats } from '../api/map'
import { getTimeline, resetTripStatus, updateTripStatus, type TripTimelineItem } from '../api/trips'
import type { Country, CountryDrawer, CountryStat } from '../api/types'
import { useMapStore } from '../stores/map'
import { newRequestId } from '../utils/uuid'
import worldNamesZh from '../assets/world-names-zh.json'

const router = useRouter()
const mapStore = useMapStore()

const chartEl = ref<HTMLDivElement | null>(null)
const countries = ref<Country[]>([])
const stats = ref<CountryStat[]>([])
const drawer = ref<CountryDrawer | null>(null)
const drawerVisible = ref(false)
const drawerSize = ref(window.innerWidth < 640 ? '92%' : '480px')
const fallback = ref(false)
const loading = ref(false)
const announcements = ref<AnnouncementView[]>([])
const announcementUnread = ref(0)
const announcementDialogVisible = ref(false)
const selectedAnnouncementId = ref<number | null>(null)
const dismissedBannerId = ref<number | null>(
  Number(sessionStorage.getItem('tm.announcement.dismissed') || 0) || null
)
const viewMode = ref<'map' | 'timeline'>('map')
const timeline = ref<TripTimelineItem[]>([])
const timelineLoading = ref(false)
const guide = ref<CountryGuide | null>(null)
const guideLoading = ref(false)
const guideStreamText = ref('')
const guideError = ref('')
let guideController: AbortController | null = null

// 默认使用本地 GeoJSON，避免依赖外部 CDN；生产环境仍需按 SRS 7.3 确认底图来源与审图号。
const WORLD_GEO_URL = import.meta.env.VITE_WORLD_GEO_URL || '/world.json'

let chart: echarts.ECharts | null = null
let geoFeatures: any[] = []
let countryByName = new Map<string, Country>()
let countryByCode = new Map<string, Country>()

const GEO_ALIASES: Record<string, string> = {
  'United States of America': 'US',
  'United States': 'US',
  'South Korea': 'KR',
  Korea: 'KR',
  Russia: 'RU',
  Vietnam: 'VN',
  Czechia: 'CZ',
  'United Kingdom': 'GB'
}

/**
 * 地图名称解析：目录中文名 → 内置对照表 → 底图原名。
 * 只做显示翻译，不参与任何业务判断。
 */
function countryNameOf(name: string): string {
  const aliasCode = GEO_ALIASES[name]
  const country = countryByName.get(name) ?? (aliasCode ? countryByCode.get(aliasCode) : undefined)
  if (country) {
    return country.nameZh
  }
  return (worldNamesZh as Record<string, string>)[name] ?? name
}

const statusColor = (stat?: CountryStat): string =>
  stat?.status === 'GREEN' ? '#2e7d32' : stat?.status === 'YELLOW' ? '#f9a825' : '#37474f'

const statusLabel = (stat?: { status?: string }): string =>
  stat?.status === 'GREEN' ? '有游记' : stat?.status === 'YELLOW' ? '仅有行程' : '无记录'

const inProgressTrips = computed(() =>
  (drawer.value?.trips ?? []).filter((trip) => trip.effectiveStatus === 'IN_PROGRESS')
)
const plannedTrips = computed(() =>
  (drawer.value?.trips ?? []).filter((trip) => trip.effectiveStatus === 'PLANNED')
)
const completedTrips = computed(() =>
  (drawer.value?.trips ?? []).filter((trip) => trip.effectiveStatus === 'COMPLETED')
)
const tripGroups = computed(() => [
  { key: 'IN_PROGRESS', title: '进行中的旅行', trips: inProgressTrips.value },
  { key: 'PLANNED', title: '计划中的旅行', trips: plannedTrips.value },
  { key: 'COMPLETED', title: '已完成的旅行', trips: completedTrips.value }
])

const tripStatusText = (status: string): string =>
  status === 'COMPLETED' ? '已完成' : status === 'IN_PROGRESS' ? '进行中' : '计划中'

const tripStatusTagType = (status: string): 'success' | 'primary' | 'warning' =>
  status === 'COMPLETED' ? 'success' : status === 'IN_PROGRESS' ? 'primary' : 'warning'

function tripDateRange(trip: CountryDrawer['trips'][number]): string {
  const start = trip.effectiveStartDate
  const end = trip.effectiveEndDate
  if (!start && !end) return ''
  if (start && end) return `${start} ~ ${end}`
  return start || end || ''
}

function tripStats(trip: CountryDrawer['trips'][number]): string[] {
  const items: string[] = []
  if (tripItineraryCount(trip) > 0) items.push(`行程 ${tripItineraryCount(trip)}`)
  if (tripNoteCount(trip) > 0) items.push(`游记 ${tripNoteCount(trip)}`)
  if (Number(trip.subtotalCny) > 0) items.push(`花费 ¥${trip.subtotalCny}`)
  return items
}

const drawerSummaryStats = computed(() => {
  const summary = drawer.value?.summary
  if (!summary) return []
  const items: string[] = []
  if (summary.itineraryCount > 0) items.push(`行程 ${summary.itineraryCount}`)
  if (summary.noteCount > 0) items.push(`游记 ${summary.noteCount}`)
  if (Number(summary.totalExpenseCny) > 0) items.push(`花费 ¥${summary.totalExpenseCny}`)
  return items
})

async function switchView(mode: 'map' | 'timeline'): Promise<void> {
  viewMode.value = mode
  if (mode === 'timeline' && timeline.value.length === 0) {
    await loadTimeline()
  }
}

async function loadTimeline(): Promise<void> {
  timelineLoading.value = true
  try {
    timeline.value = await getTimeline()
  } catch {
    timeline.value = []
  } finally {
    timelineLoading.value = false
  }
}


async function toggleStatus(tripId: number, current: string): Promise<void> {
  try {
    await updateTripStatus(tripId, current === 'COMPLETED' ? 'PLANNED' : 'COMPLETED')
    await openCountry(drawer.value?.countryCode ?? '')
    if (viewportHasTimeline()) {
      await loadTimeline()
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '更新旅行状态失败')
  }
}

async function resetStatus(tripId: number): Promise<void> {
  try {
    await resetTripStatus(tripId)
    await openCountry(drawer.value?.countryCode ?? '')
    if (viewportHasTimeline()) {
      await loadTimeline()
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '恢复自动状态失败')
  }
}

function viewportHasTimeline(): boolean {
  return viewMode.value === 'timeline'
}

function tripItineraryCount(trip: CountryDrawer['trips'][number]): number {
  return trip.cities.reduce((total, city) => total + city.itineraries.length, 0)
}

function tripNoteCount(trip: CountryDrawer['trips'][number]): number {
  return trip.cities.reduce((total, city) => total + city.notes.length, 0)
}

const fallbackCountries = computed(() => {
  const statByCode = new Map(stats.value.map((stat) => [stat.countryCode, stat]))
  return countries.value
    .map((country) => {
      const stat = statByCode.get(country.code)
      return {
        countryCode: country.code,
        countryName: country.nameZh,
        status: stat?.status ?? 'GRAY',
        itineraryCount: stat?.itineraryCount ?? 0,
        noteCount: stat?.noteCount ?? 0,
        totalCount: stat?.totalCount ?? 0,
        expired: stat?.expired ?? false
      }
    })
    .sort(
      (left, right) =>
        right.totalCount - left.totalCount ||
        left.countryName.localeCompare(right.countryName, 'zh-CN')
    )
})

const statusTagType = (status: string): 'success' | 'warning' | 'info' =>
  status === 'GREEN' ? 'success' : status === 'YELLOW' ? 'warning' : 'info'

const drawerStatusLabel = (status: string): string =>
  status === 'GREEN' ? '有游记' : status === 'YELLOW' ? '仅有行程' : '无记录'

async function load(): Promise<void> {
  loading.value = true
  try {
    const [countryList, mapStats] = await Promise.all([getCountries(), getMapStats()])
    countries.value = countryList
    stats.value = mapStats.countries
    await initMap()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载地图失败')
    fallback.value = true
  } finally {
    loading.value = false
  }
}

const POLAR_GEO_URLS = ['/antarctica.json']

async function loadPolarFeatures(): Promise<any[]> {
  const results = await Promise.allSettled(
    POLAR_GEO_URLS.map(async (url) => {
      const response = await fetch(url)
      if (!response.ok) {
        throw new Error(`${url} ${response.status}`)
      }
      return response.json()
    })
  )
  const features: any[] = []
  results.forEach((result, index) => {
    if (result.status === 'fulfilled' && Array.isArray(result.value?.features)) {
      features.push(...result.value.features)
    } else {
      console.warn(`极地底图加载失败：${POLAR_GEO_URLS[index]}`)
    }
  })
  return features
}

function mergeGeoFeatures(base: any, extraFeatures: any[]): any {
  const features = Array.isArray(base?.features) ? [...base.features] : []
  const indexByName = new Map<string, number>()
  features.forEach((feature, index) => {
    const name = feature?.properties?.name
    if (typeof name === 'string' && name) {
      indexByName.set(name, index)
    }
  })
  for (const feature of extraFeatures) {
    const name = feature?.properties?.name
    if (typeof name === 'string' && name && indexByName.has(name)) {
      features[indexByName.get(name)!] = feature
    } else {
      if (typeof name === 'string' && name) {
        indexByName.set(name, features.length)
      }
      features.push(feature)
    }
  }
  return { ...base, features }
}

async function initMap(): Promise<void> {
  if (!chartEl.value) return
  try {
    const response = await fetch(WORLD_GEO_URL)
    if (!response.ok) {
      throw new Error('地图底图加载失败')
    }
    const geo = await response.json()
    const polarFeatures = await loadPolarFeatures()
    const mergedGeo = mergeGeoFeatures(geo, polarFeatures)
    echarts.registerMap('world', mergedGeo)

    countryByName = new Map<string, Country>()
    for (const country of countries.value) {
      countryByName.set(country.nameZh, country)
      if (country.nameEn) countryByName.set(country.nameEn, country)
    }
    countryByCode = new Map(countries.value.map((country) => [country.code, country]))
    geoFeatures = Array.isArray(mergedGeo.features) ? mergedGeo.features : []
    const data = buildMapData()

    chart?.dispose()
    chart = echarts.init(chartEl.value)
    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          const label = countryNameOf(String(params.name ?? ''))
          const stat = params.data?.stat as CountryStat | undefined
          if (!stat) return `${label}<br/>暂无记录`
          return `${label}<br/>行程 ${stat.itineraryCount} · 游记 ${stat.noteCount} · 总计 ${stat.totalCount}${stat.expired ? '<br/>计划已过期' : ''}`
        }
      },
      series: [
        {
          id: 'countries',
          type: 'map',
          map: 'world',
          roam: true,
          scaleLimit: { min: 1, max: 8 },
          itemStyle: { areaColor: '#37474f', borderColor: '#546e7a', borderWidth: 0.6 },
          emphasis: {
            label: {
              show: true,
              color: '#ffffff',
              textBorderColor: '#1f2d3d',
              textBorderWidth: 3,
              formatter: (params: any) => countryNameOf(String(params.name ?? ''))
            },
            itemStyle: { areaColor: '#ffd54f' }
          },
          select: { disabled: true },
          markPoint: {
            symbol: 'circle',
            symbolSize: 14,
            animation: false,
            data: buildArcticMarker()
          },
          data
        }
      ]
    } as any)
    chart.on('click', (params: any) => {
      if (params.data?.code) {
        void openCountry(params.data.code)
        return
      }
      ElMessage.info('该区域暂未收录')
    })
    window.addEventListener('resize', resizeChart)
    fallback.value = false
  } catch {
    fallback.value = true
  }
}

function buildMapData(): any[] {
  const statByCode = new Map(stats.value.map((stat) => [stat.countryCode, stat]))
  return geoFeatures.map((feature: any) => {
    const name = feature?.properties?.name as string
    const aliasCode = GEO_ALIASES[name]
    const country = countryByName.get(name) ?? (aliasCode ? countryByCode.get(aliasCode) : undefined)
    const stat = country ? statByCode.get(country.code) : undefined
    return {
      name,
      value: stat ? (stat.status === 'GREEN' ? 2 : 1) : 0,
      stat,
      code: country?.code,
      itemStyle: { areaColor: statusColor(stat) }
    }
  })
}

function buildArcticMarker(): any[] {
  const stat = stats.value.find((item) => item.countryCode === 'X3')
  const color = statusColor(stat)
  return [
    {
      name: 'Arctic',
      coord: [0, 88],
      code: 'X3',
      stat,
      itemStyle: {
        color,
        borderColor: '#ffffff',
        borderWidth: 2,
        shadowBlur: 8,
        shadowColor: 'rgba(31, 45, 61, 0.25)'
      },
      label: {
        show: true,
        position: 'bottom',
        offset: [0, 6],
        formatter: '北极地区',
        color,
        backgroundColor: 'rgba(255, 255, 255, 0.92)',
        padding: [2, 6],
        borderRadius: 6,
        fontWeight: 600,
        fontSize: 12
      }
    }
  ]
}

async function refreshStats(): Promise<void> {
  try {
    const mapStats = await getMapStats()
    stats.value = mapStats.countries
    chart?.setOption({
      series: [
        {
          id: 'countries',
          data: buildMapData(),
          markPoint: { data: buildArcticMarker() }
        }
      ]
    } as any)
  } catch {
    // 地图统计刷新失败时保留旧数据
  }
}

watch(
  () => mapStore.version,
  () => {
    if (chart) {
      void refreshStats()
    }
  }
)

async function openCountry(code: string): Promise<void> {
  try {
    drawer.value = await getCountryDrawer(code)
    drawerVisible.value = true
    resetGuideState()
    await loadCachedGuide(code)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载国家详情失败')
  }
}

function resetGuideState(): void {
  guideController?.abort()
  guideController = null
  guide.value = null
  guideLoading.value = false
  guideStreamText.value = ''
  guideError.value = ''
}

async function loadCachedGuide(code: string): Promise<void> {
  try {
    const cached = await getCachedGuide(code)
    if (drawer.value?.countryCode === code) {
      guide.value = cached.guide
    }
  } catch {
    // 缓存读取失败不影响抽屉其他内容，用户仍可点击生成。
  }
}

async function generateGuide(): Promise<void> {
  if (!drawer.value || guideLoading.value) return
  const code = drawer.value.countryCode
  guideController?.abort()
  const controller = new AbortController()
  guideController = controller
  guideLoading.value = true
  guideError.value = ''
  guideStreamText.value = ''
  guide.value = null
  try {
    await countryGuideStream(
      newRequestId(),
      code,
      {
        onDelta: (delta) => {
          if (drawer.value?.countryCode === code) {
            guideStreamText.value += delta
          }
        },
        onDone: (result) => {
          if (drawer.value?.countryCode !== code) return
          guide.value = result
          guideStreamText.value = ''
        },
        onError: (message) => {
          if (drawer.value?.countryCode === code) {
            guideError.value = message
          }
        }
      },
      controller.signal
    )
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      guideError.value = (error as Error).message || '生成国家攻略失败'
    }
  } finally {
    if (drawer.value?.countryCode === code) {
      guideLoading.value = false
    }
  }
}

function resizeChart(): void {
  chart?.resize()
}

function syncDrawerSize(): void {
  drawerSize.value = window.innerWidth < 640 ? '92%' : '480px'
}

const bannerAnnouncement = computed(() => announcements.value[0] ?? null)
const selectedAnnouncement = computed(() =>
  announcements.value.find((item) => item.id === selectedAnnouncementId.value)
  ?? announcements.value[0]
  ?? null
)
const showAnnouncementBanner = computed(() =>
  Boolean(bannerAnnouncement.value) && bannerAnnouncement.value?.id !== dismissedBannerId.value
)

onMounted(() => {
  void load()
  void loadAnnouncements()
  window.addEventListener('resize', syncDrawerSize)
})

async function loadAnnouncements(): Promise<void> {
  try {
    const result = await getActiveAnnouncements()
    announcements.value = result.list
    announcementUnread.value = result.unread
    selectedAnnouncementId.value = result.list[0]?.id ?? null
  } catch {
    // 公告失败不影响地图主流程。
  }
}

function openAnnouncementDialog(id?: number): void {
  announcementDialogVisible.value = true
  const target = id ?? announcements.value[0]?.id
  if (target) {
    void selectAnnouncement(target)
  }
}

function openBannerAnnouncement(): void {
  if (bannerAnnouncement.value) {
    openAnnouncementDialog(bannerAnnouncement.value.id)
  }
}

async function selectAnnouncement(id: number): Promise<void> {
  selectedAnnouncementId.value = id
  const item = announcements.value.find((entry) => entry.id === id)
  if (!item || item.read) {
    return
  }
  item.read = true
  announcementUnread.value = Math.max(0, announcementUnread.value - 1)
  try {
    await markAnnouncementRead(id)
  } catch {
    item.read = false
    announcementUnread.value += 1
  }
}

function dismissAnnouncementBanner(): void {
  if (!bannerAnnouncement.value) {
    return
  }
  dismissedBannerId.value = bannerAnnouncement.value.id
  sessionStorage.setItem('tm.announcement.dismissed', String(bannerAnnouncement.value.id))
}

function formatAnnouncementTime(value: string | null): string {
  return formatDateTime(value) || '长期有效'
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  window.removeEventListener('resize', syncDrawerSize)
  guideController?.abort()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="map-page" v-loading="loading">
    <div ref="chartEl" class="map-canvas" v-show="viewMode === 'map' && !fallback"></div>

    <div class="map-topbar">
      <strong>旅迹地图</strong>
      <span>把走过的路，留在地图上</span>
    </div>

    <div class="view-switch">
      <el-button-group>
        <el-button :type="viewMode === 'map' ? 'primary' : 'default'" size="small" @click="switchView('map')">
          地图
        </el-button>
        <el-button :type="viewMode === 'timeline' ? 'primary' : 'default'" size="small" @click="switchView('timeline')">
          时间线
        </el-button>
      </el-button-group>
    </div>

    <div
      v-if="showAnnouncementBanner && bannerAnnouncement"
      class="map-announcement"
      role="button"
      tabindex="0"
      @click="openBannerAnnouncement"
      @keydown.enter="openBannerAnnouncement"
    >
      <el-tag size="small" :type="bannerAnnouncement.type === 'ANNOUNCEMENT' ? 'success' : 'info'">
        {{ bannerAnnouncement.type === 'ANNOUNCEMENT' ? '公告' : '通知' }}
      </el-tag>
      <span class="map-announcement-title">{{ bannerAnnouncement.title }}</span>
      <span v-if="announcements.length > 1 || announcementUnread > 0" class="map-announcement-more">
        共 {{ announcements.length }} 条<template v-if="announcementUnread > 0"> · 未读 {{ announcementUnread }}</template>
      </span>
      <button
        type="button"
        class="map-announcement-close"
        aria-label="关闭公告横幅"
        @click.stop="dismissAnnouncementBanner"
      >
        ×
      </button>
    </div>

    <div v-if="viewMode === 'timeline'" class="timeline-panel" v-loading="timelineLoading">
      <el-empty v-if="timeline.length === 0" description="还没有归档，先去规划或记录一次旅行吧" />
      <div
        v-for="(item, index) in timeline"
        :key="item.id"
        class="timeline-node"
        @click="router.push(`/trips/${item.id}`)"
      >
        <div class="timeline-axis">
          <span class="timeline-dot" :class="{ completed: item.status === 'COMPLETED' }" />
          <span v-if="index < timeline.length - 1" class="timeline-line" />
        </div>
        <div class="timeline-card">
          <div class="archive-head">
            <strong>{{ item.name }}</strong>
            <el-tag :type="tripStatusTagType(item.status)" size="small">
              {{ tripStatusText(item.status) }}
            </el-tag>
          </div>
          <div class="meta">
            {{ item.countryName }} · {{ item.startDate || '无日期' }} ~ {{ item.endDate || '无日期' }}
          </div>
          <div class="archive-stats">
            <span>行程 {{ item.itineraryCount }}</span>
            <span>游记 {{ item.noteCount }}</span>
            <span>花费 ¥{{ item.expenseCny }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="viewMode === 'map' && fallback" class="fallback-map">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="地图底图加载失败，已切换为国家列表"
      />
      <el-empty v-if="fallbackCountries.length === 0" description="国家目录为空" />
      <div v-else class="meta" style="margin: 8px 0">点击任意国家查看详情；未点亮国家显示为灰色。</div>
      <div
        v-for="stat in fallbackCountries"
        :key="stat.countryCode"
        class="fallback-row"
        @click="openCountry(stat.countryCode)"
      >
        <el-tag :type="statusTagType(stat.status)" size="small">{{ statusLabel(stat) }}</el-tag>
        <span style="flex: 1">{{ stat.countryName }}</span>
        <span class="meta">行程 {{ stat.itineraryCount }} · 游记 {{ stat.noteCount }} · 总计 {{ stat.totalCount }}</span>
      </div>
    </div>

    <div v-if="viewMode === 'map'" class="map-legend">
      <span><span class="legend-dot" style="background: #2e7d32"></span>绿色：有游记</span>
      <span><span class="legend-dot" style="background: #f9a825"></span>黄色：仅有行程</span>
      <span><span class="legend-dot" style="background: #37474f"></span>灰色：未点亮</span>
      <el-tag
        v-for="stat in stats"
        :key="stat.countryCode"
        :type="statusTagType(stat.status)"
        effect="plain"
        style="cursor: pointer"
        @click="openCountry(stat.countryCode)"
      >
        {{ stat.countryName }}<span v-if="stat.expired">（已过期）</span>
      </el-tag>
    </div>

    <el-drawer v-model="drawerVisible" :title="drawer?.countryName ?? '国家详情'" :size="drawerSize">
      <div v-if="drawer">
        <div class="drawer-section">
          <el-tag :type="statusTagType(drawer.status)" effect="dark">
            {{ drawerStatusLabel(drawer.status) }}
          </el-tag>
          <span v-if="drawerSummaryStats.length > 0" class="meta" style="margin-left: 8px">
            {{ drawerSummaryStats.join(' · ') }}
          </span>
          <el-alert
            v-if="drawer.summary.hasUnconfiguredCurrency"
            type="warning"
            :closable="false"
            show-icon
            style="margin-top: 8px"
            :title="`部分花费未计入：${drawer.summary.unconfiguredCurrencies.join('、')}`"
          />
        </div>

        <div class="drawer-section guide-section">
          <div class="guide-head">
            <div class="trip-title">AI 国家攻略</div>
            <el-button
              v-if="!guide && !guideLoading"
              type="primary"
              size="small"
              plain
              @click="generateGuide"
            >
              {{ guideError ? '重试生成' : '生成攻略' }}
            </el-button>
          </div>
          <div v-if="!guide && !guideLoading && !guideError" class="meta">
            点击生成国家介绍、推荐景点与旅游须知；一次消耗 1 次 AI 额度，结果缓存 24 小时。
          </div>
          <div v-if="guideLoading" class="guide-streaming">
            <div class="meta">正在生成，请稍候…</div>
            <pre class="guide-stream-text">{{ guideStreamText || '…' }}</pre>
          </div>
          <el-alert
            v-if="guideError"
            type="error"
            :closable="false"
            show-icon
            :title="guideError"
            style="margin-top: 8px"
          />
          <div v-if="guide && !guideLoading" class="guide-content">
            <div class="guide-block">
              <div class="guide-block-title">国家介绍</div>
              <p class="guide-intro">{{ guide.intro }}</p>
            </div>
            <div v-if="guide.attractions.length > 0" class="guide-block">
              <div class="guide-block-title">推荐景点</div>
              <div v-for="item in guide.attractions" :key="item.name" class="guide-attraction">
                <strong>{{ item.name }}</strong>
                <span v-if="item.description">：{{ item.description }}</span>
              </div>
            </div>
            <div class="guide-block">
              <div class="guide-block-title">旅游须知</div>
              <div v-if="guide.notice.visa" class="guide-notice-row">
                <span>签证</span><em>{{ guide.notice.visa }}</em>
              </div>
              <div v-if="guide.notice.currency" class="guide-notice-row">
                <span>货币</span><em>{{ guide.notice.currency }}</em>
              </div>
              <div v-if="guide.notice.powerPlug" class="guide-notice-row">
                <span>插头</span><em>{{ guide.notice.powerPlug }}</em>
              </div>
              <div v-if="guide.notice.bestSeason" class="guide-notice-row">
                <span>最佳季节</span><em>{{ guide.notice.bestSeason }}</em>
              </div>
              <ul v-if="guide.notice.notes.length > 0" class="guide-notes">
                <li v-for="note in guide.notice.notes" :key="note">{{ note }}</li>
              </ul>
            </div>
            <div class="meta">
              {{ guide.cached ? '来自 24 小时国家缓存，未消耗额度' : '本次消耗 1 次 AI 额度' }}
            </div>
          </div>
        </div>

        <div v-if="tripGroups.some((group) => group.trips.length > 0)" class="meta drawer-group-hint">
          状态自动判断，手动标记优先
        </div>

        <template v-for="group in tripGroups" :key="group.key">
          <div v-if="group.trips.length > 0" class="drawer-section">
            <div class="trip-title">{{ group.title }}</div>
            <div
              v-for="trip in group.trips"
              :key="trip.tripId"
              class="archive-card"
              :class="{ completed: group.key === 'COMPLETED' }"
              @click="router.push(`/trips/${trip.tripId}`)"
            >
              <div class="archive-head">
                <strong>{{ trip.tripName }}</strong>
                <div class="trip-tags">
                  <el-tag :type="tripStatusTagType(group.key)" size="small">
                    {{ tripStatusText(group.key) }}
                  </el-tag>
                  <el-tooltip
                    v-if="trip.statusManual"
                    content="这个状态是你自己改的。<br/>之后添加游记、消费或日期变化，系统都不会自动改变它。"
                    raw-content
                    popper-class="tm-tooltip"
                    placement="top"
                    :show-after="100"
                  >
                    <el-tag type="info" size="small" effect="plain">手动标记</el-tag>
                  </el-tooltip>
                  <el-tag v-if="trip.expired" type="warning" size="small" effect="plain">
                    已过期
                  </el-tag>
                </div>
              </div>
              <div v-if="tripDateRange(trip)" class="meta">{{ tripDateRange(trip) }}</div>
              <div class="archive-stats">
                <span v-for="stat in tripStats(trip)" :key="stat">{{ stat }}</span>
              </div>
              <div class="archive-actions">
                <el-button text size="small" @click.stop="toggleStatus(trip.tripId, trip.effectiveStatus)">
                  {{ trip.effectiveStatus === 'COMPLETED' ? '改回计划中' : '标记完成' }}
                </el-button>
                <el-tooltip
                  v-if="trip.statusManual"
                  content="让系统重新判断：<br/>旅行结束后有游记或消费 → 已完成；旅行中 → 进行中；否则 → 计划中。"
                    raw-content
                    popper-class="tm-tooltip"
                  placement="top"
                  :show-after="100"
                >
                  <el-button text size="small" @click.stop="resetStatus(trip.tripId)">
                    恢复自动
                  </el-button>
                </el-tooltip>
              </div>
            </div>
          </div>
        </template>

      </div>
    </el-drawer>

    <el-dialog v-model="announcementDialogVisible" title="通知公告" width="720px">
      <div v-if="announcements.length === 0" class="announcement-empty">暂无生效公告</div>
      <div v-else class="announcement-dialog">
        <div class="announcement-nav">
          <button
            v-for="item in announcements"
            :key="item.id"
            type="button"
            class="announcement-nav-item"
            :class="{ active: selectedAnnouncement?.id === item.id }"
            @click="selectAnnouncement(item.id)"
          >
            <span class="announcement-nav-title">
              <span v-if="!item.read" class="announcement-unread-dot" />
              {{ item.title }}
            </span>
            <span class="announcement-nav-meta">
              {{ item.type === 'ANNOUNCEMENT' ? '公告' : '通知' }}
              · {{ formatAnnouncementTime(item.publishAt) }}
            </span>
          </button>
        </div>
        <div v-if="selectedAnnouncement" class="announcement-detail">
          <div class="announcement-detail-head">
            <el-tag
              size="small"
              :type="selectedAnnouncement.type === 'ANNOUNCEMENT' ? 'success' : 'info'"
            >
              {{ selectedAnnouncement.type === 'ANNOUNCEMENT' ? '公告' : '通知' }}
            </el-tag>
            <strong>{{ selectedAnnouncement.title }}</strong>
          </div>
          <div class="announcement-detail-meta">
            {{ formatAnnouncementTime(selectedAnnouncement.publishAt) }}
          </div>
          <pre class="announcement-detail-content">{{ selectedAnnouncement.content }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.map-announcement {
  position: absolute;
  top: 72px;
  left: 20px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(560px, calc(100vw - 40px));
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--tm-border);
  border-radius: 999px;
  box-shadow: var(--tm-shadow);
  cursor: pointer;
  backdrop-filter: blur(6px);
}

.map-announcement-title {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--tm-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-announcement-more {
  color: var(--tm-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.map-announcement-close {
  padding: 0 2px;
  border: none;
  background: transparent;
  color: var(--tm-text-muted);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.announcement-dialog {
  display: flex;
  gap: 16px;
  min-height: 260px;
}

.announcement-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 240px;
  max-height: 420px;
  overflow: auto;
  flex-shrink: 0;
}

.announcement-nav-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--tm-border);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.announcement-nav-item.active {
  border-color: var(--tm-brand);
  background: var(--tm-brand-soft);
}

.announcement-nav-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--tm-text);
}

.announcement-nav-meta {
  font-size: 12px;
  color: var(--tm-text-muted);
}

.announcement-unread-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tm-brand);
  flex-shrink: 0;
}

.announcement-detail {
  flex: 1;
  min-width: 0;
}

.announcement-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
}

.announcement-detail-meta {
  margin: 8px 0 12px;
  font-size: 12px;
  color: var(--tm-text-muted);
}

.announcement-detail-content {
  margin: 0;
  padding: 14px 16px;
  max-height: 320px;
  overflow: auto;
  background: var(--tm-brand-soft);
  border-radius: 8px;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.announcement-empty {
  padding: 32px 0;
  text-align: center;
  color: var(--tm-text-muted);
}

@media (max-width: 640px) {
  .map-announcement {
    top: 62px;
    left: 12px;
    right: 12px;
    max-width: none;
  }

  .announcement-dialog {
    flex-direction: column;
  }

  .announcement-nav {
    width: auto;
    max-height: 160px;
  }
}
</style>
