<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { GRADES } from '@/utils/format'
import projectIcon from '@/assets/project-icon.svg'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const locale = ref(localStorage.getItem('smart_learning_locale') || 'zh')
const theme = ref(localStorage.getItem('smart_learning_theme') || 'light')
const activeMode = ref('login')
const loading = ref(false)
const agreed = ref(false)

const loginFormRef = ref()
const registerFormRef = ref()

const loginForm = reactive({
  username: '',
  password: '',
  remember: true
})

const registerForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  grade: ''
})

const messages = {
  zh: {
    brand: '智慧学习辅助系统',
    enLabel: 'English',
    login: '登录',
    register: '注册',
    noAccount: '没有账号？',
    hasAccount: '已有账号？',
    username: '用户名',
    account: '账号',
    password: '密码',
    realName: '姓名',
    phone: '手机号',
    grade: '学段',
    remember: '记住我',
    forgot: '忘记密码？',
    agree: '同意用户协议和隐私政策',
    submitLogin: '登录',
    submitRegister: '注册并登录',
    language: '语言',
    theme: '主题',
    zhLabel: '简体中文',
    light: '浅色',
    dark: '深色',
    requiredAccount: '请输入账号',
    requiredPassword: '请输入密码',
    setPassword: '请设置密码',
    accountLength: '账号长度为 4-20 位',
    passwordLength: '密码长度为 6-20 位',
    phoneInvalid: '请输入 11 位手机号',
    agreeTip: '请先同意用户协议',
    loginOk: '登录成功',
    registerOk: '注册成功，已为你登录'
  },
  en: {
    brand: 'AI Learning Assistant',
    login: 'Log in',
    register: 'Sign up',
    noAccount: 'No account?',
    hasAccount: 'Already registered?',
    username: 'Username',
    account: 'Account',
    password: 'Password',
    realName: 'Name',
    phone: 'Phone',
    grade: 'Grade',
    remember: 'Remember me',
    forgot: 'Forgot password?',
    agree: 'I agree to the terms and privacy policy',
    submitLogin: 'Log in',
    submitRegister: 'Create account',
    language: 'Language',
    theme: 'Theme',
    zhLabel: 'Simplified Chinese',
    enLabel: 'English',
    light: 'Light',
    dark: 'Dark',
    requiredAccount: 'Please enter your account',
    requiredPassword: 'Please enter your password',
    setPassword: 'Please set a password',
    accountLength: 'Account length must be 4-20 characters',
    passwordLength: 'Password length must be 6-20 characters',
    phoneInvalid: 'Please enter an 11-digit phone number',
    agreeTip: 'Please agree to the terms first',
    loginOk: 'Logged in',
    registerOk: 'Registered and logged in'
  }
}

const text = computed(() => messages[locale.value] || messages.zh)

const loginRules = computed(() => ({
  username: [{ required: true, message: text.value.requiredAccount, trigger: 'blur' }],
  password: [{ required: true, message: text.value.requiredPassword, trigger: 'blur' }]
}))

const registerRules = computed(() => ({
  username: [
    { required: true, message: text.value.requiredAccount, trigger: 'blur' },
    { min: 4, max: 20, message: text.value.accountLength, trigger: 'blur' }
  ],
  password: [
    { required: true, message: text.value.setPassword, trigger: 'blur' },
    { min: 6, max: 20, message: text.value.passwordLength, trigger: 'blur' }
  ],
  phone: [{ pattern: /^1\d{10}$/, message: text.value.phoneInvalid, trigger: 'blur' }]
}))

function applyPreferences() {
  document.documentElement.dataset.theme = theme.value
  document.documentElement.lang = locale.value === 'zh' ? 'zh-CN' : 'en'
  localStorage.setItem('smart_learning_theme', theme.value)
  localStorage.setItem('smart_learning_locale', locale.value)
}

function switchMode(mode) {
  activeMode.value = mode
}

