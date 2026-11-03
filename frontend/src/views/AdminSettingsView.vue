<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getLlmConfig,
  getMailConfig,
  getSearchConfig,
  getStorageConfig,
  updateLlmConfig,
  updateMailConfig,
  updateSearchConfig,
  updateStorageConfig,
  type LlmConfig,
  type MailConfig,
  type SearchConfig,
  type StorageConfig
} from '../api/admin'

const route = useRoute()
const router = useRouter()

const TABS = ['llm', 'search', 'storage', 'mail'] as const
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

const llmMasked = ref('')
const searchMasked = ref('')
const storageAccessMasked = ref('')
const storageSecretMasked = ref('')
const mailPasswordMasked = ref('')
const loading = ref(false)
const llmSaving = ref(false)
const searchSaving = ref(false)
const storageSaving = ref(false)
const mailSaving = ref(false)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [llmConfig, searchConfig, storageConfig, mailConfig] = await Promise.all([
      getLlmConfig(),
      getSearchConfig(),
      getStorageConfig(),
      getMailConfig()
    ])
    applyLlm(llmConfig)
    applySearch(searchConfig)
    applyStorage(storageConfig)
    applyMail(mailConfig)
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
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="以下配置保存后立即热加载，无需重启；敏感字段留空表示保留原值。"
        style="margin-bottom: 12px"
      />
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="AI 模型" name="llm">
          <div class="config-pane">
            <div class="trip-title">AI 行程模型（LLM）</div>
            <div class="meta" style="margin-bottom: 12px">
              OpenAI 兼容接口。通义千问：https://dashscope.aliyuncs.com/compatible-mode/v1；Kimi/OpenAI 请填写各自 Base URL。
            </div>
            <el-form label-width="120px" style="max-width: 720px">
              <el-form-item label="供应商" required>
                <el-select v-model="llm.provider" filterable allow-create default-first-option style="width: 100%">
                  <el-option label="qwen（通义千问）" value="qwen" />
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
              <el-form-item>
                <el-button type="primary" :loading="llmSaving" @click="saveLlm">保存 LLM 配置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane label="攻略搜索" name="search">
          <div class="config-pane">
            <div class="trip-title">攻略搜索（可选）</div>
            <div class="meta" style="margin-bottom: 12px">
              搜索失败不会阻塞行程生成，只影响参考攻略。支持 tavily / bocha；API Key 留空表示保留原值。
            </div>
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
          </div>
        </el-tab-pane>

        <el-tab-pane label="对象存储" name="storage">
          <div class="config-pane">
            <div class="trip-title">对象存储（S3 兼容）</div>
            <div class="meta" style="margin-bottom: 12px">
              支持 MinIO / 云 OSS。Access Key 与 Secret Key 留空表示保留原值。
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
</style>