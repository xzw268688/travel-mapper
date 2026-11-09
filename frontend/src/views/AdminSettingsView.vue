<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getAlertConfig,
  getLlmConfig,
  getMailConfig,
  getQuotaConfig,
  getSearchConfig,
  getStorageConfig,
  testAlert as testAlertApi,
  testStorageConfig,
  updateAlertConfig,
  updateLlmConfig,
  updateMailConfig,
  updateQuotaConfig,
  updateSearchConfig,
  updateStorageConfig,
  type AlertConfig,
  type LlmConfig,
  type MailConfig,
  type QuotaConfig,
  type SearchConfig,
  type StorageConfig
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'

const route = useRoute()
const router = useRouter()

const TABS = ['llm', 'search', 'storage', 'mail', 'alert'] as const
type TabName = (typeof TABS)[number]

const activeTab = ref<TabName>(normalizeTab(route.query.tab))
watch(
  () => route.query.tab,
  (value) => {
    activeTab.value = normalizeTab(value)
  }
)

function normalizeTab(value: unknown): TabName {
  return TABS.includes(value as TabName) ? (value as TabName) : 'llm'
}

function onTabChange(name: string | number): void {
  const tab = normalizeTab(name)
  activeTab.value = tab
  void router.replace({ query: { ...route.query, tab } })
}

const llm = reactive<LlmConfig>({
  provider: 'qwen',
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  model: 'qwen-max',
  apiKey: '',
  timeoutSeconds: 30,
  maxRetry: 1
})
const quota = reactive<QuotaConfig>({ freeLimit: 3 })
const quotaSaving = ref(false)
const search = reactive<SearchConfig>({
  provider: 'tavily',
  apiKey: '',
  dailyLimit: 50,
  timeoutSeconds: 5,
  cacheHours: 24,
  domainWhitelist: ''
})
const storage = reactive<StorageConfig>({
  provider: 'minio',
  endpoint: '',
  region: '',
  bucket: 'travelmapper',
  accessKey: '',
  secretKey: '',
  pathStyle: true,
  publicDomain: ''
})
const mail = reactive<MailConfig>({
  enabled: false,
  host: '',
  port: 587,
  username: '',
  password: '',
  from: 'no-reply@travelmapper.local',
  smtpAuth: true,
  starttls: true
})
const alert = reactive<AlertConfig>({
  enabled: true,
  webhookUrl: '',
  minIntervalSeconds: 300
})

const llmMasked = ref('')
const searchMasked = ref('')
const storageAccessMasked = ref('')
const storageSecretMasked = ref('')
const mailPasswordMasked = ref('')
const alertMasked = ref('')
const loading = ref(false)
const llmSaving = ref(false)
const searchSaving = ref(false)
const storageSaving = ref(false)
const storageTesting = ref(false)
const mailSaving = ref(false)
const alertSaving = ref(false)
const alertTesting = ref(false)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [llmConfig, quotaConfig, searchConfig, storageConfig, mailConfig, alertConfig] =
      await Promise.all([
        getLlmConfig(),
        getQuotaConfig(),
        getSearchConfig(),
        getStorageConfig(),
        getMailConfig(),
        getAlertConfig()
      ])
    applyLlm(llmConfig)
    quota.freeLimit = quotaConfig.freeLimit
    applySearch(searchConfig)
    applyStorage(storageConfig)
    applyMail(mailConfig)
    applyAlert(alertConfig)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

function applyLlm(config: LlmConfig): void {
  llm.provider = config.provider
  llm.baseUrl = config.baseUrl
  llm.model = config.model
  llm.timeoutSeconds = config.timeoutSeconds
  llm.maxRetry = config.maxRetry
  llm.apiKey = ''
  llmMasked.value = config.apiKey
}

function applySearch(config: SearchConfig): void {
  search.provider = config.provider
  search.dailyLimit = config.dailyLimit
  search.timeoutSeconds = config.timeoutSeconds
  search.cacheHours = config.cacheHours
  search.domainWhitelist = config.domainWhitelist
  search.apiKey = ''
  searchMasked.value = config.apiKey
}

