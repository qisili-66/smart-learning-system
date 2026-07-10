<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assessmentApi } from '@/api/student'
import {
  ASSESSMENT_TYPES,
  DIFFICULTY,
  SUBJECTS,
  difficultyLabel,
  formatDateTime,
  pageList
} from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadingText = ref('正在整理错题册')
const booklets = ref([])
const selectedAssessmentId = ref(null)

const filters = reactive({
  subject: '',
  assessmentType: '',
  difficulty: '',
  keyword: ''
})

const filteredBooklets = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return booklets.value.filter((book) => {
    const matchesType = filters.assessmentType === '' || Number(book.assessmentType) === Number(filters.assessmentType)
    const matchesDifficulty = filters.difficulty === '' || Number(book.difficulty) === Number(filters.difficulty)
    const text = [
      ASSESSMENT_TYPES[book.assessmentType],
      book.subject,
      book.knowledgeScope,
      book.knowledgeSummary
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return matchesType && matchesDifficulty && (!keyword || text.includes(keyword))
  })
})

function isSubmitted(item) {
  return Number(item?.assessmentStatus) === 2
}

function isWrongAnswer(item) {
  if (item?.isCorrect !== undefined && item?.isCorrect !== null) {
    return Number(item.isCorrect) === 0
  }
  const score = Number(item?.score || 0)
  const maxScore = Number(item?.maxScore || 0)
  return maxScore > 0 && score < maxScore
}

function uniqueKnowledge(items) {
  const names = items
    .map((item) => item.knowledgePoint)
    .filter(Boolean)
  return Array.from(new Set(names)).slice(0, 3)
}

function scorePercent(book) {
  const total = Number(book.totalScore || book.report?.totalScore || 0)
  if (!total) return 0
  return Math.round((Number(book.userScore || book.report?.userScore || 0) / total) * 100)
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0))
  const minutes = Math.floor(value / 60)
  const rest = value % 60
  if (!minutes) return `${rest} 秒`
  return `${minutes} 分 ${rest} 秒`
}

function bookTheme(book) {
  const value = Number(book?.assessmentId || 0)
  return `theme-${Math.abs(value) % 5}`
}

async function buildBooklet(assessment) {
  const report = await assessmentApi.report(assessment.assessmentId)
  const answerDetails = Array.isArray(report?.answerDetails) ? report.answerDetails : []
  const wrongItems = answerDetails.filter(isWrongAnswer)
  const knowledge = uniqueKnowledge(wrongItems)
  const totalUseSeconds = wrongItems.reduce((sum, item) => sum + Number(item.questionUseSeconds || 0), 0)
  return {
    ...assessment,
    report,
    wrongCount: wrongItems.length,
    answerCount: answerDetails.length,
    knowledgePoints: knowledge,
    knowledgeSummary: knowledge.join('、'),
    totalUseSeconds
  }
}