async function submitLogin() {
  try {
    await loginFormRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await auth.login({
      username: loginForm.username.trim(),
      password: loginForm.password
    })
    ElMessage.success(text.value.loginOk)
    const redirect = route.query.redirect?.toString()
    const defaultPath = data?.role === 2 ? '/admin/dashboard' : '/dashboard'
    const target =
      data?.role === 2 && (!redirect || redirect === '/' || redirect === '/dashboard')
        ? '/admin/dashboard'
        : redirect || defaultPath
    router.replace(target)
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  try {
    await registerFormRef.value?.validate()
  } catch {
    return
  }
  if (!agreed.value) {
    ElMessage.warning(text.value.agreeTip)
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: registerForm.username.trim(),
      password: registerForm.password,
      realName: registerForm.realName.trim(),
      grade: registerForm.grade,
      phone: registerForm.phone.trim()
    })
    ElMessage.success(text.value.registerOk)
    await auth.login({
      username: registerForm.username.trim(),
      password: registerForm.password
    })
    router.replace('/dashboard')
  } finally {
    loading.value = false
  }
}

watch([locale, theme], applyPreferences, { immediate: true })
onMounted(applyPreferences)
</script>

<template>
  <main class="auth-page">
    <header class="auth-header">
      <router-link class="auth-brand" to="/login">
        <span class="auth-brand-mark">
          <img :src="projectIcon" alt="" />
        </span>
        <strong>{{ text.brand }}</strong>
      </router-link>

      <div class="auth-tools">
        <el-dropdown trigger="click">
          <button class="tool-button" type="button">
            <el-icon><Switch /></el-icon>
            <span>{{ locale === 'zh' ? '中文' : 'EN' }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="locale = 'zh'">
                {{ text.zhLabel }}
                <el-icon v-if="locale === 'zh'"><Check /></el-icon>
              </el-dropdown-item>
              <el-dropdown-item @click="locale = 'en'">
                {{ text.enLabel }}
                <el-icon v-if="locale === 'en'"><Check /></el-icon>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown trigger="click">
          <button class="tool-button icon-only" type="button" :aria-label="text.theme">
            <el-icon><Brush /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="theme = 'light'">
                {{ text.light }}
                <el-icon v-if="theme === 'light'"><Check /></el-icon>
              </el-dropdown-item>
              <el-dropdown-item @click="theme = 'dark'">
                {{ text.dark }}
                <el-icon v-if="theme === 'dark'"><Check /></el-icon>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <section class="auth-center">
      <div class="auth-panel">
        <el-form
          v-if="activeMode === 'login'"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="auth-form"
          size="large"
          @submit.prevent
        >
          <el-form-item :label="text.username" prop="username">
            <el-input v-model.trim="loginForm.username" :placeholder="text.account" />
          </el-form-item>
          <el-form-item :label="text.password" prop="password">
            <el-input
              v-model="loginForm.password"
              :placeholder="text.password"
              show-password
              type="password"
              @keyup.enter="submitLogin"
            />
          </el-form-item>
          <div class="form-line">
            <el-checkbox v-model="loginForm.remember">{{ text.remember }}</el-checkbox>
            <el-link :underline="false">{{ text.forgot }}</el-link>
          </div>
          <el-button class="submit-button" size="large" :loading="loading" @click="submitLogin">
            <el-icon><Right /></el-icon>
            {{ text.submitLogin }}
          </el-button>
        </el-form>

        <el-form
          v-else
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          class="auth-form"
          size="large"
          @submit.prevent
        >
          <el-form-item :label="text.account" prop="username">
            <el-input v-model.trim="registerForm.username" :placeholder="text.account" />
          </el-form-item>
          <el-form-item :label="text.realName" prop="realName">
            <el-input v-model.trim="registerForm.realName" :placeholder="text.realName" />
          </el-form-item>
          <el-form-item :label="text.phone" prop="phone">
            <el-input v-model.trim="registerForm.phone" :placeholder="text.phone" />
          </el-form-item>
          <el-form-item :label="text.grade" prop="grade">
            <el-select v-model="registerForm.grade" :placeholder="text.grade" class="full-select">
              <el-option v-for="grade in GRADES" :key="grade" :label="grade" :value="grade" />
            </el-select>
          </el-form-item>
          <el-form-item :label="text.password" prop="password">
            <el-input
              v-model="registerForm.password"
              :placeholder="text.password"
              show-password
              type="password"
              @keyup.enter="submitRegister"
            />
          </el-form-item>
          <el-checkbox v-model="agreed" class="agree">{{ text.agree }}</el-checkbox>
          <el-button class="submit-button" size="large" :loading="loading" @click="submitRegister">
            <el-icon><Right /></el-icon>
            {{ text.submitRegister }}
          </el-button>
        </el-form>

        <p class="auth-switch-bottom">
          <span>{{ activeMode === 'login' ? text.noAccount : text.hasAccount }}</span>
          <button type="button" @click="switchMode(activeMode === 'login' ? 'register' : 'login')">
            {{ activeMode === 'login' ? text.register : text.login }}
          </button>
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  color: var(--text);
  background: var(--panel);
}

