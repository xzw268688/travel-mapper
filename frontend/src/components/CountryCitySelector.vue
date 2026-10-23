<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCities, getCountries } from '../api/catalog'
import type { City, Country } from '../api/types'

const props = defineProps<{ country: string; city: string }>()
const emit = defineEmits<{
  (event: 'update:country', value: string): void
  (event: 'update:city', value: string): void
}>()

const countries = ref<Country[]>([])
const cities = ref<City[]>([])
const loading = ref(false)

onMounted(async () => {
  try {
    countries.value = await getCountries()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载国家失败')
  }
})

watch(
  () => props.country,
  async (code) => {
    emit('update:city', '')
    cities.value = []
    if (!code) return
    loading.value = true
    try {
      cities.value = await getCities(code)
    } catch (error) {
      ElMessage.error((error as Error).message || '加载城市失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="selector-row">
    <el-select
      :model-value="country"
      placeholder="选择国家"
      filterable
      style="min-width: 180px"
      @update:model-value="emit('update:country', $event)"
    >
      <el-option v-for="item in countries" :key="item.code" :label="item.nameZh" :value="item.code" />
    </el-select>
    <el-select
      :model-value="city"
      placeholder="选择城市"
      filterable
      :loading="loading"
      :disabled="!country"
      no-data-text="暂无可选城市，请联系管理员补充"
      style="min-width: 220px"
      @update:model-value="emit('update:city', $event)"
    >
      <el-option
        v-for="item in cities"
        :key="item.cityCode"
        :label="item.displayName"
        :value="item.cityCode"
      />
    </el-select>
  </div>
</template>