async function loadBooklets() {
  loading.value = true
  loadingText.value = '正在整理错题册'
  try {
    const page = await assessmentApi.history({
      subject: filters.subject || undefined,
      pageNum: 1,
      pageSize: 60
    })
    const submitted = pageList(page).filter(isSubmitted)
    if (!submitted.length) {
      booklets.value = []
      return
    }

    loadingText.value = '正在读取测评错题'
    const results = await Promise.allSettled(submitted.map((item) => buildBooklet(item)))
    const failedCount = results.filter((item) => item.status === 'rejected').length
    booklets.value = results
      .filter((item) => item.status === 'fulfilled')
      .map((item) => item.value)
      .filter((item) => item.wrongCount > 0)
      .sort((left, right) => new Date(right.createTime || right.endTime || 0) - new Date(left.createTime || left.endTime || 0))
    if (failedCount) {
      ElMessage.warning(`有 ${failedCount} 次测评报告暂时读取失败，已跳过`)
    }
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.subject = ''
  filters.assessmentType = ''
  filters.difficulty = ''
  filters.keyword = ''
  loadBooklets()
}

function selectBook(book) {
  selectedAssessmentId.value = book.assessmentId
}

function openBook(book) {
  router.push(`/wrong-questions/books/${book.assessmentId}`)
}

function openFullList() {
  router.push({
    path: '/wrong-questions/list',
    query: filters.subject ? { subject: filters.subject, isMastered: 0 } : { isMastered: 0 }
  })
}

onMounted(loadBooklets)
</script>

<template>
  <div class="page wrong-book-page">
      <div class="page-title">
        <div>
          <h1>错题本</h1>
        <p>按每一次测评整理成长条书册，先筛选，再进入对应测评的错题详情。</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'Refresh'" @click="loadBooklets">刷新</el-button>
        <el-button type="primary" plain :icon="'List'" @click="openFullList">完整错题列表</el-button>
      </div>
    </div>

    <section class="panel panel-body filter-panel">
      <div class="filter-row">
        <el-select v-model="filters.subject" clearable placeholder="学科" @change="loadBooklets">
          <el-option v-for="item in SUBJECTS" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.assessmentType" clearable placeholder="测评类型">
          <el-option
            v-for="(label, value) in ASSESSMENT_TYPES"
            :key="value"
            :label="label"
            :value="Number(value)"
          />
        </el-select>
        <el-select v-model="filters.difficulty" clearable placeholder="难度">
          <el-option v-for="(label, value) in DIFFICULTY" :key="value" :label="label" :value="Number(value)" />
        </el-select>
        <el-input
          v-model.trim="filters.keyword"
          clearable
          placeholder="搜索知识点或测评范围"
          :prefix-icon="'Search'"
        />
        <el-button :icon="'RefreshLeft'" @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section v-loading="loading" :element-loading-text="loadingText" class="book-shelf">
      <button
        v-for="book in filteredBooklets"
        :key="book.assessmentId"
        :class="['book-row', bookTheme(book), { selected: selectedAssessmentId === book.assessmentId }]"
        type="button"
        :title="`双击进入${ASSESSMENT_TYPES[book.assessmentType] || '测评错题册'}`"
        @click="selectBook(book)"
        @dblclick="openBook(book)"
        @keydown.enter.prevent="openBook(book)"
        @keydown.space.prevent="openBook(book)"
      >
        <span class="book-spine">
          <span>{{ book.subject || '全科' }}</span>
        </span>
        <span class="book-cover">
          <span class="book-title-line">
            <strong>{{ ASSESSMENT_TYPES[book.assessmentType] || '测评错题册' }}</strong>
            <el-tag type="warning">{{ book.wrongCount }} 道错题</el-tag>
          </span>
          <span class="book-scope">{{ book.knowledgeScope || '综合知识点' }}</span>
          <span class="book-meta">
            {{ difficultyLabel(book.difficulty) }} · 得分 {{ book.userScore || 0 }}/{{ book.totalScore || 0 }} ·
            正确率 {{ Math.round(Number(book.report?.correctRate ?? scorePercent(book))) }}% ·
            {{ formatDateTime(book.endTime || book.createTime) }}
          </span>
          <span class="book-points">
            <span v-for="point in book.knowledgePoints" :key="point">{{ point }}</span>
            <span v-if="!book.knowledgePoints.length">未标注知识点</span>
          </span>
        </span>
        <span class="book-page-edge">
          <span>{{ book.answerCount || 0 }} 题</span>
          <span>{{ formatDuration(book.totalUseSeconds) }}</span>
        </span>
      </button>

      <el-empty
        v-if="!loading && !filteredBooklets.length"
        class="full-empty"
        description="暂无符合条件的测评错题册"
      >
        <el-button type="primary" @click="router.push('/assessments')">去完成一次测评</el-button>
        <el-button @click="openFullList">查看完整错题列表</el-button>
      </el-empty>
    </section>
  </div>
</template>

<style scoped>
.wrong-book-page {
  min-height: calc(100vh - 80px);
}

.title-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-panel {
  position: sticky;
  top: 0;
  z-index: 2;
}

.filter-row {
  display: grid;
  grid-template-columns: 150px 160px 140px minmax(220px, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.book-shelf {
  display: grid;
  gap: 14px;
  min-height: 240px;
  padding: 4px 0 18px;
}

.book-row {
  --book-cover: #f8fafc;
  --book-cover-deep: #e5e7eb;
  --book-spine: #111827;
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr) 132px;
  align-items: stretch;
  min-height: 108px;
  overflow: hidden;
  padding: 0;
  border: 1px solid rgba(17, 24, 39, 0.14);
  border-radius: 6px 8px 8px 6px;
  background: var(--book-cover);
  color: var(--text);
  box-shadow:
    0 10px 20px rgba(15, 23, 42, 0.08),
    inset 0 -8px 0 rgba(17, 24, 39, 0.06);
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.book-row:hover,
.book-row:focus-visible,
.book-row.selected {
  transform: translateX(4px);
  border-color: var(--primary);
  box-shadow:
    0 16px 30px rgba(15, 23, 42, 0.14),
    inset 0 -8px 0 rgba(17, 24, 39, 0.06);
  outline: none;
}

.book-row.theme-0 {
  --book-cover: #f8fafc;
  --book-cover-deep: #e5e7eb;
  --book-spine: #111827;
}

.book-row.theme-1 {
  --book-cover: #ecfeff;
  --book-cover-deep: #cffafe;
  --book-spine: #155e75;
}

.book-row.theme-2 {
  --book-cover: #f0fdf4;
  --book-cover-deep: #dcfce7;
  --book-spine: #166534;
}

.book-row.theme-3 {
  --book-cover: #fff7ed;
  --book-cover-deep: #fed7aa;
  --book-spine: #9a3412;
}

.book-row.theme-4 {
  --book-cover: #fefce8;
  --book-cover-deep: #fef08a;
  --book-spine: #854d0e;
}

.book-spine {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid rgba(255, 255, 255, 0.2);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.18), transparent 28%, rgba(0, 0, 0, 0.16) 100%),
    var(--book-spine);
  color: #ffffff;
}

.book-spine::before,
.book-spine::after {
  position: absolute;
  top: 12px;
  bottom: 12px;
  width: 1px;
  content: '';
  background: rgba(255, 255, 255, 0.28);
}

.book-spine::before {
  left: 13px;
}

.book-spine::after {
  right: 12px;
}

.book-spine span {
  position: relative;
  z-index: 1;
  font-size: 14px;
  font-weight: 800;
  writing-mode: vertical-rl;
}

.book-cover {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 9px;
  min-width: 0;
  padding: 16px 20px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.76), transparent 22%),
    linear-gradient(180deg, var(--book-cover), var(--book-cover-deep));
}

