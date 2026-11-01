<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTripDetail, type TripDetail } from '../api/trips'

const route = useRoute()
const router = useRouter()
const tripId = computed(() => {
  const value = Number(route.params.id)
  return Number.isInteger(value) && value > 0 ? value : null
})

const trip = ref<TripDetail | null>(null)
const loading = ref(false)
const error = ref('')

const CATEGORY_LABELS: Record<string, string> = {
  TRANSPORT: '交通',
  ACCOMMODATION: '住宿',
  FOOD: '餐饮',
  TICKET: '门票',
  SHOPPING: '购物',
  COMMUNICATION: '通讯',
  OTHER: '其他'
}

onMounted(load)

async function load(): Promise<void> {
  if (!tripId.value) {
    error.value = '旅行不存在'
    return
  }
  loading.value = true
  error.value = ''
  try {
    trip.value = await getTripDetail(tripId.value)
  } catch (err) {
    error.value = (err as Error).message || '旅行不存在或无权访问'
  } finally {
    loading.value = false
  }
}

function categoryLabel(value: string | null): string {
  return value ? CATEGORY_LABELS[value] ?? value : '—'
}

function formatDate(value: string | null): string {
  return value ? value.slice(0, 10) : '—'
}

function addNote(): void {
  void router.push('/notes')
}

function addExpense(): void {
  void router.push({ path: '/expenses', query: { tripId: String(tripId.value) } })
}

function addItinerary(): void {
  void router.push('/plan')
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-result v-if="error" icon="error" title="无法打开旅行" :sub-title="error">
      <template #extra>
        <el-button @click="router.push('/')">返回地图</el-button>
      </template>
    </el-result>

    <template v-else-if="trip">
      <el-card>
        <template #header>
          <div class="trip-hub-header">
            <div>
              <div class="trip-title">{{ trip.name }}</div>
              <div class="meta">
                {{ trip.countryName }} · {{ formatDate(trip.startDate) }} ~ {{ formatDate(trip.endDate) }}
              </div>
            </div>
            <div class="trip-hub-actions">
              <el-button size="small" @click="addItinerary">规划行程</el-button>
              <el-button size="small" @click="addNote">写游记</el-button>
              <el-button size="small" type="primary" @click="addExpense">记一笔</el-button>
            </div>
          </div>
        </template>
        <div class="trip-stat-grid">
          <div class="trip-stat">
            <span class="meta">行程</span>
            <strong>{{ trip.summary.itineraryCount }}</strong>
          </div>
          <div class="trip-stat">
            <span class="meta">游记</span>
            <strong>{{ trip.summary.noteCount }}</strong>
          </div>
          <div class="trip-stat">
            <span class="meta">消费笔数</span>
            <strong>{{ trip.summary.expenseCount }}</strong>
          </div>
          <div class="trip-stat">
            <span class="meta">合计花费</span>
            <strong>¥{{ trip.summary.expenseCny }}</strong>
          </div>
        </div>
        <el-alert
          v-if="trip.summary.hasUnconfiguredCurrency"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top: 12px"
          :title="`未配置汇率，未计入合计：${trip.summary.unconfiguredCurrencies.join('、')}`"
        />
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>
          <div class="section-head">
            <span class="trip-title">行程</span>
            <el-button size="small" @click="addItinerary">规划新行程</el-button>
          </div>
        </template>
        <el-empty v-if="trip.itineraries.length === 0" description="还没有行程" :image-size="60" />
        <div
          v-for="item in trip.itineraries"
          :key="item.id"
          class="trip-hub-row"
          @click="router.push(`/itineraries/${item.id}`)"
        >
          <div class="trip-hub-main">
            <strong>{{ item.title }}</strong>
            <span class="meta">{{ formatDate(item.startDate) }} ~ {{ formatDate(item.endDate) }}</span>
          </div>
          <el-tag v-if="item.expired" type="info" size="small">已过期</el-tag>
          <span class="trip-hub-link">编辑 ›</span>
        </div>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>
          <div class="section-head">
            <span class="trip-title">游记</span>
            <el-button size="small" @click="addNote">写游记</el-button>
          </div>
        </template>
        <el-empty v-if="trip.notes.length === 0" description="还没有游记" :image-size="60" />
        <div
          v-for="item in trip.notes"
          :key="item.id"
          class="trip-hub-row"
          @click="router.push(`/notes/${item.id}`)"
        >
          <div class="trip-hub-main">
            <strong>{{ item.title }}</strong>
            <span class="meta">
              {{ item.cityName }} · {{ formatDate(item.createdAt) }} ·
              {{ item.visibility === 'PRIVATE' ? '私密' : '已分享' }}
            </span>
          </div>
          <el-tag v-if="item.takedown" type="danger" size="small">已下架</el-tag>
          <span class="trip-hub-link">查看 ›</span>
        </div>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>
          <div class="section-head">
            <span class="trip-title">记账</span>
            <el-button size="small" type="primary" @click="addExpense">记一笔</el-button>
          </div>
        </template>
        <el-empty v-if="trip.recentExpenses.length === 0" description="还没有消费记录" :image-size="60" />
        <template v-else>
          <div class="meta" style="margin-bottom: 8px">最近 {{ trip.recentExpenses.length }} 条，查看全部请进入记账页。</div>
          <el-table :data="trip.recentExpenses" stripe>
            <el-table-column prop="expenseDate" label="日期" width="120" />
            <el-table-column label="分类" width="100">
              <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="原币" width="110" />
            <el-table-column prop="currency" label="币种" width="80" />
            <el-table-column label="CNY" width="110">
              <template #default="{ row }">
                <span v-if="row.rateMissing" class="meta">未配置汇率</span>
                <span v-else>{{ row.amountCny }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" />
          </el-table>
        </template>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.trip-hub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.trip-hub-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.trip-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.trip-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: #f7f9fd;
  border: 1px solid var(--tm-border);
  border-radius: var(--tm-radius-sm);
}

.trip-stat strong {
  font-size: 20px;
  color: var(--tm-text);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.trip-hub-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid var(--tm-border);
  cursor: pointer;
}

.trip-hub-row:last-child {
  border-bottom: none;
}

.trip-hub-row:hover {
  background: var(--tm-brand-soft);
}

.trip-hub-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trip-hub-link {
  color: var(--tm-brand);
  font-size: 13px;
}
</style>