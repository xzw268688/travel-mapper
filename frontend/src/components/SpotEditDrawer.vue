<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

export interface SpotForm {
  name: string
  description: string
  suggestedDuration: string
  category: string
  ticketPrice: string
  highlights: string
  bestTime: string
  nearestStation: string
  reservationRequired: boolean
  area: string
  warningNotes: string
}

const props = defineProps<{
  visible: boolean
  title: string
  initial: SpotForm
}>()

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'submit', value: SpotForm): void
}>()

const form = reactive<SpotForm>(emptyForm())
const advanced = ref<string[]>([])
const isMobile = ref(false)

const visibleModel = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      Object.assign(form, props.initial)
      advanced.value = []
    }
  }
)

function emptyForm(): SpotForm {
  return {
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
  }
}

function syncViewport(): void {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})

function submit(): void {
  emit('submit', { ...form })
}
</script>

<template>
  <el-drawer
    v-model="visibleModel"
    :title="title"
    :direction="isMobile ? 'btt' : 'rtl'"
    :size="isMobile ? '92%' : '480px'"
    class="spot-edit-drawer"
  >
    <el-form label-position="top">
      <div class="drawer-section-title">基本信息</div>
      <el-form-item label="景点" required>
        <el-input v-model="form.name" maxlength="50" show-word-limit />
      </el-form-item>
      <div class="spot-grid">
        <el-form-item label="建议时长">
          <el-input v-model="form.suggestedDuration" maxlength="20" placeholder="如 2 小时" />
        </el-form-item>
        <el-form-item label="类别">
          <el-input v-model="form.category" maxlength="32" placeholder="如 博物馆 / 自然" />
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="form.area" maxlength="128" placeholder="如 台东区" />
        </el-form-item>
        <el-form-item label="需预约">
          <el-switch v-model="form.reservationRequired" />
        </el-form-item>
      </div>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-collapse v-model="advanced">
        <el-collapse-item name="advanced" title="更多信息（门票、交通、亮点与提醒）">
          <el-form-item label="门票">
            <el-input v-model="form.ticketPrice" maxlength="64" placeholder="原币文本，如 约 2000 円 / 免费" />
          </el-form-item>
          <div class="spot-grid">
            <el-form-item label="最佳时段">
              <el-input v-model="form.bestTime" maxlength="64" placeholder="如 上午 / 傍晚" />
            </el-form-item>
            <el-form-item label="最近车站">
              <el-input v-model="form.nearestStation" maxlength="128" placeholder="如 浅草站" />
            </el-form-item>
          </div>
          <el-form-item label="亮点">
            <el-input v-model="form.highlights" maxlength="255" show-word-limit />
          </el-form-item>
          <el-form-item label="避雷备注">
            <el-input v-model="form.warningNotes" maxlength="255" show-word-limit />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
      <div class="meta drawer-hint">AI 生成的门票与交通信息仅供参考，出行前请核实。</div>
    </el-form>
    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="primary" @click="submit">确定</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.spot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.drawer-section-title {
  font-weight: 600;
  margin: 4px 0 12px;
}

.drawer-hint {
  margin-top: 12px;
}

@media (max-width: 480px) {
  .spot-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>