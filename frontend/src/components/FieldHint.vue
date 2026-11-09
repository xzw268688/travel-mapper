<script setup lang="ts">
defineProps<{
  label: string
  hint: string
}>()
</script>

<template>
  <el-tooltip
    :content="hint"
    placement="top"
    effect="dark"
    :show-after="150"
    popper-class="field-hint-popper"
  >
    <span class="field-hint">
      {{ label }}<span class="field-hint-mark" aria-hidden="true">?</span>
    </span>
  </el-tooltip>
</template>

<style scoped>
.field-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: help;
}

.field-hint-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 13px;
  height: 13px;
  border: 1px solid var(--tm-text-3, #909399);
  border-radius: 50%;
  color: var(--tm-text-3, #909399);
  font-size: 10px;
  line-height: 1;
}
</style>

<style>
.field-hint-popper {
  max-width: 320px;
  line-height: 1.6;
}
</style>