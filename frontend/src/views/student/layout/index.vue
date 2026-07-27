<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import projectIcon from '@/assets/project-icon.svg'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const moreOpen = ref(false)

const text = {
  brand: 'Smart Study Agent',
  shortBrand: '智慧学习',
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
  data: '个人数据',
  more: '更多',
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
    items: [
      { path: '/profile', label: text.profile, icon: 'DataAnalysis' },
      { path: '/personal-data', label: text.data, icon: 'Lock' }
    ]
  }
]

const mobilePrimaryPaths = ['/dashboard', '/ai', '/study-plans', '/wrong-questions']
const mobilePrimaryItems = computed(() =>
  sideGroups.flatMap((group) => group.items).filter((item) => mobilePrimaryPaths.includes(item.path))
)
const mobileMoreGroups = computed(() =>
  sideGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !mobilePrimaryPaths.includes(item.path))
    }))
    .filter((group) => group.items.length)
)
const activePath = computed(() => route.path)
const currentItem = computed(() => sideGroups.flatMap((group) => group.items).find((item) => isActive(item.path)))
const mobileMoreActive = computed(() =>
  mobileMoreGroups.value.some((group) => group.items.some((item) => isActive(item.path)))
)

function go(path) {
  moreOpen.value = false
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
        <router-link class="brand" to="/dashboard" :aria-label="text.brand">
          <span class="brand-icon"><img :src="projectIcon" alt="" /></span>
          <strong v-show="!collapsed">{{ text.brand }}</strong>
        </router-link>
        <el-button text class="collapse-button" :icon="collapsed ? 'Expand' : 'Fold'" @click="collapsed = !collapsed" />
      </div>

      <nav class="side-nav" aria-label="学生端导航">
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
      <header class="mobile-topbar">
        <router-link class="mobile-brand" to="/dashboard" :aria-label="text.brand">
          <img :src="projectIcon" alt="" />
          <span>
            <strong>{{ currentItem?.label || text.shortBrand }}</strong>
            <small>{{ text.shortBrand }}</small>
          </span>
        </router-link>
        <div class="mobile-user-actions">
          <el-avatar :size="32">{{ auth.displayName.slice(0, 1) }}</el-avatar>
          <el-button text :icon="'SwitchButton'" @click="handleLogout" />
        </div>
      </header>
      <section class="main-surface">
        <router-view />
      </section>
    </main>

    <nav class="mobile-tabbar" aria-label="移动端主导航">
      <button
        v-for="item in mobilePrimaryItems"
        :key="item.path"
        :class="{ active: isActive(item.path) }"
        type="button"
        @click="go(item.path)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
      <button :class="{ active: mobileMoreActive || moreOpen }" type="button" @click="moreOpen = true">
        <el-icon><Menu /></el-icon>
        <span>{{ text.more }}</span>
      </button>
    </nav>

    <el-drawer v-model="moreOpen" title="更多学习工具" direction="btt" size="58%" class="mobile-more-drawer">
      <div class="mobile-more-panel">
        <section v-for="group in mobileMoreGroups" :key="group.title" class="mobile-more-group">
          <h2>{{ group.title }}</h2>
          <button
            v-for="item in group.items"
            :key="item.path"
            :class="{ active: isActive(item.path) }"
            type="button"
            @click="go(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </section>
      </div>
    </el-drawer>
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
.sidebar-footer,
.mobile-brand,
.mobile-topbar,
.mobile-user-actions {
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

.brand-icon,
.mobile-brand img {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--panel-2, var(--primary-soft));
}

.brand-icon img,
.mobile-brand img {
  object-fit: cover;
}

.brand-icon img {
  width: 100%;
  height: 100%;
}

.brand strong {
  overflow: hidden;
  max-width: 174px;
  font-size: 17px;
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
  border-radius: 10px;
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
  border-radius: 18px;
  background: var(--panel);
  box-shadow: var(--shadow);
}

.mobile-topbar,
.mobile-tabbar {
  display: none;
}

.mobile-more-panel {
  display: grid;
  gap: 18px;
}

.mobile-more-group {
  display: grid;
  gap: 10px;
}

.mobile-more-group h2 {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 800;
}

.mobile-more-group button {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  color: var(--text);
  background: var(--panel);
  font: inherit;
  text-align: left;
}

.mobile-more-group button.active {
  border-color: rgba(47, 107, 79, 0.24);
  background: var(--primary-soft);
}

.mobile-more-group .el-icon:first-child {
  justify-self: center;
  color: var(--primary);
  font-size: 19px;
}

@media (max-width: 1100px) {
  .student-shell {
    --sidebar-width: 228px;
  }

  .main-surface {
    padding: 18px;
  }
}

@media (max-width: 860px) {
  .student-shell {
    display: block;
    min-height: 100dvh;
    padding-bottom: calc(76px + env(safe-area-inset-bottom));
    background: var(--bg);
  }

  .sidebar {
    display: none;
  }

  .main-wrap {
    padding: 0;
  }

  .mobile-topbar {
    position: sticky;
    top: 0;
    z-index: 20;
    justify-content: space-between;
    min-height: calc(62px + env(safe-area-inset-top));
    padding: calc(8px + env(safe-area-inset-top)) 14px 8px;
    border-bottom: 1px solid var(--line);
    background: rgba(251, 252, 250, 0.94);
    backdrop-filter: blur(14px);
  }

  .mobile-brand {
    min-width: 0;
    gap: 10px;
    color: var(--text);
  }

  .mobile-brand span {
    display: flex;
    min-width: 0;
    flex-direction: column;
  }

  .mobile-brand strong,
  .mobile-brand small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-brand strong {
    font-size: 17px;
    line-height: 1.15;
  }

  .mobile-brand small {
    margin-top: 3px;
    color: var(--muted);
    font-size: 11px;
  }

  .mobile-user-actions {
    gap: 6px;
    flex-shrink: 0;
  }

  .main-surface {
    min-height: calc(100dvh - 138px);
    padding: 14px 12px 18px;
    border: 0;
    border-radius: 0;
    background: transparent;
    box-shadow: none;
  }

  .mobile-tabbar {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 30;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 2px;
    padding: 7px 8px calc(7px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--line);
    background: rgba(251, 252, 250, 0.96);
    box-shadow: 0 -16px 40px -30px rgba(20, 40, 30, 0.5);
    backdrop-filter: blur(16px);
  }

  .mobile-tabbar button {
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 0;
    min-height: 50px;
    padding: 4px 2px;
    border: 0;
    border-radius: 14px;
    color: var(--muted);
    background: transparent;
    font: inherit;
    font-size: 11px;
    cursor: pointer;
    flex-direction: column;
    gap: 4px;
  }

  .mobile-tabbar button.active {
    color: var(--primary-dark);
    background: var(--primary-soft);
  }

  .mobile-tabbar .el-icon {
    font-size: 20px;
  }

  .mobile-tabbar span {
    overflow: hidden;
    max-width: 100%;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 430px) {
  .mobile-tabbar button {
    font-size: 10px;
  }

  .main-surface {
    padding: 12px 10px 16px;
  }
}
</style>
