<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studyPlanApi } from '@/api/student'
import {
  SUBJECTS,
  difficultyLabel,
  formatDateTime,
  pageList,
  pageTotal,
  planStatusMeta,
  resourceTypeLabel
} from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const pathLoading = ref(false)
const recommendLoading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const activePlanId = ref(null)
const finishingTaskId = ref(null)
const adjustingPlanId = ref(null)
const page = ref({ list: [], total: 0 })
const pathData = ref({})
const recommendedResources = ref([])

const filters = reactive({
  planStatus: '',
  pageNum: 1,
  pageSize: 10
})

const form = reactive({
  planName: '',
  subject: '数学',
  targetDesc: '',
  currentScore: 70,
  targetScore: 90,
  dailyMinutes: 40,
  provider: 'external',
  startDate: '',
  endDate: '',
  planStatus: 1
})

const currentPlans = computed(() => pageList(page.value))
const runningPlans = computed(() => currentPlans.value.filter((item) => Number(item.planStatus) === 1))
const activePlan = computed(() => pathData.value?.plan || currentPlans.value.find((item) => item.planId === activePlanId.value) || runningPlans.value[0] || currentPlans.value[0] || null)
const pathSteps = computed(() => (Array.isArray(pathData.value?.steps) ? pathData.value.steps : []))
const finishedSteps = computed(() => pathSteps.value.filter((item) => item.passed || Number(item.finishStatus) === 1))
const pathProgress = computed(() => {
  if (pathData.value?.progress?.percent !== undefined) return Number(pathData.value.progress.percent || 0)
  if (!pathSteps.value.length) return 0
  return Math.round((finishedSteps.value.length / pathSteps.value.length) * 100)
})
const nextStep = computed(() => pathSteps.value.find((item) => !item.locked && !item.passed && Number(item.finishStatus) !== 1) || pathSteps.value.find((item) => !item.locked) || null)
const estimatedMinutes = computed(() =>
  pathSteps.value.reduce((sum, item) => sum + Number(item.estimatedMinutes || 0), 0)
)
const planCount = computed(() => pageTotal(page.value))

function todayString() {
  return new Date().toISOString().slice(0, 10)
}

function defaultEndDate() {
  const date = new Date()
  date.setDate(date.getDate() + 6)
  return date.toISOString().slice(0, 10)
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    planName: '',
    subject: '数学',
    targetDesc: '',
    currentScore: 70,
    targetScore: 90,
    dailyMinutes: 40,
    provider: 'external',
    startDate: todayString(),
    endDate: defaultEndDate(),
    planStatus: 1
  })
}

async function loadPlans(options = {}) {
  loading.value = true
  try {
    page.value = await studyPlanApi.list({
      planStatus: filters.planStatus === '' ? undefined : filters.planStatus,
      pageNum: filters.pageNum,
      pageSize: filters.pageSize
    })
    const list = currentPlans.value
    if (options.planId) {
      activePlanId.value = options.planId
    } else if (!list.some((item) => item.planId === activePlanId.value)) {
      activePlanId.value = runningPlans.value[0]?.planId || list[0]?.planId || null
    }
    await loadPath()
  } finally {
    loading.value = false
  }
}

async function loadPath() {
  if (!activePlanId.value) {
    pathData.value = {}
    recommendedResources.value = []
    return
  }
  pathLoading.value = true
  try {
    pathData.value = await studyPlanApi.path(activePlanId.value).catch(() => ({ steps: [] }))
    await loadRecommendedResources()
  } finally {
    pathLoading.value = false
  }
}

async function loadRecommendedResources() {
  recommendLoading.value = true
  try {
    const data = await studyPlanApi.recommendedResources({
      subject: activePlan.value?.subject || undefined,
      limit: 5
    }).catch(() => ({ resources: [] }))
    recommendedResources.value = Array.isArray(data?.resources) ? data.resources : []
  } finally {
    recommendLoading.value = false
  }
}

