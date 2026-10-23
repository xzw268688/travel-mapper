<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getCountries } from '../api/catalog'
import { getCountryDrawer, getMapStats } from '../api/map'
import type { Country, CountryDrawer, CountryStat } from '../api/types'
import { useMapStore } from '../stores/map'

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
const openedItineraries = new Set<number>()

const WORLD_GEO_URL =
  import.meta.env.VITE_WORLD_GEO_URL ||
  'https://geo.datav.aliyun.com/areas_v3/bound/world.json'

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

const statusColor = (stat?: CountryStat): string =>
  stat?.status === 'GREEN' ? '#2e7d32' : stat?.status === 'YELLOW' ? '#f9a825' : '#37474f'

const statusLabel = (stat?: CountryStat): string =>
  stat?.status === 'GREEN' ? '已完成' : stat?.status === 'YELLOW' ? '计划中' : '未点亮'

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
          const stat = params.data?.stat as CountryStat | undefined
          if (!stat) return `${params.name}<br/>暂无记录`
          return `${stat.countryName}<br/>行程 ${stat.itineraryCount} · 游记 ${stat.noteCount} · 总计 ${stat.totalCount}${stat.expired ? '<br/>计划已过期' : ''}`
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
            label: { show: true, color: '#ffffff' },
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
    openedItineraries.clear()
    drawer.value = await getCountryDrawer(code)
    drawerVisible.value = true
  } catch (error) {
    ElMessage.error((error as Error).message || '加载国家详情失败')
  }
}

function openItinerary(id: number): void {
  openedItineraries.add(id)
  void router.push(`/itineraries/${id}`)
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
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="map-page" v-loading="loading">
    <div ref="chartEl" class="map-canvas" v-show="!fallback"></div>

    <div v-if="fallback" class="fallback-map">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="地图底图加载失败，已切换为国家列表"
      />
      <el-empty v-if="stats.length === 0" description="还没有点亮任何国家" />
      <div
        v-for="stat in stats"
        :key="stat.countryCode"
        class="fallback-row"
        @click="openCountry(stat.countryCode)"
      >
        <el-tag :type="statusTagType(stat.status)" size="small">{{ statusLabel(stat) }}</el-tag>
        <span style="flex: 1">{{ stat.countryName }}</span>
        <span class="meta">行程 {{ stat.itineraryCount }} · 游记 {{ stat.noteCount }} · 总计 {{ stat.totalCount }}</span>
      </div>
    </div>

    <div class="map-legend">
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

        <div v-for="trip in drawer.trips" :key="trip.tripId" class="drawer-section">
          <div class="trip-title">
            {{ trip.tripName }} <span class="meta">¥{{ trip.subtotalCny }}</span>
          </div>
          <div class="meta">{{ trip.startDate || '无日期' }} ~ {{ trip.endDate || '无日期' }}</div>
          <div v-if="trip.cities.length === 0" class="meta">暂无城市分组</div>
          <div v-for="city in trip.cities" :key="trip.tripId + '-' + city.cityCode" class="city-block">
            <div class="city-head">
              <strong>{{ city.cityName }}</strong>
              <el-tag
                :type="city.cityLabel === 'COMPLETED' ? 'success' : 'warning'"
                size="small"
              >
                {{ city.cityLabel === 'COMPLETED' ? '已完成' : '计划中' }}
              </el-tag>
              <span class="meta">花费 ¥{{ city.expenseCny }}</span>
            </div>
            <div v-for="itinerary in city.itineraries" :key="city.cityCode + '-' + itinerary.id">
              <el-link type="primary" :underline="false" @click="openItinerary(itinerary.id)">
                {{ itinerary.title }}
              </el-link>
              <span class="meta"> {{ itinerary.startDate }} ~ {{ itinerary.endDate }}</span>
              <el-tag v-if="itinerary.expired" type="info" size="small">已过期</el-tag>
            </div>
            <div
              v-for="note in city.notes"
              :key="city.cityCode + '-note-' + note.id"
              class="meta"
            >
              游记：{{ note.title }}（{{ note.visibility === 'PRIVATE' ? '私密' : '已分享' }}）
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="trip-title">未归组</div>
          <div
            v-if="drawer.ungrouped.itineraries.length === 0
              && drawer.ungrouped.notes.length === 0
              && drawer.ungrouped.expenses.length === 0"
            class="meta"
          >
            暂无未归组数据
          </div>
          <div
            v-for="itinerary in drawer.ungrouped.itineraries"
            :key="'ungrouped-' + itinerary.id"
            class="city-block"
          >
            <div class="city-head">
              <strong>{{ itinerary.title }}</strong>
              <span class="meta">{{ itinerary.startDate }} ~ {{ itinerary.endDate }}</span>
              <el-tag v-if="itinerary.expired" type="info" size="small">已过期</el-tag>
            </div>
            <div class="meta">{{ itinerary.cities.map((city) => city.cityName).join('、') }}</div>
          </div>
          <div
            v-for="note in drawer.ungrouped.notes"
            :key="'ungrouped-note-' + note.id"
            class="meta"
          >
            游记：{{ note.title }}
          </div>
          <div
            v-for="expense in drawer.ungrouped.expenses"
            :key="'ungrouped-expense-' + expense.id"
            class="meta"
          >
            {{ expense.expenseDate }} · {{ expense.currency }} {{ expense.amount }} · {{ expense.category }}
          </div>
          <div class="meta">未归组花费：¥{{ drawer.ungrouped.expenseCny }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
