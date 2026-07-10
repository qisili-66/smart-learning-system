<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

const text = {
  brand: 'Smart Learning',
  learning: '学习',
  training: '训练',
  account: '个人',
  dashboard: '学习首页',
  ai: '智能答疑',
  resources: '资源库',
  plan: '学习方案',
  exam: '测评中心',
  wrong: '错题本',
  profile: '学情画像',
  logout: '退出登录'
}

const sideGroups = [
  {
    title: text.learning,
    items: [
      { path: '/dashboard', label: text.dashboard, icon: 'House' },
      { path: '/ai', label: text.ai, icon: 'ChatLineRound' },
      { path: '/resources', label: text.resources, icon: 'FolderOpened' },
      { path: '/study-plans', label: text.plan, icon: 'Calendar' }
    ]
  },
  {
    title: text.training,
    items: [
      { path: '/assessments', label: text.exam, icon: 'DocumentChecked' },
      { path: '/wrong-questions', label: text.wrong, icon: 'Notebook' }
    ]
  },
  {
    title: text.account,
    items: [{ path: '/profile', label: text.profile, icon: 'DataAnalysis' }]
  }
]

const activePath = computed(() => route.path)

function go(path) {
  router.push(path)
}

function isActive(path) {
  return activePath.value === path || activePath.value.startsWith(`${path}/`)
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出当前账号？', text.logout, {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    await auth.logout()
    router.replace('/login')
  } catch {
    // 用户取消。
  }
}
</script>

<template>
  <div class="student-shell" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="sidebar-head">
        <router-link class="brand" to="/dashboard">
          <span class="brand-icon"><el-icon><Reading /></el-icon></span>
          <strong v-show="!collapsed">{{ text.brand }}</strong>
        </router-link>
        <el-button text class="collapse-button" :icon="collapsed ? 'Expand' : 'Fold'" @click="collapsed = !collapsed" />
      </div>

      <nav class="side-nav" aria-label="core navigation">
        <section v-for="group in sideGroups" :key="group.title" class="side-group">
          <p v-show="!collapsed">{{ group.title }}</p>
          <el-tooltip
            v-for="item in group.items"
            :key="item.path"
            :disabled="!collapsed"
            :content="item.label"
            placement="right"
          >
            <button
              :class="{ active: isActive(item.path) }"
              class="side-link"
              type="button"
              @click="go(item.path)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span v-show="!collapsed">{{ item.label }}</span>
              <el-icon v-show="!collapsed && isActive(item.path)"><ArrowRight /></el-icon>
            </button>
          </el-tooltip>
        </section>
      </nav>

      <div class="sidebar-footer">
        <el-avatar :size="34">{{ auth.displayName.slice(0, 1) }}</el-avatar>
        <div v-show="!collapsed">
          <strong>{{ auth.displayName }}</strong>
          <span>学生端</span>
        </div>
        <el-tooltip :content="text.logout" placement="right">
          <el-button text class="logout-button" :icon="'SwitchButton'" @click="handleLogout" />
        </el-tooltip>
      </div>
    </aside>

    <main class="main-wrap">
      <section class="main-surface">
        <router-view />
      </section>
    </main>
  </div>
</template>

<style scoped>
.student-shell {
  --sidebar-width: 258px;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
  color: var(--text);
  background: var(--bg);
  transition: grid-template-columns 0.2s ease;
}

.student-shell.collapsed {
  --sidebar-width: 80px;
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 18px 14px;
  border-right: 1px solid var(--line);
  background: var(--panel);
}

.sidebar-head,
.brand,
.sidebar-footer {
  display: flex;
  align-items: center;
}

.sidebar-head {
  justify-content: space-between;
  gap: 8px;
  min-height: 42px;
  margin-bottom: 24px;
}

.brand {
  min-width: 0;
  gap: 10px;
  color: var(--text);
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--primary-soft);
  font-size: 20px;
}

.brand strong {
  overflow: hidden;
  max-width: 160px;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collapse-button,
.logout-button {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--text);
}

.side-nav {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 24px;
  overflow: auto;
}

.side-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-group p {
  margin: 0 0 6px 12px;
  color: var(--subtle);
  font-size: 13px;
  font-weight: 800;
}

.side-link {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  min-height: 46px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  color: var(--text);
  background: transparent;
  font: inherit;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}

.collapsed .side-link {
  grid-template-columns: 1fr;
  justify-items: center;
  padding: 0;
}

.side-link.active,
.side-link:hover {
  background: var(--primary-soft);
}

.side-link span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-footer {
  gap: 10px;
  min-height: 56px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.sidebar-footer div {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  flex-direction: column;
}

.sidebar-footer strong,
.sidebar-footer span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-footer strong {
  font-size: 14px;
}

.sidebar-footer span {
  color: var(--muted);
  font-size: 12px;
}

.main-wrap {
  min-width: 0;
  padding: 16px;
}

.main-surface {
  min-height: calc(100vh - 32px);
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

@media (max-width: 860px) {
  .student-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
    height: auto;
    min-height: auto;
  }

  .side-nav {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }

  .side-group p {
    display: none;
  }

  .sidebar-footer {
    display: none;
  }

  .main-wrap {
    padding: 8px;
  }
}

@media (max-width: 620px) {
  .side-nav {
    grid-template-columns: 1fr;
  }

  .main-surface {
    padding: 14px;
  }
}
</style>
