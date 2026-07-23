<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assessmentApi } from '@/api/student'
import { ASSESSMENT_TYPES, QUESTION_TYPES, difficultyLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const currentAssessment = ref(null)
const questions = ref([])
const answers = reactive({})
const elapsedSeconds = reactive({})
const activeQuestionId = ref(null)
const activeStartedAt = ref(0)
const nowTick = ref(Date.now())
let timer = null

const assessmentId = computed(() => Number(route.params.assessmentId || 0))
const answeredCount = computed(() =>
  questions.value.filter((question) => String(answerValue(question)).trim()).length
)
const progress = computed(() => {
  if (!questions.value.length) return 0
  return Math.round((answeredCount.value / questions.value.length) * 100)
})
const paperTotalScore = computed(() => Number(currentAssessment.value?.totalScore || 100))
const paperSections = computed(() => {
  const grouped = new Map()
  questions.value.forEach((question) => {
    const title = question.paperSectionTitle || QUESTION_TYPES[question.questionType] || '题目'
    if (!grouped.has(title)) {
      grouped.set(title, {
        title,
        note: question.paperSectionNote || '',
        items: [],
        score: 0
      })
    }
    const section = grouped.get(title)
    section.items.push(question)
    section.score += Number(question.maxScore || 0)
  })
  return Array.from(grouped.values())
})

function answerValue(question) {
  const value = answers[question.questionId]
  return Array.isArray(value) ? value.join(',') : String(value ?? '')
}

function resetTimerState() {
  activeQuestionId.value = null
  activeStartedAt.value = 0
  Object.keys(elapsedSeconds).forEach((key) => delete elapsedSeconds[key])
}

function flushActiveTimer() {
  if (!activeQuestionId.value || !activeStartedAt.value) return
  const delta = Math.max(0, Math.floor((Date.now() - activeStartedAt.value) / 1000))
  elapsedSeconds[activeQuestionId.value] = Number(elapsedSeconds[activeQuestionId.value] || 0) + delta
  activeStartedAt.value = Date.now()
}

function activateQuestion(questionId) {
  if (!questionId || activeQuestionId.value === questionId) return
  flushActiveTimer()
  activeQuestionId.value = questionId
  activeStartedAt.value = Date.now()
}

function questionSeconds(questionId) {
  const saved = Number(elapsedSeconds[questionId] || 0)
  if (activeQuestionId.value !== questionId || !activeStartedAt.value) return saved
  return saved + Math.max(0, Math.floor((nowTick.value - activeStartedAt.value) / 1000))
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0))
  const minutes = Math.floor(value / 60)
  const rest = value % 60
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

async function loadAssessment() {
  if (!assessmentId.value) {
    router.replace('/assessments')
    return
  }
  loading.value = true
  try {
    const data = await assessmentApi.detail(assessmentId.value)
    currentAssessment.value = data.assessment
    questions.value = Array.isArray(data.questions) ? data.questions : []
    Object.keys(answers).forEach((key) => delete answers[key])
    resetTimerState()
    questions.value.forEach((question) => {
      answers[question.questionId] = Number(question.questionType) === 2 && question.options?.length ? [] : ''
      elapsedSeconds[question.questionId] = 0
    })
    if (Number(currentAssessment.value?.assessmentStatus) === 2) {
      ElMessage.info('这份测评已提交，正在打开报告')
      router.replace(`/assessments/${assessmentId.value}/report`)
      return
    }
    if (!questions.value.length) {
      ElMessage.warning('当前条件没有匹配题目，请管理员先在题库管理中新增题目')
    } else {
      activateQuestion(questions.value[0].questionId)
    }
  } finally {
    loading.value = false
  }
}

async function submitAssessment() {
  if (!currentAssessment.value?.assessmentId || !questions.value.length) {
    ElMessage.warning('当前测评没有可提交的题目')
    return
  }
  await ElMessageBox.confirm('确认提交本次测评？提交后会生成报告并返回测评主页。', '提交测评', {
    type: 'warning',
    confirmButtonText: '提交',
    cancelButtonText: '继续作答'
  })
  flushActiveTimer()
  submitting.value = true
  try {
    await assessmentApi.submit(currentAssessment.value.assessmentId, {
      answers: questions.value.map((question) => ({
        questionId: question.questionId,
        userAnswer: answerValue(question),
        questionUseSeconds: questionSeconds(question.questionId)
      })), 
      totalScore: paperTotalScore.value,
      useTime: 0
    })
    await assessmentApi.report(currentAssessment.value.assessmentId)
    ElMessage.success('测评已提交，报告已生成')
    router.push({
      path: '/assessments',
      query: { reportReady: currentAssessment.value.assessmentId }
    })
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  timer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
  loadAssessment()
})

