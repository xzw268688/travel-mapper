<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCities, getCountries } from '../api/catalog'
import { addDays, todayInBeijing } from '../utils/datetime'
import {
  generateItinerary,
  saveItinerary,
  type DayPlan,
  type GenerateResponse,
  type SpotPlan
} from '../api/itinerary'
import {
  DEFAULT_PREP_ITEMS,
  batchSaveChecklist,
  getChecklistTemplate,
  getOfficialLinks,
  type ChecklistItem,
  type OfficialLinkResponse
} from '../api/prep'
import PrepChecklist from '../components/PrepChecklist.vue'
import SpotEditDrawer, { type SpotForm } from '../components/SpotEditDrawer.vue'
import type { City, Country } from '../api/types'
import { useMapStore } from '../stores/map'
import { newRequestId } from '../utils/uuid'

const router = useRouter()
const mapStore = useMapStore()

const countries = ref<Country[]>([])
const cities = ref<City[]>([])
const draftCities = ref<City[]>([])
const country = ref('JP')
const cityCodes = ref<string[]>(['TYO'])
const days = ref(3)
const startDate = ref(addDays(todayInBeijing(), 1))
const preference = ref('')
const generating = ref(false)
const generatingSeconds = ref(0)
let generatingTimer: ReturnType<typeof setInterval> | null = null
const saving = ref(false)
const mode = ref<'AI' | 'MANUAL'>('AI')
const draftMode = ref<'AI' | 'MANUAL'>('AI')
const draft = ref<GenerateResponse | null>(null)
const draftTitle = ref('')
const savedId = ref<number | null>(null)
const savedStatus = ref('')
const officialLinks = ref<OfficialLinkResponse | null>(null)
const prepItems = ref<ChecklistItem[]>([...DEFAULT_PREP_ITEMS])

const spotDialog = reactive<SpotForm & { visible: boolean; mode: 'add' | 'edit' }>({
  visible: false,
  mode: 'add',
  name: '',
  description: '',
  suggestedDuration: '',
  category: '',
  ticketPrice: '',
  highlights: '',
  bestTime: '',
  nearestStation: '',
  reservationRequired: false,
  area: '',
  warningNotes: ''
})
const spotTarget = ref<{ day: DayPlan; index: number } | null>(null)
const moveDialog = reactive({ visible: false, targetIndex: 0 })
const moveSource = ref<{ day: DayPlan; index: number } | null>(null)
const dragSpot = ref<{ dayIndex: number; index: number } | null>(null)

const countryName = computed(() =>
  countries.value.find((item) => item.code === country.value)?.nameZh || country.value
)

// 行程生成不再产出“行前准备”；仅在历史草稿仍有内容时展示，避免空卡片。
const hasPreparations = computed(() => {
  const preparations = draft.value?.preparations
  if (!preparations) return false
  return Boolean(
    preparations.visa ||
      preparations.currency ||
      preparations.powerPlug ||
      preparations.bestSeason ||
      (preparations.notes && preparations.notes.length > 0)
  )
})

onMounted(async () => {
  try {
    countries.value = await getCountries()
    if (!countries.value.some((item) => item.code === country.value)) {
      country.value = countries.value[0]?.code || ''
    }
    await loadCities()
    await loadPrepTemplate()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载国家失败')
  }
})

async function loadPrepTemplate(): Promise<void> {
  try {
    const result = await getChecklistTemplate()
    prepItems.value = result.items
  } catch {
    prepItems.value = [...DEFAULT_PREP_ITEMS]
  }
}

function resetPrepForNewPlan(): void {
  if (savedId.value !== null) {
    prepItems.value = [...DEFAULT_PREP_ITEMS]
  }
}

watch(country, async () => {
  cityCodes.value = []
  await loadCities()
})

async function loadCities(): Promise<void> {
  if (!country.value) return
  try {
    cities.value = await getCities(country.value)
    if (cityCodes.value.length === 0 && cities.value.length > 0) {
      cityCodes.value = [cities.value[0].cityCode]
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '加载城市失败')
  }
}


