<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listExchangeRates,
  upsertExchangeRate,
  type ExchangeRateView
} from '../api/admin'

const rates = ref<ExchangeRateView[]>([])
const loading = ref(false)
const saving = ref(false)
const currency = ref('')
const rateToCny = ref('')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    rates.value = await listExchangeRates()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载汇率失败')
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  const code = currency.value.trim().toUpperCase()
  if (!/^[A-Z]{3}$/.test(code)) {
    ElMessage.warning('币种必须是 3 位大写字母')
    return
  }
  if (!rateToCny.value.trim()) {
    ElMessage.warning('请填写汇率')
    return
  }
  saving.value = true
  try {
    const result = await upsertExchangeRate(code, rateToCny.value.trim())
    const backfill = result.created
      ? `，已回填 ${result.affectedUnconfiguredRecords} 条历史消费`
      : '（已有币种，不回填历史消费）'
    ElMessage.success(`${code} 汇率已保存${backfill}`)
    currency.value = ''
    rateToCny.value = ''
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '保存汇率失败')
  } finally {
    saving.value = false
  }
}

function edit(row: ExchangeRateView): void {
  currency.value = row.currency
  rateToCny.value = row.rateToCny
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">汇率维护</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-input v-model="currency" maxlength="3" placeholder="币种，如 JPY" style="width: 160px" />
        <el-input v-model="rateToCny" placeholder="汇率，1 单位币种 = ? CNY" style="width: 260px" />
        <el-button type="primary" :loading="saving" @click="submit">保存汇率</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="新增币种会回填该币种未配置汇率的历史消费；修改已有币种只影响之后的新消费，历史汇总不变。"
      />
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <template #header><div class="trip-title">已有汇率</div></template>
      <el-empty v-if="rates.length === 0" description="暂无汇率配置" />
      <el-table v-else :data="rates" stripe>
        <el-table-column prop="currency" label="币种" width="120" />
        <el-table-column prop="rateToCny" label="汇率（→ CNY）" width="200" />
        <el-table-column label="更新时间" width="200">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="edit(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
