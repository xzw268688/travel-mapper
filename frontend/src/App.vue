<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const bare = computed(() => Boolean(route.meta.bare))
const displayName = computed(
  () => auth.user?.nickname || auth.user?.username || '个人中心'
)
const avatarInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())

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

function onUserCommand(command: string): void {
  if (command === 'logout') {
    logout()
    return
  }
  void router.push(command)
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell-bare': bare }">
    <header v-if="!bare" class="app-header">
      <div class="brand" @click="router.push('/')">
        <span class="brand-mark" aria-hidden="true"></span>
        <span class="brand-text">
          <span class="brand-name">旅迹地图</span>
          <span class="brand-sub">TravelMapper</span>
        </span>
      </div>
      <nav v-if="auth.isAuthenticated">
        <router-link to="/">世界地图</router-link>
        <router-link to="/plan">规划行程</router-link>
        <router-link to="/notes">游记</router-link>
        <router-link to="/expenses">记账</router-link>
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
        <el-dropdown v-if="auth.isAuthenticated" trigger="click" @command="onUserCommand">
          <button type="button" class="user-menu" aria-label="账户菜单">
            <span class="user-avatar">{{ avatarInitial }}</span>
            <span class="user-name">{{ displayName }}</span>
            <span class="user-caret">▾</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="/profile">我的额度与卡密</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <router-link v-else to="/login" class="login-link">登录</router-link>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>
