<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <div class="auth-brand-line">
          <span class="auth-mark"></span>
          <span class="auth-logo">旅迹地图</span>
        </div>
        <span class="auth-tagline">TravelMapper · 把走过的路，留在地图上</span>
      </div>
      <h1 class="auth-title">{{ title }}</h1>
      <p v-if="subtitle" class="auth-subtitle">{{ subtitle }}</p>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: linear-gradient(180deg, #f4f6fb 0%, #e9eef9 100%);
}

.auth-card {
  width: min(440px, 100%);
  padding: 32px 32px 28px;
  background: #ffffff;
  border: 1px solid var(--tm-border);
  border-radius: 14px;
  box-shadow: var(--tm-shadow);
}

.auth-brand {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 22px;
}

.auth-brand-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--tm-brand) 0%, #5c7cfa 100%);
  box-shadow: 0 4px 10px rgba(59, 91, 219, 0.28);
  position: relative;
}

.auth-mark::after {
  content: "";
  position: absolute;
  inset: 7px;
  border-radius: 50% 50% 50% 0;
  background: #ffffff;
  transform: rotate(-45deg);
}

.auth-logo {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--tm-text);
}

.auth-tagline {
  font-size: 12px;
  color: var(--tm-text-muted);
}

.auth-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--tm-text);
}

.auth-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--tm-text-secondary);
}

@media (max-width: 480px) {
  .auth-card {
    padding: 24px 20px;
  }
}
</style>