onBeforeUnmount(() => {
  flushActiveTimer()
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h1>测评答题</h1>
        <p v-if="currentAssessment">
          {{ ASSESSMENT_TYPES[currentAssessment.assessmentType] || '测评' }} ·
          {{ currentAssessment.subject || '全科' }} ·
          {{ difficultyLabel(currentAssessment.difficulty) }} ·
          {{ currentAssessment.knowledgeScope || '综合知识点' }} · 满分 {{ paperTotalScore }} 分
        </p>
        <p v-else>正在加载测评题目。</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'ArrowLeft'" @click="router.push('/assessments')">返回主页</el-button>
        <el-button
          type="primary"
          :disabled="!questions.length"
          :loading="submitting"
          :icon="'Checked'"
          @click="submitAssessment"
        >
          提交测评
        </el-button>
      </div>
    </div>

    <section class="paper-status panel">
      <div>
        <span>答题进度</span>
        <strong>{{ answeredCount }}/{{ questions.length }}</strong>
      </div>
      <el-progress :percentage="progress" :stroke-width="10" />
      <div class="score-total">满分 {{ paperTotalScore }} 分</div>
    </section>

    <section v-loading="loading" class="paper panel panel-body">
      <div v-if="questions.length" class="question-list">
        <section v-for="section in paperSections" :key="section.type" class="paper-section">
            <div class="section-head">
              <div>
                <h2>{{ section.title }}</h2>
                <p v-if="section.note">{{ section.note }}</p>
              </div>
            <strong>{{ section.items.length }} 题 / {{ section.score }} 分</strong>
          </div>
          <article
            v-for="question in section.items"
            :key="question.questionId"
            class="question-card"
            :class="{ active: activeQuestionId === question.questionId }"
            @click="activateQuestion(question.questionId)"
            @focusin="activateQuestion(question.questionId)"
          >
            <div class="question-meta">
              <el-tag>{{ questions.findIndex((item) => item.questionId === question.questionId) + 1 }}</el-tag>
              <span>{{ QUESTION_TYPES[question.questionType] || '题目' }}</span>
              <span>{{ question.knowledgePoint || '未标注知识点' }}</span>
              <span>用时 {{ formatDuration(questionSeconds(question.questionId)) }}</span>
              <strong>{{ question.maxScore || 0 }} 分</strong>
            </div>
            <h3>{{ question.questionText }}</h3>
            <el-checkbox-group
              v-if="Number(question.questionType) === 2 && question.options?.length"
              v-model="answers[question.questionId]"
            >
              <el-checkbox v-for="option in question.options" :key="option" :label="option" />
            </el-checkbox-group>
            <el-radio-group
              v-else-if="question.options?.length"
              v-model="answers[question.questionId]"
            >
              <el-radio v-for="option in question.options" :key="option" :label="option" />
            </el-radio-group>
            <el-input
              v-else
              v-model="answers[question.questionId]"
              type="textarea"
              :rows="Number(question.questionType) === 4 ? 6 : 3"
              placeholder="输入你的答案"
            />
          </article>
        </section>
      </div>

      <el-empty v-else description="暂无题目">
        <span class="muted">请返回测评主页重新生成，或让管理员补充对应题库。</span>
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

.paper-status {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 18px;
}

.paper-status span,
.question-meta {
  color: var(--muted);
}

.paper-status strong {
  display: block;
  margin-top: 6px;
  font-size: 28px;
  line-height: 1;
}

.score-total {
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #ffffff;
  color: var(--text);
  font-weight: 700;
}

.paper {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.paper-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 0 8px;
  border-bottom: 2px solid var(--text);
}

.section-head h2 {
  margin: 0;
  font-size: 18px;
}

.section-head p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.section-head strong {
  white-space: nowrap;
}

.question-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.question-card.active {
  border-color: var(--primary);
  box-shadow: inset 3px 0 0 var(--primary);
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
}

.question-card h3 {
  margin: 0;
  font-size: 17px;
  line-height: 1.55;
  font-weight: 650;
}

@media (max-width: 720px) {
  .paper-status {
    grid-template-columns: 1fr;
  }

  .section-head {
    flex-direction: column;
  }
}
</style>
