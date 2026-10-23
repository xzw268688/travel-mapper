<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getLlmConfig,
  getSearchConfig,
  updateLlmConfig,
  updateSearchConfig,
  type LlmConfig,
  type SearchConfig
} from '../api/admin'

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

const llmMasked = ref('')
const searchMasked = ref('')
const loading = ref(false)
const llmSaving = ref(false)
const searchSaving = ref(false)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [llmConfig, searchConfig] = await Promise.all([getLlmConfig(), getSearchConfig()])
    applyLlm(llmConfig)
    applySearch(searchConfig)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载 AI 配置失败')
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
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-card>
      <template #header><div class="trip-title">AI 行程模型（LLM）</div></template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="OpenAI 兼容接口。通义千问：https://dashscope.aliyuncs.com/compatible-mode/v1；Kimi/OpenAI 请填写各自 Base URL。API Key 留空表示保留原值。"
        style="margin-bottom: 12px"
      />
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
          <el-button @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header><div class="trip-title">攻略搜索（可选）</div></template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="搜索失败不会阻塞行程生成，只影响参考攻略。支持 tavily / bocha；API Key 留空表示保留原值。"
        style="margin-bottom: 12px"
      />
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
    </el-card>
  </div>
</template>
