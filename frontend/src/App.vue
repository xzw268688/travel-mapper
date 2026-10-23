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

function openAdmin(path: string): void {
  void router.push(path)
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
        <el-dropdown v-if="auth.user?.role === 'ADMIN'" @command="openAdmin">
          <span class="admin-menu">管理 ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/admin/users">用户管理</el-dropdown-item>
              <el-dropdown-item command="/admin/cards">卡密管理</el-dropdown-item>
              <el-dropdown-item command="/admin/cities">城市维护</el-dropdown-item>
              <el-dropdown-item command="/admin/official-links">官方链接</el-dropdown-item>
              <el-dropdown-item command="/admin/rates">汇率维护</el-dropdown-item>
              <el-dropdown-item command="/admin/notes">内容管理</el-dropdown-item>
              <el-dropdown-item command="/admin/settings">AI 配置</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </nav>
      <div class="header-right">
        <router-link v-if="auth.isAuthenticated" to="/profile" class="user-name">
          {{ auth.user?.nickname || auth.user?.username || '个人中心' }}
        </router-link>
        <el-button v-if="auth.isAuthenticated" text @click="logout">退出</el-button>
        <router-link v-else to="/login">登录</router-link>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>
