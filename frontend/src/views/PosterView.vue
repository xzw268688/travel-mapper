<script setup lang="ts">
import { formatDate as formatDateValue } from '../utils/datetime'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { getMapStats } from '../api/map'
import {
  fetchSharePage,
  getNote,
  shareNote,
  type NoteResponse,
  type SharePage
} from '../api/notes'
import type { CountryStat, MapStats } from '../api/types'
import { sharePageUrl } from '../utils/share'

const route = useRoute()
const router = useRouter()
const noteId = computed(() => {
  const value = Number(route.params.id)
  return Number.isInteger(value) && value > 0 ? value : null
})

const note = ref<NoteResponse | null>(null)
const share = ref<SharePage | null>(null)
const token = ref('')
const stats = ref<MapStats | null>(null)
const loading = ref(false)
const rendering = ref(false)
const error = ref('')
const openId = ref('')
const expireDays = ref(7)
const posterRef = ref<HTMLElement | null>(null)
const stageRef = ref<HTMLElement | null>(null)
const routeCanvas = ref<HTMLCanvasElement | null>(null)
const qrCanvas = ref<HTMLCanvasElement | null>(null)

const POSTER_WIDTH = 720
const posterScale = ref(1)
const posterHeight = ref(0)
let stageObserver: ResizeObserver | null = null
let posterObserver: ResizeObserver | null = null

function updatePosterLayout(): void {
  const available = stageRef.value?.clientWidth ?? 0
  if (available > 0) {
    posterScale.value = Math.min(1, available / POSTER_WIDTH)
  }
  if (posterRef.value) {
    posterHeight.value = posterRef.value.offsetHeight
  }
}

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined' && stageRef.value) {
    stageObserver = new ResizeObserver(updatePosterLayout)
    stageObserver.observe(stageRef.value)
  }
  window.addEventListener('resize', updatePosterLayout)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updatePosterLayout)
  stageObserver?.disconnect()
  posterObserver?.disconnect()
})

watch(posterRef, (element) => {
  posterObserver?.disconnect()
  if (element && typeof ResizeObserver !== 'undefined') {
    posterObserver = new ResizeObserver(updatePosterLayout)
    posterObserver.observe(element)
  }
  void nextTick(updatePosterLayout)
}, { flush: 'post' })

watch(share, () => {
  void nextTick(updatePosterLayout)
})

const visited = computed<CountryStat[]>(() =>
  (stats.value?.countries ?? []).filter(
    (country) => country.status === 'GREEN' || country.status === 'YELLOW'
  )
)

const totals = computed(() => {
  let itineraryCount = 0
  let noteCount = 0
  visited.value.forEach((country) => {
    itineraryCount += country.itineraryCount
    noteCount += country.noteCount
  })
  return { countries: visited.value.length, itineraryCount, noteCount }
})

const routeCountries = computed(() =>
  [...visited.value].sort((a, b) => b.totalCount - a.totalCount).slice(0, 8)
)

const gallery = computed(() => {
  if (!share.value) {
    return []
  }
  const cover = share.value.coverImageUrl
  return share.value.images.filter((image) => image !== cover).slice(0, 6)
})

const shareUrl = computed(() => (token.value ? sharePageUrl(token.value) : ''))

watch(
  noteId,
  () => {
    void load()
  },
  { immediate: true }
)

async function load(): Promise<void> {
  if (!noteId.value) {
    return
  }
  loading.value = true
  error.value = ''
  share.value = null
  note.value = null
  try {
    const detail = await getNote(noteId.value)
    if (detail.takedown) {
      throw new Error('游记已下架，不能生成海报')
    }
    note.value = detail
    let activeToken = detail.share.active ? detail.share.token : null
    if (!activeToken) {
      const created = await shareNote(detail.id, expireDays.value)
      activeToken = created.token
      note.value = await getNote(detail.id)
    }
    token.value = activeToken ?? ''
    if (!token.value) {
      throw new Error('分享令牌创建失败')
    }
    share.value = await fetchSharePage(token.value)
    await loadStats()
    await nextTick()
    drawRoute()
    await drawQr()
  } catch (err) {
    error.value = (err as Error).message || '海报生成失败'
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await getMapStats()
  } catch {
    stats.value = { countries: [] }
  }
}

function openNote(): void {
  const id = Number(openId.value)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.warning('请输入正确的游记 ID')
    return
  }
  void router.push(`/poster/${id}`)
}

