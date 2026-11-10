<script setup lang="ts">
import { formatDateTimeSeconds } from '../utils/datetime'
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  listAuditLogs,
  listLoginLogs,
  type AdminAuditView,
  type AdminLoginLogView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'
import FieldHint from '../components/FieldHint.vue'

type LogTab = 'operations' | 'logins'

const route = useRoute()
const router = useRouter()
const activeTab = ref<LogTab>(route.query.tab === 'logins' ? 'logins' : 'operations')

const TARGET_TYPES = ['CARD', 'USER', 'NOTE', 'CONFIG', 'RATE', 'TRIP', 'ITINERARY', 'EXPENSE', 'FEEDBACK', 'REPORT', 'ANNOUNCEMENT', 'CACHE']
const ROLES = ['ADMIN', 'USER']
const ACTOR_ROLES = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'USER', label: '用户' },
  { value: 'SYSTEM', label: '系统' },
  { value: 'ANONYMOUS', label: '匿名' }
]
const ACTOR_ROLE_LABELS: Record<string, string> = Object.fromEntries(
  ACTOR_ROLES.map((item) => [item.value, item.label])
)
const REASON_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  USER_NOT_FOUND: '账号不存在',
  BAD_CREDENTIALS: '密码错误',
  ACCOUNT_BANNED: '账号已封禁',
  ACCOUNT_DELETED: '账号已注销',
  EMAIL_NOT_VERIFIED: '邮箱未验证',
  RATE_LIMITED: '失败次数过多'
}

const ACTION_LABELS: Record<string, string> = {
  CONFIG_UPDATE: '修改系统配置',
  USER_BAN: '封禁用户',
  USER_UNBAN: '解封用户',
  USER_QUOTA: '调整用户额度',
  USER_VIP_SET: '设置 VIP 到期日',
  CARD_GENERATE: '生成卡密',
  CARD_VOID: '作废卡密',
  CARD_REDEEM: '兑换卡密',
  CITY_CREATE: '新增城市',
  CITY_UPDATE: '修改城市',
  CITY_DELETE: '删除城市',
  OFFICIAL_LINK_CREATE: '新增官方链接',
  OFFICIAL_LINK_UPDATE: '修改官方链接',
  OFFICIAL_LINK_DELETE: '删除官方链接',
  RATE_CREATE: '新增汇率',
  RATE_UPDATE: '修改汇率',
  RATE_REFRESH: '刷新汇率',
  FEEDBACK_UPDATE: '处理用户反馈',
  NOTE_TAKEDOWN: '下架游记',
  NOTE_RESTORE: '恢复游记',
  REPORT_ACCEPT: '采纳举报',
  REPORT_REJECT: '驳回举报',
  ACCOUNT_DELETE: '注销账号',
  TRIP_UPDATE: '重命名旅行',
  TRIP_STATUS: '修改旅行状态',
  TRIP_DELETE: '删除旅行',
  ITINERARY_CREATE: '保存行程',
  ITINERARY_UPDATE: '修改行程',
  ITINERARY_DELETE: '删除行程',
  ITINERARY_GENERATE: 'AI 生成行程',
  NOTE_CREATE: '创建游记',
  NOTE_UPDATE: '修改游记',
  NOTE_DELETE: '删除游记',
  NOTE_SHARE: '开启游记分享',
  NOTE_UNSHARE: '取消游记分享',
  NOTE_REPORT: '举报游记',
  EXPENSE_CREATE: '新增消费',
  EXPENSE_UPDATE: '修改消费',
  EXPENSE_DELETE: '删除消费',
  CHECKLIST_SAVE: '保存行前清单',
  USER_PROFILE_UPDATE: '修改个人资料',
  FEEDBACK_CREATE: '提交反馈',
  USER_KICK: '强制下线',
  CACHE_CLEAR: '清理缓存',
  ANNOUNCEMENT_CREATE: '创建公告',
  ANNOUNCEMENT_UPDATE: '修改公告',
  ANNOUNCEMENT_PUBLISH: '发布公告',
  ANNOUNCEMENT_OFFLINE: '下线公告',
  ANNOUNCEMENT_DELETE: '删除公告'
}

