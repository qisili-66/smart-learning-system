<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import {
  profileApi,
  studyRecordApi
} from '@/api/student'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const duration = ref({ totalDuration: 0 })
const progressReport = ref({ summary: {}, recommendations: [], dailyTrend: [] })
const profileOverview = ref({ recommendations: [] })

const totalHours = computed(() => Math.round(Number(duration.value.totalDuration || 0) / 60))
const reportSummary = computed(() => progressReport.value?.summary || {})

const profileAdvice = computed(() => {
  const items = Array.isArray(profileOverview.value?.recommendations) ? profileOverview.value.recommendations : []
  return items.slice(0, 3).map((item) => {
    if (typeof item === 'string') return item
    const content = item.content || ''
    if (item.type === 'review_weak_points') return `优先复盘薄弱点：${content}`
    if (item.type === 'finish_pending_tasks') return 'AI 建议先完成未结束任务，再进入新知识学习。'
    if (item.type === 'accuracy_training') return 'AI 建议围绕低分知识点做专项训练。'
    if (item.type === 'increase_study_duration') return 'AI 建议把有效学习时长稳定到每个学习日 30 分钟以上。'
    return content || 'AI 建议根据本周表现调整任务顺序。'
  })
})

async function loadHome() {
  loading.value = true
  try {
    const [profileData, durationData, reportData] = await Promise.all([
      profileApi.my().catch(() => ({})),
      studyRecordApi.durationStatistics({ type: 'week' }).catch(() => ({})),
      studyRecordApi.progressReport({ period: 'week' }).catch(() => ({ summary: {}, recommendations: [] }))
    ])
    profileOverview.value = profileData || { recommendations: [] }
    duration.value = durationData || {}
    progressReport.value = reportData || { summary: {}, recommendations: [], dailyTrend: [] }
  } finally {
    loading.value = false
  }
}

onMounted(loadHome)
</script>

<template>
  <div class="home-page" v-loading="loading">
    <section class="dashboard-head panel-block">
      <div>
        <p>基于 AI Agent 的智慧学习辅助系统</p>
        <h1>{{ auth.displayName }}，今天先完成一件最重要的事</h1>
        <span>{{ profileAdvice[0] || '完成一次测评或错题复盘后，系统会生成更准确的学习建议。' }}</span>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="router.push('/study-records')">学习记录</el-button>
        <el-button @click="router.push('/profile')">查看画像</el-button>
      </div>
    </section>

    <section class="stat-strip">
      <article>
        <span>累计学习</span>
        <strong>{{ totalHours }}h</strong>
      </article>
      <article>
        <span>任务完成率</span>
        <strong>{{ reportSummary.taskCompletionRate || 0 }}%</strong>
      </article>
      <article>
        <span>测评均分</span>
        <strong>{{ reportSummary.assessmentAverageScore || 0 }}%</strong>
      </article>
      <article>
        <span>错题掌握</span>
        <strong>{{ reportSummary.wrongQuestionMasteryRate || 0 }}%</strong>
      </article>
    </section>

    <section class="progress-grid">
      <div class="panel-block progress-panel">
        <div class="section-head compact">
          <h2>本周进度报告</h2>
          <el-button text @click="router.push('/study-records')">查看记录</el-button>
        </div>
        <div class="progress-metrics">
          <div>
            <span>任务完成率</span>
            <strong>{{ reportSummary.taskCompletionRate || 0 }}%</strong>
            <el-progress :percentage="reportSummary.taskCompletionRate || 0" />
          </div>
          <div>
            <span>测评均分</span>
            <strong>{{ reportSummary.assessmentAverageScore || 0 }}%</strong>
            <el-progress :percentage="reportSummary.assessmentAverageScore || 0" status="success" />
          </div>
          <div>
            <span>错题掌握率</span>
            <strong>{{ reportSummary.wrongQuestionMasteryRate || 0 }}%</strong>
            <el-progress :percentage="reportSummary.wrongQuestionMasteryRate || 0" status="warning" />
          </div>
        </div>
        <div class="recommend-list">
          <p v-for="item in progressReport.recommendations" :key="item">{{ item }}</p>
        </div>
      </div>

    </section>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-head,
.stat-strip,
.panel-block {
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--panel);
}

.dashboard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
}

.dashboard-head p {
  margin: 0 0 14px;
  color: var(--muted);
  font-weight: 800;
}

.dashboard-head h1 {
  max-width: 920px;
  margin: 0;
  color: var(--text);
  font-size: 28px;
  line-height: 1.25;
  letter-spacing: 0;
}

.dashboard-head span {
  display: block;
  max-width: 820px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.6;
}

.head-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.dashboard-head + .stat-strip {
  margin-top: -2px;
}

.stat-strip span {
  color: var(--muted);
  font-weight: 800;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
}

.stat-strip article {
  min-height: 92px;
  padding: 18px 24px;
}

.stat-strip article + article {
  border-left: 1px solid var(--line);
}

.stat-strip strong {
  display: block;
  margin-top: 10px;
  color: var(--text);
  font-size: 28px;
  line-height: 1;
}

.progress-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.progress-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.progress-metrics > div {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 16px;
}

.progress-metrics span,
.progress-metrics small {
  color: var(--muted);
  font-size: 13px;
}

.progress-metrics strong {
  display: block;
  margin: 8px 0 10px;
  font-size: 28px;
}

.recommend-list {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.recommend-list p {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  color: var(--text);
  background: var(--primary-soft);
}

.panel-block {
  padding: 20px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.section-head.compact {
  align-items: center;
  margin-bottom: 16px;
}

.section-head h2,
.panel-block h2 {
  margin: 0;
  color: var(--text);
  font-size: 20px;
  line-height: 1.25;
}

.section-head p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 16px;
}

@media (max-width: 1180px) {
  .progress-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-head,
  .panel-block {
    padding: 20px;
  }

  .dashboard-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .dashboard-head h1 {
    font-size: 24px;
  }

  .head-actions {
    width: 100%;
  }

  .stat-strip {
    grid-template-columns: 1fr 1fr;
  }

  .stat-strip article:nth-child(odd) {
    border-left: 0;
  }

  .stat-strip article:nth-child(n + 3) {
    border-top: 1px solid var(--line);
  }

  .progress-metrics,
  .progress-grid {
    grid-template-columns: 1fr;
  }
}
</style>
