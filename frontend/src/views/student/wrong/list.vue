<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { wrongQuestionApi } from '@/api/student'
import {
  SUBJECTS,
  WRONG_REASON,
  formatDateTime,
  pageList,
  pageTotal,
  wrongReasonLabel
} from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const clearing = ref(false)
const deletingWrongId = ref(null)
const page = ref({ list: [], total: 0 })
const stats = ref({ total: 0, mastered: 0, notMastered: 0 })

const filters = reactive({
  subject: '',
  wrongReason: '',
  isMastered: '',
  pageNum: 1,
  pageSize: 10
})

function queryNumber(value) {
  if (value === undefined || value === null || value === '') return ''
  const number = Number(value)
  return Number.isNaN(number) ? '' : number
}

function syncFiltersFromRoute() {
  filters.subject = typeof route.query.subject === 'string' ? route.query.subject : ''
  filters.wrongReason = queryNumber(route.query.wrongReason)
  filters.isMastered = queryNumber(route.query.isMastered)
  filters.pageNum = queryNumber(route.query.pageNum) || 1
}

async function loadList() {
  loading.value = true
  try {
    page.value = await wrongQuestionApi.list({
      ...filters,
      subject: filters.subject || undefined,
      wrongReason: filters.wrongReason === '' ? undefined : filters.wrongReason,
      isMastered: filters.isMastered === '' ? undefined : filters.isMastered
    })
    stats.value = await wrongQuestionApi.statistics({
      subject: filters.subject || undefined
    })
  } finally {
    loading.value = false
  }
}

async function openDetail(wrongId) {
  router.push(`/wrong-questions/${wrongId}`)
}

async function markMastered(row, value) {
  try {
    await wrongQuestionApi.markMastered(row.wrongId, value)
    ElMessage.success(value ? '已标记掌握' : '已移回待复盘')
    await loadList()
  } catch (error) {
    ElMessage.error(error.message || '标记失败')
  }
}

async function exportBook() {
  exporting.value = true
  try {
    const data = await wrongQuestionApi.export({
      subject: filters.subject || undefined,
      isMastered: filters.isMastered === '' ? undefined : filters.isMastered,
      format: 'doc'
    })
    if (data?.downloadUrl) {
      const blob = await wrongQuestionApi.downloadExport(data.downloadUrl)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = data.fileName || 'wrong-question-book.doc'
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success('错题本已导出')
    } else {
      ElMessage.success('导出请求已提交')
    }
  } finally {
    exporting.value = false
  }
}

