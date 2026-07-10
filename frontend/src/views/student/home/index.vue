<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import {
  personalDataApi,
  profileApi,
  studyPlanApi,
  studyRecordApi,
  wrongQuestionApi
} from '@/api/student'
import { asPercent, pageList } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const profile = ref({})
const weakPoints = ref([])
const plans = ref([])
const dailyTasks = ref([])
const wrongStats = ref({ total: 0, mastered: 0, notMastered: 0 })
const duration = ref({ totalDuration: 0 })
const personalOverview = ref({})

const ability = computed(() => asPercent(profile.value.abilityScore, 0))
const mastery = computed(() => asPercent(profile.value.knowledgeMastery, 0))
const totalHours = computed(() => Math.round(Number(duration.value.totalDuration || 0) / 60))
const todayTasks = computed(() => dailyTasks.value.slice(0, 4))
const nextTask = computed(() => todayTasks.value[0])
const todayProgress = computed(() => {
  if (!todayTasks.value.length) return Math.max(18, mastery.value)
  const finished = todayTasks.value.filter((item) => Number(item.finishStatus) === 1).length
  return Math.max(18, Math.round((finished / todayTasks.value.length) * 100))
})

const nextActions = computed(() => [
  {
    title: nextTask.value ? '继续今日任务' : '建立学习方案',
    desc: nextTask.value?.description || '根据当前薄弱点开始一轮练习',
    path: nextTask.value ? '/study-plans' : '/study-plans',
    icon: 'Aim'
  },
  {
    title: '复盘错题',
    desc: `${wrongStats.value.notMastered || 0} 道题等待重新掌握`,
    path: '/wrong-questions/list',
    icon: 'Notebook'
  }
])

async function loadHome() {
  loading.value = true
  try {
    const [profileData, weakData, planPage, taskData, stats, durationData, overview] = await Promise.all([
      profileApi.my().catch(() => ({})),
      profileApi.weakPoints({ limit: 5 }).catch(() => []),
      studyPlanApi.list({ pageNum: 1, pageSize: 6 }).catch(() => ({ list: [] })),
      studyPlanApi.dailyTasks({ date: new Date().toISOString().slice(0, 10) }).catch(() => ({ tasks: [] })),
      wrongQuestionApi.statistics().catch(() => ({})),
      studyRecordApi.durationStatistics({ type: 'week' }).catch(() => ({})),
      personalDataApi.overview().catch(() => ({}))
    ])
    profile.value = profileData || {}
    weakPoints.value = Array.isArray(weakData) ? weakData : []
    plans.value = pageList(planPage)
    dailyTasks.value = Array.isArray(taskData?.tasks) ? taskData.tasks : []
    wrongStats.value = stats || {}
    duration.value = durationData || {}
    personalOverview.value = overview || {}
  } finally {
    loading.value = false
  }
}

onMounted(loadHome)
</script>

