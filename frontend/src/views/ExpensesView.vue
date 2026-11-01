<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import CountryCitySelector from '../components/CountryCitySelector.vue'
import { getCities, getCountries } from '../api/catalog'
import { getTripDetail, getTrips } from '../api/trips'
import {
  createExpense,
  deleteExpense,
  getExpenseSummary,
  listExpenses,
  updateExpense,
  type ExpenseResponse,
  type ExpenseSummary
} from '../api/expenses'
import type { City, Country, TripView } from '../api/types'

const route = useRoute()

const CATEGORIES = [
  { value: 'TRANSPORT', label: '交通' },
  { value: 'ACCOMMODATION', label: '住宿' },
  { value: 'FOOD', label: '餐饮' },
  { value: 'TICKET', label: '门票' },
  { value: 'SHOPPING', label: '购物' },
  { value: 'COMMUNICATION', label: '通讯' },
  { value: 'OTHER', label: '其他' }
]

const GROUP_OPTIONS = [
  { value: 'country', label: '按国家' },
  { value: 'city', label: '按城市' },
  { value: 'trip', label: '按旅行' },
  { value: 'date', label: '按日期' },
  { value: 'country,city', label: '国家 + 城市' }
]

const countries = ref<Country[]>([])
const filterCities = ref<City[]>([])
const filterTrips = ref<TripView[]>([])
const formTrips = ref<TripView[]>([])

const filters = reactive({
  countryCode: '',
  cityCode: '',
  tripId: null as number | null,
  category: '',
  ungrouped: false,
  range: [] as string[]
})

const page = ref(1)
const size = ref(20)
const total = ref(0)
const items = ref<ExpenseResponse[]>([])
const summary = ref<ExpenseSummary | null>(null)
const groupBy = ref('country')
const loading = ref(false)
const saving = ref(false)

const formVisible = ref(false)
const form = reactive({
  id: 0,
  country: 'JP',
  city: '',
  tripId: null as number | null,
  currency: 'CNY',
  amount: '',
  category: 'FOOD',
  expenseDate: todayString(),
  remark: ''
})

const pendingTripId = ref<number | null>(null)
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

onMounted(async () => {
  try {
    countries.value = await getCountries()
    if (!countries.value.some((item) => item.code === form.country)) {
      form.country = countries.value[0]?.code || ''
    }
    const queryTrip = Number(route.query.tripId)
    if (Number.isInteger(queryTrip) && queryTrip > 0) {
      try {
        const trip = await getTripDetail(queryTrip)
        filters.countryCode = trip.countryCode
        pendingTripId.value = trip.id
      } catch {
        // 旅行不存在时忽略预选
      }
    }
    await Promise.all([loadAll(), loadFormTrips()])
  } catch (error) {
    ElMessage.error((error as Error).message || '加载消费失败')
  }
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

watch(groupBy, loadSummary)
watch(summary, async () => {
  await nextTick()
  renderChart()
})
watch(
  () => filters.countryCode,
  async (code) => {
    filters.cityCode = ''
    filters.tripId = null
    filterCities.value = []
    filterTrips.value = []
    if (code) {
      try {
        const [cities, trips] = await Promise.all([getCities(code), getTrips(code)])
        filterCities.value = cities
        filterTrips.value = trips
        if (pendingTripId.value) {
          filters.tripId = filterTrips.value.some((trip) => trip.id === pendingTripId.value)
            ? pendingTripId.value
            : null
          pendingTripId.value = null
          await loadAll()
        }
      } catch {
        filterCities.value = []
        filterTrips.value = []
      }
    }
  }
)
watch(
  () => form.country,
  async (code) => {
    form.tripId = null
    formTrips.value = []
    if (code) {
      try {
        formTrips.value = await getTrips(code)
      } catch {
        formTrips.value = []
      }
    }
  }
)

async function loadAll(): Promise<void> {
  await Promise.all([loadList(), loadSummary()])
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const result = await listExpenses({
      page: page.value,
      size: size.value,
      countryCode: filters.countryCode || undefined,
      cityCode: filters.cityCode || undefined,
      tripId: filters.tripId ?? undefined,
      ungrouped: filters.ungrouped || undefined,
      category: filters.category || undefined,
      from: filters.range?.[0] || undefined,
      to: filters.range?.[1] || undefined
    })
    items.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载消费失败')
  } finally {
    loading.value = false
  }
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await getExpenseSummary(groupBy.value, {
      countryCode: filters.countryCode || undefined,
      tripId: filters.tripId ?? undefined,
      from: filters.range?.[0] || undefined,
      to: filters.range?.[1] || undefined
    })
  } catch (error) {
    ElMessage.error((error as Error).message || '加载汇总失败')
  }
}