function cityNameOf(cityCode: string): string {
  return (
    draftCities.value.find((item) => item.cityCode === cityCode)?.name ||
    cities.value.find((item) => item.cityCode === cityCode)?.name ||
    cityCode
  )
}

async function loadDraftCities(countryCode: string): Promise<void> {
  try {
    draftCities.value = await getCities(countryCode)
  } catch {
    draftCities.value = cities.value
  }
}

async function generate(): Promise<void> {
  resetPrepForNewPlan()
  if (!country.value || cityCodes.value.length === 0) {
    ElMessage.warning('请选择国家和至少一个城市')
    return
  }
  if (days.value < 1 || days.value > 15) {
    ElMessage.warning('天数必须为 1-15')
    return
  }
  generating.value = true
  generatingSeconds.value = 0
  generatingTimer = setInterval(() => {
    generatingSeconds.value += 1
  }, 1000)
  try {
    draftMode.value = 'AI'
    draft.value = await generateItinerary({
      requestId: newRequestId(),
      countryCode: country.value,
      cityCodes: cityCodes.value,
      days: days.value,
      startDate: startDate.value,
      preference: preference.value
    })
    draftTitle.value = `${draft.value.days[0]?.cityName || countryName.value} ${draft.value.days.length} 日`
    await loadDraftCities(draft.value.countryCode)
    savedId.value = null
    officialLinks.value = null
    savedStatus.value = ''
    ElMessage.success(draft.value.deduplicated ? '已返回 60 秒内相同请求的结果' : '行程已生成')
  } catch (error) {
    ElMessage.error((error as Error).message || '生成失败')
  } finally {
    generating.value = false
    if (generatingTimer) {
      clearInterval(generatingTimer)
      generatingTimer = null
    }
  }
}

function createManualDraft(): void {
  resetPrepForNewPlan()
  if (!country.value) {
    ElMessage.warning('请选择国家')
    return
  }
  if (cityCodes.value.length === 0 || cityCodes.value.length > 5) {
    ElMessage.warning('请选择 1-5 个城市')
    return
  }
  if (days.value < 1 || days.value > 15) {
    ElMessage.warning('天数必须为 1-15')
    return
  }
  if (!startDate.value) {
    ElMessage.warning('请选择开始日期')
    return
  }
  const primaryCity = cityCodes.value[0]
  const primaryCityName =
    cities.value.find((item) => item.cityCode === primaryCity)?.name || countryName.value
  const dayPlans: DayPlan[] = Array.from({ length: days.value }, (_, index) => ({
    dayIndex: index + 1,
    date: addDays(startDate.value, index),
    cityCode: primaryCity,
    cityName: primaryCityName,
    spots: []
  }))
  draftMode.value = 'MANUAL'
  draft.value = {
    requestId: '',
    countryCode: country.value,
    startDate: startDate.value,
    endDate: dayPlans[dayPlans.length - 1].date,
    days: dayPlans,
    guides: [],
    preparations: { visa: '', currency: '', powerPlug: '', bestSeason: '', notes: [] },
    warnings: [],
    deduplicated: false,
    quota: { source: 'MANUAL', freeUsedToday: 0, freeLimit: 0, packBalance: 0 }
  }
  draftTitle.value = `${primaryCityName} ${days.value} 日`
  savedId.value = null
  savedStatus.value = ''
  officialLinks.value = null
  void loadDraftCities(country.value)
  ElMessage.success('已创建手动行程草稿，请逐天添加景点')
}