function applyStorage(config: StorageConfig): void {
  storage.provider = config.provider
  storage.endpoint = config.endpoint
  storage.region = config.region
  storage.bucket = config.bucket
  storage.pathStyle = config.pathStyle
  storage.publicDomain = config.publicDomain
  storage.accessKey = ''
  storage.secretKey = ''
  storageAccessMasked.value = config.accessKey
  storageSecretMasked.value = config.secretKey
}

function applyMail(config: MailConfig): void {
  mail.enabled = config.enabled
  mail.host = config.host
  mail.port = config.port
  mail.username = config.username
  mail.from = config.from
  mail.smtpAuth = config.smtpAuth
  mail.starttls = config.starttls
  mail.password = ''
  mailPasswordMasked.value = config.password
}

function applyAlert(config: AlertConfig): void {
  alert.enabled = config.enabled
  alert.minIntervalSeconds = config.minIntervalSeconds
  alert.webhookUrl = ''
  alertMasked.value = config.webhookUrl
}

async function saveAlert(): Promise<void> {
  alertSaving.value = true
  try {
    const body = {
      enabled: alert.enabled,
      webhookUrl: alert.webhookUrl.trim(),
      minIntervalSeconds: alert.minIntervalSeconds
    }
    applyAlert(await updateAlertConfig(body))
    ElMessage.success('故障提醒配置已保存并生效')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存故障提醒配置失败')
  } finally {
    alertSaving.value = false
  }
}

async function testAlert(): Promise<void> {
  alertTesting.value = true
  try {
    await testAlertApi()
    ElMessage.success('测试消息已发送，请到飞书群确认')
  } catch (error) {
    ElMessage.error((error as Error).message || '测试消息发送失败')
  } finally {
    alertTesting.value = false
  }
}

async function saveLlm(): Promise<void> {
  if (!llm.provider.trim() || !llm.baseUrl.trim() || !llm.model.trim()) {
    ElMessage.warning('请填写供应商、Base URL 和模型名')
    return
  }
  llmSaving.value = true
  try {
    const body: Partial<LlmConfig> = {
      provider: llm.provider.trim(),
      baseUrl: llm.baseUrl.trim(),
      model: llm.model.trim(),
      timeoutSeconds: llm.timeoutSeconds,
      maxRetry: llm.maxRetry
    }
    if (llm.apiKey.trim()) {
      body.apiKey = llm.apiKey.trim()
    }
    applyLlm(await updateLlmConfig(body))
    ElMessage.success('LLM 配置已保存并热加载')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存 LLM 配置失败')
  } finally {
    llmSaving.value = false
  }
}

async function saveQuota(): Promise<void> {
  quotaSaving.value = true
  try {
    const result = await updateQuotaConfig({ freeLimit: quota.freeLimit })
    quota.freeLimit = result.freeLimit
    ElMessage.success('每日免费额度已保存')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存免费额度失败')
  } finally {
    quotaSaving.value = false
  }
}

async function saveSearch(): Promise<void> {
  if (!search.provider.trim()) {
    ElMessage.warning('请选择搜索供应商')
    return
  }
  searchSaving.value = true
  try {
    const body: Partial<SearchConfig> = {
      provider: search.provider.trim(),
      dailyLimit: search.dailyLimit,
      timeoutSeconds: search.timeoutSeconds,
      cacheHours: search.cacheHours,
      domainWhitelist: search.domainWhitelist
    }
    if (search.apiKey.trim()) {
      body.apiKey = search.apiKey.trim()
    }
    applySearch(await updateSearchConfig(body))
    ElMessage.success('搜索配置已保存并热加载')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存搜索配置失败')
  } finally {
    searchSaving.value = false
  }
}

