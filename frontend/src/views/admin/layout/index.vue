<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

const navGroups = [
  { path: '/admin/dashboard', title: '后台首页', icon: 'Monitor' },
  {
    title: '系统管理',
    icon: 'Setting',
    children: [{ path: '/admin/users', title: '用户管理', icon: 'UserFilled' }]
  },
  {
    title: '教学管理',
    icon: 'Reading',
    children: [
      { path: '/admin/resources', title: '资源管理', icon: 'FolderOpened' },
      { path: '/admin/questions', title: '题库管理', icon: 'DocumentChecked' }
    ]
  },
  {
    title: '智能引擎',
    icon: 'Cpu',
    children: [{ path: '/admin/ai', title: 'AI 配置', icon: 'MagicStick' }]
  },
  {
    title: '系统运维',
    icon: 'Operation',
    children: [{ path: '/admin/system', title: '系统状态', icon: 'DataLine' }]
  }
]

const activePath = computed(() => route.path)
const breadcrumbs = computed(() => route.matched.filter((item) => item.meta?.title))

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出后台管理？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    await auth.logout()
    router.replace('/login')
  } catch {
    // 用户取消退出。
  }
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside :width="collapsed ? '64px' : '220px'" class="admin-aside">
      <router-link class="admin-logo" to="/admin/dashboard">
        <span class="admin-logo-mark">SL</span>
        <strong v-show="!collapsed">智慧学习后台</strong>
      </router-link>

      <el-menu
        :default-active="activePath"
        :collapse="collapsed"
        class="admin-menu"
        background-color="#ffffff"
        text-color="#111827"
        active-text-color="#111827"
        router
        unique-opened
      >
        <template v-for="item in navGroups" :key="item.title || item.path">
          <el-menu-item v-if="!item.children" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>

          <el-sub-menu v-else :index="item.title">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
              <el-icon><component :is="child.icon" /></el-icon>
              <template #title>{{ child.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="admin-main-wrap">
      <el-header class="admin-navbar">
        <div class="admin-navbar-left">
          <el-button text :icon="collapsed ? 'Expand' : 'Fold'" @click="collapsed = !collapsed" />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">后台</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="admin-navbar-right">
          <el-tooltip content="返回学生端" placement="bottom">
            <el-button text :icon="'House'" @click="router.push('/dashboard')" />
          </el-tooltip>
          <el-tooltip content="刷新当前页" placement="bottom">
            <el-button text :icon="'Refresh'" @click="router.go(0)" />
          </el-tooltip>
          <el-dropdown trigger="click">
            <button class="admin-user-menu" type="button">
              <el-avatar :size="30">{{ auth.displayName.slice(0, 1) }}</el-avatar>
              <span>{{ auth.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/admin/users')">用户管理</el-dropdown-item>
                <el-dropdown-item @click="router.push('/admin/system')">系统状态</el-dropdown-item>
                <el-dropdown-item @click="router.push('/admin/ai')">AI 配置</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-shell {
  --el-color-primary: #111827;
  --el-color-primary-light-3: #374151;
  --el-color-primary-light-5: #6b7280;
  --el-color-primary-light-7: #d1d5db;
  --el-color-primary-light-8: #e5e7eb;
  --el-color-primary-light-9: #f3f4f6;
  --el-color-primary-dark-2: #030712;
  --el-color-success: #111827;
  --el-color-success-light-9: #f3f4f6;
  --el-color-success-light-8: #e5e7eb;
  --el-color-warning: #6b7280;
  --el-color-warning-light-9: #f3f4f6;
  --el-color-warning-light-8: #e5e7eb;
  --el-color-danger: #111827;
  --el-color-danger-light-9: #f3f4f6;
  --el-color-danger-light-8: #e5e7eb;
  --el-color-info: #6b7280;
  --el-color-info-light-9: #f3f4f6;
  --el-color-info-light-8: #e5e7eb;
  min-height: 100vh;
  background: #f7f7f8;
}

.admin-aside {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
  box-shadow: none;
  transition: width 0.2s ease;
}

.admin-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 54px;
  padding: 0 14px;
  overflow: hidden;
  border-bottom: 1px solid #e5e7eb;
  color: #111827;
  background: #ffffff;
  white-space: nowrap;
}

.admin-logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  color: #ffffff;
  background: #111827;
  font-weight: 800;
}

.admin-logo strong {
  font-size: 16px;
  letter-spacing: 0;
}

.admin-menu {
  flex: 1 1 auto;
  border-right: 0;
  background: #ffffff;
}

.admin-menu :deep(.el-menu) {
  background: #ffffff;
}

.admin-menu :deep(.el-menu-item),
.admin-menu :deep(.el-sub-menu__title) {
  color: #111827;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: #f3f4f6;
  color: #111827;
  font-weight: 700;
}

.admin-menu :deep(.el-sub-menu__title:hover),
.admin-menu :deep(.el-menu-item:hover) {
  background: #f7f7f8;
}

.admin-main-wrap {
  min-width: 0;
}

.admin-navbar {
  position: sticky;
  top: 0;
  z-index: 18;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.admin-navbar-left,
.admin-navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.admin-user-menu {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  color: #111827;
  background: transparent;
  cursor: pointer;
}

.admin-content {
  min-width: 0;
  padding: 16px;
}

@media (max-width: 900px) {
  .admin-aside {
    display: none;
  }

  .admin-content {
    padding: 12px;
  }
}
</style>
