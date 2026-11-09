<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getApiDocs } from '../../api/admin'

interface ApiOperation {
  tags?: string[]
  summary?: string
  description?: string
  deprecated?: boolean
}

interface ApiItem {
  method: string
  path: string
  summary: string
}

interface ApiGroup {
  tag: string
  items: ApiItem[]
}

const HTTP_METHODS = ['get', 'post', 'put', 'delete', 'patch']

const loading = ref(false)
const docs = ref<Record<string, unknown> | null>(null)
const keyword = ref('')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    docs.value = await getApiDocs()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载接口文档失败')
  } finally {
    loading.value = false
  }
}

const groups = computed<ApiGroup[]>(() => {
  const paths = docs.value?.paths
  if (!paths || typeof paths !== 'object') {
    return []
  }
  const filter = keyword.value.trim().toLowerCase()
  const grouped = new Map<string, ApiItem[]>()
  for (const [path, operations] of Object.entries(paths as Record<string, unknown>)) {
    if (!operations || typeof operations !== 'object') {
      continue
    }
    for (const [method, raw] of Object.entries(operations as Record<string, unknown>)) {
      if (!HTTP_METHODS.includes(method) || !raw || typeof raw !== 'object') {
        continue
      }
      const operation = raw as ApiOperation
      const summary = operation.summary || operation.description || ''
      const upperMethod = method.toUpperCase()
      if (filter && !`${upperMethod} ${path} ${summary}`.toLowerCase().includes(filter)) {
        continue
      }
      const tag = operation.tags?.[0] || '其他'
      const items = grouped.get(tag) ?? []
      items.push({ method: upperMethod, path, summary })
      grouped.set(tag, items)
    }
  }
  return Array.from(grouped.entries())
    .map(([tag, items]) => ({
      tag,
      items: items.sort((a, b) => a.path.localeCompare(b.path))
    }))
    .sort((a, b) => a.tag.localeCompare(b.tag, 'zh-CN'))
})

const totalApis = computed(() => groups.value.reduce((sum, group) => sum + group.items.length, 0))

function methodTagType(method: string): 'success' | 'warning' | 'danger' | 'primary' | 'info' {
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'primary'
  if (method === 'PUT') return 'warning'
  if (method === 'DELETE') return 'danger'
  return 'info'
}
</script>

<template>
  <div v-loading="loading">
    <div class="monitor-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索方法、路径或摘要"
        clearable
        style="width: 260px"
      />
      <span class="monitor-hint">共 {{ totalApis }} 个接口</span>
      <el-button @click="load">刷新</el-button>
      <a class="api-docs-link" href="/swagger-ui.html" target="_blank" rel="noopener">
        打开 Swagger UI 调试 ↗
      </a>
    </div>

    <el-empty v-if="!loading && groups.length === 0" description="没有匹配的接口" />
    <div v-for="group in groups" :key="group.tag" class="api-docs-group">
      <h4>{{ group.tag }}<span class="monitor-sub">（{{ group.items.length }}）</span></h4>
      <el-table :data="group.items" size="small" border>
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="methodTagType(row.method)">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" min-width="320">
          <template #default="{ row }">
            <code class="api-docs-path">{{ row.path }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.api-docs-link {
  margin-left: auto;
  font-size: 13px;
  color: var(--tm-brand);
  text-decoration: none;
}

.api-docs-group {
  margin-bottom: 18px;
}

.api-docs-group h4 {
  margin: 0 0 8px;
  font-size: 15px;
  color: var(--tm-text);
}

.api-docs-path {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}
</style>