async function confirmMastered(row) {
  await ElMessageBox.confirm('确认这道错题已经掌握？', '标记掌握', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await markMastered(row, 1)
}

async function deleteWrong(row) {
  await ElMessageBox.confirm(`确认删除错题 #${row.wrongId}？`, '删除错题记录', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  deletingWrongId.value = row.wrongId
  try {
    await wrongQuestionApi.remove(row.wrongId)
    ElMessage.success('错题记录已删除')
    await loadList()
  } finally {
    deletingWrongId.value = null
  }
}

async function clearWrongQuestions() {
  await ElMessageBox.confirm('确认删除当前筛选条件下的错题记录？', '一键删除错题记录', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  clearing.value = true
  try {
    const data = await wrongQuestionApi.clear({
      subject: filters.subject || undefined,
      wrongReason: filters.wrongReason === '' ? undefined : filters.wrongReason,
      isMastered: filters.isMastered === '' ? undefined : filters.isMastered
    })
    ElMessage.success(`已删除 ${data?.deleted || 0} 条错题记录`)
    await loadList()
  } finally {
    clearing.value = false
  }
}

watch(
  () => route.query,
  async () => {
    syncFiltersFromRoute()
    await loadList()
  }
)

onMounted(async () => {
  syncFiltersFromRoute()
  await loadList()
})
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h1>错题列表</h1>
        <p>按学科、错误原因和掌握状态管理具体错题。</p>
      </div>
      <div class="title-actions">
        <el-tag type="info">总 {{ stats.total || 0 }}</el-tag>
        <el-tag type="warning">待复盘 {{ stats.notMastered || 0 }}</el-tag>
        <el-tag type="success">已掌握 {{ stats.mastered || 0 }}</el-tag>
        <el-button plain :icon="'ArrowLeft'" @click="router.push('/wrong-questions')">
          返回封面
        </el-button>
        <el-button type="danger" plain :disabled="!pageList(page).length" :loading="clearing" :icon="'Delete'" @click="clearWrongQuestions">
          一键删除
        </el-button>
        <el-button type="primary" :loading="exporting" :icon="'Download'" @click="exportBook">
          导出错题本
        </el-button>
      </div>
    </div>

    <section v-if="false" class="stat-grid">
      <div class="stat-panel panel">
        <span>总错题</span>
        <strong>{{ stats.total || 0 }}</strong>
      </div>
      <div class="stat-panel panel">
        <span>已掌握</span>
        <strong>{{ stats.mastered || 0 }}</strong>
      </div>
      <div class="stat-panel panel">
        <span>待复盘</span>
        <strong>{{ stats.notMastered || 0 }}</strong>
      </div>
      <div class="stat-panel panel">
        <span>掌握率</span>
        <strong>{{ stats.total ? Math.round((stats.mastered / stats.total) * 100) : 0 }}%</strong>
      </div>
    </section>

    <section class="panel panel-body">
      <div class="toolbar">
        <el-select v-model="filters.subject" clearable placeholder="学科" @change="loadList">
          <el-option v-for="item in SUBJECTS" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.wrongReason" clearable placeholder="错误原因" @change="loadList">
          <el-option
            v-for="(label, value) in WRONG_REASON"
            :key="value"
            :label="label"
            :value="Number(value)"
          />
        </el-select>
        <el-select v-model="filters.isMastered" clearable placeholder="掌握状态" @change="loadList">
          <el-option label="待复盘" :value="0" />
          <el-option label="已掌握" :value="1" />
        </el-select>
        <el-button :icon="'Refresh'" @click="loadList">刷新</el-button>
      </div>
    </section>

    <section v-loading="loading" class="wrong-grid">
      <article v-for="item in pageList(page)" :key="item.wrongId" class="wrong-card panel lift-card">
        <div class="wrong-head">
          <el-tag :type="item.isMastered ? 'success' : 'warning'">
            {{ item.isMastered ? '已掌握' : '待复盘' }}
          </el-tag>
          <span>{{ formatDateTime(item.firstWrongTime) }}</span>
        </div>
        <h2>错题 #{{ item.wrongId }}</h2>
        <p>题目 ID：{{ item.questionId || '-' }}</p>
        <p>错误答案：{{ item.wrongAnswer || '-' }}</p>
        <p>下次复盘：{{ item.nextReviewTime || '-' }}</p>
        <div class="tag-row">
          <el-tag type="info">{{ wrongReasonLabel(item.wrongReason) }}</el-tag>
          <el-tag>错误 {{ item.wrongCount || 1 }} 次</el-tag>
          <el-tag v-if="item.reviewDue" type="danger">到期</el-tag>
        </div>
        <div class="wrong-actions">
          <el-button type="primary" plain :icon="'View'" @click="openDetail(item.wrongId)">解析</el-button>
          <el-button
            v-if="!item.isMastered"
            type="success"
            plain
            :icon="'Check'"
            @click="confirmMastered(item)"
          >
            掌握
          </el-button>
          <el-button v-else plain :icon="'RefreshLeft'" @click="markMastered(item, 0)">复盘</el-button>
          <el-button
            type="danger"
            plain
            :loading="deletingWrongId === item.wrongId"
            :icon="'Delete'"
            @click="deleteWrong(item)"
          >
            删除
          </el-button>
        </div>
      </article>

      <el-empty v-if="!loading && !pageList(page).length" class="full-empty" description="暂无错题" />
    </section>

    <div class="pager">
      <el-pagination
        v-model:current-page="filters.pageNum"
        v-model:page-size="filters.pageSize"
        layout="total, sizes, prev, pager, next"
        :total="pageTotal(page)"
        @change="loadList"
      />
    </div>
  </div>
</template>

<style scoped>
.stat-panel {
  padding: 18px;
}

.stat-panel span {
  color: var(--muted);
}

.stat-panel strong {
  display: block;
  margin-top: 10px;
  font-size: 30px;
}

.toolbar .el-select {
  width: 160px;
}

.title-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.wrong-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.wrong-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.wrong-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--muted);
  font-size: 13px;
}

.wrong-card h2 {
  margin: 0;
  font-size: 18px;
}

.wrong-card p {
  margin: 0;
  color: var(--muted);
}

.wrong-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}

.full-empty {
  grid-column: 1 / -1;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .wrong-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .wrong-grid {
    grid-template-columns: 1fr;
  }

}
</style>
