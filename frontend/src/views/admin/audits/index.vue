<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { adminPersonalDataAuditApi } from '@/api/admin'
import { formatDateTime, formatFileSize, pageList, pageTotal } from '@/utils/format'

const activeTab = ref('exports')
const exportLoading = ref(false)
const clearLoading = ref(false)
const exportPage = ref({})
const clearPage = ref({})

const exportQuery = reactive({
  userId: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})

const clearQuery = reactive({
  userId: '',
  pageNum: 1,
  pageSize: 10
})

const statusOptions = [
  { label: '可下载', value: 1 },
  { label: '已过期', value: 2 },
  { label: '次数用完', value: 3 },
  { label: '已删除', value: 4 }
]

const statusMap = {
  active: { text: '可下载', type: 'success' },
  expired: { text: '已过期', type: 'info' },
  consumed: { text: '次数用完', type: 'warning' },
  deleted: { text: '已删除', type: 'danger' },
  unknown: { text: '未知', type: 'info' }
}

const exportTotalDownloads = computed(() =>
  pageList(exportPage.value).reduce((sum, item) => sum + Number(item.downloadCount || 0), 0)
)

function statusMeta(status) {
  return statusMap[status] || statusMap.unknown
}

function cleanExportParams() {
  return {
    userId: exportQuery.userId || undefined,
    status: exportQuery.status === '' ? undefined : exportQuery.status,
    pageNum: exportQuery.pageNum,
    pageSize: exportQuery.pageSize
  }
}

function cleanClearParams() {
  return {
    userId: clearQuery.userId || undefined,
    pageNum: clearQuery.pageNum,
    pageSize: clearQuery.pageSize
  }
}

async function loadExportLogs() {
  exportLoading.value = true
  try {
    exportPage.value = await adminPersonalDataAuditApi.exportLogs(cleanExportParams())
  } finally {
    exportLoading.value = false
  }
}

async function loadClearLogs() {
  clearLoading.value = true
  try {
    clearPage.value = await adminPersonalDataAuditApi.clearLogs(cleanClearParams())
  } finally {
    clearLoading.value = false
  }
}

function searchExports() {
  exportQuery.pageNum = 1
  loadExportLogs()
}

function resetExports() {
  Object.assign(exportQuery, { userId: '', status: '', pageNum: 1, pageSize: 10 })
  loadExportLogs()
}

function searchClears() {
  clearQuery.pageNum = 1
  loadClearLogs()
}

function resetClears() {
  Object.assign(clearQuery, { userId: '', pageNum: 1, pageSize: 10 })
  loadClearLogs()
}

function countValue(counts, key) {
  const value = counts?.[key]
  return Number.isFinite(Number(value)) ? Number(value) : 0
}

onMounted(() => {
  loadExportLogs()
  loadClearLogs()
})
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-title">
      <div>
        <h1>个人数据审计</h1>
        <p>审计用户个人数据导出、下载次数、过期状态和清空记录。</p>
      </div>
      <div class="admin-title-actions">
        <el-button :icon="'Refresh'" @click="activeTab === 'exports' ? loadExportLogs() : loadClearLogs()">刷新</el-button>
      </div>
    </div>

    <section class="admin-kpi-grid">
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon blue"><el-icon><Download /></el-icon></span>
        <span>
          <em>{{ pageTotal(exportPage) }}</em>
          <strong>导出记录</strong>
        </span>
      </div>
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon green"><el-icon><CircleCheckFilled /></el-icon></span>
        <span>
          <em>{{ exportTotalDownloads }}</em>
          <strong>当前页下载次数</strong>
        </span>
      </div>
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon orange"><el-icon><Delete /></el-icon></span>
        <span>
          <em>{{ pageTotal(clearPage) }}</em>
          <strong>清空记录</strong>
        </span>
      </div>
      <div class="admin-kpi-card static">
        <span class="admin-kpi-icon red"><el-icon><WarningFilled /></el-icon></span>
        <span>
          <em>24h</em>
          <strong>导出有效期</strong>
        </span>
      </div>
    </section>

    <section class="admin-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="导出审计" name="exports">
          <el-form class="admin-toolbar" :model="exportQuery" inline @submit.prevent>
            <el-form-item label="用户 ID">
              <el-input v-model.trim="exportQuery.userId" placeholder="全部用户" clearable @keyup.enter="searchExports" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="exportQuery.status" placeholder="全部" clearable class="admin-filter">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="searchExports">查询</el-button>
              <el-button :icon="'RefreshLeft'" @click="resetExports">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="exportLoading" :data="pageList(exportPage)" empty-text="暂无导出审计记录">
            <el-table-column prop="exportId" label="ID" width="80" />
            <el-table-column prop="userId" label="用户 ID" width="100" />
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

          <div class="admin-pagination">
            <el-pagination
              v-model:current-page="exportQuery.pageNum"
              v-model:page-size="exportQuery.pageSize"
              :total="pageTotal(exportPage)"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadExportLogs"
              @current-change="loadExportLogs"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="清空审计" name="clears">
          <el-form class="admin-toolbar" :model="clearQuery" inline @submit.prevent>
            <el-form-item label="用户 ID">
              <el-input v-model.trim="clearQuery.userId" placeholder="全部用户" clearable @keyup.enter="searchClears" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="'Search'" @click="searchClears">查询</el-button>
              <el-button :icon="'RefreshLeft'" @click="resetClears">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="clearLoading" :data="pageList(clearPage)" empty-text="暂无清空审计记录">
            <el-table-column prop="logId" label="ID" width="80" />
            <el-table-column prop="userId" label="用户 ID" width="100" />
            <el-table-column prop="clearScope" label="清空范围" min-width="300" show-overflow-tooltip />
            <el-table-column label="删除计数" min-width="300">
              <template #default="{ row }">
                <div class="count-tags">
                  <el-tag>计划 {{ countValue(row.counts, 'studyPlans') }}</el-tag>
                  <el-tag>任务 {{ countValue(row.counts, 'studyTasks') }}</el-tag>
                  <el-tag>答疑 {{ countValue(row.counts, 'qaMessages') }}</el-tag>
                  <el-tag>测评 {{ countValue(row.counts, 'assessments') }}</el-tag>
                  <el-tag>错题 {{ countValue(row.counts, 'wrongQuestions') }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="confirmationText" label="确认文本" width="150" />
            <el-table-column label="清空时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="admin-pagination">
            <el-pagination
              v-model:current-page="clearQuery.pageNum"
              v-model:page-size="clearQuery.pageSize"
              :total="pageTotal(clearPage)"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadClearLogs"
              @current-change="loadClearLogs"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.count-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