function search(): void {
  page.value = 1
  void loadAll()
}

function resetFilters(): void {
  filters.countryCode = ''
  filters.cityCode = ''
  filters.tripId = null
  filters.category = ''
  filters.ungrouped = false
  filters.range = []
  filterCities.value = []
  filterTrips.value = []
  search()
}

async function loadFormTrips(): Promise<void> {
  if (!form.country) return
  try {
    formTrips.value = await getTrips(form.country)
  } catch {
    formTrips.value = []
  }
}

function openCreate(): void {
  form.id = 0
  form.country = filters.countryCode || countries.value[0]?.code || 'JP'
  form.city = filters.cityCode || ''
  form.tripId = filters.tripId
  form.currency = 'CNY'
  form.amount = ''
  form.category = 'FOOD'
  form.expenseDate = todayString()
  form.remark = ''
  formVisible.value = true
  void loadFormTrips()
}

function openEdit(row: ExpenseResponse): void {
  form.id = row.id
  form.country = row.countryCode
  form.city = row.cityCode || ''
  form.tripId = row.tripId
  form.currency = row.currency
  form.amount = row.amount
  form.category = row.category
  form.expenseDate = row.expenseDate
  form.remark = row.remark || ''
  formVisible.value = true
  void loadFormTrips()
}

async function submit(): Promise<void> {
  if (!form.country || !form.amount.trim()) {
    ElMessage.warning('请选择国家并填写金额')
    return
  }
  if (!/^-?\d+(\.\d{1,2})?$/.test(form.amount.trim())) {
    ElMessage.warning('金额最多两位小数')
    return
  }
  saving.value = true
  try {
    const body = {
      countryCode: form.country,
      cityCode: form.city || null,
      tripId: form.tripId ?? null,
      currency: form.currency.trim().toUpperCase(),
      amount: form.amount.trim(),
      category: form.category,
      expenseDate: form.expenseDate,
      remark: form.remark
    }
    if (form.id) {
      await updateExpense(form.id, body)
    } else {
      await createExpense(body)
    }
    formVisible.value = false
    await loadAll()
    ElMessage.success(form.id ? '消费已更新' : '消费已记录')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: ExpenseResponse): Promise<void> {
  try {
    await ElMessageBox.confirm('删除这条消费记录？', '删除消费', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteExpense(row.id)
    await loadAll()
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error((error as Error).message || '删除失败')
  }
}

function renderChart(): void {
  const el = chartEl.value
  if (!el || !summary.value) return
  if (chart && chart.getDom() !== el) {
    chart.dispose()
    chart = null
  }
  if (!chart) {
    chart = echarts.init(el)
  }
  const groups = [...summary.value.groups]
    .filter((group) => Number(group.totalCny) !== 0)
    .sort((left, right) => Number(right.totalCny) - Number(left.totalCny))
  if (groups.length === 0) {
    chart.clear()
    return
  }
  const labels = groups.map((group) => group.label)
  const values = groups.map((group) => Number(group.totalCny))
  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        valueFormatter: (value: unknown) => `¥${Number(value).toFixed(2)}`
      },
      grid: { left: 8, right: 88, top: 8, bottom: 8, containLabel: true },
      xAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: '#eef0f3' } },
        axisLabel: { color: '#52514e', formatter: (value: number) => `¥${value}` }
      },
      yAxis: {
        type: 'category',
        data: labels,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#52514e', width: 150, overflow: 'truncate' }
      },
      series: [
        {
          type: 'bar',
          data: values,
          barMaxWidth: 22,
          itemStyle: { color: '#2a78d6', borderRadius: [0, 4, 4, 0] },
          label: {
            show: true,
            position: 'right',
            color: '#52514e',
            formatter: (params: { value: number }) => `¥${Number(params.value).toFixed(2)}`
          }
        }
      ]
    },
    true
  )
  chart.resize()
}

function resizeChart(): void {
  chart?.resize()
}

function categoryLabel(value: string): string {
  return CATEGORIES.find((item) => item.value === value)?.label || value
}