.book-cover::after {
  position: absolute;
  right: 14px;
  bottom: 12px;
  left: 18px;
  height: 1px;
  content: '';
  background: rgba(17, 24, 39, 0.12);
}

.book-title-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.book-title-line strong {
  overflow: hidden;
  font-size: 20px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-scope,
.book-meta {
  overflow: hidden;
  color: var(--muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-scope {
  font-size: 15px;
  line-height: 1.55;
}

.book-meta {
  font-size: 13px;
}

.book-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.book-points span {
  max-width: 160px;
  overflow: hidden;
  padding: 4px 9px;
  border: 1px solid rgba(17, 24, 39, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: var(--text);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-page-edge {
  display: grid;
  align-content: center;
  justify-items: end;
  gap: 9px;
  padding: 16px 18px;
  border-left: 1px solid rgba(17, 24, 39, 0.12);
  background:
    repeating-linear-gradient(
      180deg,
      #ffffff,
      #ffffff 7px,
      #eef2f7 8px,
      #ffffff 10px
    );
  color: var(--muted);
  font-size: 13px;
}

.book-page-edge span:first-child {
  color: var(--text);
  font-size: 18px;
  font-weight: 800;
}

.full-empty {
  grid-column: 1 / -1;
  padding: 48px 0;
}

@media (max-width: 1180px) {
  .filter-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .filter-panel {
    position: static;
  }

  .filter-row {
    grid-template-columns: 1fr;
  }

  .book-row {
    grid-template-columns: 54px minmax(0, 1fr);
    min-height: 140px;
  }

  .book-page-edge {
    grid-column: 1 / -1;
    display: flex;
    justify-content: space-between;
    border-top: 1px solid rgba(17, 24, 39, 0.12);
    border-left: 0;
  }

  .book-title-line {
    align-items: flex-start;
    flex-direction: column;
  }

  .book-title-line strong,
  .book-scope,
  .book-meta {
    white-space: normal;
  }
}
</style>
