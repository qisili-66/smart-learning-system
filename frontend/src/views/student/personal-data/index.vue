<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { personalDataApi } from '@/api/student'
import { formatDateTime, formatFileSize } from '@/utils/format'

const loading = ref(false)
const exporting = ref(false)
const overview = ref({})
const exportLogs = ref({ items: [] })
const clearLogs = ref({ items: [] })

const exportItems = computed(() => Array.isArray(exportLogs.value?.items) ? exportLogs.value.items : [])
const clearItems = computed(() => Array.isArray(clearLogs.value?.items) ? clearLogs.value.items : [])

const statusMap = {
  active: { text: '可下载', type: 'success' },
  expired: { text: '已过期', type: 'info' },
  consumed: { text: '次数用完', type: 'warning' },
  deleted: { text: '已删除', type: 'danger' },
  unknown: { text: '未知', type: 'info' }
}

function statusMeta(status) {
  return statusMap[status] || statusMap.unknown
}

function countValue(counts, key) {
  const value = counts?.[key]
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

async function loadData() {
  loading.value = true
  try {
    const [overviewData, exportData, clearData] = await Promise.all([
      personalDataApi.overview().catch(() => ({})),
      personalDataApi.exportLogs().catch(() => ({ items: [] })),
      personalDataApi.clearLogs().catch(() => ({ items: [] }))
    ])
    overview.value = overviewData || {}
    exportLogs.value = exportData || { items: [] }
    clearLogs.value = clearData || { items: [] }
  } finally {
    loading.value = false
  }
}

async function createExport() {
  exporting.value = true
  try {
    const data = await personalDataApi.export()
    ElMessage.success('个人数据导出已生成')
    if (data?.downloadUrl) {
      window.open(data.downloadUrl, '_blank', 'noopener')
    }
    await loadData()
  } finally {
    exporting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page" v-loading="loading">
    <section class="data-header panel panel-body">
      <div>
        <h1>个人数据</h1>
        <p>查看导出记录、清空记录和当前个人学习数据概览。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="'Refresh'" @click="loadData">刷新</el-button>
        <el-button type="primary" :icon="'Download'" :loading="exporting" @click="createExport">生成导出</el-button>
      </div>
    </section>

    <section class="stat-grid">
      <div class="stat-card panel">
        <span>学习计划</span>
        <strong>{{ overview.studyPlanCount || 0 }}</strong>
      </div>
      <div class="stat-card panel">
        <span>学习记录</span>
        <strong>{{ overview.studyRecordCount || 0 }}</strong>
      </div>
      <div class="stat-card panel">
        <span>错题</span>
        <strong>{{ overview.wrongQuestionCount || 0 }}</strong>
      </div>
      <div class="stat-card panel">
        <span>测评</span>
        <strong>{{ overview.assessmentCount || 0 }}</strong>
      </div>
    </section>

    <section class="panel panel-body audit-panel">
      <div class="panel-head">
        <div>
          <h2>导出审计</h2>
          <p>导出链接有效期 24 小时，最多成功下载 3 次。</p>
        </div>
      </div>
      <el-table :data="exportItems" empty-text="暂无导出记录">
        <el-table-column prop="exportId" label="ID" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="260" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="下载次数" width="120">
          <template #default="{ row }">{{ row.downloadCount || 0 }} / {{ row.maxDownloadCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="最后下载" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastDownloadTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel panel-body audit-panel">
      <div class="panel-head">
        <div>
          <h2>清空审计</h2>
          <p>记录每次个人数据清空的范围和删除计数。</p>
        </div>
      </div>
      <el-table :data="clearItems" empty-text="暂无清空记录">
        <el-table-column prop="logId" label="ID" width="80" />
        <el-table-column prop="clearScope" label="清空范围" min-width="260" show-overflow-tooltip />
        <el-table-column label="主要计数" min-width="260">
          <template #default="{ row }">
            <div class="count-tags">
              <el-tag>计划 {{ countValue(row.counts, 'studyPlans') }}</el-tag>
              <el-tag>任务 {{ countValue(row.counts, 'studyTasks') }}</el-tag>
              <el-tag>答疑 {{ countValue(row.counts, 'qaMessages') }}</el-tag>
              <el-tag>错题 {{ countValue(row.counts, 'wrongQuestions') }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="confirmationText" label="确认文本" width="150" />
        <el-table-column label="清空时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.data-header,
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.data-header h1,
.panel-head h2 {
  margin: 0 0 6px;
  letter-spacing: 0;
}

.data-header p,
.panel-head p,
.stat-card span {
  margin: 0;
  color: var(--muted);
}

.header-actions {
  display: flex;
  gap: 10px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  padding: 18px;
}

.stat-card strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
}

.audit-panel {
  overflow: hidden;
}

.count-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 900px) {
  .data-header,
  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
