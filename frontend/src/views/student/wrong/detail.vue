<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { wrongQuestionApi } from '@/api/student'
import { formatDateTime, wrongReasonLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const currentDetail = ref(null)
const similarQuestions = ref([])

const reviewForm = reactive({
  personalNote: '',
  reviewCycleDays: 3,
  nextReviewTime: ''
})

async function loadDetail() {
  const wrongId = Number(route.params.wrongId || 0)
  if (!wrongId) {
    router.replace('/wrong-questions/list')
    return
  }
  loading.value = true
  try {
    currentDetail.value = await wrongQuestionApi.detail(wrongId)
    Object.assign(reviewForm, {
      personalNote: currentDetail.value.personalNote || '',
      reviewCycleDays: Number(currentDetail.value.reviewCycleDays || 3),
      nextReviewTime: currentDetail.value.nextReviewTime || ''
    })
    const similar = await wrongQuestionApi.similar(wrongId, { limit: 5 }).catch(() => [])
    similarQuestions.value = Array.isArray(similar) ? similar : []
  } finally {
    loading.value = false
  }
}

async function markMastered(value) {
  if (!currentDetail.value?.wrongId) return
  await wrongQuestionApi.markMastered(currentDetail.value.wrongId, value)
  ElMessage.success(value ? '已标记掌握' : '已移回待复盘')
  await loadDetail()
}

async function saveReviewPlan() {
  if (!currentDetail.value?.wrongId) return
  saving.value = true
  try {
    const data = await wrongQuestionApi.updateReviewPlan(currentDetail.value.wrongId, {
      personalNote: reviewForm.personalNote,
      reviewCycleDays: Number(reviewForm.reviewCycleDays || 3),
      nextReviewTime: reviewForm.nextReviewTime
    })
    currentDetail.value = {
      ...currentDetail.value,
      ...data
    }
    ElMessage.success('复盘计划已保存')
  } finally {
    saving.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <div>
        <h1>错题详情</h1>
        <p v-if="currentDetail">
          {{ currentDetail.knowledgePoint || '未标注知识点' }} ·
          {{ wrongReasonLabel(currentDetail.wrongReason) }} ·
          {{ currentDetail.isMastered ? '已掌握' : '待复盘' }}
        </p>
        <p v-else>正在加载错题解析。</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'ArrowLeft'" @click="router.push('/wrong-questions/list')">返回错题列表</el-button>
        <el-button
          v-if="currentDetail && !currentDetail.isMastered"
          type="success"
          :icon="'Check'"
          @click="markMastered(1)"
        >
          标记掌握
        </el-button>
        <el-button
          v-else-if="currentDetail"
          plain
          :icon="'RefreshLeft'"
          @click="markMastered(0)"
        >
          移回复盘
        </el-button>
      </div>
    </div>

    <section v-if="currentDetail" class="detail-grid">
      <div class="panel panel-body question-panel">
        <div class="panel-head">
          <h2>{{ currentDetail.questionText || `错题 #${currentDetail.wrongId}` }}</h2>
          <el-tag :type="currentDetail.isMastered ? 'success' : 'warning'">
            {{ currentDetail.isMastered ? '已掌握' : '待复盘' }}
          </el-tag>
        </div>

        <div v-if="currentDetail.options?.length" class="options">
          <p v-for="option in currentDetail.options" :key="option">{{ option }}</p>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="知识点">{{ currentDetail.knowledgePoint || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误答案">{{ currentDetail.wrongAnswer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="正确答案">{{ currentDetail.correctAnswer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误原因">
            {{ wrongReasonLabel(currentDetail.wrongReason) }}
          </el-descriptions-item>
          <el-descriptions-item label="首次错误">{{ formatDateTime(currentDetail.firstWrongTime) }}</el-descriptions-item>
          <el-descriptions-item label="下次复盘">{{ currentDetail.nextReviewTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="解析">{{ currentDetail.analysis || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <aside class="side-stack">
        <section class="panel panel-body">
          <div class="panel-head">
            <h2>复盘计划</h2>
          </div>
          <el-form label-position="top" :model="reviewForm" class="review-form">
            <el-form-item label="个人备注">
              <el-input
                v-model="reviewForm.personalNote"
                type="textarea"
                :rows="4"
                placeholder="记录本题易错点、复盘心得或下次注意事项"
              />
            </el-form-item>
            <el-form-item label="复盘周期（天）">
              <el-input-number v-model="reviewForm.reviewCycleDays" :min="1" :max="30" />
            </el-form-item>
            <el-form-item label="下次复盘时间">
              <el-date-picker
                v-model="reviewForm.nextReviewTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="选择时间"
                class="full"
              />
            </el-form-item>
            <el-button type="primary" :loading="saving" :icon="'Calendar'" @click="saveReviewPlan">
              保存复盘计划
            </el-button>
          </el-form>
        </section>

        <section class="panel panel-body">
          <div class="panel-head">
            <h2>同类练习</h2>
          </div>
          <div class="similar-list">
            <article v-for="item in similarQuestions" :key="item.questionId || item.wrongId" class="similar-item">
              <strong>{{ item.questionText || item.title || '同类题' }}</strong>
              <span>{{ item.knowledgePoint || currentDetail.knowledgePoint || '相关知识点' }}</span>
            </article>
            <p v-if="!similarQuestions.length" class="empty-copy">暂无同类练习，可先完成一次专项测评补充题库数据。</p>
          </div>
        </section>
      </aside>
    </section>

    <el-empty v-if="!loading && !currentDetail" description="未找到错题">
      <el-button type="primary" @click="router.push('/wrong-questions/list')">返回错题列表</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.title-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.75fr);
  gap: 16px;
}

.question-panel,
.side-stack,
.review-form,
.similar-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
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
  line-height: 1.5;
}

.options {
  display: grid;
  gap: 8px;
}

.options p,
.similar-item {
  margin: 0;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #f8fafc;
}

.full {
  width: 100%;
}

.similar-item {
  display: grid;
  gap: 5px;
}

.similar-item strong {
  color: var(--text);
  line-height: 1.5;
}

.similar-item span {
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
