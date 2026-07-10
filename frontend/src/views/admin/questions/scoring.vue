<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminQuestionApi } from '@/api/admin'
import { difficultyLabel, questionTypeLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const question = ref(null)

const form = reactive({
  scoringPoints: ''
})

const questionId = computed(() => Number(route.params.questionId || 0))
const pointList = computed(() =>
  String(form.scoringPoints || '')
    .split(/[\n;；]+/)
    .map((item) => item.replace(/^[-*\d.、\s]+/, '').trim())
    .filter(Boolean)
)

async function loadQuestion() {
  if (!questionId.value) {
    router.replace('/admin/questions')
    return
  }
  loading.value = true
  try {
    question.value = await adminQuestionApi.detail(questionId.value)
    form.scoringPoints = question.value?.scoringPoints || ''
  } finally {
    loading.value = false
  }
}

async function saveScoringPoints() {
  if (!question.value?.questionId) return
  submitting.value = true
  try {
    await adminQuestionApi.update(question.value.questionId, {
      ...question.value,
      scoringPoints: form.scoringPoints
    })
    ElMessage.success('评分要点已保存')
    await loadQuestion()
  } finally {
    submitting.value = false
  }
}

onMounted(loadQuestion)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <div class="admin-page-title">
      <div>
        <h1>评分要点配置</h1>
        <p v-if="question">
          {{ question.subject || '未设置学科' }} · {{ questionTypeLabel(question.questionType) }} ·
          {{ difficultyLabel(question.difficulty) }}
        </p>
      </div>
      <div class="admin-title-actions">
        <el-button :icon="'ArrowLeft'" @click="router.push('/admin/questions')">返回题库</el-button>
        <el-button type="primary" :loading="submitting" :icon="'Check'" @click="saveScoringPoints">保存</el-button>
      </div>
    </div>

    <section v-if="question" class="scoring-layout">
      <div class="admin-panel context-panel">
        <div class="admin-panel-head">
          <h2>题目上下文</h2>
          <el-tag :type="Number(question.questionType) === 4 ? 'success' : 'info'">
            {{ questionTypeLabel(question.questionType) }}
          </el-tag>
        </div>
        <dl class="context-list">
          <div>
            <dt>知识点</dt>
            <dd>{{ question.knowledgePoint || '-' }}</dd>
          </div>
          <div>
            <dt>题干</dt>
            <dd>{{ question.questionText || '-' }}</dd>
          </div>
          <div>
            <dt>参考答案</dt>
            <dd>{{ question.answer || '-' }}</dd>
          </div>
          <div>
            <dt>解析</dt>
            <dd>{{ question.analysis || '-' }}</dd>
          </div>
        </dl>
      </div>

      <div class="admin-panel edit-panel">
        <div class="admin-panel-head">
          <h2>评分要点</h2>
          <el-tag>{{ pointList.length }} 项</el-tag>
        </div>
        <el-input
          v-model="form.scoringPoints"
          type="textarea"
          :rows="12"
          maxlength="1200"
          show-word-limit
          placeholder="每行一个评分要点"
        />
        <div class="point-preview">
          <span v-for="point in pointList" :key="point">{{ point }}</span>
          <p v-if="!pointList.length">暂无评分要点</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.scoring-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 14px;
}

.context-panel,
.edit-panel {
  min-width: 0;
}

.context-list {
  display: grid;
  gap: 14px;
  margin: 0;
}

.context-list div {
  display: grid;
  gap: 6px;
}

.context-list dt {
  color: #6b7280;
  font-size: 13px;
}

.context-list dd {
  margin: 0;
  color: #111827;
  line-height: 1.7;
  white-space: pre-wrap;
}

.point-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.point-preview span {
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  color: #111827;
  background: #f9fafb;
  font-size: 13px;
}

.point-preview p {
  margin: 0;
  color: #6b7280;
}

@media (max-width: 960px) {
  .scoring-layout {
    grid-template-columns: 1fr;
  }
}
</style>