async function testStorage(): Promise<void> {
  storageTesting.value = true
  try {
    const result = await testStorageConfig({
      provider: storage.provider,
      endpoint: storage.endpoint,
      region: storage.region,
      bucket: storage.bucket,
      accessKey: storage.accessKey,
      secretKey: storage.secretKey,
      pathStyle: storage.pathStyle,
      publicDomain: storage.publicDomain
    })
    if (result.ok) {
      ElMessage.success(`${result.message}（${result.latencyMs} ms）`)
    } else {
      ElMessage.error(result.message || '存储连接失败')
    }
  } catch (error) {
    const message = (error as Error).message
    if (message === 'Failed to fetch' || message.includes('Network Error')) {
      ElMessage.error('无法连接后端：请确认后端已启动，刷新页面后重试')
    } else {
      ElMessage.error(message || '存储连接测试失败')
    }
  } finally {
    storageTesting.value = false
  }
}

async function saveStorage(): Promise<void> {
  if (!storage.provider.trim() || !storage.endpoint.trim() || !storage.bucket.trim()) {
    ElMessage.warning('请填写供应商、端点和桶名')
    return
  }
  storageSaving.value = true
  try {
    const body: Partial<StorageConfig> = {
      provider: storage.provider.trim(),
      endpoint: storage.endpoint.trim(),
      region: storage.region.trim(),
      bucket: storage.bucket.trim(),
      pathStyle: storage.pathStyle,
      publicDomain: storage.publicDomain.trim()
    }
    if (storage.accessKey.trim()) {
      body.accessKey = storage.accessKey.trim()
    }
    if (storage.secretKey.trim()) {
      body.secretKey = storage.secretKey.trim()
    }
    applyStorage(await updateStorageConfig(body))
    ElMessage.success('对象存储配置已保存并热加载')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存对象存储配置失败')
  } finally {
    storageSaving.value = false
  }
}

