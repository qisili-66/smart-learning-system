<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assessmentApi } from '@/api/student'
import { ASSESSMENT_TYPES, difficultyLabel, formatDateTime, scoreStatusMeta } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const currentAssessment = ref(null)
const report = ref(null)
const wrongAnswers = ref([])

const wrongKnowledge = computed(() => {
  const names = wrongAnswers.value.map((item) => item.knowledgePoint).filter(Boolean)
  return Array.from(new Set(names))
})

function isWrongAnswer(item) {
  if (item?.isCorrect !== undefined && item?.isCorrect !== null) {
    return Number(item.isCorrect) === 0
  }
  const score = Number(item?.score || 0)
  const maxScore = Number(item?.maxScore || 0)
  return maxScore > 0 && score < maxScore
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0))
  const minutes = Math.floor(value / 60)
  const rest = value % 60
  if (!minutes) return `${rest} 秒`
  return `${minutes} 分 ${rest} 秒`
}

function scoreText(item) {
  return `${Number(item.score || 0).toFixed(2).replace(/\.00$/, '')}/${Number(item.maxScore || 0).toFixed(2).replace(/\.00$/, '')}`
}

async function loadBook() {
  const assessmentId = Number(route.params.assessmentId || 0)
  if (!assessmentId) {
    router.replace('/wrong-questions')
    return
  }
  loading.value = true
  try {
    const detail = await assessmentApi.detail(assessmentId)
    currentAssessment.value = detail.assessment
    if (Number(currentAssessment.value?.assessmentStatus) !== 2) {
      ElMessage.info('这次测评还未提交，先进入答题页')
      router.replace(`/assessments/${assessmentId}/take`)
      return
    }
    report.value = await assessmentApi.report(assessmentId)
    const answerDetails = Array.isArray(report.value?.answerDetails)
      ? report.value.answerDetails
      : Array.isArray(detail.answerDetails)
        ? detail.answerDetails
        : []
    wrongAnswers.value = answerDetails.filter(isWrongAnswer)
  } finally {
    loading.value = false
  }
}

function openWrongList() {
  router.push({
    path: '/wrong-questions/list',
    query: currentAssessment.value?.subject ? { subject: currentAssessment.value.subject, isMastered: 0 } : { isMastered: 0 }
  })
}

function openReport() {
  const id = currentAssessment.value?.assessmentId || route.params.assessmentId
  router.push(`/assessments/${id}/report`)
}

onMounted(loadBook)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <div>
        <h1>测评错题册</h1>
        <p v-if="currentAssessment">
          {{ ASSESSMENT_TYPES[currentAssessment.assessmentType] || '测评' }} ·
          {{ currentAssessment.subject || '全科' }} ·
          {{ difficultyLabel(currentAssessment.difficulty) }} ·
          {{ formatDateTime(currentAssessment.endTime || currentAssessment.createTime) }}
        </p>
        <p v-else>正在读取本次测评错题。</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'ArrowLeft'" @click="router.push('/wrong-questions')">返回错题本</el-button>
        <el-button :icon="'Document'" @click="openReport">测评报告</el-button>
        <el-button type="primary" plain :icon="'List'" @click="openWrongList">完整错题列表</el-button>
      </div>
    </div>

    <section v-if="currentAssessment" class="book-summary panel panel-body">
      <div>
        <span>本册错题</span>
        <strong>{{ wrongAnswers.length }}</strong>
      </div>
      <div>
        <span>测评得分</span>
        <strong>{{ currentAssessment.userScore || 0 }}/{{ currentAssessment.totalScore || 0 }}</strong>
      </div>
      <div>
        <span>正确率</span>
        <strong>{{ Math.round(Number(report?.correctRate || 0)) }}%</strong>
      </div>
      <div>
        <span>薄弱知识点</span>
        <strong>{{ wrongKnowledge.length || 0 }}</strong>
      </div>
    </section>

    <section v-if="wrongKnowledge.length" class="panel panel-body knowledge-strip">
      <span>本次主要复盘点</span>
      <div class="tag-row">
        <el-tag v-for="point in wrongKnowledge" :key="point" type="warning">{{ point }}</el-tag>
      </div>
    </section>

    <section class="wrong-answer-list">
      <article v-for="(item, index) in wrongAnswers" :key="item.answerId || item.questionId" class="wrong-answer-card panel panel-body">
        <div class="answer-head">
          <div>
            <el-tag type="danger">错题 {{ index + 1 }}</el-tag>
            <el-tag :type="scoreStatusMeta(item.scoreStatus).type">{{ scoreStatusMeta(item.scoreStatus).text }}</el-tag>
          </div>
          <strong>{{ scoreText(item) }} 分</strong>
        </div>

        <h2>{{ item.questionText || `题目 #${item.questionId}` }}</h2>

        <div class="answer-info">
          <span>题型：{{ item.questionTypeName || '题目' }}</span>
          <span>知识点：{{ item.knowledgePoint || '未标注' }}</span>
          <span>单题用时：{{ formatDuration(item.questionUseSeconds) }}</span>
          <span v-if="item.aiScore !== null && item.aiScore !== undefined">
            AI 评分：{{ item.aiScore }}/{{ item.maxScore || 0 }}，置信度 {{ item.aiConfidence || 0 }}%
          </span>
        </div>

        <div class="answer-columns">
          <section>
            <h3>我的答案</h3>
            <p>{{ item.userAnswer || '-' }}</p>
          </section>
          <section>
            <h3>参考答案</h3>
            <p>{{ item.correctAnswer || '-' }}</p>
          </section>
        </div>

        <div class="analysis-box">
          <h3>解析与评分说明</h3>
          <p>{{ item.scoringDetail || item.analysis || '暂无解析' }}</p>
          <div v-if="(item.scoringPoints || []).length" class="point-list">
            <span v-for="point in item.scoringPoints" :key="point">{{ point }}</span>
          </div>
        </div>
      </article>

      <el-empty v-if="!loading && currentAssessment && !wrongAnswers.length" description="本次测评没有错题">
        <el-button type="primary" @click="openReport">查看测评报告</el-button>
      </el-empty>
    </section>
  </div>
</template>

<style scoped>
.title-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.book-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
}

.book-summary div {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--primary-soft);
}

.book-summary span,
.knowledge-strip > span,
.answer-info,
.analysis-box p {
  color: var(--muted);
}

.book-summary strong {
  display: block;
  overflow: hidden;
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.wrong-answer-list {
  display: grid;
  gap: 14px;
}

.wrong-answer-card {
  display: grid;
  gap: 14px;
}

.answer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.answer-head > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.answer-head strong {
  white-space: nowrap;
}

.wrong-answer-card h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.6;
}

.answer-info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  font-size: 13px;
}

.answer-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.answer-columns section,
.analysis-box {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.answer-columns h3,
.analysis-box h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.answer-columns p,
.analysis-box p {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.point-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.point-list span {
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--text);
  background: var(--primary-soft);
  font-size: 13px;
}

@media (max-width: 900px) {
  .book-summary,
  .answer-info,
  .answer-columns {
    grid-template-columns: 1fr;
  }
}
</style>
