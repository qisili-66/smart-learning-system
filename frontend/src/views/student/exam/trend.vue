<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { assessmentApi } from '@/api/student'
import { ASSESSMENT_TYPES, difficultyLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const trend = ref(null)

const assessmentId = computed(() => Number(route.params.assessmentId || 0))
const points = computed(() => (Array.isArray(trend.value?.points) ? trend.value.points : []))
const deltaRate = computed(() => Number(trend.value?.deltaCorrectRate || 0))
const deltaScore = computed(() => Number(trend.value?.deltaScore || 0))
const current = computed(() => trend.value?.current || {})

const chartPoints = computed(() => {
  const list = points.value
  const width = 640
  const height = 220
  const padding = 28
  if (!list.length) return []
  return list.map((item, index) => {
    const x = list.length === 1 ? width / 2 : padding + (index * (width - padding * 2)) / (list.length - 1)
    const rate = Math.max(0, Math.min(100, Number(item.correctRate || 0)))
    const y = padding + ((100 - rate) * (height - padding * 2)) / 100
    return { ...item, x, y, rate }
  })
})
const polyline = computed(() => chartPoints.value.map((item) => `${item.x},${item.y}`).join(' '))

async function loadTrend() {
  if (!assessmentId.value) {
    router.replace('/assessments')
    return
  }
  loading.value = true
  try {
    trend.value = await assessmentApi.trend(assessmentId.value)
  } finally {
    loading.value = false
  }
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0))
  const minutes = Math.floor(value / 60)
  const rest = value % 60
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

function deltaText(value, suffix = '') {
  const num = Number(value || 0)
  return `${num > 0 ? '+' : ''}${num.toFixed(2)}${suffix}`
}

onMounted(loadTrend)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <div>
        <h1>成绩趋势</h1>
        <p>{{ trend?.subject || '测评' }} · 最近同学科测评对比</p>
      </div>
      <div class="title-actions">
        <el-button :icon="'Document'" @click="router.push(`/assessments/${assessmentId}/report`)">返回报告</el-button>
        <el-button :icon="'ArrowLeft'" @click="router.push('/assessments')">测评主页</el-button>
      </div>
    </div>

    <section v-if="trend" class="trend-grid">
      <div class="panel panel-body">
        <div class="panel-head">
          <h2>本次对比</h2>
          <el-tag :type="deltaRate >= 0 ? 'success' : 'warning'">
            {{ deltaText(deltaRate, '%') }}
          </el-tag>
        </div>
        <div class="metric-row">
          <div>
            <span>本次正确率</span>
            <strong>{{ Number(current.correctRate || 0).toFixed(2) }}%</strong>
          </div>
          <div>
            <span>得分变化</span>
            <strong>{{ deltaText(deltaScore) }}</strong>
          </div>
          <div>
            <span>本次用时</span>
            <strong>{{ formatDuration(current.totalUseSeconds) }}</strong>
          </div>
        </div>
        <p class="trend-suggestion">{{ trend.trendSuggestion }}</p>
      </div>

      <div class="panel panel-body">
        <div class="panel-head">
          <h2>最近曲线</h2>
          <span class="muted">{{ points.length }} 次</span>
        </div>
        <div class="chart-wrap">
          <svg viewBox="0 0 640 220" role="img" aria-label="成绩趋势曲线">
            <line x1="28" y1="28" x2="28" y2="192" class="axis" />
            <line x1="28" y1="192" x2="612" y2="192" class="axis" />
            <polyline v-if="chartPoints.length > 1" :points="polyline" class="trend-line" />
            <g v-for="item in chartPoints" :key="item.assessmentId">
              <circle :cx="item.x" :cy="item.y" r="6" class="point" />
              <text :x="item.x" :y="Math.max(18, item.y - 12)" text-anchor="middle">
                {{ Math.round(item.rate) }}%
              </text>
            </g>
          </svg>
        </div>
      </div>
    </section>

    <section v-if="points.length" class="panel panel-body">
      <div class="panel-head">
        <h2>历史对比</h2>
      </div>
      <div class="trend-list">
        <article v-for="item in points" :key="item.assessmentId" class="trend-row">
          <button type="button" @click="router.push(`/assessments/${item.assessmentId}/report`)">
            <strong>{{ ASSESSMENT_TYPES[item.assessmentType] || '测评' }} · {{ item.knowledgeScope || '综合知识点' }}</strong>
            <span>{{ difficultyLabel(item.difficulty) }} · {{ item.createTime || '-' }}</span>
          </button>
          <div>
            <strong>{{ Number(item.correctRate || 0).toFixed(2) }}%</strong>
            <span>{{ item.userScore || 0 }}/{{ item.totalScore || 100 }} 分 · {{ formatDuration(item.totalUseSeconds) }}</span>
          </div>
        </article>
      </div>
    </section>

    <el-empty v-if="!loading && !trend" description="暂无趋势数据">
      <el-button type="primary" @click="router.push('/assessments')">返回测评主页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.title-actions,
.panel-head,
.metric-row,
.trend-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.title-actions {
  flex-wrap: wrap;
}

.panel-head {
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
}

.trend-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(420px, 1.1fr);
  gap: 16px;
}

.metric-row {
  justify-content: flex-start;
}

.metric-row div {
  min-width: 130px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.metric-row span,
.trend-row span,
.trend-suggestion {
  color: var(--muted);
}

.metric-row strong {
  display: block;
  margin-top: 6px;
  font-size: 22px;
}

.trend-suggestion {
  margin: 14px 0 0;
  line-height: 1.7;
}

.chart-wrap {
  width: 100%;
  aspect-ratio: 16 / 6;
  min-height: 220px;
}

.chart-wrap svg {
  width: 100%;
  height: 100%;
}

.axis {
  stroke: var(--line);
  stroke-width: 2;
}

.trend-line {
  fill: none;
  stroke: var(--primary);
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.point {
  fill: var(--panel);
  stroke: var(--primary);
  stroke-width: 4;
}

text {
  fill: var(--muted);
  font-size: 14px;
}

.trend-list {
  display: grid;
  gap: 10px;
}

.trend-row {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.trend-row button {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 0;
  border: 0;
  color: var(--text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.trend-row > div {
  display: grid;
  gap: 5px;
  min-width: 150px;
  text-align: right;
}

@media (max-width: 960px) {
  .trend-grid,
  .metric-row,
  .trend-row {
    grid-template-columns: 1fr;
  }

  .trend-grid {
    display: grid;
  }

  .metric-row,
  .trend-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-row > div {
    text-align: left;
  }
}
</style>