.auth-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 96px;
  padding: 0 clamp(24px, 4vw, 56px);
}

.auth-brand,
.auth-tools,
.tool-button {
  display: flex;
  align-items: center;
}

.auth-brand {
  gap: 12px;
  color: var(--text);
}

.auth-brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--primary-soft);
  font-size: 22px;
}

.auth-brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auth-brand strong {
  font-size: 28px;
  line-height: 1;
}

.auth-tools {
  gap: 10px;
}

.tool-button {
  gap: 8px;
  min-height: 40px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--text);
  background: var(--panel);
  cursor: pointer;
}

.tool-button.icon-only {
  width: 40px;
  justify-content: center;
  padding: 0;
}

.auth-center {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: calc(100vh - 96px);
  padding: clamp(84px, 15vh, 150px) 24px 56px;
}

.auth-panel {
  width: min(100%, 624px);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.auth-form :deep(.el-form-item__label) {
  color: var(--text);
  font-size: 18px;
  font-weight: 800;
}

.auth-form :deep(.el-input__wrapper),
.auth-form :deep(.el-select__wrapper) {
  min-height: 48px;
  border-radius: 999px;
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px var(--line);
}

.auth-form :deep(.el-input__inner) {
  color: var(--text);
}

.form-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 2px 0 22px;
}

.submit-button {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: 999px;
  color: #ffffff;
  background: #050505;
  font-size: 18px;
  font-weight: 800;
}

:global(:root[data-theme='dark']) .submit-button {
  color: #050505;
  background: #ffffff;
}

.submit-button:hover,
.submit-button:focus {
  color: #ffffff;
  background: #111827;
}

:global(:root[data-theme='dark']) .submit-button:hover,
:global(:root[data-theme='dark']) .submit-button:focus {
  color: #050505;
  background: #f3f4f6;
}

.full-select {
  width: 100%;
}

.agree {
  margin-bottom: 20px;
}

.auth-switch-bottom {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin: 26px 0 0;
  color: var(--muted);
  font-size: 16px;
}

.auth-switch-bottom button {
  padding: 0;
  border: 0;
  color: var(--text);
  background: transparent;
  font: inherit;
  font-weight: 800;
  text-decoration: underline;
  cursor: pointer;
}

@media (max-width: 720px) {
  .auth-header {
    height: 76px;
    padding: 0 18px;
  }

  .auth-brand strong {
    font-size: 22px;
  }

  .tool-button span {
    display: none;
  }

  .auth-center {
    min-height: calc(100vh - 76px);
    padding: 56px 18px;
  }

  .auth-switch-bottom {
    flex-wrap: wrap;
    font-size: 15px;
  }
}
</style>
