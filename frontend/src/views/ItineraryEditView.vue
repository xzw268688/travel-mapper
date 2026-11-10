<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCities } from '../api/catalog'
import { addDays } from '../utils/datetime'
import {
  addDay,
  addSpot,
  deleteDay,
  deleteItinerary,
  deleteSpot,
  getItinerary,
  moveSpot,
  regenerateItinerary,
  updateDay,
  updateItinerary,
  updateSpot,
  type DayView,
  type ItineraryDetail,
  type SpotView
} from '../api/itinerary'
import {
  batchSaveChecklist,
  getChecklist,
  type ChecklistItem
} from '../api/prep'
import type { City } from '../api/types'
import PrepChecklist from '../components/PrepChecklist.vue'
import SpotEditDrawer, { type SpotForm } from '../components/SpotEditDrawer.vue'
import { useMapStore } from '../stores/map'
import { newRequestId } from '../utils/uuid'

const route = useRoute()
const router = useRouter()
const mapStore = useMapStore()

const itineraryId = computed(() => {
  const value = Number(route.params.id)
  return Number.isInteger(value) && value > 0 ? value : null
})

const detail = ref<ItineraryDetail | null>(null)
const cities = ref<City[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const title = ref('')
const startDate = ref('')
const endDate = ref('')
const messages = ref<string[]>([])
const prepItems = ref<ChecklistItem[]>([])
const prepLoading = ref(false)

const spotDialog = reactive<SpotForm & {
  visible: boolean
  mode: 'add' | 'edit'
  dayId: number
  spotId: number
}>({
  visible: false,
  mode: 'add',
  dayId: 0,
  spotId: 0,
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

const moveDialog = reactive({ visible: false, spotId: 0, targetDayId: 0 })
const dragSpot = ref<{ dayId: number; spotId: number; index: number } | null>(null)

const dayDialog = reactive({
  visible: false,
  mode: 'add' as 'add' | 'edit',
  dayId: 0,
  insertIndex: 0,
  date: '',
  cityCode: ''
})

const regen = reactive({ keepManualSpots: true, preference: '', running: false })

watch(itineraryId, () => void load(), { immediate: true })

async function loadPrep(): Promise<void> {
  if (!itineraryId.value) return
  prepLoading.value = true
  try {
    const result = await getChecklist(itineraryId.value)
    prepItems.value = result.items
  } catch {
    prepItems.value = []
  } finally {
    prepLoading.value = false
  }
}

async function persistPrep(items: ChecklistItem[]): Promise<void> {
  prepItems.value = items
  if (!itineraryId.value) return
  try {
    const result = await batchSaveChecklist(itineraryId.value, items)
    prepItems.value = result.items
  } catch (err) {
    ElMessage.error((err as Error).message || '保存行前清单失败')
    await loadPrep()
  }
}

async function load(): Promise<void> {
  if (!itineraryId.value) return
  loading.value = true
  error.value = ''
  try {
    const data = await getItinerary(itineraryId.value)
    applyDetail(data)
    await loadPrep()
    try {
      cities.value = await getCities(data.countryCode)
    } catch {
      cities.value = []
    }
  } catch (err) {
    error.value = (err as Error).message || '行程不存在或无权访问'
  } finally {
    loading.value = false
  }
}

function applyDetail(data: ItineraryDetail): void {
  detail.value = data
  title.value = data.title
  startDate.value = data.startDate
  endDate.value = data.endDate
}

async function saveInfo(): Promise<void> {
  if (!itineraryId.value || !detail.value) return
  if (!title.value.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  if (!startDate.value || !endDate.value || startDate.value > endDate.value) {
    ElMessage.warning('请填写正确的日期区间')
    return
  }
  // 只提交发生变化的字段：仅改开始日期时由后端按天数平移结束日期，
  // 避免同时提交旧结束日期触发“编辑日期不能改变天数”。
  const body: { title?: string; startDate?: string; endDate?: string } = {}
  if (title.value.trim() !== detail.value.title) {
    body.title = title.value.trim()
  }
  if (startDate.value !== detail.value.startDate) {
    body.startDate = startDate.value
  }
  if (endDate.value !== detail.value.endDate) {
    body.endDate = endDate.value
  }
  if (Object.keys(body).length === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  busy.value = true
  try {
    applyDetail(await updateItinerary(itineraryId.value, body))
    mapStore.requestRefresh()
    ElMessage.success('行程信息已更新')
  } catch (err) {
    ElMessage.error((err as Error).message || '保存失败')
  } finally {
    busy.value = false
  }
}

// ---------- 景点 ----------

function openAddSpot(day: DayView): void {
  spotDialog.mode = 'add'
  spotDialog.dayId = day.dayId
  spotDialog.spotId = 0
  fillSpotForm()
  spotDialog.visible = true
}

function openEditSpot(spot: SpotView): void {
  spotDialog.mode = 'edit'
  spotDialog.spotId = spot.spotId
  spotDialog.dayId = 0
  fillSpotForm(spot)
  spotDialog.visible = true
}

function fillSpotForm(spot?: SpotView): void {
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

function spotExtendedPayload(form: SpotForm) {
  return {
    category: form.category.trim().slice(0, 32),
    ticketPrice: form.ticketPrice.trim().slice(0, 64),
    highlights: form.highlights.trim().slice(0, 255),
    bestTime: form.bestTime.trim().slice(0, 64),
    nearestStation: form.nearestStation.trim().slice(0, 128),
    reservationRequired: form.reservationRequired,
    area: form.area.trim().slice(0, 128),
    warningNotes: form.warningNotes.trim().slice(0, 255)
  }
}

async function submitSpot(form: SpotForm): Promise<void> {
  if (!itineraryId.value) return
  if (!form.name.trim()) {
    ElMessage.warning('请输入景点名称')
    return
  }
  busy.value = true
  try {
    if (spotDialog.mode === 'add') {
      await addSpot(itineraryId.value, spotDialog.dayId, {
        name: form.name.trim(),
        description: form.description.trim(),
        suggestedDuration: form.suggestedDuration.trim(),
        ...spotExtendedPayload(form)
      })
    } else {
      await updateSpot(spotDialog.spotId, {
        name: form.name.trim(),
        description: form.description.trim(),
        suggestedDuration: form.suggestedDuration.trim(),
        ...spotExtendedPayload(form)
      })
    }
    spotDialog.visible = false
    await load()
    ElMessage.success('景点已保存')
  } catch (err) {
    ElMessage.error((err as Error).message || '保存景点失败')
  } finally {
    busy.value = false
  }
}

function spotMeta(spot: SpotView): string {
  const parts = [spot.suggestedDuration, spot.ticketPrice, spot.bestTime, spot.nearestStation]
    .filter((item) => item && item.trim())
  return parts.join(' · ')
}

async function removeSpot(spot: SpotView): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除景点“${spot.name}”？`, '删除景点', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await deleteSpot(spot.spotId)
    await load()
    ElMessage.success('景点已删除')
  } catch (err) {
    ElMessage.error((err as Error).message || '删除失败')
  } finally {
    busy.value = false
  }
}

async function moveWithinDay(day: DayView, index: number, offset: number): Promise<void> {
  const spot = day.spots[index]
  if (!spot) return
  const targetIndex = index + offset
  if (targetIndex < 0 || targetIndex >= day.spots.length) return
  await moveSpotTo(spot, day.dayId, targetIndex)
}

function openMoveSpot(spot: SpotView): void {
  if (!detail.value || detail.value.days.length < 2) {
    ElMessage.info('至少需要两天才能移动景点')
    return
  }
  moveDialog.spotId = spot.spotId
  moveDialog.targetDayId = detail.value.days.find((day) => !day.spots.some((item) => item.spotId === spot.spotId))?.dayId
    ?? detail.value.days[0].dayId
  moveDialog.visible = true
}

async function confirmMoveSpot(): Promise<void> {
  if (!detail.value) return
  const spot = detail.value.days.flatMap((day) => day.spots).find((item) => item.spotId === moveDialog.spotId)
  if (spot) {
    await moveSpotTo(spot, moveDialog.targetDayId)
  }
  moveDialog.visible = false
}

async function moveSpotTo(spot: SpotView, targetDayId: number, targetIndex?: number): Promise<void> {
  busy.value = true
  try {
    await moveSpot(spot.spotId, { targetDayId, targetIndex })
    await load()
  } catch (err) {
    ElMessage.error((err as Error).message || '移动失败')
  } finally {
    busy.value = false
  }
}

function onSpotDragStart(day: DayView, spot: SpotView, index: number, event: DragEvent): void {
  dragSpot.value = { dayId: day.dayId, spotId: spot.spotId, index }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(spot.spotId))
  }
}

async function onSpotDrop(day: DayView, targetIndex: number): Promise<void> {
  const source = dragSpot.value
  dragSpot.value = null
  if (!source) return
  if (source.dayId !== day.dayId) {
    ElMessage.info('跨天移动请使用“移到…”菜单')
    return
  }
  if (source.index === targetIndex) return
  const spot = day.spots.find((item) => item.spotId === source.spotId)
  if (spot) {
    await moveSpotTo(spot, day.dayId, targetIndex)
  }
}

// ---------- 天 ----------


function openAddDay(): void {
  if (!detail.value) return
  const last = detail.value.days[detail.value.days.length - 1]
  dayDialog.mode = 'add'
  dayDialog.dayId = 0
  // 后端 insertIndex 为 1-based，days.length + 1 表示追加到末尾
  dayDialog.insertIndex = detail.value.days.length + 1
  dayDialog.date = last ? addDays(last.date, 1) : startDate.value
  dayDialog.cityCode = last?.cityCode || cities.value[0]?.cityCode || ''
  dayDialog.visible = true
}

function openEditDay(day: DayView): void {
  dayDialog.mode = 'edit'
  dayDialog.dayId = day.dayId
  dayDialog.date = day.date
  dayDialog.cityCode = day.cityCode
  dayDialog.visible = true
}

async function submitDay(): Promise<void> {
  if (!itineraryId.value) return
  if (!dayDialog.date || !dayDialog.cityCode) {
    ElMessage.warning('请选择日期和城市')
    return
  }
  busy.value = true
  try {
    if (dayDialog.mode === 'add') {
      applyDetail(
        await addDay(itineraryId.value, {
          insertIndex: dayDialog.insertIndex,
          date: dayDialog.date,
          cityCode: dayDialog.cityCode
        })
      )
    } else {
      applyDetail(
        await updateDay(itineraryId.value, dayDialog.dayId, {
          date: dayDialog.date,
          cityCode: dayDialog.cityCode
        })
      )
    }
    dayDialog.visible = false
    ElMessage.success('天信息已更新')
  } catch (err) {
    ElMessage.error((err as Error).message || '保存天信息失败')
  } finally {
    busy.value = false
  }
}

async function removeDay(day: DayView): Promise<void> {
  if (!itineraryId.value || !detail.value) return
  if (detail.value.days.length <= 1) {
    ElMessage.warning('至少保留 1 天')
    return
  }
  try {
    await ElMessageBox.confirm(
      `删除第 ${day.dayIndex} 天及当天 ${day.spots.length} 个景点？`,
      '删除一天',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const result = await deleteDay(itineraryId.value, day.dayId)
    await load()
    ElMessage.success(`已删除 ${result.deletedSpots} 个景点`)
  } catch (err) {
    ElMessage.error((err as Error).message || '删除失败')
  } finally {
    busy.value = false
  }
}

// ---------- 重新生成 / 删除 ----------

async function regenerate(): Promise<void> {
  if (!itineraryId.value) return
  regen.running = true
  messages.value = []
  try {
    const result = await regenerateItinerary(itineraryId.value, {
      requestId: newRequestId(),
      keepManualSpots: regen.keepManualSpots,
      preference: regen.preference
    })
    applyDetail(result.itinerary)
    messages.value = [...result.mergeWarnings, ...result.warnings]
    mapStore.requestRefresh()
    ElMessage.success(
      `重新生成完成，保留手动景点 ${result.mergedManualSpots} 个（今日额度 ${result.quota.freeUsedToday}/${result.quota.freeLimit}）`
    )
  } catch (err) {
    ElMessage.error((err as Error).message || '重新生成失败')
  } finally {
    regen.running = false
  }
}

async function removeItinerary(): Promise<void> {
  if (!itineraryId.value || !detail.value) return
  try {
    await ElMessageBox.confirm('删除后行程及其景点将不可恢复，确定删除吗？', '删除行程', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  busy.value = true
  try {
    const result = await deleteItinerary(itineraryId.value)
    mapStore.requestRefresh()
    ElMessage.success(
      result.tripDeleted
        ? '行程已删除，旅行已无内容并自动清理'
        : `行程已删除，地图状态 ${result.countryStatusAfter}`
    )
    await router.push('/')
  } catch (err) {
    ElMessage.error((err as Error).message || '删除失败')
  } finally {
    busy.value = false
  }
}

function statusText(status: string): string {
  return status === 'GREEN' ? '已完成（绿色）' : status === 'YELLOW' ? '计划中（黄色）' : '未点亮（灰色）'
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-result v-if="error" icon="error" title="无法打开行程" :sub-title="error">
      <template #extra>
        <el-button @click="router.push('/plan')">返回行程规划</el-button>
      </template>
    </el-result>

    <template v-else-if="detail">
      <el-card>
        <template #header>
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
            <span class="trip-title">编辑行程 #{{ detail.id }}</span>
            <el-tag :type="detail.status === 'GREEN' ? 'success' : 'warning'">
              {{ statusText(detail.status) }}
            </el-tag>
            <el-tag v-if="detail.expired" type="info">已过期</el-tag>
            <span class="meta">旅行：{{ detail.tripName || '未归组' }}</span>
            <el-button type="danger" plain :loading="busy" @click="removeItinerary">删除行程</el-button>
            <el-button @click="router.push('/plan')">返回规划</el-button>
          </div>
        </template>
        <div class="selector-row">
          <el-input v-model="title" maxlength="50" show-word-limit style="width: 320px" placeholder="行程标题" />
          <el-date-picker v-model="startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
          <el-date-picker v-model="endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
          <el-button type="primary" :loading="busy" @click="saveInfo">保存基本信息</el-button>
          <span class="meta">手动景点 {{ detail.manualSpotCount }}/30 · {{ detail.countryName }}</span>
        </div>
      </el-card>

      <div v-loading="prepLoading" style="margin-top: 16px">
        <PrepChecklist
          v-model:items="prepItems"
          :country-name="detail.countryName"
          @change="persistPrep"
        />
      </div>

      <el-card v-if="detail.guides?.length" style="margin-top: 16px">
        <template #header><div class="trip-title">参考攻略</div></template>
        <div class="meta" style="margin-bottom: 8px">来自攻略搜索，仅供参考。</div>
        <el-link
          v-for="link in detail.guides"
          :key="link.url"
          :href="link.url"
          target="_blank"
          rel="noopener"
          style="display: block; margin-top: 6px"
        >
          {{ link.title }}
        </el-link>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>
          <div style="display: flex; align-items: center; gap: 12px">
            <span class="trip-title">逐日安排</span>
            <el-button size="small" @click="openAddDay">+ 增加一天</el-button>
          </div>
        </template>
        <div v-for="day in detail.days" :key="day.dayId" class="city-block">
          <div class="city-head" style="flex-wrap: wrap">
            <strong>第 {{ day.dayIndex }} 天</strong>
            <el-tag size="small">{{ day.date }}</el-tag>
            <el-tag size="small" type="success">{{ day.cityName }}</el-tag>
            <el-tag v-if="day.cityDisabled" size="small" type="warning">城市已停用</el-tag>
            <el-button text size="small" @click="openEditDay(day)">修改本天</el-button>
            <el-button text size="small" @click="openAddSpot(day)">+ 添加景点</el-button>
            <el-button text type="danger" size="small" @click="removeDay(day)">删除本天</el-button>
          </div>
          <el-empty v-if="day.spots.length === 0" description="当天暂无景点" :image-size="60" />
          <div
            v-for="(spot, index) in day.spots"
            :key="spot.spotId"
            class="fallback-row draggable-row"
            draggable="true"
            title="拖拽可调整当天顺序"
            @dragstart="onSpotDragStart(day, spot, index, $event)"
            @dragover.prevent
            @drop="onSpotDrop(day, index)"
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
            <el-button text size="small" :disabled="index === 0" @click="moveWithinDay(day, index, -1)">↑</el-button>
            <el-button
              text
              size="small"
              :disabled="index === day.spots.length - 1"
              @click="moveWithinDay(day, index, 1)"
            >
              ↓
            </el-button>
            <el-button text size="small" @click="openEditSpot(spot)">编辑</el-button>
            <el-button text size="small" @click="openMoveSpot(spot)">移到…</el-button>
            <el-button text type="danger" size="small" @click="removeSpot(spot)">删除</el-button>
          </div>
        </div>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header><div class="trip-title">AI 重新生成</div></template>
        <div class="meta">重新生成会消耗 1 次额度；勾选保留手动景点时，会按同日同城合并并提示冲突。</div>
        <div class="selector-row" style="margin-top: 8px">
          <el-checkbox v-model="regen.keepManualSpots">保留手动景点</el-checkbox>
          <el-input
            v-model="regen.preference"
            maxlength="200"
            show-word-limit
            placeholder="新的偏好（可选）"
            style="width: 360px"
          />
          <el-button type="primary" :loading="regen.running" @click="regenerate">重新生成</el-button>
        </div>
        <el-alert
          v-for="(message, index) in messages"
          :key="index"
          type="warning"
          :closable="false"
          show-icon
          :title="message"
          style="margin-top: 8px"
        />
      </el-card>
    </template>

    <SpotEditDrawer
      :visible="spotDialog.visible"
      :title="spotDialog.mode === 'add' ? '添加景点' : '编辑景点'"
      :initial="spotDialog"
      @update:visible="spotDialog.visible = $event"
      @submit="submitSpot"
    />

    <el-dialog v-model="moveDialog.visible" title="移动景点到某天" width="420px">
      <el-select v-model="moveDialog.targetDayId" style="width: 100%">
        <el-option
          v-for="day in detail?.days || []"
          :key="day.dayId"
          :label="`第 ${day.dayIndex} 天 · ${day.date} · ${day.cityName}`"
          :value="day.dayId"
        />
      </el-select>
      <template #footer>
        <el-button @click="moveDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="confirmMoveSpot">移动</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dayDialog.visible"
      :title="dayDialog.mode === 'add' ? '增加一天' : '修改本天'"
      width="420px"
    >
      <el-form label-width="80px">
        <el-form-item label="日期" required>
          <el-date-picker v-model="dayDialog.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="城市" required>
          <el-select v-model="dayDialog.cityCode" filterable style="width: 100%">
            <el-option
              v-for="city in cities"
              :key="city.cityCode"
              :label="city.displayName"
              :value="city.cityCode"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dayDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitDay">确定</el-button>
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
