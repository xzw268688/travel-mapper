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
let loadToken = 0

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
    const token = ++loadToken
    cities.value = []
    if (!code) {
      emit('update:city', '')
      return
    }
    loading.value = true
    try {
      const list = await getCities(code)
      if (token !== loadToken) {
        return
      }
      cities.value = list
      // 仅当当前城市不属于新国家时才清空，避免编辑表单回填时误清城市。
      if (props.city && !list.some((item) => item.cityCode === props.city)) {
        emit('update:city', '')
      }
    } catch (error) {
      if (token === loadToken) {
        ElMessage.error((error as Error).message || '加载城市失败')
      }
    } finally {
      if (token === loadToken) {
        loading.value = false
      }
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