function drawRoute(): void {
  const canvas = routeCanvas.value
  if (!canvas) {
    return
  }
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return
  }
  const width = 640
  const height = 120
  const ratio = window.devicePixelRatio || 1
  canvas.width = width * ratio
  canvas.height = height * ratio
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  ctx.scale(ratio, ratio)
  ctx.clearRect(0, 0, width, height)

  const countries = routeCountries.value
  if (countries.length === 0) {
    ctx.fillStyle = '#909399'
    ctx.font = '18px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('还没有足迹，先规划一次旅行吧', width / 2, height / 2)
    return
  }

  const left = 50
  const right = width - 50
  const gap = countries.length > 1 ? (right - left) / (countries.length - 1) : 0
  const points = countries.map((country, index) => ({
    x: countries.length > 1 ? left + index * gap : width / 2,
    y: 56 + Math.sin(index * 1.15) * 16,
    code: country.countryCode,
    count: country.totalCount
  }))

  if (points.length > 1) {
    ctx.strokeStyle = '#7a9bf5'
    ctx.lineWidth = 2
    ctx.setLineDash([6, 6])
    ctx.beginPath()
    points.forEach((point, index) => {
      if (index === 0) {
        ctx.moveTo(point.x, point.y)
      } else {
        ctx.lineTo(point.x, point.y)
      }
    })
    ctx.stroke()
    ctx.setLineDash([])
  }

  points.forEach((point) => {
    ctx.beginPath()
    ctx.arc(point.x, point.y, 7, 0, Math.PI * 2)
    ctx.fillStyle = '#ffffff'
    ctx.fill()
    ctx.lineWidth = 3
    ctx.strokeStyle = '#4c6ef5'
    ctx.stroke()

    ctx.fillStyle = '#303133'
    ctx.font = 'bold 13px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(point.code, point.x, point.y + 30)
    ctx.fillStyle = '#909399'
    ctx.font = '11px sans-serif'
    ctx.fillText(String(point.count), point.x, point.y + 46)
  })
}

async function drawQr(): Promise<void> {
  if (!qrCanvas.value || !shareUrl.value) {
    return
  }
  try {
    await QRCode.toCanvas(qrCanvas.value, shareUrl.value, {
      width: 150,
      margin: 1,
      color: { dark: '#1f2d3d', light: '#ffffff' }
    })
  } catch {
    // 二维码失败不影响其余海报内容
  }
}

async function ensurePosterImagesLoaded(root: HTMLElement): Promise<void> {
  const images = Array.from(root.querySelectorAll('img'))
  images.forEach((image) => image.setAttribute('loading', 'eager'))
  await Promise.all(
    images.map((image) =>
      image.complete
        ? Promise.resolve()
        : new Promise<void>((resolve) => {
            image.addEventListener('load', () => resolve(), { once: true })
            image.addEventListener('error', () => resolve(), { once: true })
            // 触发懒加载图片开始请求，避免导出时出现空白。
            image.src = image.src
          })
    )
  )
}