function tripLabel(trip: TripView): string {
  return `${trip.name}${trip.startDate ? `（${trip.startDate}）` : ''}`
}

function todayString(): string {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">记账</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-select v-model="filters.countryCode" placeholder="全部国家" clearable filterable style="width: 160px">
          <el-option v-for="item in countries" :key="item.code" :label="item.nameZh" :value="item.code" />
        </el-select>
        <el-select
          v-model="filters.cityCode"
          placeholder="全部城市"
          clearable
          filterable
          :disabled="!filters.countryCode"
          style="width: 180px"
        >
          <el-option v-for="city in filterCities" :key="city.cityCode" :label="city.displayName" :value="city.cityCode" />
        </el-select>
        <el-select
          v-model="filters.tripId"
          placeholder="全部旅行"
          clearable
          filterable
          :disabled="!filters.countryCode"
          style="width: 200px"
        >
          <el-option v-for="trip in filterTrips" :key="trip.id" :label="tripLabel(trip)" :value="trip.id" />
        </el-select>
        <el-select v-model="filters.category" placeholder="全部分类" clearable style="width: 140px">
          <el-option v-for="item in CATEGORIES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker
          v-model="filters.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
        <el-checkbox v-model="filters.ungrouped">仅未归组</el-checkbox>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="success" @click="openCreate">记一笔</el-button>
      </div>
      <div class="meta" style="margin-top: 8px">
        汇总支持国家、旅行与日期筛选；列表筛选同时支持城市、分类与未归组。
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <template #header><div class="trip-title">消费记录</div></template>
      <el-empty v-if="items.length === 0" description="暂无消费记录" />
      <el-table v-else :data="items" stripe>
        <el-table-column prop="expenseDate" label="日期" width="120" />
        <el-table-column prop="countryName" label="国家" width="100" />
        <el-table-column prop="cityName" label="城市" width="100" />
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="原币金额" width="110" />
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="CNY" width="110">
          <template #default="{ row }">
            <span v-if="row.rateMissing" class="meta">未配置汇率</span>
            <span v-else>{{ row.amountCny }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadList"
        @size-change="search"
      />
    </el-card>

    <el-card v-if="summary" style="margin-top: 16px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
          <span class="trip-title">汇总</span>
          <el-select v-model="groupBy" style="width: 160px">
            <el-option v-for="item in GROUP_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <span class="meta">合计 ¥{{ summary.totalCny }}</span>
        </div>
      </template>
      <el-alert
        v-if="summary.hasUnconfiguredCurrency"
        type="warning"
        :closable="false"
        show-icon
        :title="`未配置汇率，未计入总额：${summary.unconfiguredCurrencies.join('、')}`"
      />
      <el-empty v-if="summary.groups.length === 0" description="当前筛选下暂无汇总数据" :image-size="70" />
      <template v-else>
        <div ref="chartEl" class="expense-chart" />
        <el-table :data="summary.groups" style="margin-top: 12px">
          <el-table-column prop="label" label="分组" min-width="160" />
          <el-table-column prop="totalCny" label="CNY 小计" width="130" />
          <el-table-column label="分类小计" min-width="260">
            <template #default="{ row }">
              <span v-for="item in row.categories" :key="item.category" class="meta" style="margin-right: 10px">
                {{ categoryLabel(item.category) }} {{ item.totalCny }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="原币明细" min-width="220">
            <template #default="{ row }">
              <span v-for="item in row.originalCurrencies" :key="item.currency" class="meta" style="margin-right: 10px">
                {{ item.currency }} {{ item.total }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑消费' : '记一笔'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="国家/城市" required>
          <CountryCitySelector v-model:country="form.country" v-model:city="form.city" />
        </el-form-item>
        <el-form-item label="旅行">
          <el-select v-model="form.tripId" clearable filterable placeholder="不关联旅行" style="width: 100%">
            <el-option v-for="trip in formTrips" :key="trip.id" :label="tripLabel(trip)" :value="trip.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种" required>
          <el-input v-model="form.currency" maxlength="3" style="width: 120px" />
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input v-model="form.amount" placeholder="如 125.00，可为负数" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="item in CATEGORIES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="100" show-word-limit placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存消费</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.expense-chart {
  width: 100%;
  height: 320px;
  margin-top: 12px;
}
</style>
