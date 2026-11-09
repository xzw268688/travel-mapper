<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  applyExchangeRateRefresh,
  listExchangeRates,
  previewExchangeRateRefresh,
  upsertExchangeRate,
  type ExchangeRateRefreshResponse,
  type ExchangeRateView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'

const rates = ref<ExchangeRateView[]>([])
const loading = ref(false)
const saving = ref(false)
const currency = ref('')
const rateToCny = ref('')
const refreshing = ref(false)
const applying = ref(false)
const refreshDialogVisible = ref(false)
const refreshPreview = ref<ExchangeRateRefreshResponse | null>(null)

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

async function openRefresh(): Promise<void> {
  refreshing.value = true
  try {
    refreshPreview.value = await previewExchangeRateRefresh()
    refreshDialogVisible.value = true
    if (refreshPreview.value.updatedCount === 0) {
      ElMessage.info('所有已配置币种均为最新汇率')
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '获取最新汇率失败')
  } finally {
    refreshing.value = false
  }
}

async function applyRefresh(): Promise<void> {
  if (!refreshPreview.value || refreshPreview.value.updatedCount === 0) {
    return
  }
  applying.value = true
  try {
    const result = await applyExchangeRateRefresh()
    refreshPreview.value = result
    await load()
    refreshDialogVisible.value = false
    ElMessage.success(`已更新 ${result.updatedCount} 个币种汇率`)
  } catch (error) {
    ElMessage.error((error as Error).message || '更新汇率失败')
  } finally {
    applying.value = false
  }
}

function refreshStatusLabel(status: string): string {
  if (status === 'CHANGED') {
    return refreshPreview.value?.applied ? '已更新' : '待更新'
  }
  if (status === 'UNSUPPORTED') {
    return '数据源不支持'
  }
  return '无变化'
}

function refreshStatusType(status: string): 'success' | 'info' | 'warning' {
  if (status === 'CHANGED') {
    return 'success'
  }
  if (status === 'UNSUPPORTED') {
    return 'warning'
  }
  return 'info'
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
        <el-button type="success" :loading="refreshing" @click="openRefresh">一键更新汇率</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
      <AdminPageIntro title="这个页面是干什么的？">
        维护各币种兑 CNY 的汇率，用于把记账金额换算成人民币汇总。新增币种会回填该币种未配置汇率的历史消费；
        修改已有币种只影响之后的新消费，历史进账不变。「一键更新」会从公开汇率源拉取已有币种的最新价，
        先预览再确认写入。
      </AdminPageIntro>
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

    <el-dialog v-model="refreshDialogVisible" title="汇率一键更新" width="640px">
      <template v-if="refreshPreview">
        <div class="meta" style="margin-bottom: 12px">
          数据源：{{ refreshPreview.source }} · 获取时间：{{ formatTime(refreshPreview.fetchedAt) }}
        </div>
        <el-alert
          v-if="refreshPreview.unsupportedCount > 0"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          :title="`${refreshPreview.unsupportedCount} 个币种不在数据源覆盖范围内，将保持原值`"
        />
        <el-table :data="refreshPreview.items" stripe max-height="360">
          <el-table-column prop="currency" label="币种" width="90" />
          <el-table-column prop="oldRate" label="当前汇率" width="150" />
          <el-table-column label="最新汇率" width="150">
            <template #default="{ row }">{{ row.newRate || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="refreshStatusType(row.status)" size="small">
                {{ refreshStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="meta" style="margin-top: 12px">
          将更新 {{ refreshPreview.updatedCount }} 个币种，{{ refreshPreview.unchangedCount }} 个无变化。
          确认后立即生效，并写入审计日志。
        </div>
      </template>
      <template #footer>
        <el-button @click="refreshDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="applying"
          :disabled="!refreshPreview || refreshPreview.updatedCount === 0"
          @click="applyRefresh"
        >
          确认更新 {{ refreshPreview?.updatedCount ?? 0 }} 个
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