async function saveMail(): Promise<void> {
  if (mail.enabled && (!mail.host.trim() || !mail.from.trim())) {
    ElMessage.warning('启用 SMTP 时必须填写主机和发件人')
    return
  }
  mailSaving.value = true
  try {
    const body: Partial<MailConfig> = {
      enabled: mail.enabled,
      host: mail.host.trim(),
      port: mail.port,
      username: mail.username.trim(),
      from: mail.from.trim(),
      smtpAuth: mail.smtpAuth,
      starttls: mail.starttls
    }
    if (mail.password.trim()) {
      body.password = mail.password.trim()
    }
    applyMail(await updateMailConfig(body))
    ElMessage.success('邮件配置已保存并热加载')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存邮件配置失败')
  } finally {
    mailSaving.value = false
  }
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap">
          <div class="trip-title" style="margin-bottom: 0">配置中心</div>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>
      <AdminPageIntro title="这个页面是干什么的？">
        维护 AI 模型、攻略搜索、对象存储、邮件 SMTP 与故障提醒等运行参数。保存后立即热加载，无需重启；
        敏感字段留空表示保留原值；所有变更都会写入审计日志。
      </AdminPageIntro>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="AI 模型" name="llm">
          <div class="config-pane">
            <div class="trip-title">AI 行程模型（LLM）</div>
            <el-form label-width="120px" style="max-width: 720px">
              <el-form-item label="供应商" required>
                <el-select v-model="llm.provider" filterable allow-create default-first-option style="width: 100%">
                  <el-option label="qwen（通义千问）" value="qwen" />
                  <el-option label="volcengine（火山方舟）" value="volcengine" />
                  <el-option label="kimi" value="kimi" />
                  <el-option label="openai" value="openai" />
                  <el-option label="deepseek" value="deepseek" />
                </el-select>
              </el-form-item>
              <el-form-item label="Base URL" required>
                <el-input v-model="llm.baseUrl" placeholder="https://…/v1" />
              </el-form-item>
              <el-form-item label="模型名" required>
                <el-input v-model="llm.model" placeholder="qwen-max / kimi-k2 / gpt-4o-mini" />
              </el-form-item>
              <el-form-item label="API Key">
                <el-input
                  v-model="llm.apiKey"
                  type="password"
                  show-password
                  :placeholder="llmMasked ? `当前：${llmMasked}，留空不修改` : '填写 API Key'"
                />
              </el-form-item>
              <el-form-item label="超时（秒）">
                <el-input-number v-model="llm.timeoutSeconds" :min="1" :max="120" />
                <span class="meta" style="margin-left: 8px">解析失败重试</span>
                <el-input-number v-model="llm.maxRetry" :min="0" :max="5" style="margin-left: 8px" />
              </el-form-item>
              <el-form-item label="每日免费额度">
                <el-input-number v-model="quota.freeLimit" :min="0" :max="100" />
                <span class="meta" style="margin-left: 8px">
                  生成与润色共用；0 表示不提供免费次数
                </span>
                <el-button
                  type="primary"
                  plain
                  :loading="quotaSaving"
                  style="margin-left: 12px"
                  @click="saveQuota"
                >
                  保存额度
                </el-button>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="llmSaving" @click="saveLlm">保存 LLM 配置</el-button>
              </el-form-item>
            </el-form>

            <div class="llm-guide">
              <div class="llm-guide-head">
                <span class="llm-guide-title">配置说明</span>
                <el-tag size="small" effect="plain" type="info">保存后立即生效</el-tag>
              </div>
              <div class="llm-guide-grid">
                <div class="llm-guide-item">
                  <span class="llm-guide-label">接口协议</span>
                  <span>OpenAI 兼容</span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">推荐模型</span>
                  <span class="llm-inline">
                    <code>qwen-turbo</code><code>doubao-*-turbo</code><code>deepseek-chat</code>
                  </span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">超时</span>
                  <span>单次最长等待，建议 ≥ 60 秒；超时不重试，立即失败并退回额度</span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">解析失败重试</span>
                  <span>仅在模型返回格式不合法时生效，受整体时间预算约束</span>
                </div>
                <div class="llm-guide-item llm-guide-wide">
                  <span class="llm-guide-label">深度思考</span>
                  <span>火山方舟 <code>doubao</code> 模型自动关闭（thinking=disabled）；推理模型（R1、thinking 系列）单次可能需要 60 秒以上，更容易超时</span>
                </div>
              </div>
              <div class="llm-guide-section">
                <span class="llm-guide-label">Base URL 示例</span>
                <div class="llm-endpoint-row">
                  <span class="llm-endpoint-name">通义千问</span>
                  <code>https://dashscope.aliyuncs.com/compatible-mode/v1</code>
                </div>
                <div class="llm-endpoint-row">
                  <span class="llm-endpoint-name">DeepSeek</span>
                  <code>https://api.deepseek.com/v1</code>
                </div>
                <div class="llm-endpoint-row">
                  <span class="llm-endpoint-name">火山方舟</span>
                  <code>https://ark.cn-beijing.volces.com/api/v3</code>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="攻略搜索" name="search">
          <div class="config-pane">
            <div class="trip-title">攻略搜索（可选）</div>
            <el-form label-width="120px" style="max-width: 720px">
              <el-form-item label="供应商">
                <el-select v-model="search.provider" style="width: 100%">
                  <el-option label="tavily" value="tavily" />
                  <el-option label="bocha（博查）" value="bocha" />
                </el-select>
              </el-form-item>
              <el-form-item label="API Key">
                <el-input
                  v-model="search.apiKey"
                  type="password"
                  show-password
                  :placeholder="searchMasked ? `当前：${searchMasked}，留空不修改` : '填写搜索 API Key'"
                />
              </el-form-item>
              <el-form-item label="每日上限">
                <el-input-number v-model="search.dailyLimit" :min="1" :max="1000" />
              </el-form-item>
              <el-form-item label="超时（秒）">
                <el-input-number v-model="search.timeoutSeconds" :min="1" :max="30" />
                <span class="meta" style="margin-left: 8px">缓存（小时）</span>
                <el-input-number v-model="search.cacheHours" :min="1" :max="168" style="margin-left: 8px" />
              </el-form-item>
              <el-form-item label="域名白名单">
                <el-input v-model="search.domainWhitelist" placeholder="逗号分隔，可留空" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="searchSaving" @click="saveSearch">保存搜索配置</el-button>
              </el-form-item>
            </el-form>

            <div class="llm-guide">
              <div class="llm-guide-head">
                <span class="llm-guide-title">这个配置用来做什么</span>
                <el-tag size="small" effect="plain" type="info">可选</el-tag>
              </div>
              <div class="llm-guide-grid">
                <div class="llm-guide-item llm-guide-wide">
                  <span class="llm-guide-label">作用</span>
                  <span>生成行程时并行搜索「城市 + 天数 + 偏好」，在行程页展示最多 3 条攻略参考链接；不参与 AI 写行程内容。</span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">每日上限</span>
                  <span>每用户每天最多搜索次数，超出时只返回空链接，不报错</span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">超时</span>
                  <span>搜索独立超时，失败不影响行程生成</span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">缓存</span>
                  <span>相同国家、城市、天数与偏好的攻略缓存时长</span>
                </div>
                <div class="llm-guide-item">
                  <span class="llm-guide-label">不配置</span>
                  <span>行程照常生成，只是没有参考攻略链接</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="对象存储" name="storage">
          <div class="config-pane">
            <div class="trip-title">对象存储（S3 兼容）</div>
            <div class="meta" style="margin-bottom: 12px">
              支持 MinIO / 云 OSS。Access Key 与 Secret Key 留空表示保留原值。
              阿里云 OSS：Endpoint 形如 <code>https://oss-cn-hangzhou.aliyuncs.com</code>，
              Region 填 <code>cn-hangzhou</code>，Path Style 关闭；测试连接不会保存配置。
            </div>
            <el-form label-width="120px" style="max-width: 720px">
              <el-form-item label="供应商" required>
                <el-select v-model="storage.provider" filterable allow-create style="width: 100%">
                  <el-option label="minio" value="minio" />
                  <el-option label="oss" value="oss" />
                </el-select>
              </el-form-item>
              <el-form-item label="Endpoint" required>
                <el-input v-model="storage.endpoint" placeholder="http://127.0.0.1:9000" />
              </el-form-item>
              <el-form-item label="Region">
                <el-input v-model="storage.region" placeholder="MinIO 可留空" />
              </el-form-item>
              <el-form-item label="Bucket" required>
                <el-input v-model="storage.bucket" />
              </el-form-item>
              <el-form-item label="Access Key">
                <el-input
                  v-model="storage.accessKey"
                  type="password"
                  show-password
                  :placeholder="storageAccessMasked ? `当前：${storageAccessMasked}，留空不修改` : '填写 Access Key'"
                />
              </el-form-item>
              <el-form-item label="Secret Key">
                <el-input
                  v-model="storage.secretKey"
                  type="password"
                  show-password
                  :placeholder="storageSecretMasked ? `当前：${storageSecretMasked}，留空不修改` : '填写 Secret Key'"
                />
              </el-form-item>
              <el-form-item label="Path Style">
                <el-switch v-model="storage.pathStyle" />
                <span class="meta" style="margin-left: 8px">MinIO 通常开启，云 OSS 关闭</span>
              </el-form-item>
              <el-form-item label="公开域名">
                <el-input v-model="storage.publicDomain" placeholder="可选 CDN 域名" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="storageSaving" @click="saveStorage">保存对象存储配置</el-button>
                <el-button :loading="storageTesting" @click="testStorage">测试连接</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="邮件 SMTP" name="mail">
          <div class="config-pane">
            <div class="trip-title">邮件 SMTP</div>
            <div class="meta" style="margin-bottom: 12px">
              启用后验证码与重置密码邮件通过 SMTP 发送；未启用时开发环境仅在后端日志打印验证码。密码留空表示保留原值。
            </div>
            <el-form label-width="120px" style="max-width: 720px">
              <el-form-item label="启用 SMTP">
                <el-switch v-model="mail.enabled" />
              </el-form-item>
              <el-form-item label="SMTP 主机">
                <el-input v-model="mail.host" placeholder="smtp.example.com" />
              </el-form-item>
              <el-form-item label="端口">
                <el-input-number v-model="mail.port" :min="1" :max="65535" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input v-model="mail.username" placeholder="可选" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="mail.password"
                  type="password"
                  show-password
                  :placeholder="mailPasswordMasked ? `当前：${mailPasswordMasked}，留空不修改` : '填写 SMTP 密码'"
                />
              </el-form-item>
              <el-form-item label="发件人">
                <el-input v-model="mail.from" placeholder="no-reply@example.com" />
              </el-form-item>
              <el-form-item label="认证">
                <el-switch v-model="mail.smtpAuth" />
                <span class="meta" style="margin-left: 12px">STARTTLS</span>
                <el-switch v-model="mail.starttls" style="margin-left: 8px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="mailSaving" @click="saveMail">保存邮件配置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="故障提醒" name="alert">
          <div class="config-pane">
            <div class="trip-title">故障提醒（飞书机器人）</div>
            <div class="meta" style="margin-bottom: 12px">
              接口 5xx、数据库/Redis/对象存储异常时发送飞书提醒。Webhook 留空表示保留原值；仅支持飞书 HTTPS 机器人地址。测试按钮使用已保存的配置，修改后请先保存。
            </div>
            <el-form label-width="150px" style="max-width: 720px">
              <el-form-item label="启用故障提醒">
                <el-switch v-model="alert.enabled" />
              </el-form-item>
              <el-form-item label="机器人 Webhook">
                <el-input
                  v-model="alert.webhookUrl"
                  :placeholder="alertMasked
                    ? `当前：${alertMasked}，留空不修改`
                    : 'https://open.feishu.cn/open-apis/bot/v2/hook/...'"
                />
              </el-form-item>
              <el-form-item label="最小间隔（秒）">
                <el-input-number v-model="alert.minIntervalSeconds" :min="1" :max="3600" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="alertSaving" @click="saveAlert">
                  保存故障提醒
                </el-button>
                <el-button :loading="alertTesting" @click="testAlert">发送测试消息</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.config-pane {
  padding: 8px 4px 4px;
}

.config-pane .trip-title {
  margin-bottom: 4px;
}

.llm-guide {
  max-width: 720px;
  margin-top: 8px;
  padding: 14px 16px;
  border: 1px solid var(--tm-border);
  border-radius: var(--tm-radius-sm, 8px);
  background: var(--tm-brand-soft, #f5f7ff);
}

.llm-guide-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.llm-guide-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tm-text, #1f2937);
}

.llm-guide-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}

.llm-guide-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--tm-text-secondary, #4b5563);
}

.llm-guide-wide {
  grid-column: 1 / -1;
}

.llm-guide-label {
  flex: none;
  min-width: 84px;
  color: var(--tm-text-muted, #6b7280);
}

.llm-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.llm-guide code {
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(59, 91, 219, 0.08);
  color: var(--tm-brand, #3b5bdb);
  font-size: 12px;
  word-break: break-all;
}

.llm-guide-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--tm-border);
  font-size: 13px;
  color: var(--tm-text-secondary, #4b5563);
}

.llm-endpoint-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 6px;
}

.llm-endpoint-name {
  flex: none;
  min-width: 84px;
  color: var(--tm-text-muted, #6b7280);
}

@media (max-width: 768px) {
  .llm-guide-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .llm-guide-item,
  .llm-endpoint-row {
    flex-direction: column;
    gap: 2px;
  }

  .llm-guide-label,
  .llm-endpoint-name {
    min-width: 0;
  }
}
</style>