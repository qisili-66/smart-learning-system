<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assessmentApi } from '@/api/student'
import { ASSESSMENT_TYPES, difficultyLabel, scoreStatusMeta } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const currentAssessment = ref(null)
const report = ref(null)
const answerDetails = ref([])
const reviewForms = reactive({})

function setAnswerDetails(list) {
  answerDetails.value = Array.isArray(list) ? list : []
  Object.keys(reviewForms).forEach((key) => delete reviewForms[key])
  answerDetails.value.forEach((item) => {
    reviewForms[item.answerId] = {
      score: Number(item.score || 0),
      reviewComment: item.reviewComment || ''
    }
  })
}

async function loadReport() {
  const assessmentId = Number(route.params.assessmentId || 0)
  if (!assessmentId) {
    router.replace('/assessments')
    return
  }
  loading.value = true
  try {
    const detail = await assessmentApi.detail(assessmentId)
    currentAssessment.value = detail.assessment
    if (Number(currentAssessment.value?.assessmentStatus) !== 2) {
      ElMessage.info('这份测评尚未提交，正在进入答题页')
      router.replace(`/assessments/${assessmentId}/take`)
      return
    }
    report.value = await assessmentApi.report(assessmentId)
    setAnswerDetails(report.value.answerDetails || detail.answerDetails || [])
  } finally {
    loading.value = false
  }
}

async function saveReview(answer) {
  if (!currentAssessment.value?.assessmentId || !answer?.answerId) return
  const form = reviewForms[answer.answerId] || {}
  const data = await assessmentApi.reviewAnswer(currentAssessment.value.assessmentId, answer.answerId, {
    score: Number(form.score || 0),
    reviewComment: form.reviewComment || ''
  })
  report.value = data.report || (await assessmentApi.report(currentAssessment.value.assessmentId))
  setAnswerDetails(report.value.answerDetails || [])
  currentAssessment.value = data.assessment || currentAssessment.value
  ElMessage.success('复核分数已保存')
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0))
  const minutes = Math.floor(value / 60)
  const rest = value % 60
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

function openTrend() {
  const id = currentAssessment.value?.assessmentId || route.params.assessmentId
  router.push(`/assessments/${id}/trend`)
}

onMounted(loadReport)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <div>
        <h1>测评报告</h1>
        <p v-if="currentAssessment">
          {{ ASSESSMENT_TYPES[currentAssessment.assessmentType] || '测评' }} ·
          {{ currentAssessment.subject || '全科' }} ·
          {{ difficultyLabel(currentAssessment.difficulty) }} ·
          {{ currentAssessment.knowledgeScope || '综合知识点' }}
        </p>
        <p v-else>正在加载报告。</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'TrendCharts'" @click="openTrend">
          查看趋势
        </el-button>
        <el-button :icon="'ArrowLeft'" @click="router.push('/assessments')">返回测评主页</el-button>
      </div>
    </div>

    <section v-if="report" class="report-grid">
      <div class="report-main panel panel-body">
        <div class="panel-head">
          <h2>成绩概览</h2>
          <el-tag type="success">已同步画像与错题本</el-tag>
        </div>
        <div class="score-row">
          <div>
            <span>得分</span>
            <strong>{{ report.userScore || 0 }}/{{ report.totalScore || 100 }}</strong>
          </div>
          <div>
            <span>正确率</span>
            <strong>{{ Math.round(Number(report.correctRate || 0)) }}%</strong>
          </div>
          <div>
            <span>同水平估计</span>
            <strong>{{ report.rankPercent || 0 }}%</strong>
          </div>
        </div>
        <p>{{ report.abilityAnalysis }}</p>
        <p>{{ report.improveSuggestion }}</p>
      </div>

      <div class="panel panel-body">
        <div class="panel-head">
          <h2>复盘建议</h2>
        </div>
        <div class="review-list">
          <div v-for="task in report.reviewTasks || []" :key="task.knowledgePoint" class="review-item">
            <strong>{{ task.knowledgePoint }}</strong>
            <span>错题 {{ task.wrongCount }} 道</span>
          </div>
          <p v-if="!(report.reviewTasks || []).length" class="empty-copy">本次暂无明显薄弱点。</p>
        </div>
      </div>
    </section>

    <section v-if="answerDetails.length" class="panel panel-body score-detail-panel">
      <div class="panel-head">
        <h2>评分明细</h2>
        <el-tag v-if="report?.pendingManualCount" type="warning">
          {{ report.pendingManualCount }} 题待复核
        </el-tag>
      </div>
      <div class="answer-detail-list">
        <article v-for="item in answerDetails" :key="item.answerId" class="answer-detail-row">
          <div class="answer-summary">
            <div>
              <el-tag :type="scoreStatusMeta(item.scoreStatus).type">
                {{ scoreStatusMeta(item.scoreStatus).text }}
              </el-tag>
              <strong>{{ item.questionTypeName }} · {{ item.knowledgePoint || '未标注知识点' }}</strong>
            </div>
            <span>{{ item.score || 0 }}/{{ item.maxScore || 0 }} 分</span>
          </div>
          <p>{{ item.questionText }}</p>
          <div class="answer-columns">
            <span>学生答案：{{ item.userAnswer || '-' }}</span>
            <span>参考答案：{{ item.correctAnswer || '-' }}</span>
            <span>单题用时：{{ formatDuration(item.questionUseSeconds) }}</span>
            <span v-if="item.aiScore !== null && item.aiScore !== undefined">
              AI 评分：{{ item.aiScore }}/{{ item.maxScore || 0 }}，置信度 {{ item.aiConfidence || 0 }}%
            </span>
            <span v-if="(item.scoringPoints || []).length">
              评分要点：{{ item.scoringPoints.join('；') }}
            </span>
            <span>评分说明：{{ item.scoringDetail || '-' }}</span>
          </div>
          <div class="review-controls">
            <el-input-number
              v-model="reviewForms[item.answerId].score"
              :min="0"
              :max="Number(item.maxScore || 0)"
              :precision="2"
              controls-position="right"
            />
            <el-input
              v-model="reviewForms[item.answerId].reviewComment"
              placeholder="复核意见"
              clearable
            />
            <el-button type="primary" plain :icon="'EditPen'" @click="saveReview(item)">保存复核</el-button>
          </div>
        </article>
      </div>
    </section>

    <el-empty v-if="!loading && !report" description="暂无测评报告">
      <el-button type="primary" @click="router.push('/assessments')">返回测评主页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.panel-head,
.score-row,
.title-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.title-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.panel-head {
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
}

.report-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: 16px;
}

.report-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report-main p,
.score-row span,
.review-item span,
.answer-summary span,
.answer-columns {
  color: var(--muted);
}

.score-row {
  justify-content: flex-start;
}

.score-row div {
  min-width: 150px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.score-row strong {
  display: block;
  margin-top: 6px;
  font-size: 22px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.score-detail-panel {
  display: grid;
  gap: 12px;
}

.answer-detail-list {
  display: grid;
  gap: 12px;
}

.answer-detail-row {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.answer-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.answer-summary > div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.answer-summary strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.answer-detail-row p {
  margin: 0;
  line-height: 1.6;
}

.answer-columns {
  display: grid;
  gap: 6px;
  font-size: 13px;
}

.review-controls {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  gap: 10px;
}

@media (max-width: 960px) {
  .report-grid,
  .review-controls {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .score-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
