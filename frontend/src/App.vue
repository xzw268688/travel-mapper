<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

onMounted(() => {
  void auth.loadProfile()
})

function logout(): void {
  auth.logout()
  void router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand" @click="router.push('/')">TravelMapper</div>
      <nav v-if="auth.isAuthenticated">
        <router-link to="/">世界地图</router-link>
        <router-link to="/plan">规划行程</router-link>
        <router-link to="/notes">游记</router-link>
        <router-link to="/expenses">记账</router-link>
        <router-link to="/polish">AI 润色</router-link>
        <router-link v-if="auth.user?.role === 'ADMIN'" to="/admin/rates">汇率</router-link>
        <router-link v-if="auth.user?.role === 'ADMIN'" to="/admin/notes">内容管理</router-link>
        <router-link v-if="auth.user?.role === 'ADMIN'" to="/admin/settings">AI 配置</router-link>
      </nav>
      <div class="header-right">
        <span v-if="auth.user" class="user-name">{{ auth.user.nickname || auth.user.username }}</span>
        <el-button v-if="auth.isAuthenticated" text @click="logout">退出</el-button>
        <router-link v-else to="/login">登录</router-link>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>
