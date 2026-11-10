<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import echarts, { type EChartsType } from '../utils/echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import CountryCitySelector from '../components/CountryCitySelector.vue'
import { currentBeijingMonth, monthRange, todayInBeijing } from '../utils/datetime'
import { getCities, getCountries } from '../api/catalog'
import { getTripDetail, getTrips } from '../api/trips'
import {
  createExpense,
  deleteExpense,
  getExpenseSummary,
  listExpenses,
  listExpensesByDate,
  updateExpense,
  type ExpenseDateGrouped,
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
  { value: 'trip', label: '按归档' },
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
const viewMode = ref<'LIST' | 'DATE'>('LIST')
const dateGrouped = ref<ExpenseDateGrouped | null>(null)
const dateLoading = ref(false)
const month = ref(currentBeijingMonth())
const monthSummary = ref<ExpenseSummary | null>(null)

const formVisible = ref(false)
const form = reactive({
  id: 0,
  country: 'JP',
  city: '',
  tripId: null as number | null,
  currency: 'CNY',
  amount: '',
  category: 'FOOD',
  expenseDate: todayInBeijing(),
  remark: ''
})

const pendingTripId = ref<number | null>(null)
const chartEl = ref<HTMLDivElement | null>(null)
const categoryChartEl = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null
let categoryChart: EChartsType | null = null

const PREF_KEY = 'tm_expense_prefs'

const amountPreview = computed(() => {
  const raw = form.amount.trim()
  if (!raw || !/[+\-*/()]/.test(raw)) {
    return ''
  }
  const value = evaluateExpression(raw)
  return value === null ? '表达式无效' : `= ${value}`
})

const monthTotalCny = computed(() => {
  const groups = monthSummary.value?.groups ?? []
  return groups
    .reduce((total, group) => total + Number(group.totalCny || 0), 0)
    .toFixed(2)
})

const monthOriginalCurrencies = computed(() => {
  const totals = new Map<string, number>()
  for (const group of monthSummary.value?.groups ?? []) {
    for (const item of group.originalCurrencies) {
      totals.set(item.currency, (totals.get(item.currency) ?? 0) + Number(item.total || 0))
    }
  }
  return [...totals.entries()].map(([currency, total]) => ({
    currency,
    total: total.toFixed(2)
  }))
})

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
watch(viewMode, (mode) => {
  if (mode === 'DATE') {
    void loadDateGroups()
  }
})
watch(summary, async () => {
  await nextTick()
  renderChart()
  renderCategoryChart()
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
    formTrips.value = []
    if (!code) {
      form.tripId = null
      return
    }
    try {
      const trips = await getTrips(code)
      formTrips.value = trips
      // 只有当前归档不属于新国家时才清空，避免编辑表单回填时丢失归档。
      if (form.tripId && !trips.some((trip) => trip.id === form.tripId)) {
        form.tripId = null
      }
    } catch {
      formTrips.value = []
    }
  }
)

async function loadAll(): Promise<void> {
  const tasks = [loadList(), loadSummary(), loadMonthSummary()]
  if (viewMode.value === 'DATE') {
    tasks.push(loadDateGroups())
  }
  await Promise.all(tasks)
}

function filterParams(): Record<string, unknown> {
  return {
    countryCode: filters.countryCode || undefined,
    cityCode: filters.cityCode || undefined,
    tripId: filters.tripId ?? undefined,
    category: filters.category || undefined,
    from: filters.range?.[0] || undefined,
    to: filters.range?.[1] || undefined
  }
}

async function loadDateGroups(): Promise<void> {
  dateLoading.value = true
  try {
    dateGrouped.value = await listExpensesByDate(filterParams())
  } catch (error) {
    ElMessage.error((error as Error).message || '加载按日期消费失败')
    dateGrouped.value = null
  } finally {
    dateLoading.value = false
  }
}

async function loadMonthSummary(): Promise<void> {
  const [from, to] = monthRange(month.value)
  try {
    monthSummary.value = await getExpenseSummary('country', {
      countryCode: filters.countryCode || undefined,
      cityCode: filters.cityCode || undefined,
      tripId: filters.tripId ?? undefined,
      category: filters.category || undefined,
      from,
      to
    })
  } catch {
    monthSummary.value = null
  }
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
      cityCode: filters.cityCode || undefined,
      tripId: filters.tripId ?? undefined,
      category: filters.category || undefined,
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
  const prefs = loadPrefs()
  const preferredCountry = prefs?.countryCode
    && countries.value.some((item) => item.code === prefs.countryCode)
    ? prefs.countryCode
    : ''
  form.id = 0
  form.country = filters.countryCode || preferredCountry || countries.value[0]?.code || 'JP'
  form.city = filters.cityCode || ''
  form.tripId = filters.tripId
  form.currency = prefs?.currency || 'CNY'
  form.amount = ''
  form.category = prefs?.category || 'FOOD'
  form.expenseDate = todayInBeijing()
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
  const amount = normalizeAmountInput(form.amount)
  if (amount === null) {
    ElMessage.warning('金额或表达式无效')
    return
  }
  saving.value = true
  try {
    const body = {
      countryCode: form.country,
      cityCode: form.city || null,
      tripId: form.tripId ?? null,
      currency: form.currency.trim().toUpperCase(),
      amount,
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
    if (!form.id) {
      savePrefs({
        countryCode: form.country,
        currency: form.currency.trim().toUpperCase(),
        category: form.category
      })
    }
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
    const result = await deleteExpense(row.id)
    await loadAll()
    ElMessage.success(result.tripDeleted ? '已删除，旅行已无内容并自动清理' : '已删除')
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

function renderCategoryChart(): void {
  const el = categoryChartEl.value
  if (!el || !summary.value) return
  if (categoryChart && categoryChart.getDom() !== el) {
    categoryChart.dispose()
    categoryChart = null
  }
  if (!categoryChart) {
    categoryChart = echarts.init(el)
  }
  const totals = new Map<string, number>()
  for (const group of summary.value.groups) {
    for (const item of group.categories) {
      totals.set(item.category, (totals.get(item.category) ?? 0) + Number(item.totalCny))
    }
  }
  const data = [...totals.entries()]
    .filter(([, value]) => value !== 0)
    .map(([category, value]) => ({ name: categoryLabel(category), value }))
  if (data.length === 0) {
    categoryChart.clear()
    return
  }
  categoryChart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        valueFormatter: (value: unknown) => `¥${Number(value).toFixed(2)}`
      },
      legend: { bottom: 0, textStyle: { color: '#52514e' } },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '44%'],
          data,
          label: { formatter: '{b} ¥{c}' },
          itemStyle: { borderRadius: 4, borderColor: '#ffffff', borderWidth: 2 }
        }
      ]
    },
    true
  )
  categoryChart.resize()
}

