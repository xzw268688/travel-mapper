<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  exportCards,
  generateCards,
  getCardBatchStats,
  listCardRedemptions,
  listCards,
  voidCard,
  type CardBatchStatsView,
  type CardKeyView,
  type CardRedemptionView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'

const form = reactive({
  type: 'AI_QUOTA',
  value: 10,
  priceCny: null as number | null,
  quantity: 10,
  expireAt: '',
  prefix: ''
})
const generating = ref(false)
const generated = ref<{ batchId: string; quantity: number; codes: string[] } | null>(null)
const generatedVisible = ref(false)

const activeTab = ref<'list' | 'stats' | 'redemptions'>('list')

const batchId = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const cards = ref<CardKeyView[]>([])
const loading = ref(false)
const acting = ref(false)

const stats = ref<CardBatchStatsView[]>([])
const statsLoading = ref(false)

const redemptions = ref<CardRedemptionView[]>([])
const redemptionUser = ref('')
const redemptionPage = ref(1)
const redemptionSize = ref(20)
const redemptionTotal = ref(0)
const redemptionLoading = ref(false)

onMounted(load)

watch(activeTab, (tab) => {
  if (tab === 'stats') {
    void loadStats()
  } else if (tab === 'redemptions') {
    void loadRedemptions()
  }
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listCards({
      batchId: batchId.value.trim() || undefined,
      status: status.value || undefined,
      page: page.value,
      size: size.value
    })
    cards.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载卡密失败')
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  try {
    stats.value = await getCardBatchStats()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载批次统计失败')
  } finally {
    statsLoading.value = false
  }
}

async function loadRedemptions(): Promise<void> {
  redemptionLoading.value = true
  try {
    const user = Number(redemptionUser.value)
    const result = await listCardRedemptions({
      userId: Number.isInteger(user) && user > 0 ? user : undefined,
      page: redemptionPage.value,
      size: redemptionSize.value
    })
    redemptions.value = result.list
    redemptionTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载兑换记录失败')
  } finally {
    redemptionLoading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function searchRedemptions(): void {
  redemptionPage.value = 1
  void loadRedemptions()
}

async function generate(): Promise<void> {
  if (!form.value || form.value <= 0 || !form.quantity || form.quantity <= 0) {
    ElMessage.warning('面额与数量必须大于 0')
    return
  }
  if (form.priceCny !== null && form.priceCny < 0) {
    ElMessage.warning('售价不能为负数')
    return
  }
  generating.value = true
  try {
    generated.value = await generateCards({
      type: form.type,
      value: form.value,
      priceCny: form.priceCny,
      quantity: form.quantity,
      expireAt: form.expireAt || null,
      prefix: form.prefix.trim() || undefined
    })
    generatedVisible.value = true
    activeTab.value = 'list'
    await load()
    ElMessage.success(`已生成 ${generated.value.quantity} 张卡密`)
  } catch (error) {
    ElMessage.error((error as Error).message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function copyCodes(): Promise<void> {
  if (!generated.value) return
  try {
    await navigator.clipboard.writeText(generated.value.codes.join('\n'))
    ElMessage.success('已复制全部卡密')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

async function voidOne(card: CardKeyView): Promise<void> {
  try {
    await ElMessageBox.confirm(`作废卡密 ${card.code}？`, '作废卡密', {
      type: 'warning',
      confirmButtonText: '作废',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    await voidCard(card.id)
    await load()
    ElMessage.success('卡密已作废')
  } catch (error) {
    ElMessage.error((error as Error).message || '作废失败')
  } finally {
    acting.value = false
  }
}

async function downloadBatch(id: string): Promise<void> {
  try {
    const { blob, filename } = await exportCards(id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename || `cards_${id}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error((error as Error).message || '导出失败')
  }
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function money(value: string | null): string {
  return value === null || value === undefined ? '—' : `¥${Number(value).toFixed(2)}`
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">生成卡密</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        批量生成 AI 次数包或 VIP 天数卡密，并查看卡密列表、批次统计与兑换记录。作废后卡密不可再用；售价只用于估算批次收入，不影响实际收款。
      </AdminPageIntro>
      <div class="selector-row">
        <el-select v-model="form.type" style="width: 160px">
          <el-option label="AI 次数包" value="AI_QUOTA" />
          <el-option label="VIP 天数" value="VIP" />
        </el-select>
        <el-input-number v-model="form.value" :min="1" :max="100000" />
        <span class="meta">{{ form.type === 'VIP' ? '天' : '次' }}</span>
        <el-input-number
          v-model="form.priceCny"
          :min="0"
          :precision="2"
          :controls="false"
          placeholder="售价￥"
          style="width: 120px"
        />
        <span class="meta">售价（可选）</span>
        <el-input-number v-model="form.quantity" :min="1" :max="500" />
        <span class="meta">张</span>
        <el-date-picker
          v-model="form.expireAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="过期时间（可选）"
        />
        <el-input v-model="form.prefix" maxlength="10" placeholder="前缀（可选）" style="width: 140px" />
        <el-button type="primary" :loading="generating" @click="generate">生成</el-button>
      </div>
      <div class="meta" style="margin-top: 8px">
        AI 次数包的 value 为次数，VIP 的 value 为天数；售价用于批次估算收入（已用 × 售价），不代表实际收款。
      </div>
    </el-card>

    <el-card style="margin-top: 16px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="卡密列表" name="list">
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 8px">
            <el-input v-model="batchId" placeholder="批次号" style="width: 220px" @keyup.enter="search" />
            <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px">
              <el-option label="未使用" value="UNUSED" />
              <el-option label="已使用" value="USED" />
              <el-option label="已作废" value="VOID" />
              <el-option label="已过期" value="EXPIRED" />
            </el-select>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button v-if="batchId.trim()" @click="downloadBatch(batchId.trim())">导出该批次 CSV</el-button>
          </div>
          <el-empty v-if="cards.length === 0" description="暂无卡密" />
          <el-table v-else v-loading="loading" :data="cards" stripe>
            <el-table-column prop="batchId" label="批次" width="150" />
            <el-table-column prop="code" label="卡密" min-width="220" />
            <el-table-column prop="type" label="类型" width="110" />
            <el-table-column prop="value" label="面额" width="90" />
            <el-table-column label="售价" width="100">
              <template #default="{ row }">{{ money(row.priceCny) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="过期时间" width="150">
              <template #default="{ row }">{{ formatTime(row.expireAt) }}</template>
            </el-table-column>
            <el-table-column label="使用人" width="100">
              <template #default="{ row }">{{ row.usedBy ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button text size="small" @click="downloadBatch(row.batchId)">导出</el-button>
                <el-button
                  text
                  type="danger"
                  size="small"
                  :disabled="row.status !== 'UNUSED' || acting"
                  @click="voidOne(row)"
                >
                  作废
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="load"
            @size-change="search"
          />
        </el-tab-pane>

        <el-tab-pane label="批次统计" name="stats">
          <el-empty v-if="!statsLoading && stats.length === 0" description="暂无卡密批次" />
          <el-table v-else v-loading="statsLoading" :data="stats" stripe>
            <el-table-column prop="batchId" label="批次" width="150" />
            <el-table-column prop="type" label="类型" width="110" />
            <el-table-column prop="value" label="面额" width="90" />
            <el-table-column label="售价" width="100">
              <template #default="{ row }">{{ money(row.priceCny) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="total" label="生成" width="80" />
            <el-table-column prop="used" label="已用" width="80" />
            <el-table-column prop="unused" label="未用" width="80" />
            <el-table-column prop="voided" label="作废" width="80" />
            <el-table-column prop="expired" label="过期" width="80" />
            <el-table-column label="估算收入" width="120">
              <template #default="{ row }">{{ money(row.estimatedRevenueCny) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="兑换记录" name="redemptions">
          <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 8px">
            <el-input
              v-model="redemptionUser"
              placeholder="按用户 ID 筛选"
              style="width: 180px"
              @keyup.enter="searchRedemptions"
            />
            <el-button type="primary" @click="searchRedemptions">查询</el-button>
          </div>
          <el-empty v-if="!redemptionLoading && redemptions.length === 0" description="暂无兑换记录" />
          <el-table v-else v-loading="redemptionLoading" :data="redemptions" stripe>
            <el-table-column prop="code" label="卡密" min-width="220" />
            <el-table-column prop="batchId" label="批次" width="150" />
            <el-table-column prop="type" label="类型" width="110" />
            <el-table-column prop="value" label="面额" width="90" />
            <el-table-column label="售价" width="100">
              <template #default="{ row }">{{ money(row.priceCny) }}</template>
            </el-table-column>
            <el-table-column label="兑换用户" width="160">
              <template #default="{ row }">
                {{ row.username ? `${row.username}（#${row.userId}）` : `#${row.userId ?? '—'}` }}
              </template>
            </el-table-column>
            <el-table-column label="兑换时间" width="160">
              <template #default="{ row }">{{ formatTime(row.usedAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="redemptionPage"
            v-model:page-size="redemptionSize"
            :total="redemptionTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="loadRedemptions"
            @size-change="searchRedemptions"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="generatedVisible" title="生成结果" width="560px">
      <template v-if="generated">
        <div class="meta">批次：{{ generated.batchId }} · 共 {{ generated.quantity }} 张</div>
        <el-input
          :model-value="generated.codes.join('\n')"
          type="textarea"
          :rows="8"
          readonly
          style="margin-top: 8px"
        />
      </template>
      <template #footer>
        <el-button @click="generatedVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyCodes">复制全部</el-button>
      </template>
    </el-dialog>
  </div>
</template>