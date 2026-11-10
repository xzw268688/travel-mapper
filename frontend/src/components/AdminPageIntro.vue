<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
    <template #title>
      <span class="admin-page-intro-title">{{ title }}</span>
    </template>
    <div class="admin-page-intro-text">
      <slot />
    </div>
  </el-alert>
</template>

<style scoped>
.admin-page-intro-title {
  font-size: 16px;
  font-weight: 600;
}

.admin-page-intro-text {
  font-size: 15px;
  line-height: 1.9;
  color: #303133;
}
</style>