function resizeChart(): void {
  chart?.resize()
  categoryChart?.resize()
}

function normalizeAmountInput(raw: string): string | null {
  const text = raw.trim()
  if (/^-?\d+(\.\d{1,2})?$/.test(text)) {
    return text
  }
  return evaluateExpression(text)
}

function evaluateExpression(input: string): string | null {
  const expr = input.replace(/\s+/g, '').replace(/×/g, '*').replace(/÷/g, '/')
  if (!expr || expr.length > 100 || !/^[0-9+\-*/().]+$/.test(expr)) {
    return null
  }
  let pos = 0
  const peek = () => expr[pos]
  const fail = (): never => {
    throw new Error('invalid')
  }
  const parseExpression = (): number => {
    let value = parseTerm()
    while (peek() === '+' || peek() === '-') {
      const op = expr[pos++]
      const right = parseTerm()
      value = op === '+' ? value + right : value - right
    }
    return value
  }
  const parseTerm = (): number => {
    let value = parseFactor()
    while (peek() === '*' || peek() === '/') {
      const op = expr[pos++]
      const right = parseFactor()
      if (op === '/' && right === 0) {
        fail()
      }
      value = op === '*' ? value * right : value / right
    }
    return value
  }
  const parseFactor = (): number => {
    if (peek() === '+' || peek() === '-') {
      const op = expr[pos++]
      const value = parseFactor()
      return op === '-' ? -value : value
    }
    if (peek() === '(') {
      pos++
      const value = parseExpression()
      if (peek() !== ')') {
        fail()
      }
      pos++
      return value
    }
    const match = /^\d*\.?\d+/.exec(expr.slice(pos))
    if (!match) {
      throw new Error('invalid')
    }
    pos += match[0].length
    const value = Number(match[0])
    if (!Number.isFinite(value)) {
      fail()
    }
    return value
  }
  try {
    const value = parseExpression()
    if (pos !== expr.length || !Number.isFinite(value)) {
      return null
    }
    return (Math.round((value + Number.EPSILON) * 100) / 100).toFixed(2)
  } catch {
    return null
  }
}

