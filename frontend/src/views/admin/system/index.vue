<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminSystemApi } from '@/api/admin'
import { formatDateTime, pageList } from '@/utils/format'

const loading = ref(false)
const backupLoading = ref(false)
const status = ref({})
const logs = ref({})
const faults = ref({})

function formatUptime(value) {
  const seconds = Math.floor(Number(value || 0) / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${days}天 ${hours}小时 ${minutes}分钟`
}

async function loadSystem() {
  loading.value = true
  try {
    const [statusData, logsData, faultsData] = await Promise.all([
      adminSystemApi.status(),
      adminSystemApi.logs({ pageNum: 1, pageSize: 10 }),
      adminSystemApi.faults({ pageNum: 1, pageSize: 10 })
    ])
    status.value = statusData || {}
    logs.value = logsData || {}
    faults.value = faultsData || {}
  } finally {
    loading.value = false
  }
}

async function createBackup() {
  backupLoading.value = true
  try {
    const result = await adminSystemApi.backup()
    ElMessage.success(`备份已创建：${result?.backupId || '-'}`)
  } finally {
    backupLoading.value = false
  }
}

onMounted(loadSystem)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <div class="admin-page-title">
      <div>
        <h1>系统运维</h1>
        <p>已接通系统状态、日志、故障列表和备份接口。</p>
      </div>
      <div class="admin-title-actions">
        <el-button :icon="'Refresh'" @click="loadSystem">刷新</el-button>
        <el-button type="primary" :icon="'Download'" :loading="backupLoading" @click="createBackup">创建备份</el-button>
      </div>
    </div>

    <section class="admin-kpi-grid">
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon green"><el-icon><CircleCheckFilled /></el-icon></span>
        <span>
          <em>{{ status.status || 'UNKNOWN' }}</em>
          <strong>服务状态</strong>
        </span>
      </div>
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon blue"><el-icon><Timer /></el-icon></span>
        <span>
          <em>{{ formatUptime(status.uptimeMillis) }}</em>
          <strong>运行时长</strong>
        </span>
      </div>
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon orange"><el-icon><Document /></el-icon></span>
        <span>
          <em>{{ logs.total || 0 }}</em>
          <strong>系统日志</strong>
        </span>
      </div>
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon red"><el-icon><WarningFilled /></el-icon></span>
        <span>
          <em>{{ faults.total || 0 }}</em>
          <strong>故障记录</strong>
        </span>
      </div>
    </section>

    <section class="admin-two-col">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>系统信息</h2>
          <el-tag :type="status.status === 'UP' ? 'success' : 'danger'">{{ status.status || '-' }}</el-tag>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="接口时间">{{ formatDateTime(status.time) }}</el-descriptions-item>
          <el-descriptions-item label="运行毫秒">{{ status.uptimeMillis || 0 }}</el-descriptions-item>
          <el-descriptions-item label="接口路径">GET /admin/system/status</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>备份操作</h2>
        </div>
        <p class="admin-muted">当前接口会返回备份编号和创建状态，用于先跑通后台运维链路。</p>
        <el-button type="primary" :loading="backupLoading" @click="createBackup">立即备份</el-button>
      </div>
    </section>

    <section class="admin-two-col">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>系统日志</h2>
        </div>
        <el-table :data="pageList(logs)" empty-text="暂无日志">
          <el-table-column prop="time" label="时间" width="170" />
          <el-table-column prop="level" label="级别" width="90" />
          <el-table-column prop="message" label="内容" min-width="180" />
        </el-table>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>故障记录</h2>
        </div>
        <el-table :data="pageList(faults)" empty-text="暂无故障">
          <el-table-column prop="time" label="时间" width="170" />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="message" label="描述" min-width="180" />
        </el-table>
      </div>
    </section>
  </div>
</template>
