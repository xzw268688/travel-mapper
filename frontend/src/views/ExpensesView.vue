<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import CountryCitySelector from '../components/CountryCitySelector.vue'
import {
  createExpense,
  deleteExpense,
  getExpenseSummary,
  listExpenses,
  type ExpenseResponse,
  type ExpenseSummary
} from '../api/expenses'

const CATEGORIES = [
  { value: 'TRANSPORT', label: '交通' },
  { value: 'ACCOMMODATION', label: '住宿' },
  { value: 'FOOD', label: '餐饮' },
  { value: 'TICKET', label: '门票' },
  { value: 'SHOPPING', label: '购物' },
  { value: 'COMMUNICATION', label: '通讯' },
  { value: 'OTHER', label: '其他' }
]

const country = ref('JP')
const city = ref('')
const currency = ref('CNY')
const amount = ref('')
const category = ref('FOOD')
const expenseDate = ref(todayString())
const remark = ref('')
const items = ref<ExpenseResponse[]>([])
const summary = ref<ExpenseSummary | null>(null)
const groupBy = ref('country')
const loading = ref(false)

onMounted(loadAll)
watch(groupBy, loadSummary)

async function loadAll(): Promise<void> {
  await Promise.all([loadList(), loadSummary()])
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const result = await listExpenses({ page: 1, size: 50 })
    items.value = result.list
  } catch (error) {
    ElMessage.error((error as Error).message || '加载消费失败')
  } finally {
    loading.value = false
  }
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await getExpenseSummary(groupBy.value)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载汇总失败')
  }
}

async function add(): Promise<void> {
  if (!country.value || !amount.value.trim()) {
    ElMessage.warning('请选择国家并填写金额')
    return
  }
  try {
    await createExpense({
      countryCode: country.value,
      cityCode: city.value || null,
      currency: currency.value.toUpperCase(),
      amount: amount.value,
      category: category.value,
      expenseDate: expenseDate.value,
      remark: remark.value
    })
    amount.value = ''
    remark.value = ''
    await loadAll()
    ElMessage.success('消费已记录')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  }
}

async function remove(id: number): Promise<void> {
  try {
    await deleteExpense(id)
    await loadAll()
  } catch (error) {
    ElMessage.error((error as Error).message || '删除失败')
  }
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
      <template #header><div class="trip-title">记一笔</div></template>
      <CountryCitySelector v-model:country="country" v-model:city="city" />
      <div class="selector-row" style="margin-top: 12px">
        <el-input v-model="currency" maxlength="3" style="width: 100px" placeholder="CNY" />
        <el-input v-model="amount" style="width: 160px" placeholder="金额，如 125.00" />
        <el-select v-model="category" style="width: 140px">
          <el-option v-for="item in CATEGORIES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker v-model="expenseDate" type="date" value-format="YYYY-MM-DD" />
        <el-input v-model="remark" style="width: 200px" maxlength="100" placeholder="备注" />
        <el-button type="primary" @click="add">保存消费</el-button>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <template #header><div class="trip-title">消费记录</div></template>
      <el-empty v-if="items.length === 0" description="暂无消费记录" />
      <el-table v-else :data="items" stripe>
        <el-table-column prop="expenseDate" label="日期" width="120" />
        <el-table-column prop="countryName" label="国家" width="100" />
        <el-table-column prop="cityName" label="城市" width="100" />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="amount" label="原币金额" width="120" />
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="CNY" width="120">
          <template #default="{ row }">
            <span v-if="row.rateMissing" class="meta">未配置汇率</span>
            <span v-else>{{ row.amountCny }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="summary" style="margin-top: 16px">
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px">
          <span class="trip-title">汇总</span>
          <el-select v-model="groupBy" style="width: 140px">
            <el-option label="按国家" value="country" />
            <el-option label="按旅行" value="trip" />
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
      <el-table :data="summary.groups" style="margin-top: 8px">
        <el-table-column prop="label" label="分组" />
        <el-table-column prop="totalCny" label="CNY 小计" width="120" />
        <el-table-column label="分类小计">
          <template #default="{ row }">
            <span v-for="item in row.categories" :key="item.category" class="meta" style="margin-right: 10px">
              {{ item.category }} {{ item.totalCny }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
