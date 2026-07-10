<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  adminAiApi,
  adminQuestionApi,
  adminResourceApi,
  adminSystemApi,
  adminUserApi
} from '@/api/admin'
import { formatDateTime, pageList, pageTotal, resourceTypeLabel } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const usersPage = ref({})
const resourcesPage = ref({})
const questionsPage = ref({})
const systemStatus = ref({})
const aiModels = ref([])

const kpis = computed(() => [
  {
    label: '用户总数',
    value: pageTotal(usersPage.value),
    icon: 'UserFilled',
    path: '/admin/users'
  },
  {
    label: '资源数量',
    value: pageTotal(resourcesPage.value),
    icon: 'FolderOpened',
    path: '/admin/resources'
  },
  {
    label: '题库题目',
    value: pageTotal(questionsPage.value),
    icon: 'DocumentChecked',
    path: '/admin/questions'
  },
  {
    label: 'AI 模型',
    value: aiModels.value.length,
    icon: 'Cpu',
    path: '/admin/ai'
  }
])

function formatUptime(value) {
  const seconds = Math.floor(Number(value || 0) / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}小时 ${minutes}分钟`
}

async function loadDashboard() {
  loading.value = true
  try {
    const [users, resources, questions, status, models] = await Promise.all([
      adminUserApi.list({ pageNum: 1, pageSize: 5 }).catch(() => ({})),
      adminResourceApi.list({ pageNum: 1, pageSize: 5 }).catch(() => ({})),
      adminQuestionApi.list({ pageNum: 1, pageSize: 5 }).catch(() => ({})),
      adminSystemApi.status().catch(() => ({})),
      adminAiApi.models().catch(() => [])
    ])
    usersPage.value = users || {}
    resourcesPage.value = resources || {}
    questionsPage.value = questions || {}
    systemStatus.value = status || {}
    aiModels.value = Array.isArray(models) ? models : []
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <div class="admin-page-title">
      <div>
        <h1>后台首页</h1>
        <p>管理端接口联调总览，覆盖用户、资源、题库、AI 和系统状态。</p>
      </div>
      <el-button type="primary" :icon="'Refresh'" @click="loadDashboard">刷新</el-button>
    </div>

    <section class="admin-kpi-grid">
      <button v-for="item in kpis" :key="item.label" class="admin-kpi-card" type="button" @click="router.push(item.path)">
        <span class="admin-kpi-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <span>
          <em>{{ item.value }}</em>
          <strong>{{ item.label }}</strong>
        </span>
      </button>
    </section>

    <section class="admin-two-col">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>系统运行</h2>
          <el-tag effect="plain" class="admin-neutral-tag">
            {{ systemStatus.status || 'UNKNOWN' }}
          </el-tag>
        </div>
        <dl class="admin-status-list">
          <div>
            <dt>当前时间</dt>
            <dd>{{ formatDateTime(systemStatus.time) }}</dd>
          </div>
          <div>
            <dt>运行时长</dt>
            <dd>{{ formatUptime(systemStatus.uptimeMillis) }}</dd>
          </div>
          <div>
            <dt>接口状态</dt>
            <dd>已连接 /admin/system/status</dd>
          </div>
        </dl>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>AI 模型</h2>
          <el-button text type="primary" @click="router.push('/admin/ai')">配置</el-button>
        </div>
        <el-table :data="aiModels" size="small" empty-text="暂无模型">
          <el-table-column prop="modelName" label="模型" min-width="120" />
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ row.status || '-' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="admin-two-col">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>最新资源</h2>
          <el-button text type="primary" @click="router.push('/admin/resources')">更多</el-button>
        </div>
        <el-table :data="pageList(resourcesPage)" size="small" empty-text="暂无资源">
          <el-table-column prop="resourceName" label="资源名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="subject" label="学科" width="90" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>最新题目</h2>
          <el-button text type="primary" @click="router.push('/admin/questions')">更多</el-button>
        </div>
        <el-table :data="pageList(questionsPage)" size="small" empty-text="暂无题目">
          <el-table-column prop="questionText" label="题干" min-width="220" show-overflow-tooltip />
          <el-table-column prop="subject" label="学科" width="90" />
          <el-table-column prop="knowledgePoint" label="知识点" width="130" show-overflow-tooltip />
        </el-table>
      </div>
    </section>
  </div>
</template>