function loadPrefs(): { countryCode: string; currency: string; category: string } | null {
  try {
    const raw = localStorage.getItem(PREF_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (typeof parsed?.countryCode !== 'string'
        || typeof parsed?.currency !== 'string'
        || typeof parsed?.category !== 'string') {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function savePrefs(prefs: { countryCode: string; currency: string; category: string }): void {
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify(prefs))
  } catch {
    // 忽略隐私模式下的存储失败
  }
}


function categoryLabel(value: string): string {
  return CATEGORIES.find((item) => item.value === value)?.label || value
}

function tripLabel(trip: TripView): string {
  return `${trip.name}${trip.startDate ? `（${trip.startDate}）` : ''}`
}

</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header>
        <div class="expense-card-head">
          <div class="trip-title">记账</div>
          <el-button type="success" @click="openCreate">记一笔</el-button>
        </div>
      </template>
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
          placeholder="全部归档"
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
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="meta" style="margin-top: 8px">
        列表、汇总与月度合计使用相同的筛选条件（月度合计固定按所选月份统计）。
      </div>
    </el-card>

    <el-card class="month-card" style="margin-top: 16px">
      <template #header>
        <div class="month-head">
          <span class="trip-title">月度合计</span>
          <el-date-picker
            v-model="month"
            type="month"
            value-format="YYYY-MM"
            style="width: 160px"
            @change="loadMonthSummary"
          />
        </div>
      </template>
      <div class="month-total">¥{{ monthTotalCny }}</div>
      <div v-if="monthOriginalCurrencies.length" class="month-originals">
        <span v-for="item in monthOriginalCurrencies" :key="item.currency">
          {{ item.currency }} {{ item.total }}
        </span>
      </div>
      <div v-else-if="monthTotalCny === '0.00'" class="meta">当前筛选下本月暂无消费</div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
          <div class="trip-title" style="margin-bottom: 0">消费记录</div>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="LIST">明细</el-radio-button>
            <el-radio-button value="DATE">按日期</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <template v-if="viewMode === 'LIST'">
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
      </template>
      <div v-else v-loading="dateLoading">
        <el-empty
          v-if="!dateGrouped || dateGrouped.groups.length === 0"
          description="当前筛选下暂无消费记录"
        />
        <template v-else>
          <div v-for="group in dateGrouped.groups" :key="group.date" class="expense-date-group">
            <div class="expense-date-head">
              <strong>{{ group.date }}</strong>
              <span class="expense-date-total">CNY ¥{{ group.totalCny }}</span>
              <span v-for="item in group.originalCurrencies" :key="item.currency" class="meta">
                {{ item.currency }} {{ item.total }}
              </span>
            </div>
            <div v-for="row in group.items" :key="row.id" class="expense-date-row">
              <el-tag size="small">{{ categoryLabel(row.category) }}</el-tag>
              <span class="expense-date-place">
                {{ row.countryName }}<template v-if="row.cityName"> · {{ row.cityName }}</template>
              </span>
              <span class="expense-date-amount">
                {{ row.amount }} {{ row.currency }}
                <template v-if="!row.rateMissing"> ≈ ¥{{ row.amountCny }}</template>
                <span v-else class="meta">（未配置汇率）</span>
              </span>
              <span class="meta expense-date-remark">{{ row.remark }}</span>
              <div class="expense-date-actions">
                <el-button text size="small" type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
              </div>
            </div>
          </div>
          <div class="meta" style="margin-top: 8px">
            筛选合计 CNY ¥{{ dateGrouped.totalCny }}
            <span v-if="dateGrouped.hasUnconfiguredCurrency">
              · 未配置汇率：{{ dateGrouped.unconfiguredCurrencies.join('、') }}
            </span>
          </div>
        </template>
      </div>
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
        <div class="expense-charts">
          <div ref="chartEl" class="expense-chart" />
          <div ref="categoryChartEl" class="expense-chart" />
        </div>
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
        <el-form-item label="归档">
          <el-select v-model="form.tripId" clearable filterable placeholder="不关联归档" style="width: 100%">
            <el-option v-for="trip in formTrips" :key="trip.id" :label="tripLabel(trip)" :value="trip.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种" required>
          <el-input v-model="form.currency" maxlength="3" style="width: 120px" />
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input v-model="form.amount" placeholder="如 125.00，支持 12+3.5*2，可为负数" />
          <div v-if="amountPreview" class="meta" style="margin-top: 4px">
            {{ amountPreview }}
          </div>
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
.expense-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

/* 移动端：表格与筛选控件的固有宽度会把卡片撑出视口，
   限制卡片宽度并让卡片内部横向滚动，保证标题栏操作按钮可见。 */
@media (max-width: 768px) {
  :deep(.el-card) {
    max-width: 100%;
  }

  :deep(.el-card__body) {
    overflow-x: auto;
  }
}
.expense-charts {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 1fr);
  gap: 16px;
  margin-top: 12px;
}

.expense-chart {
  width: 100%;
  height: 320px;
}

.month-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.month-head .trip-title {
  margin-bottom: 0;
}

.month-total {
  font-size: 28px;
  font-weight: 600;
  color: var(--tm-brand);
}

.month-originals {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 4px;
  font-size: 13px;
  color: var(--tm-text-secondary);
}

.expense-date-group {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: var(--tm-surface);
  border: 1px solid var(--tm-border);
  border-radius: var(--tm-radius);
}

.expense-date-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--tm-border);
}

.expense-date-total {
  color: var(--tm-brand);
  font-weight: 600;
}

.expense-date-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--tm-border);
  font-size: 13px;
}

.expense-date-row:last-child {
  border-bottom: 0;
}

.expense-date-place {
  min-width: 110px;
}

.expense-date-amount {
  min-width: 180px;
}

.expense-date-remark {
  flex: 1;
  min-width: 120px;
}

.expense-date-actions {
  display: flex;
  gap: 4px;
}

@media (max-width: 640px) {
  .expense-charts {
    grid-template-columns: 1fr;
  }

  .expense-chart {
    height: 260px;
  }

  .expense-date-row {
    align-items: flex-start;
  }

  .expense-date-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