async function download(): Promise<void> {
  if (!posterRef.value || !note.value) {
    return
  }
  rendering.value = true
  try {
    await ensurePosterImagesLoaded(posterRef.value)
    // 按需加载，避免首屏携带 html2canvas
    const { default: html2canvas } = await import('html2canvas')
    // 预览在手机上会等比缩小；导出时临时去掉 transform，保证输出完整尺寸。
    const previousTransform = posterRef.value.style.transform
    posterRef.value.style.transform = 'none'
    let canvas: HTMLCanvasElement
    try {
      canvas = await html2canvas(posterRef.value, {
        scale: 2,
        useCORS: true,
        backgroundColor: '#ffffff',
        logging: false
      })
    } finally {
      posterRef.value.style.transform = previousTransform
    }
    const link = document.createElement('a')
    link.download = `travelmapper-note-${note.value.id}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    ElMessage.success('海报已生成')
  } catch (err) {
    ElMessage.error(
      (err as Error).message || '海报生成失败，请确认对象存储允许跨域访问图片'
    )
  } finally {
    rendering.value = false
  }
}

function formatDate(value: string | null | undefined): string {
  return formatDateValue(value) || ''
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-card>
      <template #header><div class="trip-title">生成游记海报</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-input
          v-model="openId"
          placeholder="游记 ID"
          style="width: 160px"
          @keyup.enter="openNote"
        />
        <el-button @click="openNote">打开游记</el-button>
        <template v-if="note">
          <el-select v-model="expireDays" style="width: 140px">
            <el-option label="分享 7 天" :value="7" />
            <el-option label="分享 30 天" :value="30" />
            <el-option label="永久分享" :value="0" />
          </el-select>
          <el-button type="primary" :loading="rendering" @click="download">下载海报</el-button>
        </template>
      </div>
      <div class="meta" style="margin-top: 8px">
        生成海报会自动开启（或复用）分享令牌；海报只使用分享页白名单内容（标题、摘要、封面、最多 6 张图片），不包含消费金额和私密正文。不需要公开时可回游记详情撤销分享。
      </div>
    </el-card>

    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      show-icon
      style="margin-top: 16px"
      :title="error"
    />

    <div v-if="share" ref="stageRef" class="poster-stage">
      <div
        class="poster-fit"
        :style="{
          width: `${POSTER_WIDTH * posterScale}px`,
          height: `${posterHeight * posterScale}px`
        }"
      >
        <div
          ref="posterRef"
          class="poster"
          :style="{ transform: `scale(${posterScale})` }"
        >
        <div class="poster-head">
          <span class="poster-brand">旅迹地图</span>
          <span class="poster-slogan">把走过的路，留在地图上</span>
        </div>

        <div class="poster-title">{{ share.title }}</div>
        <div class="poster-meta">
          {{ share.countryName }} · {{ share.cityName }} · {{ formatDate(share.createdAt) }}
        </div>

        <div v-if="share.coverImageUrl" class="poster-cover">
          <img :src="share.coverImageUrl" alt="封面" />
        </div>

        <p class="poster-summary">{{ share.summary }}</p>

        <div v-if="gallery.length" class="poster-gallery">
          <img
            v-for="image in gallery"
            :key="image"
            :src="image"
            alt="旅途照片"
            loading="lazy"
            decoding="async"
          />
        </div>

        <canvas ref="routeCanvas" class="poster-route" />

        <div class="poster-stats">
          <div class="poster-stat">
            <b>{{ totals.countries }}</b>
            <span>点亮国家</span>
          </div>
          <div class="poster-stat">
            <b>{{ totals.itineraryCount }}</b>
            <span>行程</span>
          </div>
          <div class="poster-stat">
            <b>{{ totals.noteCount }}</b>
            <span>游记</span>
          </div>
        </div>

        <div class="poster-foot">
          <canvas ref="qrCanvas" class="poster-qr" />
          <div class="poster-foot-text">
            <div>扫码查看分享游记</div>
            <div class="poster-foot-nick">{{ share.nicknameMasked }}</div>
          </div>
        </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.poster-stage {
  margin-top: 16px;
  overflow-x: auto;
  padding-bottom: 16px;
}

.poster-fit {
  position: relative;
  margin: 0 auto;
  overflow: hidden;
}

.poster {
  width: 720px;
  transform-origin: top left;
  padding: 40px;
  background: linear-gradient(160deg, #f7f9ff 0%, #ffffff 45%, #eef4ff 100%);
  border: 1px solid #e4e7ed;
  border-radius: 18px;
  box-shadow: 0 18px 48px rgba(31, 45, 61, 0.12);
  color: #303133;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  box-sizing: border-box;
}

.poster-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  border-bottom: 2px solid #4c6ef5;
  padding-bottom: 12px;
}

.poster-brand {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #3b5bdb;
}

.poster-slogan {
  font-size: 13px;
  color: #909399;
}

.poster-title {
  margin-top: 28px;
  font-size: 34px;
  line-height: 1.35;
  font-weight: 700;
}

.poster-meta {
  margin-top: 10px;
  font-size: 15px;
  color: #606266;
}

.poster-cover {
  margin-top: 22px;
  border-radius: 12px;
  overflow: hidden;
}

.poster-cover img {
  width: 100%;
  max-height: 340px;
  object-fit: cover;
  display: block;
}

.poster-summary {
  margin-top: 18px;
  font-size: 16px;
  line-height: 1.9;
  color: #444;
  white-space: pre-line;
}

.poster-gallery {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.poster-gallery img {
  width: 100%;
  height: 130px;
  object-fit: cover;
  border-radius: 8px;
  display: block;
}

.poster-route {
  display: block;
  margin: 22px auto 0;
}

.poster-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 10px;
  padding: 16px 0;
  background: #f4f7ff;
  border-radius: 12px;
}

.poster-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.poster-stat b {
  font-size: 28px;
  color: #3b5bdb;
}

.poster-stat span {
  font-size: 13px;
  color: #606266;
}

.poster-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #c0c4cc;
}

.poster-qr {
  width: 150px;
  height: 150px;
}

.poster-foot-text {
  font-size: 16px;
  color: #303133;
}

.poster-foot-nick {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
</style>
