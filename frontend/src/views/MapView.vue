<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getCountries } from '../api/catalog'
import { countryGuideStream, getCachedGuide, type CountryGuide } from '../api/guide'
import { getCountryDrawer, getMapStats } from '../api/map'
import { getTimeline, updateTripStatus, type TripTimelineItem } from '../api/trips'
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
  stat?.status === 'GREEN' ? '已完成' : stat?.status === 'YELLOW' ? '计划中' : '未点亮'

const plannedTrips = computed(() =>
  (drawer.value?.trips ?? []).filter((trip) => trip.status !== 'COMPLETED')
)
const completedTrips = computed(() =>
  (drawer.value?.trips ?? []).filter((trip) => trip.status === 'COMPLETED')
)

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
    ElMessage.error((error as Error).message || '更新归档状态失败')
  }
}

function viewportHasTimeline(): boolean {
  return viewMode.value === 'timeline'
}

const hasUngrouped = computed(() => {
  const ungrouped = drawer.value?.ungrouped
  if (!ungrouped) return false
  return ungrouped.itineraries.length + ungrouped.notes.length + ungrouped.expenses.length > 0
})

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
  status === 'GREEN' ? '已完成' : status === 'YELLOW' ? '计划中' : '未点亮'

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

async function initMap(): Promise<void> {
  if (!chartEl.value) return
  try {
    const response = await fetch(WORLD_GEO_URL)
    if (!response.ok) {
      throw new Error('地图底图加载失败')
    }
    const geo = await response.json()
    echarts.registerMap('world', geo)

    countryByName = new Map<string, Country>()
    for (const country of countries.value) {
      countryByName.set(country.nameZh, country)
      if (country.nameEn) countryByName.set(country.nameEn, country)
    }
    countryByCode = new Map(countries.value.map((country) => [country.code, country]))
    geoFeatures = Array.isArray(geo.features) ? geo.features : []
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
          data
        }
      ]
    } as any)
    chart.on('click', (params: any) => {
      if (params.data?.code) {
        void openCountry(params.data.code)
      }
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

async function refreshStats(): Promise<void> {
  try {
    const mapStats = await getMapStats()
    stats.value = mapStats.countries
    chart?.setOption({ series: [{ data: buildMapData() }] } as any)
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

onMounted(() => {
  void load()
  window.addEventListener('resize', syncDrawerSize)
})

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

    <div v-if="viewMode === 'timeline'" class="timeline-panel" v-loading="timelineLoading">
      <el-empty v-if="timeline.length === 0" description="还没有归档，先去规划或记录一次旅行吧" />
      <div v-for="(item, index) in timeline" :key="item.id" class="timeline-node" @click="router.push(`/trips/${item.id}`)">
        <div class="timeline-axis">
          <span class="timeline-dot" :class="{ completed: item.status === 'COMPLETED' }" />
          <span v-if="index < timeline.length - 1" class="timeline-line" />
        </div>
        <div class="timeline-card">
          <div class="archive-head">
            <strong>{{ item.name }}</strong>
            <el-tag :type="item.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
              {{ item.status === 'COMPLETED' ? '已完成' : '计划中' }}
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
          <span class="meta" style="margin-left: 8px">
            行程 {{ drawer.summary.itineraryCount }} · 游记 {{ drawer.summary.noteCount }} ·
            花费 ¥{{ drawer.summary.totalExpenseCny }}
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

        <div class="drawer-section">
          <div class="trip-title">计划中</div>
          <el-empty v-if="plannedTrips.length === 0" description="暂无计划中的归档" :image-size="60" />
          <div
            v-for="trip in plannedTrips"
            :key="trip.tripId"
            class="archive-card"
            @click="router.push(`/trips/${trip.tripId}`)"
          >
            <div class="archive-head">
              <strong>{{ trip.tripName }}</strong>
              <span class="archive-link">查看全部 ›</span>
            </div>
            <div class="meta">{{ trip.startDate || '无日期' }} ~ {{ trip.endDate || '无日期' }}</div>
            <div class="archive-stats">
              <span>行程 {{ tripItineraryCount(trip) }}</span>
              <span>游记 {{ tripNoteCount(trip) }}</span>
              <span>花费 ¥{{ trip.subtotalCny }}</span>
            </div>
            <div class="archive-actions">
              <el-button text size="small" type="success" @click.stop="toggleStatus(trip.tripId, trip.status)">
                标记完成
              </el-button>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="trip-title">已完成</div>
          <el-empty v-if="completedTrips.length === 0" description="暂无已完成的归档" :image-size="60" />
          <div
            v-for="trip in completedTrips"
            :key="trip.tripId"
            class="archive-card completed"
            @click="router.push(`/trips/${trip.tripId}`)"
          >
            <div class="archive-head">
              <strong>{{ trip.tripName }}</strong>
              <el-tag type="success" size="small">已完成</el-tag>
            </div>
            <div class="meta">{{ trip.startDate || '无日期' }} ~ {{ trip.endDate || '无日期' }}</div>
            <div class="archive-stats">
              <span>行程 {{ tripItineraryCount(trip) }}</span>
              <span>游记 {{ tripNoteCount(trip) }}</span>
              <span>花费 ¥{{ trip.subtotalCny }}</span>
            </div>
            <div class="archive-actions">
              <el-button text size="small" @click.stop="toggleStatus(trip.tripId, trip.status)">
                改回计划中
              </el-button>
            </div>
          </div>
        </div>

        <div v-if="hasUngrouped" class="drawer-section">
          <div class="trip-title">未归组</div>
          <div class="meta">
            行程 {{ drawer.ungrouped.itineraries.length }} ·
            游记 {{ drawer.ungrouped.notes.length }} ·
            消费 {{ drawer.ungrouped.expenses.length }} ·
            花费 ¥{{ drawer.ungrouped.expenseCny }}
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
