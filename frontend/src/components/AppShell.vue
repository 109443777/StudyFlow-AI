<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Collection, DataAnalysis, House, Notebook, SwitchButton, UploadFilled } from '@element-plus/icons-vue'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activePath = computed(() => route.path)

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar page-card">
      <div class="brand">
        <div class="brand-mark">SF</div>
        <div>
          <strong>StudyFlow AI</strong>
          <span>学习资料工作台</span>
        </div>
      </div>
      <el-menu :default-active="activePath" router class="menu">
        <el-menu-item index="/dashboard"><el-icon><House /></el-icon>首页概览</el-menu-item>
        <el-menu-item index="/materials"><el-icon><UploadFilled /></el-icon>资料中心</el-menu-item>
        <el-menu-item index="/qa"><el-icon><Collection /></el-icon>资料问答</el-menu-item>
        <el-menu-item index="/study-plans"><el-icon><Notebook /></el-icon>学习计划</el-menu-item>
        <el-menu-item index="/dashboard"><el-icon><DataAnalysis /></el-icon>系统状态</el-menu-item>
      </el-menu>
    </aside>

    <main class="main">
      <header class="topbar page-card">
        <div>
          <p>欢迎回来，{{ auth.user?.nickname || auth.user?.username || '同学' }}</p>
          <h1>把课程资料变成可复习、可追问的知识库</h1>
        </div>
        <el-button :icon="SwitchButton" @click="logout">退出登录</el-button>
      </header>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 22px;
  min-height: 100vh;
  padding: 22px;
}

.sidebar {
  position: sticky;
  top: 22px;
  align-self: start;
  padding: 18px;
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 22px;
}

.brand-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 18px;
  background: #1f6f8b;
  color: #fff;
  font-weight: 900;
}

.brand span {
  display: block;
  margin-top: 4px;
  color: var(--sf-muted);
  font-size: 13px;
}

.menu {
  border-right: 0;
  background: transparent;
}

.main {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 26px;
  margin-bottom: 20px;
}

.topbar p {
  margin: 0 0 6px;
  color: var(--sf-muted);
}

.topbar h1 {
  margin: 0;
  font-size: 28px;
}

@media (max-width: 960px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
  }
}
</style>
