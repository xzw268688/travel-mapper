<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchSharePage, type SharePage } from '../api/notes'

const route = useRoute()
const page = ref<SharePage | null>(null)
const error = ref('')
const loading = ref(true)

onMounted(async () => {
  const token = String(route.params.token || '')
  try {
    page.value = await fetchSharePage(token)
  } catch (err) {
    error.value = (err as Error).message || '分享已失效'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-result
      v-if="error"
      icon="error"
      title="分享已失效"
      :sub-title="error"
    />
    <el-card v-else-if="page">
      <template #header>
        <div class="trip-title">{{ page.title }}</div>
      </template>
      <div class="meta">
        {{ page.countryName }} · {{ page.cityName }} · {{ page.createdAt?.slice(0, 10) }} ·
        来自 {{ page.nicknameMasked }}
      </div>
      <p style="line-height: 1.8">{{ page.summary }}</p>
      <img
        v-if="page.coverImageUrl"
        :src="page.coverImageUrl"
        alt="封面"
        style="max-width: 100%; border-radius: 8px"
      />
      <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px">
        <el-image
          v-for="image in page.images"
          :key="image"
          :src="image"
          fit="cover"
          style="width: 140px; height: 100px; border-radius: 6px"
        />
      </div>
    </el-card>
  </div>
</template>
