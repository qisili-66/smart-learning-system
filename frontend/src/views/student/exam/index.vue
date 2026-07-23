<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assessmentApi } from '@/api/student'
import {
  ASSESSMENT_TYPES,
  DIFFICULTY,
  SUBJECTS,
  difficultyLabel,
  formatDateTime,
  pageList,
  pageTotal
} from '@/utils/format'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const creating = ref(false)
const deletingAssessmentId = ref(null)
const clearing = ref(false)
const history = ref({ list: [], total: 0 })

const filters = reactive({
  subject: '',
  pageNum: 1,
  pageSize: 8
})

const form = reactive({
  assessmentType: 2,
  subject: '数学',
  knowledgeScope: '',
  difficulty: 1
})

const historyList = computed(() => pageList(history.value))
const stats = computed(() => {
  const list = historyList.value
  const completed = list.filter((item) => Number(item.assessmentStatus) === 2)
  const avg = completed.length
    ? Math.round(
        completed.reduce((sum, item) => {
          const total = Number(item.totalScore || 100) || 100
          return sum + (Number(item.userScore || 0) / total) * 100
        }, 0) / completed.length
      )
    : 0
  return {
    total: pageTotal(history.value),
    completed: completed.length,
    pending: list.length - completed.length,
    avg
  }
})

async function loadHistory() {
  loading.value = true
  try {
    history.value = await assessmentApi.history({
      subject: filters.subject || undefined,
      pageNum: filters.pageNum,
      pageSize: filters.pageSize
    })
  } finally {
    loading.value = false
  }
}

async function createAssessment() {
  creating.value = true
  try {
    const created = await assessmentApi.create({ ...form })
    ElMessage.success('测评已创建，正在进入答题页')
    router.push(`/assessments/${created.assessmentId}/take`)
  } finally {
    creating.value = false
  }
}

function openAssessment(row) {
  if (Number(row.assessmentStatus) === 2) {
    router.push(`/assessments/${row.assessmentId}/report`)
    return
  }
  router.push(`/assessments/${row.assessmentId}/take`)
}

function resetFilters() {
  filters.subject = ''
  filters.pageNum = 1
  loadHistory()
}

async function deleteAssessment(row) {
  await ElMessageBox.confirm(`确认删除这条${ASSESSMENT_TYPES[row.assessmentType] || '测评'}记录？`, '删除测评记录', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  deletingAssessmentId.value = row.assessmentId
  try {
    await assessmentApi.remove(row.assessmentId)
    ElMessage.success('测评记录已删除')
    await loadHistory()
  } finally {
    deletingAssessmentId.value = null
  }
}

async function clearAssessments() {
  await ElMessageBox.confirm(
    filters.subject ? `确认删除「${filters.subject}」下的全部测评记录？` : '确认删除全部测评记录？',
    '一键删除测评记录',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    }
  )
  clearing.value = true
  try {
    const data = await assessmentApi.clear({ subject: filters.subject || undefined })
    ElMessage.success(`已删除 ${data?.deleted || 0} 条测评记录`)
    await loadHistory()
  } finally {
    clearing.value = false
  }
}

