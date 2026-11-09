<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ChecklistItem } from '../api/prep'
import { newRequestId } from '../utils/uuid'

const props = defineProps<{
  countryName?: string
}>()

const items = defineModel<ChecklistItem[]>('items', { required: true })
const emit = defineEmits<{
  (event: 'change', items: ChecklistItem[]): void
}>()

const open = ref(['prep'])
const adding = ref(false)
const draftTitle = ref('')
const draftDays = ref(7)
const draftDescription = ref('')

const checkedCount = computed(() =>
  (items.value ?? []).filter((item) => item.checked).length
)

function updateItems(next: ChecklistItem[]): void {
  items.value = next
  emit('change', next)
}

function setChecked(item: ChecklistItem, checked: boolean): void {
  updateItems(
    (items.value ?? []).map((current) =>
      current.itemKey === item.itemKey ? { ...current, checked } : current
    )
  )
}

function removeCustom(item: ChecklistItem): void {
  if (!item.custom) return
  updateItems((items.value ?? []).filter((current) => current.itemKey !== item.itemKey))
}

function resetAddForm(): void {
  adding.value = false
  draftTitle.value = ''
  draftDays.value = 7
  draftDescription.value = ''
}

function addItem(): void {
  const title = draftTitle.value.trim()
  if (!title) {
    ElMessage.warning('请输入准备事项')
    return
  }
  if (draftDescription.value.length > 255) {
    ElMessage.warning('说明不能超过 255 字')
    return
  }
  const current = items.value ?? []
  const nextSort =
    current.reduce((max, item) => Math.max(max, item.sortOrder || 0), 100) + 10
  const next: ChecklistItem = {
    id: null,
    itemKey: `CUSTOM_${newRequestId().replace(/-/g, '')}`,
    label: title,
    title,
    description: draftDescription.value.trim() || null,
    daysBefore: draftDays.value,
    checked: false,
    custom: true,
    sortOrder: nextSort
  }
  updateItems([...current, next])
  resetAddForm()
}
</script>

<template>
  <el-card class="prep-card">
    <el-collapse v-model="open">
      <el-collapse-item name="prep">
        <template #title>
          <div class="prep-head">
            <strong>行前准备</strong>
            <span class="meta">
              {{ countryName || '目的地' }} · 已完成 {{ checkedCount }}/{{ items.length }}
            </span>
          </div>
        </template>
        <div class="meta prep-hint">
          出发前按时间节点逐项确认；内容为通用提醒，请以官方最新政策为准。
        </div>
        <div v-for="item in items" :key="item.itemKey" class="prep-row">
          <div class="prep-main">
            <el-checkbox
              :model-value="item.checked"
              @change="(value: boolean | string | number) => setChecked(item, Boolean(value))"
            >
              {{ item.title }}
              <el-tag v-if="item.custom" type="info" size="small" effect="plain">自定义</el-tag>
            </el-checkbox>
            <div v-if="item.description" class="meta prep-desc">{{ item.description }}</div>
          </div>
          <el-tag size="small" effect="plain" class="prep-time">
            出发前 {{ item.daysBefore }} 天
          </el-tag>
          <el-button
            v-if="item.custom"
            text
            type="danger"
            size="small"
            class="prep-delete"
            @click="removeCustom(item)"
          >
            删除
          </el-button>
        </div>

        <div class="prep-add">
          <el-button v-if="!adding" text type="primary" @click="adding = true">
            + 添加一项
          </el-button>
          <div v-else class="prep-add-form">
            <el-input
              v-model="draftTitle"
              maxlength="64"
              placeholder="准备事项，如：租车"
              style="width: 220px"
            />
            <el-input-number v-model="draftDays" :min="0" :max="365" />
            <el-input
              v-model="draftDescription"
              maxlength="255"
              placeholder="说明（可选）"
              style="width: 260px"
            />
            <el-button type="primary" size="small" @click="addItem">添加</el-button>
            <el-button text size="small" @click="resetAddForm">取消</el-button>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<style scoped>
.prep-card {
  margin-bottom: 16px;
}

.prep-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.prep-head strong {
  font-size: 15px;
}

.prep-hint {
  margin-bottom: 8px;
}

.prep-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--tm-border);
}

.prep-row:last-of-type {
  border-bottom: none;
}

.prep-main {
  flex: 1;
}

.prep-desc {
  margin-top: 2px;
  padding-left: 24px;
}

.prep-time {
  flex: 0 0 auto;
}

.prep-delete {
  flex: 0 0 auto;
}

.prep-add {
  margin-top: 10px;
}

.prep-add-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
</style>