<template>
  <div class="home-page" v-loading="loading">
    <section class="hero-grid">
      <div class="hero-card">
        <p>学习首页</p>
        <h1>{{ auth.displayName }}，今天从一个明确任务开始</h1>
        <span>首页只保留最需要马上处理的学习动作。报告、画像、资源和账号设置已经收进右上角用户名菜单。</span>
        <div class="hero-actions">
          <el-button type="primary" @click="router.push('/ai')">开始答疑</el-button>
          <el-button @click="router.push('/wrong-questions/list')">复盘错题</el-button>
        </div>
      </div>

      <div class="goal-card">
        <span>今日目标</span>
        <strong>{{ todayProgress }}%</strong>
        <el-progress :percentage="todayProgress" :stroke-width="10" />
        <p>{{ nextTask?.description || '完成一组练习并复盘未掌握内容' }}</p>
      </div>
    </section>

    <section class="stat-strip">
      <article>
        <span>综合能力</span>
        <strong>{{ ability }}%</strong>
      </article>
      <article>
        <span>知识掌握</span>
        <strong>{{ mastery }}%</strong>
      </article>
      <article>
        <span>累计学习</span>
        <strong>{{ totalHours }}h</strong>
      </article>
      <article>
        <span>当前计划</span>
        <strong>{{ personalOverview.studyPlanCount || plans.length }}</strong>
      </article>
    </section>

    <section class="content-grid">
      <div class="panel-block">
        <div class="section-head">
          <div>
            <h2>今日学习任务</h2>
            <p>按照任务顺序完成，避免在多个功能之间来回切换。</p>
          </div>
          <el-button text @click="router.push('/study-plans')">
            全部任务
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div v-if="todayTasks.length" class="task-list">
          <article v-for="(plan, index) in todayTasks" :key="plan.planId || index" class="task-row">
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ plan.title || '学习任务' }}</strong>
              <p>{{ plan.taskTypeName || '任务' }} · {{ plan.knowledgePoint || '基础知识' }}</p>
            </div>
            <el-tag :type="Number(plan.finishStatus) === 1 ? 'success' : 'info'">
              {{ Number(plan.finishStatus) === 1 ? '已完成' : '待完成' }}
            </el-tag>
            <el-button round @click="router.push('/study-plans')">开始</el-button>
          </article>
        </div>
        <el-empty v-else description="暂无学习任务">
          <el-button type="primary" @click="router.push('/study-plans')">创建方案</el-button>
        </el-empty>
      </div>

      <aside class="side-stack">
        <section class="panel-block">
          <h2>下一步</h2>
          <button v-for="item in nextActions" :key="item.title" class="action-row" type="button" @click="router.push(item.path)">
            <span><el-icon><component :is="item.icon" /></el-icon></span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.desc }}</p>
            </div>
          </button>
        </section>

        <section class="panel-block">
          <div class="section-head compact">
            <h2>薄弱点</h2>
            <el-button text @click="router.push('/profile')">画像</el-button>
          </div>
          <div v-if="weakPoints.length" class="weak-list">
            <div v-for="(point, index) in weakPoints" :key="point">
              <span>{{ point }}</span>
              <el-progress :percentage="Math.max(20, 84 - index * 12)" :show-text="false" />
            </div>
          </div>
          <p v-else class="muted-copy">完成一次测评后会生成薄弱知识点。</p>
        </section>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 22px;
}

.hero-card,
.goal-card,
.stat-strip,
.panel-block {
  border: 1px solid var(--line);
  border-radius: 22px;
  background: var(--panel);
}

.hero-card {
  min-height: 270px;
  padding: 38px;
}

.hero-card p {
  margin: 0 0 14px;
  color: var(--muted);
  font-weight: 800;
}

.hero-card h1 {
  max-width: 920px;
  margin: 0;
  color: var(--text);
  font-size: clamp(34px, 4.6vw, 56px);
  line-height: 1.12;
  letter-spacing: 0;
}

.hero-card span {
  display: block;
  max-width: 840px;
  margin-top: 22px;
  color: var(--muted);
  font-size: 17px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}

.goal-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 34px;
}

.goal-card span,
.stat-strip span {
  color: var(--muted);
  font-weight: 800;
}

.goal-card strong {
  margin: 20px 0 18px;
  color: var(--text);
  font-size: 58px;
  line-height: 1;
}

.goal-card p {
  margin: 22px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
}

.stat-strip article {
  min-height: 120px;
  padding: 26px 34px;
}

.stat-strip article + article {
  border-left: 1px solid var(--line);
}

.stat-strip strong {
  display: block;
  margin-top: 16px;
  color: var(--text);
  font-size: 38px;
  line-height: 1;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.8fr);
  gap: 22px;
}

.panel-block {
  padding: 26px;
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
  font-size: 26px;
  line-height: 1.25;
}

.section-head p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 16px;
}

.task-list,
.side-stack,
.weak-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.task-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  min-height: 80px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 18px;
}

.task-row > span,
.action-row > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  color: var(--text);
  background: var(--primary-soft);
  font-weight: 800;
}

.task-row strong,
.action-row strong {
  display: block;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-row p,
.action-row p,
.muted-copy {
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 82px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.action-row:hover {
  background: var(--primary-soft);
}

.weak-list div {
  display: grid;
  grid-template-columns: minmax(96px, 150px) minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.weak-list span {
  overflow: hidden;
  color: var(--text);
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .hero-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-card,
  .goal-card,
  .panel-block {
    padding: 20px;
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

  .task-row {
    grid-template-columns: 44px minmax(0, 1fr);
  }
}
</style>