onMounted(async () => {
  await loadHistory()
  if (route.query.reportReady) {
    ElMessage.success('测评报告已生成，可在历史测评中查看')
  }
})
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h1>测评中心</h1>
        <p>主页只负责发起测评和查看记录；答题与报告会进入独立页面。</p>
      </div>
      <el-button :icon="'Refresh'" @click="loadHistory">刷新</el-button>
    </div>

    <section class="stat-grid">
      <div class="stat-panel panel">
        <span>测评总数</span>
        <strong>{{ stats.total }}</strong>
      </div>
      <div class="stat-panel panel">
        <span>已完成</span>
        <strong>{{ stats.completed }}</strong>
      </div>
      <div class="stat-panel panel">
        <span>进行中</span>
        <strong>{{ stats.pending }}</strong>
      </div>
      <div class="stat-panel panel">
        <span>平均正确率</span>
        <strong>{{ stats.avg }}%</strong>
      </div>
    </section>

    <section class="two-col">
      <div class="panel panel-body">
        <div class="panel-head">
          <div>
            <h2>发起测评</h2>
            <p>生成后会跳转到独立答题页。</p>
          </div>
          <el-tag type="info">题目来自后台题库</el-tag>
        </div>
        <el-form label-position="top" :model="form">
          <div class="form-grid">
            <el-form-item label="测评模式">
              <el-select v-model="form.assessmentType" class="full">
                <el-option
                  v-for="(label, value) in ASSESSMENT_TYPES"
                  :key="value"
                  :label="label"
                  :value="Number(value)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="学科">
              <el-select v-model="form.subject" class="full">
                <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
              </el-select>
            </el-form-item>
          </div>
          <div class="form-grid">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty" class="full">
                <el-option v-for="(label, value) in DIFFICULTY" :key="value" :label="label" :value="Number(value)" />
              </el-select>
            </el-form-item>
            <el-form-item label="知识点范围">
              <el-input v-model.trim="form.knowledgeScope" placeholder="如：一次函数，可用逗号分隔多个知识点" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="creating" :icon="'Plus'" @click="createAssessment">
            生成并进入答题页
          </el-button>
        </el-form>
      </div>

      <div class="panel panel-body history-panel">
        <div class="panel-head">
          <div>
            <h2>历史测评</h2>
            <p>未提交的测评继续答题，已提交的测评查看报告。</p>
          </div>
          <div class="history-tools">
            <el-select v-model="filters.subject" clearable placeholder="学科" @change="loadHistory">
              <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
            </el-select>
            <el-button :icon="'RefreshLeft'" @click="resetFilters">重置</el-button>
            <el-button
              type="danger"
              plain
              :disabled="!historyList.length"
              :loading="clearing"
              :icon="'Delete'"
              @click="clearAssessments"
            >
              一键删除
            </el-button>
          </div>
        </div>
        <div v-loading="loading" class="history-list">
          <article
            v-for="item in historyList"
            :key="item.assessmentId"
            class="history-item"
          >
            <button class="history-main" type="button" @click="openAssessment(item)">
              <span>{{ ASSESSMENT_TYPES[item.assessmentType] || '测评' }} · {{ item.subject || '全科' }}</span>
              <small>
                {{ difficultyLabel(item.difficulty) }} ·
                {{ item.assessmentStatus === 2 ? `得分 ${item.userScore || 0}` : '未提交' }} ·
                {{ formatDateTime(item.createTime) }}
              </small>
              <el-tag :type="item.assessmentStatus === 2 ? 'success' : 'warning'">
                {{ item.assessmentStatus === 2 ? '查看报告' : '继续答题' }}
              </el-tag>
            </button>
            <el-button
              type="danger"
              plain
              :loading="deletingAssessmentId === item.assessmentId"
              :icon="'Delete'"
              @click="deleteAssessment(item)"
            >
              删除
            </el-button>
          </article>
          <el-empty v-if="!loading && !historyList.length" description="暂无测评记录" />
        </div>
        <el-pagination
          v-model:current-page="filters.pageNum"
          v-model:page-size="filters.pageSize"
          size="small"
          layout="prev, pager, next"
          :total="pageTotal(history)"
          @change="loadHistory"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.stat-panel {
  padding: 18px;
}

.stat-panel span,
.panel-head p,
.history-item small {
  color: var(--muted);
}

.stat-panel strong {
  display: block;
  margin-top: 10px;
  font-size: 30px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
}

.panel-head p {
  margin: 6px 0 0;
  font-size: 13px;
}

.history-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-tools .el-select {
  width: 120px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.full {
  width: 100%;
}

.history-panel {
  min-width: 0;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.history-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.history-item:hover {
  background: var(--primary-soft);
}

.history-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 5px 12px;
  min-width: 0;
  padding: 0;
  border: 0;
  color: var(--text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.history-main span,
.history-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-main small {
  grid-column: 1;
}

.history-main .el-tag {
  grid-column: 2;
  grid-row: 1 / span 2;
}

@media (max-width: 960px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .panel-head {
    flex-direction: column;
  }
}
</style>