async function switchMode(next: 'AI' | 'MANUAL'): Promise<void> {
  if (next === mode.value) return
  if (draft.value && !savedId.value) {
    try {
      await ElMessageBox.confirm(
        '切换模式会清空当前未保存的草稿，确定继续？',
        '切换规划模式',
        { type: 'warning', confirmButtonText: '切换并清空', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  mode.value = next
  draft.value = null
  draftTitle.value = ''
  officialLinks.value = null
}

function onModeChange(value: string | number | boolean | undefined): void {
  void switchMode(value === 'MANUAL' ? 'MANUAL' : 'AI')
}

async function save(): Promise<void> {
  if (!draft.value) {
    ElMessage.warning(draftMode.value === 'MANUAL' ? '请先创建手动行程' : '请先生成行程')
    return
  }
  if (!draftTitle.value.trim()) {
    ElMessage.warning('请填写行程标题')
    return
  }
  saving.value = true
  try {
    const result = await saveItinerary({
      countryCode: draft.value.countryCode,
      title: draftTitle.value.trim(),
      startDate: draft.value.startDate,
      endDate: draft.value.endDate,
      guides: draft.value.guides,
      preparations: draft.value.preparations,
      days: draft.value.days.map((day: DayPlan) => ({
        dayIndex: day.dayIndex,
        date: day.date,
        cityCode: day.cityCode,
        spots: day.spots
      }))
    })
    savedId.value = result.itineraryId
    savedStatus.value = result.countryStatusAfter
    mapStore.requestRefresh()
    try {
      await batchSaveChecklist(result.itineraryId, prepItems.value)
    } catch {
      ElMessage.warning('行程已保存，但行前清单未能保存')
    }
    ElMessage.success('行程已保存，地图状态已更新')
    await loadPrep(draft.value.countryCode)
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function loadPrep(countryCode: string): Promise<void> {
  try {
    officialLinks.value = await getOfficialLinks(countryCode)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载官方信息失败')
  }
}

// ---------- 草稿天管理 ----------

function reindexDraftDays(): void {
  const current = draft.value
  if (!current) return
  current.days.forEach((day, index) => {
    day.dayIndex = index + 1
    day.date = addDays(current.startDate, index)
  })
  current.endDate = current.days[current.days.length - 1]?.date || current.startDate
}

function addDraftDay(): void {
  const current = draft.value
  if (!current) return
  if (current.days.length >= 15) {
    ElMessage.warning('最多 15 天')
    return
  }
  const last = current.days[current.days.length - 1]
  const cityCode = last?.cityCode || draftCities.value[0]?.cityCode || ''
  const date = last ? addDays(last.date, 1) : current.startDate
  current.days.push({
    dayIndex: current.days.length + 1,
    date,
    cityCode,
    cityName: cityNameOf(cityCode),
    spots: []
  })
  current.endDate = date
}

async function removeDraftDay(index: number): Promise<void> {
  const current = draft.value
  if (!current) return
  if (current.days.length <= 1) {
    ElMessage.warning('至少保留 1 天')
    return
  }
  try {
    await ElMessageBox.confirm(`删除第 ${index + 1} 天及其景点？`, '删除一天', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  current.days.splice(index, 1)
  reindexDraftDays()
}

function moveDraftDay(index: number, offset: number): void {
  const current = draft.value
  if (!current) return
  const target = index + offset
  if (target < 0 || target >= current.days.length) return
  const swapped = current.days[index]
  current.days[index] = current.days[target]
  current.days[target] = swapped
  reindexDraftDays()
}

function onDayDateChange(index: number, value: string | null): void {
  const current = draft.value
  if (!current || !value) return
  const day = current.days[index]
  if (index > 0 && value <= current.days[index - 1].date) {
    ElMessage.warning('日期必须晚于前一天')
    day.date = addDays(current.days[index - 1].date, 1)
    return
  }
  day.date = value
  if (index === 0) {
    current.startDate = value
  }
  for (let i = index + 1; i < current.days.length; i++) {
    current.days[i].date = addDays(current.days[index].date, i - index)
  }
  current.endDate = current.days[current.days.length - 1].date
}

function onDayCityChange(day: DayPlan, cityCode: string): void {
  day.cityCode = cityCode
  day.cityName = cityNameOf(cityCode)
}

// ---------- 草稿景点 ----------

function openAddSpot(day: DayPlan): void {
  const current = draft.value
  const manualCount = current
    ? current.days.reduce(
        (total, item) => total + item.spots.filter((spot) => spot.source === 'MANUAL').length,
        0
      )
    : 0
  if (manualCount >= 30) {
    ElMessage.warning('手动景点最多 30 个')
    return
  }
  if (day.spots.length >= 10) {
    ElMessage.warning('每天最多 10 个景点')
    return
  }
  spotTarget.value = { day, index: -1 }
  spotDialog.mode = 'add'
  fillSpotForm()
  spotDialog.visible = true
}

function openEditSpot(day: DayPlan, index: number): void {
  const spot = day.spots[index]
  if (!spot) return
  spotTarget.value = { day, index }
  spotDialog.mode = 'edit'
  fillSpotForm(spot)
  spotDialog.visible = true
}

function fillSpotForm(spot?: SpotPlan): void {
  spotDialog.name = spot?.name ?? ''
  spotDialog.description = spot?.description ?? ''
  spotDialog.suggestedDuration = spot?.suggestedDuration ?? ''
  spotDialog.category = spot?.category ?? ''
  spotDialog.ticketPrice = spot?.ticketPrice ?? ''
  spotDialog.highlights = spot?.highlights ?? ''
  spotDialog.bestTime = spot?.bestTime ?? ''
  spotDialog.nearestStation = spot?.nearestStation ?? ''
  spotDialog.reservationRequired = spot?.reservationRequired ?? false
  spotDialog.area = spot?.area ?? ''
  spotDialog.warningNotes = spot?.warningNotes ?? ''
}

function submitSpot(form: SpotForm): void {
  const target = spotTarget.value
  if (!target) return
  if (!form.name.trim()) {
    ElMessage.warning('请输入景点名称')
    return
  }
  const payload: SpotPlan = {
    name: form.name.trim().slice(0, 50),
    description: form.description.trim().slice(0, 200),
    suggestedDuration: form.suggestedDuration.trim().slice(0, 20),
    category: form.category.trim().slice(0, 32),
    ticketPrice: form.ticketPrice.trim().slice(0, 64),
    highlights: form.highlights.trim().slice(0, 255),
    bestTime: form.bestTime.trim().slice(0, 64),
    nearestStation: form.nearestStation.trim().slice(0, 128),
    reservationRequired: form.reservationRequired,
    area: form.area.trim().slice(0, 128),
    warningNotes: form.warningNotes.trim().slice(0, 255),
    source: spotDialog.mode === 'add' ? 'MANUAL' : target.day.spots[target.index].source
  }
  if (spotDialog.mode === 'add') {
    target.day.spots.push(payload)
  } else {
    Object.assign(target.day.spots[target.index], payload)
  }
  spotDialog.visible = false
}

function spotMeta(spot: SpotPlan): string {
  const parts = [spot.suggestedDuration, spot.ticketPrice, spot.bestTime, spot.nearestStation]
    .filter((item) => item && item.trim())
  return parts.join(' · ')
}

function removeSpot(day: DayPlan, index: number): void {
  day.spots.splice(index, 1)
}

function moveSpotWithinDay(day: DayPlan, index: number, offset: number): void {
  const target = index + offset
  if (target < 0 || target >= day.spots.length) return
  const swapped = day.spots[index]
  day.spots[index] = day.spots[target]
  day.spots[target] = swapped
}

function onSpotDragStart(dayIndex: number, index: number, event: DragEvent): void {
  dragSpot.value = { dayIndex, index }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `spot-${dayIndex}-${index}`)
  }
}

function onSpotDrop(dayIndex: number, targetIndex: number): void {
  const source = dragSpot.value
  dragSpot.value = null
  const current = draft.value
  if (!source || !current) return
  if (source.dayIndex !== dayIndex) {
    ElMessage.info('跨天移动请使用“移到…”菜单')
    return
  }
  if (source.index === targetIndex) return
  const spots = current.days[dayIndex]?.spots
  if (!spots) return
  const [spot] = spots.splice(source.index, 1)
  if (spot) {
    spots.splice(targetIndex, 0, spot)
  }
}

function openMoveSpot(day: DayPlan, index: number): void {
  const current = draft.value
  if (!current || current.days.length < 2) {
    ElMessage.info('至少需要两天才能移动景点')
    return
  }
  moveSource.value = { day, index }
  const currentIndex = current.days.indexOf(day)
  moveDialog.targetIndex = currentIndex === 0 ? 1 : 0
  moveDialog.visible = true
}

function confirmMoveSpot(): void {
  const current = draft.value
  const source = moveSource.value
  if (!current || !source) return
  const targetDay = current.days[moveDialog.targetIndex]
  if (!targetDay || targetDay === source.day) {
    ElMessage.info('目标天与当前天相同')
    return
  }
  const [spot] = source.day.spots.splice(source.index, 1)
  if (spot) {
    targetDay.spots.push(spot)
  }
  moveDialog.visible = false
}

function statusText(status: string): string {
  return status === 'GREEN' ? '已完成（绿色）' : status === 'YELLOW' ? '计划中（黄色）' : '未点亮（灰色）'
}

function openSearch(): void {
  if (officialLinks.value?.searchUrl) {
    window.open(officialLinks.value.searchUrl, '_blank', 'noopener')
  }
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
          <div class="trip-title" style="margin-bottom: 0">
            {{ mode === 'AI' ? 'AI 行程规划' : '手动规划' }}
          </div>
          <el-radio-group :model-value="mode" size="small" @change="onModeChange">
            <el-radio-button value="AI">AI 生成</el-radio-button>
            <el-radio-button value="MANUAL">手动规划</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div class="selector-row">
        <el-select v-model="country" placeholder="选择国家" filterable style="min-width: 180px">
          <el-option v-for="item in countries" :key="item.code" :label="item.nameZh" :value="item.code" />
        </el-select>
        <el-select
          v-model="cityCodes"
          multiple
          :multiple-limit="5"
          placeholder="选择 1-5 个城市"
          filterable
          no-data-text="该国家暂未配置城市，请联系管理员补充"
          style="min-width: 260px"
        >
          <el-option
            v-for="item in cities"
            :key="item.cityCode"
            :label="item.displayName"
            :value="item.cityCode"
          />
        </el-select>
        <el-input-number v-model="days" :min="1" :max="15" />
        <el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
      </div>
      <el-input
        v-if="mode === 'AI'"
        v-model="preference"
        type="textarea"
        :rows="2"
        maxlength="200"
        show-word-limit
        placeholder="偏好，例如：喜欢美食和寺庙，节奏慢一点"
        style="margin-top: 12px"
      />
      <div style="margin-top: 12px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
        <el-button
          v-if="mode === 'AI'"
          type="primary"
          :loading="generating"
          @click="generate"
        >
          生成行程
        </el-button>
        <el-button v-else type="primary" @click="createManualDraft">创建手动行程</el-button>
        <span v-if="mode === 'AI' && generating" class="meta">
          正在生成，已等待 {{ generatingSeconds }} 秒（通常需要 30–90 秒，请勿关闭页面）
        </span>
        <span v-else-if="mode === 'AI'" class="meta">
          生成与润色共享每日免费额度；额度不足会返回 2011。
        </span>
        <span v-else class="meta">手动规划不调用 AI、不消耗额度；创建草稿后逐天添加景点。</span>
      </div>
    </el-card>

    <el-card v-if="draft" style="margin-top: 16px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
          <el-input v-model="draftTitle" maxlength="50" show-word-limit style="width: 320px" />
          <el-tag v-if="draftMode === 'MANUAL'" type="success">手动规划 · 未消耗额度</el-tag>
          <el-tag v-else-if="draft.deduplicated" type="info">60 秒去重结果</el-tag>
          <span v-if="draftMode === 'AI'" class="meta">
            扣费来源 {{ draft.quota.source }} · 今日已用 {{ draft.quota.freeUsedToday }}/{{ draft.quota.freeLimit }}
          </span>
          <el-button type="success" :loading="saving" @click="save">保存行程</el-button>
          <el-button v-if="savedId" type="primary" plain @click="router.push(`/itineraries/${savedId}`)">
            打开已保存行程
          </el-button>
        </div>
      </template>
      <el-alert
        v-for="(warning, index) in draft.warnings"
        :key="index"
        type="warning"
        :closable="false"
        show-icon
        :title="warning"
        style="margin-bottom: 8px"
      />
      <div class="meta" style="margin-bottom: 8px">
        草稿微调只在本地生效，点击“保存行程”后统一提交。手动景点上限 30 个，每天最多 10 个。
      </div>

      <PrepChecklist
        v-model:items="prepItems"
        :country-name="countryName"
        style="margin-top: 12px"
      />

      <div v-for="(day, dayIndex) in draft.days" :key="day.dayIndex" class="city-block">
        <div class="city-head" style="flex-wrap: wrap">
          <strong>第 {{ day.dayIndex }} 天</strong>
          <el-date-picker
            :model-value="day.date"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 150px"
            @update:model-value="(value: string | null) => onDayDateChange(dayIndex, value)"
          />
          <el-select
            :model-value="day.cityCode"
            filterable
            no-data-text="该国家暂未配置城市，请联系管理员补充"
            style="width: 180px"
            @update:model-value="(value: string) => onDayCityChange(day, value)"
          >
            <el-option
              v-for="city in draftCities"
              :key="city.cityCode"
              :label="city.displayName"
              :value="city.cityCode"
            />
          </el-select>
          <el-button text size="small" :disabled="dayIndex === 0" @click="moveDraftDay(dayIndex, -1)">上移</el-button>
          <el-button
            text
            size="small"
            :disabled="dayIndex === draft.days.length - 1"
            @click="moveDraftDay(dayIndex, 1)"
          >
            下移
          </el-button>
          <el-button text type="danger" size="small" @click="removeDraftDay(dayIndex)">删除天</el-button>
          <el-button text size="small" type="primary" @click="openAddSpot(day)">+ 添加手动景点</el-button>
        </div>
        <el-empty v-if="day.spots.length === 0" description="当天暂无景点" :image-size="60" />
        <div
          v-for="(spot, index) in day.spots"
          :key="spot.name + index"
          class="fallback-row draggable-row"
          draggable="true"
          title="拖拽可调整当天顺序"
          @dragstart="onSpotDragStart(dayIndex, index, $event)"
          @dragover.prevent
          @drop="onSpotDrop(dayIndex, index)"
        >
          <el-tag :type="spot.source === 'MANUAL' ? 'warning' : 'info'" size="small">
            {{ spot.source === 'MANUAL' ? '手动' : 'AI' }}
          </el-tag>
          <div class="spot-row-main">
            <div class="spot-title-row">
              <strong>{{ spot.name }}</strong>
              <el-tag v-if="spot.category" size="small" effect="plain">{{ spot.category }}</el-tag>
              <el-tag v-if="spot.area" size="small" effect="plain" type="info">{{ spot.area }}</el-tag>
              <el-tag v-if="spot.reservationRequired" size="small" type="warning">需预约</el-tag>
            </div>
            <div v-if="spotMeta(spot)" class="meta">{{ spotMeta(spot) }}</div>
            <div v-if="spot.description" class="meta">{{ spot.description }}</div>
            <div v-if="spot.highlights" class="meta">亮点：{{ spot.highlights }}</div>
            <div v-if="spot.warningNotes" class="meta spot-warning">避雷：{{ spot.warningNotes }}</div>
          </div>
          <el-button text size="small" :disabled="index === 0" @click="moveSpotWithinDay(day, index, -1)">↑</el-button>
          <el-button
            text
            size="small"
            :disabled="index === day.spots.length - 1"
            @click="moveSpotWithinDay(day, index, 1)"
          >
            ↓
          </el-button>
          <el-button text size="small" @click="openEditSpot(day, index)">编辑</el-button>
          <el-button text size="small" @click="openMoveSpot(day, index)">移到…</el-button>
          <el-button text type="danger" size="small" @click="removeSpot(day, index)">删除</el-button>
        </div>
      </div>
      <el-button style="margin-top: 12px" :disabled="draft.days.length >= 15" @click="addDraftDay">
        + 增加一天
      </el-button>
    </el-card>

    <el-alert
      v-if="savedId"
      type="success"
      :closable="false"
      show-icon
      :title="`已保存行程 #${savedId}，地图状态：${statusText(savedStatus)}`"
      style="margin-top: 16px"
    >
      <el-button type="primary" plain size="small" @click="router.push(`/itineraries/${savedId}`)">
        打开已保存行程
      </el-button>
    </el-alert>

    <el-card v-if="draft && hasPreparations" style="margin-top: 16px">
      <template #header><div class="trip-title">AI 行前提示</div></template>
      <div class="meta">仅供参考，请以官方最新政策为准。</div>
      <div style="margin-top: 8px">
        <p><strong>签证</strong>：{{ draft.preparations.visa }}</p>
        <p><strong>货币</strong>：{{ draft.preparations.currency }}</p>
        <p><strong>插头</strong>：{{ draft.preparations.powerPlug }}</p>
        <p><strong>最佳季节</strong>：{{ draft.preparations.bestSeason }}</p>
        <p v-for="note in draft.preparations.notes" :key="note" class="meta">· {{ note }}</p>
      </div>
    </el-card>

    <el-card v-if="draft && draft.guides?.length" style="margin-top: 16px">
      <template #header><div class="trip-title">参考攻略</div></template>
      <div class="meta" style="margin-bottom: 8px">来自攻略搜索，仅供参考。</div>
      <el-link
        v-for="link in draft.guides"
        :key="link.url"
        :href="link.url"
        target="_blank"
        rel="noopener"
        style="display: block; margin-top: 6px"
      >
        {{ link.title }}
      </el-link>
    </el-card>

    <el-card v-if="officialLinks" style="margin-top: 16px">
      <template #header><div class="trip-title">官方信息入口</div></template>
      <template v-if="officialLinks.fallback">
        <span class="meta">未配置该国官方链接，可使用通用搜索。</span>
        <el-button type="primary" link style="margin-left: 8px" @click="openSearch">
          去搜索
        </el-button>
      </template>
      <template v-else>
        <el-link
          v-for="link in officialLinks.links"
          :key="link.url"
          :href="link.url"
          target="_blank"
          rel="noopener"
          style="display: block"
        >
          {{ link.title }}
        </el-link>
      </template>
    </el-card>

    <SpotEditDrawer
      :visible="spotDialog.visible"
      :title="spotDialog.mode === 'add' ? '添加手动景点' : '编辑景点'"
      :initial="spotDialog"
      @update:visible="spotDialog.visible = $event"
      @submit="submitSpot"
    />

    <el-dialog v-model="moveDialog.visible" title="移动景点到某天" width="420px">

      <el-select v-model="moveDialog.targetIndex" style="width: 100%">
        <el-option
          v-for="(day, index) in draft?.days || []"
          :key="day.dayIndex"
          :label="`第 ${day.dayIndex} 天 · ${day.date} · ${day.cityName}`"
          :value="index"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmMoveSpot">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.spot-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.spot-warning {
  color: #b45309;
}

.draggable-row {
  cursor: grab;
}

.draggable-row:active {
  cursor: grabbing;
}
</style>
