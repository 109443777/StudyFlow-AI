<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/stores/auth'
import { getDisplayError } from '@/utils/result'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || form.username,
      })
      ElMessage.success('注册并登录成功')
    } else {
      await auth.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
    }
    router.push((route.query.redirect as string) || '/dashboard')
  } catch (error) {
    ElMessage.error(getDisplayError(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="hero">
      <p class="eyebrow">StudyFlow AI</p>
      <h1>让课程资料真正变成你的复习搭子</h1>
      <p>上传 PDF、PPT、Word、录音或视频，系统自动解析、总结、向量化，并支持基于资料的智能问答。</p>
      <div class="hero-points">
        <span>异步解析</span>
        <span>RAG 问答</span>
        <span>复习计划</span>
      </div>
    </section>

    <section class="login-card page-card">
      <h2>{{ mode === 'login' ? '登录学习工作台' : '创建学习账号' }}</h2>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="昵称">
          <el-input v-model="form.nickname" size="large" placeholder="例如：算法课代表" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="full-btn" @click="submit">
          {{ mode === 'login' ? '登录' : '注册并登录' }}
        </el-button>
      </el-form>
      <el-button link type="primary" class="switch-btn" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '还没有账号？去注册' : '已有账号？去登录' }}
      </el-button>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 480px);
  gap: 34px;
  align-items: center;
  min-height: 100vh;
  padding: 48px;
}

.hero {
  max-width: 760px;
}

.eyebrow {
  margin: 0 0 18px;
  color: var(--sf-blue);
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero h1 {
  margin: 0 0 24px;
  font-size: clamp(42px, 6vw, 76px);
  line-height: 1.02;
}

.hero p {
  max-width: 620px;
  color: var(--sf-muted);
  font-size: 18px;
  line-height: 1.8;
}

.hero-points {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.hero-points span {
  padding: 10px 16px;
  border-radius: 999px;
  background: rgba(31, 111, 139, 0.12);
  color: var(--sf-blue);
  font-weight: 700;
}

.login-card {
  padding: 34px;
}

.login-card h2 {
  margin: 0 0 28px;
}

.full-btn {
  width: 100%;
}

.switch-btn {
  width: 100%;
  margin-top: 14px;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
    padding: 24px;
  }
}
</style>