function selectPlan(plan) {
  activePlanId.value = plan.planId
  loadPath()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(plan) {
  editingId.value = plan.planId
  Object.assign(form, {
    planName: plan.planName || '',
    subject: plan.subject || '数学',
    targetDesc: plan.targetDesc || '',
    currentScore: Number(plan.currentScore || 70),
    targetScore: Number(plan.targetScore || 90),
    dailyMinutes: Number(plan.dailyMinutes || 40),
    provider: plan.aiProvider || 'external',
    startDate: plan.startDate || todayString(),
    endDate: plan.endDate || defaultEndDate(),
    planStatus: plan.planStatus || 1
  })
  dialogVisible.value = true
}

async function savePlan() {
  if (!form.planName.trim()) {
    ElMessage.warning('请输入计划名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      planName: form.planName.trim(),
      targetDesc: form.targetDesc.trim(),
      targetType: '提分目标'
    }
    let savedPlan = null
    if (editingId.value) {
      savedPlan = await studyPlanApi.update(editingId.value, payload)
      ElMessage.success('计划信息已更新')
    } else {
      const data = await studyPlanApi.createTarget(payload)
      savedPlan = data?.plan
      ElMessage.success('AI 学习路径已生成')
    }
    dialogVisible.value = false
    await loadPlans({ planId: savedPlan?.planId || editingId.value })
  } finally {
    saving.value = false
  }
}

async function removePlan(plan) {
  await ElMessageBox.confirm(`确认删除「${plan.planName || '学习计划'}」？`, '删除计划', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await studyPlanApi.remove(plan.planId)
  ElMessage.success('计划已删除')
  if (activePlanId.value === plan.planId) {
    activePlanId.value = null
  }
  await loadPlans()
}

function stepIcon(step) {
  const type = step?.stepType || ''
  if (type === 'diagnostic_test') return 'DataAnalysis'
  if (type === 'practice') return 'EditPen'
  if (type === 'wrong_review') return 'Notebook'
  if (type === 'stage_test') return 'DocumentChecked'
  return 'Reading'
}

function stepActionText(step) {
  const type = step?.stepType || ''
  if (type === 'wrong_review') return '去复盘'
  if (type === 'resource_study') return '学资源'
  if (type === 'diagnostic_test') return '做诊断'
  if (type === 'stage_test') return '阶段测评'
  return '去练习'
}

function stepPath(step) {
  const actionPath = step?.actionPath || ''
  if (actionPath) return actionPath
  const query = {
    subject: activePlan.value?.subject || undefined,
    knowledgePoint: step?.knowledgePoint || undefined
  }
  if (step?.stepType === 'wrong_review') return { path: '/wrong-questions/list', query }
  if (step?.stepType === 'resource_study') return { path: '/resources', query: { ...query, resourceId: step.resourceId || undefined } }
  return { path: '/assessments', query }
}

function executeStep(step) {
  if (step?.locked) {
    ElMessage.warning(step.unlockHint || '请先完成前置步骤')
    return
  }
  router.push(stepPath(step))
}

async function finishStep(step) {
  if (!step?.taskId || step.locked) return
  let correctRate = Number(step.correctRate ?? step.targetCorrectRate ?? 100)
  if (['diagnostic_test', 'practice', 'stage_test'].includes(step.stepType)) {
    const result = await ElMessageBox.prompt('请输入本步骤练习/测评正确率', '完成步骤', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: String(correctRate || 80),
      inputPattern: /^(100|[1-9]?\d)(\.\d{1,2})?$/,
      inputErrorMessage: '请输入 0-100 之间的数字'
    })
    correctRate = Number(result.value)
  }
  finishingTaskId.value = step.taskId
  try {
    const data = await studyPlanApi.finishTask(step.taskId, {
      finishStatus: 1,
      studyDuration: Number(step.estimatedMinutes || 0),
      correctRate
    })
    const action = data?.adjustment?.action
    ElMessage.success(action === 'add_reinforcement' ? '未达标，已追加复盘巩固步骤' : '步骤已完成')
    await loadPath()
  } finally {
    finishingTaskId.value = null
  }
}

async function adjustPlan(plan = activePlan.value) {
  if (!plan?.planId) {
    ElMessage.warning('请先选择一个进行中的计划')
    return
  }
  adjustingPlanId.value = plan.planId
  try {
    const data = await studyPlanApi.adjust(plan.planId)
    const actionText = {
      increase_difficulty: '已提升后续任务难度',
      add_reinforcement: '已追加巩固复盘任务',
      keep: '当前节奏合适，计划保持不变'
    }[data?.action] || '计划已调整'
    ElMessage.success(actionText)
    await loadPlans({ planId: plan.planId })
  } finally {
    adjustingPlanId.value = null
  }
}

function openResource(item) {
  router.push({
    path: '/resources',
    query: {
      resourceId: item.resourceId || undefined,
      knowledgePoint: item.knowledgePoint || undefined,
      subject: item.subject || activePlan.value?.subject || undefined
    }
  })
}

onMounted(loadPlans)
</script>

<template>
  <div class="page study-page">
    <div class="page-title">
      <div>
        <h1>AI 学习路径</h1>
        <p>根据目标、画像、错题和测评结果生成可执行路径，并通过练习、复盘、资源和测评形成闭环。</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'Refresh'" @click="loadPlans">刷新</el-button>
        <el-button type="primary" :icon="'Plus'" @click="openCreate">生成 AI 路径</el-button>
      </div>
    </div>

    <section class="execution-board panel" v-loading="pathLoading">
      <div class="execution-main">
        <el-tag type="primary">{{ pathData.provider || activePlan?.aiProvider || 'AI' }}</el-tag>
        <h2>{{ activePlan?.planName || '先生成一个 AI 学习路径' }}</h2>
        <p>{{ pathData.summary || activePlan?.aiPlanSummary || activePlan?.targetDesc || '系统会按目标和薄弱点生成诊断、练习、复盘、资源学习、阶段测评路径。' }}</p>
        <div class="execution-progress">
          <strong>{{ pathProgress }}%</strong>
          <el-progress :percentage="pathProgress" :stroke-width="10" />
        </div>
        <div class="execution-actions">
          <el-button
            type="primary"
            :disabled="!nextStep"
            :icon="stepIcon(nextStep)"
            @click="executeStep(nextStep)"
          >
            {{ nextStep ? stepActionText(nextStep) : '暂无步骤' }}
          </el-button>
          <el-button
            :disabled="!activePlan"
            :loading="adjustingPlanId === activePlan?.planId"
            :icon="'MagicStick'"
            @click="adjustPlan()"
          >
            规则调整
          </el-button>
        </div>
      </div>

      <div class="execution-stats">
        <article>
          <span>路径步骤</span>
          <strong>{{ finishedSteps.length }}/{{ pathSteps.length }}</strong>
        </article>
        <article>
          <span>预计投入</span>
          <strong>{{ estimatedMinutes }} 分钟</strong>
        </article>
        <article>
          <span>目标分</span>
          <strong>{{ activePlan?.targetScore || '-' }}</strong>
        </article>
      </div>
    </section>

    <section class="workbench-grid">
      <div class="panel panel-body path-panel" v-loading="pathLoading">
        <div class="panel-head">
          <div>
            <h2>学习路径</h2>
            <p>按顺序执行；练习和测评达到目标正确率后，下一步会解锁。</p>
          </div>
          <el-tag type="info">{{ activePlan?.subject || '全科' }}</el-tag>
        </div>

        <div v-if="pathSteps.length" class="path-list">
          <article
            v-for="(step, index) in pathSteps"
            :key="step.taskId || index"
            :class="{ done: step.passed, locked: step.locked }"
            class="path-step"
          >
            <div class="step-index">
              <span>{{ index + 1 }}</span>
              <el-icon><component :is="stepIcon(step)" /></el-icon>
            </div>
            <div class="step-content">
              <div class="step-title-line">
                <strong>{{ step.title || '学习步骤' }}</strong>
                <el-tag :type="step.passed ? 'success' : step.locked ? 'info' : 'primary'">
                  {{ step.passed ? '已达标' : step.locked ? '未解锁' : step.taskTypeName || '执行中' }}
                </el-tag>
              </div>
              <p>{{ step.description || '完成这一步后再进入下一项。' }}</p>
              <p v-if="step.aiReason" class="reason">{{ step.aiReason }}</p>
              <div class="step-meta">
                <span>{{ step.knowledgePoint || '基础知识' }}</span>
                <span>{{ difficultyLabel(step.difficulty) }}</span>
                <span>{{ step.estimatedMinutes || 0 }} 分钟</span>
                <span v-if="step.targetCorrectRate">目标 {{ step.targetCorrectRate }}%</span>
              </div>
            </div>
            <div class="step-actions">
              <el-button plain :icon="stepIcon(step)" :disabled="step.locked" @click="executeStep(step)">
                {{ stepActionText(step) }}
              </el-button>
              <el-button
                type="primary"
                plain
                :disabled="step.locked || step.passed"
                :loading="finishingTaskId === step.taskId"
                :icon="'Check'"
                @click="finishStep(step)"
              >
                完成
              </el-button>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无学习路径">
          <el-button type="primary" @click="openCreate">生成 AI 路径</el-button>
        </el-empty>
      </div>

      <aside class="side-stack">
        <section class="panel panel-body" v-loading="recommendLoading">
          <div class="panel-head compact">
            <h2>推荐资源</h2>
            <el-button text :icon="'Refresh'" @click="loadRecommendedResources">刷新</el-button>
          </div>
          <div class="recommend-list">
            <button
              v-for="item in recommendedResources"
              :key="item.resourceId"
              class="recommend-item"
              type="button"
              @click="openResource(item)"
            >
              <strong>{{ item.resourceName || '学习资源' }}</strong>
              <span>{{ item.knowledgePoint || '薄弱知识点' }} · {{ resourceTypeLabel(item.resourceType) }}</span>
            </button>
            <p v-if="!recommendedResources.length" class="empty-copy">暂无推荐资源，先完成测评或补充资源库。</p>
          </div>
        </section>

        <section class="panel panel-body quick-actions">
          <h2>执行入口</h2>
          <button type="button" @click="router.push('/assessments')">
            <el-icon><DocumentChecked /></el-icon>
            <span>专项测评</span>
          </button>
          <button type="button" @click="router.push('/wrong-questions/list')">
            <el-icon><Notebook /></el-icon>
            <span>错题复盘</span>
          </button>
          <button type="button" @click="router.push('/resources')">
            <el-icon><FolderOpened /></el-icon>
            <span>资源学习</span>
          </button>
        </section>
      </aside>
    </section>

    <section class="panel panel-body">
      <div class="panel-head">
        <div>
          <h2>我的计划</h2>
          <p>计划保存目标和 AI 路径，执行结果会反向影响后续步骤。</p>
        </div>
        <div class="toolbar compact-toolbar">
          <el-select v-model="filters.planStatus" clearable placeholder="计划状态" @change="loadPlans">
            <el-option label="进行中" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已终止" :value="3" />
          </el-select>
        </div>
      </div>

      <div v-loading="loading" class="plan-grid">
        <article
          v-for="plan in currentPlans"
          :key="plan.planId"
          :class="{ active: activePlanId === plan.planId }"
          class="plan-card lift-card"
        >
          <div class="plan-card-head">
            <el-tag :type="planStatusMeta(plan.planStatus).type">
              {{ planStatusMeta(plan.planStatus).text }}
            </el-tag>
            <span>{{ formatDateTime(plan.updateTime || plan.createTime) }}</span>
          </div>
          <h3>{{ plan.planName || '学习计划' }}</h3>
          <p>{{ plan.aiPlanSummary || plan.targetDesc || '暂无目标描述' }}</p>
          <div class="plan-meta">
            <span><el-icon><Collection /></el-icon>{{ plan.subject || '全科' }}</span>
            <span><el-icon><Aim /></el-icon>{{ plan.currentScore || '-' }} -> {{ plan.targetScore || '-' }} 分</span>
            <span><el-icon><Calendar /></el-icon>{{ plan.startDate || '-' }} 至 {{ plan.endDate || '-' }}</span>
          </div>
          <div class="plan-actions">
            <el-button type="primary" :icon="'Aim'" @click="selectPlan(plan)">进入路径</el-button>
            <el-button plain :icon="'Edit'" @click="openEdit(plan)">调整</el-button>
            <el-button text type="danger" :icon="'Delete'" @click="removePlan(plan)">删除</el-button>
          </div>
        </article>
        <el-empty v-if="!loading && !currentPlans.length" class="full-empty" description="暂无学习计划">
          <el-button type="primary" @click="openCreate">生成 AI 路径</el-button>
        </el-empty>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="filters.pageNum"
          v-model:page-size="filters.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="pageTotal(page)"
          @change="loadPlans"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '调整学习计划' : '生成 AI 学习路径'" width="640px">
      <el-form label-position="top" :model="form">
        <el-form-item label="计划名称" required>
          <el-input v-model="form.planName" maxlength="40" show-word-limit placeholder="如：30 天数学提分计划" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="学科">
            <el-select v-model="form.subject" class="full">
              <el-option v-for="item in SUBJECTS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="AI 模式">
            <el-select v-model="form.provider" class="full" :disabled="Boolean(editingId)">
              <el-option label="外部 API" value="external" />
              <el-option label="Ollama 本地" value="ollama" />
              <el-option label="自动兜底" value="auto" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="当前分数">
            <el-input-number v-model="form.currentScore" :min="0" :max="150" class="full" />
          </el-form-item>
          <el-form-item label="目标分数">
            <el-input-number v-model="form.targetScore" :min="0" :max="150" class="full" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="每天可学">
            <el-input-number v-model="form.dailyMinutes" :min="5" :max="240" class="full" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.planStatus" class="full">
              <el-option label="进行中" :value="1" />
              <el-option label="已完成" :value="2" />
              <el-option label="已终止" :value="3" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="开始日期">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" class="full" />
          </el-form-item>
          <el-form-item label="截止日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" class="full" />
          </el-form-item>
        </div>
        <el-form-item label="目标描述">
          <el-input
            v-model="form.targetDesc"
            type="textarea"
            :rows="4"
            maxlength="240"
            show-word-limit
            placeholder="如：数学从 70 分提高到 90 分，重点提升一次函数、勾股定理和应用题"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlan">
          {{ editingId ? '保存' : '生成路径' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.study-page {
  gap: 18px;
}

.title-actions,
.execution-actions,
.plan-actions,
.step-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.execution-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 20px;
  padding: 22px;
}

.execution-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.execution-main h2 {
  margin: 0;
  color: var(--text);
  font-size: 28px;
  line-height: 1.25;
}

.execution-main p,
.panel-head p,
.step-content p,
.plan-card p,
.recommend-item span {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.execution-progress {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  max-width: 620px;
}

.execution-progress strong {
  font-size: 34px;
  line-height: 1;
}

.execution-stats {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.execution-stats article {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--primary-soft);
}

.execution-stats span,
.step-meta,
.plan-card-head,
.plan-meta {
  color: var(--muted);
  font-size: 13px;
}

.execution-stats strong {
  display: block;
  margin-top: 8px;
  color: var(--text);
  font-size: 24px;
  line-height: 1.15;
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.8fr);
  gap: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-head.compact {
  align-items: center;
}

.panel-head h2,
.quick-actions h2 {
  margin: 0;
  color: var(--text);
  font-size: 19px;
  line-height: 1.3;
}

.path-list,
.side-stack,
.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.path-step {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 104px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.path-step.done {
  background: #f8fafc;
}

.path-step.locked {
  opacity: 0.68;
}

.step-index {
  display: grid;
  place-items: center;
  gap: 4px;
  width: 56px;
  height: 56px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--primary-soft);
  color: var(--text);
}

.step-index span {
  font-size: 12px;
  font-weight: 700;
}

.step-index .el-icon {
  font-size: 22px;
}

.step-content {
  min-width: 0;
}

.step-title-line,
.step-meta,
.plan-card-head,
.plan-meta span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-title-line {
  justify-content: space-between;
  margin-bottom: 6px;
}

.step-title-line strong,
.recommend-item strong,
.plan-card h3 {
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reason {
  margin-top: 6px !important;
  font-size: 13px;
}

.step-meta {
  flex-wrap: wrap;
  margin-top: 8px;
}

.step-actions {
  justify-content: flex-end;
}

.recommend-item,
.quick-actions button {
  display: grid;
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
  text-align: left;
  cursor: pointer;
}

.recommend-item {
  gap: 5px;
  padding: 12px;
}

.recommend-item:hover,
.quick-actions button:hover {
  background: var(--primary-soft);
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-actions button {
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 10px 12px;
  color: var(--text);
  font: inherit;
}

.compact-toolbar .el-select {
  width: 160px;
}

.plan-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.plan-card {
  display: flex;
  flex-direction: column;
  gap: 13px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.plan-card.active {
  border-color: var(--primary);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
}

.plan-card-head {
  justify-content: space-between;
}

.plan-card h3 {
  margin: 0;
  font-size: 18px;
}

.plan-card p {
  display: -webkit-box;
  min-height: 48px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.plan-meta {
  display: grid;
  gap: 7px;
}

.plan-actions {
  margin-top: auto;
}

.full-empty {
  grid-column: 1 / -1;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.full {
  width: 100%;
}

@media (max-width: 1180px) {
  .execution-board,
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .execution-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .plan-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .execution-board,
  .path-panel,
  .panel-body {
    padding: 14px;
  }

  .execution-stats,
  .plan-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .path-step {
    align-items: flex-start;
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .step-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .panel-head,
  .step-title-line {
    align-items: flex-start;
    flex-direction: column;
  }

  .pager {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