const ACTION_OPTIONS = Object.entries(ACTION_LABELS)
  .map(([value, label]) => ({ value, label }))
  .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))

const OBJECT_TYPE_LABELS: Record<string, string> = {
  CARD: '卡密',
  USER: '用户',
  NOTE: '游记',
  CONFIG: '系统配置',
  RATE: '汇率',
  FEEDBACK: '反馈',
  REPORT: '举报',
  TRIP: '旅行',
  ITINERARY: '行程',
  EXPENSE: '消费',
  ANNOUNCEMENT: '公告',
  CACHE: '缓存'
}

// 操作日志
const action = ref('')
const userId = ref('')
const actorRole = ref('')
const targetType = ref('')
const range = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const auditLogs = ref<AdminAuditView[]>([])
const auditLoading = ref(false)

// 登录日志
const loginUsername = ref('')
const loginUserId = ref('')
const loginRole = ref('')
const loginResult = ref<'' | 'SUCCESS' | 'FAILURE'>('')
const loginRange = ref<[string, string] | null>(null)
const loginPage = ref(1)
const loginSize = ref(20)
const loginTotal = ref(0)
const loginLogs = ref<AdminLoginLogView[]>([])
const loginLoading = ref(false)
const loginLoaded = ref(false)

onMounted(() => {
  if (activeTab.value === 'logins') {
    void loadLoginLogs()
  } else {
    void loadAuditLogs()
  }
})

watch(activeTab, (tab) => {
  router.replace({ query: { ...route.query, tab } })
  if (tab === 'logins' && !loginLoaded.value) {
    void loadLoginLogs()
  }
})

// 通过菜单再次进入 /admin/logs 时，允许外部查询参数切换页签。
watch(() => route.query.tab, (tab) => {
  const next = tab === 'logins' ? 'logins' : 'operations'
  if (next !== activeTab.value) {
    activeTab.value = next
  }
})

async function loadAuditLogs(): Promise<void> {
  auditLoading.value = true
  try {
    const id = Number(userId.value)
    const result = await listAuditLogs({
      action: action.value.trim() || undefined,
      userId: Number.isInteger(id) && id > 0 ? id : undefined,
      actorRole: actorRole.value || undefined,
      targetType: targetType.value || undefined,
      from: range.value?.[0],
      to: range.value?.[1],
      page: page.value,
      size: size.value
    })
    auditLogs.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载操作日志失败')
  } finally {
    auditLoading.value = false
  }
}

async function loadLoginLogs(): Promise<void> {
  loginLoading.value = true
  try {
    const id = Number(loginUserId.value)
    const result = await listLoginLogs({
      username: loginUsername.value.trim() || undefined,
      userId: Number.isInteger(id) && id > 0 ? id : undefined,
      role: loginRole.value || undefined,
      success: loginResult.value === 'SUCCESS'
        ? true
        : loginResult.value === 'FAILURE'
          ? false
          : undefined,
      from: loginRange.value?.[0],
      to: loginRange.value?.[1],
      page: loginPage.value,
      size: loginSize.value
    })
    loginLogs.value = result.list
    loginTotal.value = result.total
    loginLoaded.value = true
  } catch (error) {
    ElMessage.error((error as Error).message || '加载登录日志失败')
  } finally {
    loginLoading.value = false
  }
}

function searchAudit(): void {
  page.value = 1
  void loadAuditLogs()
}

function resetAudit(): void {
  action.value = ''
  userId.value = ''
  actorRole.value = ''
  targetType.value = ''
  range.value = null
  searchAudit()
}

function searchLogin(): void {
  loginPage.value = 1
  void loadLoginLogs()
}

function resetLogin(): void {
  loginUsername.value = ''
  loginUserId.value = ''
  loginRole.value = ''
  loginResult.value = ''
  loginRange.value = null
  searchLogin()
}

