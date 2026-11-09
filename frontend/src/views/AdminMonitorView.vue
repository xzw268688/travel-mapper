<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminPageIntro from '../components/AdminPageIntro.vue'
import OnlineUsersPanel from '../components/monitor/OnlineUsersPanel.vue'
import ServerPanel from '../components/monitor/ServerPanel.vue'
import DatabasePanel from '../components/monitor/DatabasePanel.vue'
import CachePanel from '../components/monitor/CachePanel.vue'
import StoragePanel from '../components/monitor/StoragePanel.vue'
import ApiDocsPanel from '../components/monitor/ApiDocsPanel.vue'

const SECTIONS = [
  { key: 'online', label: '在线用户', component: OnlineUsersPanel },
  { key: 'server', label: '服务监控', component: ServerPanel },
  { key: 'database', label: '数据库监控', component: DatabasePanel },
  { key: 'cache', label: '缓存监控', component: CachePanel },
  { key: 'storage', label: '存储监控', component: StoragePanel },
  { key: 'api', label: '接口文档', component: ApiDocsPanel }
] as const

const route = useRoute()
const router = useRouter()

const activeSection = computed({
  get: () => {
    const key = String(route.params.section || 'online')
    return SECTIONS.some((section) => section.key === key) ? key : 'online'
  },
  set: (value: string) => {
    if (value !== route.params.section) {
      void router.replace(`/admin/monitor/${value}`)
    }
  }
})

const current = computed(() =>
  SECTIONS.find((section) => section.key === activeSection.value) ?? SECTIONS[0]
)
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">监控中心</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        查看系统运行状态：谁正在线、服务器与 JVM 资源、数据库连接池、Redis 缓存、对象存储健康状态，以及当前接口清单。监控只读；只有强制下线与清理安全缓存会改变系统状态并写入审计。
      </AdminPageIntro>

      <el-tabs v-model="activeSection">
        <el-tab-pane v-for="section in SECTIONS" :key="section.key" :label="section.label" :name="section.key" />
      </el-tabs>

      <component :is="current.component" :key="activeSection" />
    </el-card>
  </div>
</template>