function formatTime(value: string): string {
  return formatDateTimeSeconds(value) || '—'
}

function pretty(detail: string | null): string {
  if (!detail) {
    return '—'
  }
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return detail
  }
}

function reasonLabel(reason: string | null): string {
  if (!reason) {
    return '—'
  }
  return REASON_LABELS[reason] ?? reason
}

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}

function objectLabel(row: AdminAuditView): string {
  if (!row.targetType) {
    return '—'
  }
  const label = OBJECT_TYPE_LABELS[row.targetType] ?? row.targetType
  return row.targetId ? `${label} #${row.targetId}` : label
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">日志管理</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        集中查看平台日志：操作日志记录管理员与系统的关键操作留痕，登录日志记录每次登录尝试的结果与来源。日志只读、不可修改，用于问题追溯与账号安全排查。
      </AdminPageIntro>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="操作日志" name="operations">
          <div class="log-filters">
            <el-select
              v-model="action"
              placeholder="全部动作"
              clearable
              filterable
              allow-create
              default-first-option
              style="width: 220px"
            >
              <el-option v-for="item in ACTION_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="userId" placeholder="操作人 ID" style="width: 140px" @keyup.enter="searchAudit" />
            <el-select v-model="actorRole" placeholder="全部角色" clearable style="width: 130px">
              <el-option v-for="item in ACTOR_ROLES" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="targetType" placeholder="全部对象" clearable style="width: 150px">
              <el-option v-for="item in TARGET_TYPES" :key="item" :label="item" :value="item" />
            </el-select>
            <el-date-picker
              v-model="range"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
            />
            <el-button type="primary" :loading="auditLoading" @click="searchAudit">查询</el-button>
            <el-button @click="resetAudit">重置</el-button>
          </div>

          <el-empty v-if="!auditLoading && auditLogs.length === 0" description="暂无操作日志" />
          <el-table v-else v-loading="auditLoading" :data="auditLogs" stripe>
            <el-table-column type="expand" width="80">
              <template #header>
                <FieldHint label="详情" hint="脱敏后的操作详情，只含对象 ID、变更字段名、数量或枚举值，不含标题、正文、金额与备注原文。点击行首箭头展开。" />
              </template>
              <template #default="{ row }">
                <pre class="audit-detail">{{ pretty(row.detail) }}</pre>
              </template>
            </el-table-column>
            <el-table-column width="170">
              <template #header>
                <FieldHint label="时间" hint="操作发生的时间。本地开发环境的数据库时区为 UTC，显示时间可能比北京时间少 8 小时。" />
              </template>
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column width="170">
              <template #header>
                <FieldHint label="操作人" hint="发起操作的用户。系统任务显示「系统」，未登录的匿名请求没有用户名。" />
              </template>
              <template #default="{ row }">
                {{ row.username ? `${row.username}（#${row.userId}）` : row.userId ? `#${row.userId}` : '系统' }}
              </template>
            </el-table-column>
            <el-table-column width="90">
              <template #header>
                <FieldHint label="角色" hint="操作发生时的角色快照：管理员、用户、系统或匿名。不会因用户后续升/降级而改变。" />
              </template>
              <template #default="{ row }">
                <el-tag v-if="row.actorRole" size="small" :type="row.actorRole === 'ADMIN' ? 'warning' : 'info'">
                  {{ ACTOR_ROLE_LABELS[row.actorRole] ?? row.actorRole }}
                </el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column min-width="200">
              <template #header>
                <FieldHint label="动作" hint="操作类型的中文说明。下方灰色小字为原始动作编码，可在上方下拉框中直接按中文选择筛选。" />
              </template>
              <template #default="{ row }">
                <div>{{ actionLabel(row.action) }}</div>
                <div class="audit-action-code">{{ row.action }}</div>
              </template>
            </el-table-column>
            <el-table-column width="160">
              <template #header>
                <FieldHint label="对象" hint="被操作的对象类型与 ID，已转换为中文类型名；系统级或生成类操作可能没有对象 ID。" />
              </template>
              <template #default="{ row }">{{ objectLabel(row) }}</template>
            </el-table-column>
            <el-table-column prop="ip" width="150">
              <template #header>
                <FieldHint label="IP" hint="发起请求的客户端 IP。经反向代理时取 X-Forwarded-For 的首个地址，支持 IPv6。" />
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
            @current-change="loadAuditLogs"
            @size-change="searchAudit"
          />
        </el-tab-pane>

        <el-tab-pane label="登录日志" name="logins">
          <div class="log-filters">
            <el-input v-model="loginUsername" placeholder="账号（模糊）" style="width: 180px" @keyup.enter="searchLogin" />
            <el-input v-model="loginUserId" placeholder="用户 ID" style="width: 130px" @keyup.enter="searchLogin" />
            <el-select v-model="loginRole" placeholder="全部角色" clearable style="width: 130px">
              <el-option v-for="item in ROLES" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="loginResult" placeholder="全部结果" clearable style="width: 130px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILURE" />
            </el-select>
            <el-date-picker
              v-model="loginRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
            />
            <el-button type="primary" :loading="loginLoading" @click="searchLogin">查询</el-button>
            <el-button @click="resetLogin">重置</el-button>
          </div>

          <el-empty v-if="!loginLoading && loginLogs.length === 0" description="暂无登录日志" />
          <el-table v-else v-loading="loginLoading" :data="loginLogs" stripe>
            <el-table-column width="170">
              <template #header>
                <FieldHint label="时间" hint="登录尝试发生的时间。本地开发环境的数据库时区为 UTC，显示时间可能比北京时间少 8 小时。" />
              </template>
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column min-width="180">
              <template #header>
                <FieldHint label="账号" hint="登录时提交的账号快照（用户名或邮箱，邮箱已转小写）。下方 #数字 为解析到的用户 ID。" />
              </template>
              <template #default="{ row }">
                <div>{{ row.username }}</div>
                <div v-if="row.userId" class="login-user-id">#{{ row.userId }}</div>
              </template>
            </el-table-column>
            <el-table-column width="90">
              <template #header>
                <FieldHint label="角色" hint="已解析用户的角色：管理员或普通用户；账号不存在或未登录时为空。" />
              </template>
              <template #default="{ row }">
                <el-tag v-if="row.role" size="small" :type="row.role === 'ADMIN' ? 'warning' : 'info'">
                  {{ row.role }}
                </el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column width="90">
              <template #header>
                <FieldHint label="结果" hint="本次登录尝试是否成功。" />
              </template>
              <template #default="{ row }">
                <el-tag size="small" :type="row.success ? 'success' : 'danger'">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column width="140">
              <template #header>
                <FieldHint label="原因" hint="失败原因：账号不存在、密码错误、账号封禁、账号注销、邮箱未验证或失败次数过多；成功时显示「成功」。" />
              </template>
              <template #default="{ row }">{{ reasonLabel(row.reason) }}</template>
            </el-table-column>
            <el-table-column prop="ip" width="150">
              <template #header>
                <FieldHint label="IP" hint="客户端 IP。经反向代理时取 X-Forwarded-For 的首个地址，支持 IPv6。" />
              </template>
            </el-table-column>
            <el-table-column prop="userAgent" min-width="220" show-overflow-tooltip>
              <template #header>
                <FieldHint label="User-Agent" hint="客户端浏览器、操作系统或脚本的标识，例如 Chrome on Windows、iPhone Safari、curl。由客户端自行上报，可能被伪造或为空，仅作辅助线索。" />
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="loginPage"
            v-model:page-size="loginSize"
            :total="loginTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="loadLoginLogs"
            @size-change="searchLogin"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.log-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.audit-detail {
  margin: 0;
  padding: 8px 12px;
  max-height: 260px;
  overflow: auto;
  background: var(--tm-brand-soft);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.login-user-id {
  font-size: 12px;
  color: var(--tm-text-3, #909399);
}

.audit-action-code {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--tm-text-3, #909399);
}
</style>