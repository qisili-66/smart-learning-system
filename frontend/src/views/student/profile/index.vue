<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { profileApi } from '@/api/student'
import { useAuthStore } from '@/store/auth'
import { GRADES, SUBJECTS, asPercent, formatDateTime } from '@/utils/format'

const AVATAR_KEY = 'smart_learning_avatar'

const router = useRouter()
const auth = useAuthStore()
const activeTab = ref('subjects')
const loading = ref(false)
const saving = ref(false)
const profile = ref({})
const profileOverview = ref({ metrics: {}, recommendations: [] })
const weakPoints = ref([])
const avatarUrl = ref(localStorage.getItem(AVATAR_KEY) || '')
const uploadNote = ref('')

const userForm = reactive({
  realName: '',
  grade: '',
  subject: '',
  phone: ''
})

const profileForm = reactive({
  preference: '',
  customWeakPointsText: ''
})

const metrics = computed(() => profileOverview.value?.metrics || {})
const recommendations = computed(() => Array.isArray(profileOverview.value?.recommendations) ? profileOverview.value.recommendations : [])
const ability = computed(() => asPercent(profile.value.abilityScore ?? metrics.value.abilityScore, 70))
const mastery = computed(() => asPercent(profile.value.knowledgeMastery ?? metrics.value.knowledgeMastery, 64))
const completionRate = computed(() => asPercent(metrics.value.recordCompletionRate, 0))
const wrongMasteryRate = computed(() => asPercent(metrics.value.wrongMasteryRate, 0))
const hasLearningEvidence = computed(() => {
  const metricValues = [
    metrics.value.activeDays14,
    metrics.value.studyRecordCount,
    metrics.value.recordCount,
    metrics.value.assessmentCount,
    metrics.value.wrongQuestionCount,
    metrics.value.totalDuration,
    metrics.value.totalStudyDurationMinutes,
    completionRate.value,
    wrongMasteryRate.value
  ]
  return weakPoints.value.length > 0 || metricValues.some((value) => Number(value || 0) > 0)
})

const weakPointItems = computed(() => weakPoints.value.map((point) => ({
  subject: inferSubject(point),
  point
})))

const subjectProfiles = computed(() => SUBJECTS.map((subject, index) => {
  const subjectWeakPoints = weakPointItems.value.filter((item) => item.subject === subject).map((item) => item.point)
  if (!hasLearningEvidence.value && !subjectWeakPoints.length) {
    return {
      subject,
      score: null,
      weakPoints: [],
      priority: '待诊断',
      advice: subjectAdvice(subject, [], null)
    }
  }
  const penalty = subjectWeakPoints.length * 12 + (index % 3) * 3
  const score = Math.max(0, Math.min(96, mastery.value - penalty + 8))
  const priority = subjectWeakPoints.length >= 2 || score < 55 ? '高' : subjectWeakPoints.length === 1 || score < 72 ? '中' : '低'
  return {
    subject,
    score,
    weakPoints: subjectWeakPoints,
    priority,
    advice: subjectAdvice(subject, subjectWeakPoints, score)
  }
}))

const focusSubjects = computed(() => subjectProfiles.value.filter((item) => !['低', '待诊断'].includes(item.priority)).slice(0, 4))
const aiAdvice = computed(() => {
  const mapped = recommendations.value.map((item) => {
    if (typeof item === 'string') return item
    if (item.type === 'review_weak_points') return `先复盘薄弱点：${item.content || weakPoints.value.join('、')}`
    if (item.type === 'finish_pending_tasks') return '任务完成率偏低，建议今天先清理未完成任务，再开始新练习。'
    if (item.type === 'accuracy_training') return '测评正确率偏低，建议按学科做 20 分钟专项训练。'
    if (item.type === 'increase_study_duration') return '近 14 天有效学习时长偏少，建议每个学习日稳定 30 分钟以上。'
    return item.content || ''
  }).filter(Boolean)
  if (mapped.length) return mapped
  return ['AI 建议先完成一次测评或错题复盘，系统会据此刷新更精确的学科画像。']
})

function normalizeProfileResponse(data) {
  profileOverview.value = data || { metrics: {}, recommendations: [] }
  profile.value = data?.profile || data || {}
  return profile.value
}

function inferSubject(point) {
  const text = String(point || '')
  const matched = SUBJECTS.find((subject) => text.includes(subject))
  if (matched) return matched
  if (/词|语法|阅读|写作|英语|时态|从句/i.test(text)) return '英语'
  if (/函数|方程|几何|代数|概率|数学/i.test(text)) return '数学'
  if (/古诗|作文|文言|现代文|语文/.test(text)) return '语文'
  if (/力|电|光|热|压强|浮力|物理/.test(text)) return '物理'
  if (/化学|元素|方程式|酸|碱|盐|实验/.test(text)) return '化学'
  if (/细胞|遗传|生态|生物/.test(text)) return '生物'
  if (/历史|朝代|近代|世界史/.test(text)) return '历史'
  if (/地理|地图|气候|区域/.test(text)) return '地理'
  if (/法律|权利|义务|道德|法治/.test(text)) return '道德与法治'
  return auth.user?.subject || '数学'
}

function subjectAdvice(subject, points, score) {
  if (points.length) return `AI 建议先处理 ${points.slice(0, 2).join('、')}，再做同类题巩固。`
  if (score === null) return `${subject}暂无学习或测评记录，建议先完成一次诊断测评，再生成优先级。`
  if (score < 70) return `AI 建议本周给${subject}安排一次诊断测评，确认薄弱知识点。`
  return `${subject}当前较稳定，建议保持错题复盘和每周小测。`
}

async function loadProfile() {
  loading.value = true
  try {
    const [profileData, weakData] = await Promise.all([
      profileApi.my().catch(() => ({})),
      profileApi.weakPoints({ limit: 16 }).catch(() => [])
    ])
    const currentProfile = normalizeProfileResponse(profileData)
    weakPoints.value = Array.isArray(weakData) ? weakData : currentProfile.weakPoints || []
    profileForm.preference = currentProfile.preference || ''
    profileForm.customWeakPointsText = weakPoints.value.join('，')
    Object.assign(userForm, {
      realName: auth.user?.realName || '',
      grade: auth.user?.grade || '',
      subject: auth.user?.subject || '',
      phone: auth.user?.phone || ''
    })
  } finally {
    loading.value = false
  }
}

async function refreshProfile() {
  loading.value = true
  try {
    normalizeProfileResponse(await profileApi.refresh())
    const weakData = await profileApi.weakPoints({ limit: 16 }).catch(() => [])
    weakPoints.value = Array.isArray(weakData) ? weakData : profile.value.weakPoints || []
    ElMessage.success('画像已刷新')
  } finally {
    loading.value = false
  }
}

async function saveUserInfo() {
  saving.value = true
  try {
    await auth.updateInfo({ ...userForm })
    ElMessage.success('个人信息已保存')
  } finally {
    saving.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const points = profileForm.customWeakPointsText
      .split(/[，,]/)
      .map((item) => item.trim())
      .filter(Boolean)
    normalizeProfileResponse(await profileApi.updateMy({
      preference: profileForm.preference,
      customWeakPoints: points,
      reason: '学生在个人中心手动修正'
    }))
    weakPoints.value = points
    ElMessage.success('画像修正已保存')
  } finally {
    saving.value = false
  }
}

function beforeAvatarUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isSmallEnough = file.size / 1024 / 1024 < 2
  if (!isImage) ElMessage.warning('请上传图片文件')
  if (!isSmallEnough) ElMessage.warning('图片大小需小于 2MB')
  return isImage && isSmallEnough
}

function handleAvatarUpload(file) {
  if (!beforeAvatarUpload(file.raw)) return false
  const reader = new FileReader()
  reader.onload = () => {
    avatarUrl.value = String(reader.result || '')
    localStorage.setItem(AVATAR_KEY, avatarUrl.value)
    ElMessage.success('头像已更新到本地预览')
  }
  reader.readAsDataURL(file.raw)
  return false
}

function handleProfileFile(file) {
  const raw = file.raw
  if (!raw) return false
  const reader = new FileReader()
  reader.onload = () => {
    const text = String(reader.result || '')
    const points = text
      .split(/[\n，,;；]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, 20)
    if (!points.length) {
      ElMessage.warning('未识别到可导入的薄弱点文本')
      return
    }
    profileForm.customWeakPointsText = points.join('，')
    uploadNote.value = `已从 ${raw.name} 识别 ${points.length} 个候选薄弱点，保存画像后生效。`
    ElMessage.success('画像材料已读取')
  }
  reader.readAsText(raw, 'UTF-8')
  return false
}

function askAi(subjectName) {
  router.push({ path: '/ai', query: { subject: subjectName } })
}

onMounted(loadProfile)
</script>

<template>
  <div class="page" v-loading="loading">
    <section class="profile-hero panel">
      <div class="identity">
        <el-avatar :size="76" :src="avatarUrl">{{ auth.displayName.slice(0, 1) }}</el-avatar>
        <div>
          <h1>{{ auth.displayName }}</h1>
          <p>{{ auth.user?.grade || '未设置学段' }} · {{ auth.user?.subject || '全科' }}</p>
          <div class="identity-actions">
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handleAvatarUpload">
              <el-button size="small" :icon="'UploadFilled'">上传头像</el-button>
            </el-upload>
            <el-button size="small" :icon="'Refresh'" @click="refreshProfile">刷新画像</el-button>
          </div>
        </div>
      </div>
      <div class="hero-metrics">
        <article>
          <span>综合能力</span>
          <strong>{{ ability }}%</strong>
        </article>
        <article>
          <span>知识掌握</span>
          <strong>{{ mastery }}%</strong>
        </article>
        <article>
          <span>任务完成</span>
          <strong>{{ completionRate }}%</strong>
        </article>
        <article>
          <span>错题掌握</span>
          <strong>{{ wrongMasteryRate }}%</strong>
        </article>
      </div>
    </section>

    <section class="ai-panel panel panel-body">
      <div>
        <h2>AI 学习建议</h2>
        <p>建议来自测评、错题、学习记录和手动修正画像的综合分析。</p>
      </div>
      <div class="ai-advice-list">
        <p v-for="item in aiAdvice" :key="item">{{ item }}</p>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="panel profile-tabs">
      <el-tab-pane label="学科画像" name="subjects">
        <div class="subject-grid panel-body">
          <article v-for="item in subjectProfiles" :key="item.subject" class="subject-card">
            <div class="subject-head">
              <div>
                <h2>{{ item.subject }}</h2>
                <span>优先级：{{ item.priority }}</span>
              </div>
              <strong>{{ item.score === null ? '未评估' : `${item.score}%` }}</strong>
            </div>
            <el-progress :percentage="item.score || 0" :status="item.score === null ? undefined : item.score < 60 ? 'warning' : 'success'" />
            <div class="weak-tags">
              <el-tag v-for="point in item.weakPoints.slice(0, 3)" :key="point" type="warning">{{ point }}</el-tag>
              <el-tag v-if="!item.weakPoints.length" type="info">{{ item.score === null ? '等待诊断' : '暂无明显薄弱点' }}</el-tag>
            </div>
            <p>{{ item.advice }}</p>
            <el-button text :icon="'ChatLineRound'" @click="askAi(item.subject)">向 AI 追问</el-button>
          </article>
        </div>
      </el-tab-pane>

      <el-tab-pane label="重点学科" name="focus">
        <div class="panel-body list-stack">
          <div v-for="item in focusSubjects" :key="item.subject" class="knowledge-row">
            <div>
              <strong>{{ item.subject }}</strong>
              <span>{{ item.weakPoints.join('、') || '建议做一次诊断测评' }}</span>
            </div>
            <div>
              <p>{{ item.advice }}</p>
              <el-progress :percentage="item.score" />
            </div>
            <el-button :icon="'ChatLineRound'" @click="askAi(item.subject)">AI 建议</el-button>
          </div>
          <el-empty v-if="!focusSubjects.length" description="暂无高优先级学科" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="学习习惯" name="habit">
        <div class="panel-body habit-grid">
          <div class="habit-card">
            <span>偏好</span>
            <strong>{{ profile.preference || '未设置' }}</strong>
          </div>
          <div class="habit-card">
            <span>习惯</span>
            <strong>{{ profile.studyHabit || '稳定学习' }}</strong>
          </div>
          <div class="habit-card">
            <span>活跃天数</span>
            <strong>{{ metrics.activeDays14 || 0 }} 天</strong>
          </div>
          <div class="habit-card">
            <span>更新时间</span>
            <strong>{{ formatDateTime(profile.updateTime) }}</strong>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="资料与修正" name="edit">
        <div class="edit-grid panel-body">
          <el-form label-position="top" :model="userForm">
            <h2>基础信息</h2>
            <el-form-item label="姓名">
              <el-input v-model="userForm.realName" />
            </el-form-item>
            <el-form-item label="学段">
              <el-select v-model="userForm.grade" class="full">
                <el-option v-for="grade in GRADES" :key="grade" :label="grade" :value="grade" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认学科">
              <el-select v-model="userForm.subject" class="full" clearable>
                <el-option v-for="item in SUBJECTS" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="userForm.phone" />
            </el-form-item>
            <el-button type="primary" :loading="saving" @click="saveUserInfo">保存信息</el-button>
          </el-form>

          <el-form label-position="top" :model="profileForm">
            <h2>学习画像</h2>
            <el-form-item label="学习偏好">
              <el-input v-model="profileForm.preference" placeholder="例：视频讲解、分步推导" />
            </el-form-item>
            <el-form-item label="薄弱知识点">
              <el-input
                v-model="profileForm.customWeakPointsText"
                type="textarea"
                :rows="5"
                placeholder="用逗号分隔多个知识点，可写成：英语时态，数学函数，物理电路"
              />
            </el-form-item>
            <div class="upload-row">
              <el-upload :auto-upload="false" :show-file-list="false" accept=".txt,.csv" :on-change="handleProfileFile">
                <el-button :icon="'Upload'">上传画像材料</el-button>
              </el-upload>
              <span>{{ uploadNote || '支持上传 txt/csv，本地读取后填入薄弱点。' }}</span>
            </div>
            <el-button type="primary" :loading="saving" @click="saveProfile">保存画像</el-button>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.profile-hero {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  align-items: center;
  gap: 24px;
  padding: 24px;
}

.identity {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.identity h1 {
  margin: 0 0 6px;
  font-size: 24px;
  letter-spacing: 0;
}

.identity p,
.ai-panel p,
.subject-card p,
.knowledge-row span,
.habit-card span,
.upload-row span {
  margin: 0;
  color: var(--muted);
}

.identity-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.hero-metrics,
.subject-grid,
.habit-grid,
.edit-grid {
  display: grid;
  gap: 16px;
}

.hero-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.hero-metrics article,
.subject-card,
.habit-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--panel);
}

.hero-metrics span,
.subject-head span {
  color: var(--muted);
  font-size: 13px;
}

.hero-metrics strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  line-height: 1;
}

.ai-panel {
  display: grid;
  grid-template-columns: minmax(220px, 0.55fr) minmax(0, 1fr);
  gap: 16px;
}

.ai-panel h2,
.subject-card h2,
.edit-grid h2 {
  margin: 0 0 8px;
  font-size: 18px;
}

.ai-advice-list {
  display: grid;
  gap: 8px;
}

.ai-advice-list p {
  padding: 12px 14px;
  border-radius: 12px;
  color: var(--text);
  background: var(--primary-soft);
  line-height: 1.6;
}

.profile-tabs {
  padding: 0 18px 18px;
}

.subject-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.subject-card {
  display: flex;
  min-height: 250px;
  flex-direction: column;
  gap: 12px;
}

.subject-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.subject-head strong {
  font-size: 24px;
}

.weak-tags,
.upload-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.subject-card .el-button {
  align-self: flex-start;
  margin-top: auto;
}

.knowledge-row {
  display: grid;
  grid-template-columns: minmax(150px, 220px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
}

.knowledge-row p {
  margin: 0 0 8px;
  color: var(--text);
  line-height: 1.6;
}

.habit-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.habit-card strong {
  display: block;
  margin-top: 12px;
  font-size: 20px;
  line-height: 1.35;
}

.edit-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.upload-row {
  align-items: center;
  margin-bottom: 16px;
}

.upload-row span {
  font-size: 13px;
}

.full {
  width: 100%;
}

@media (max-width: 1180px) {
  .hero-metrics,
  .subject-grid,
  .habit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .profile-hero,
  .ai-panel,
  .edit-grid,
  .knowledge-row {
    grid-template-columns: 1fr;
  }

  .knowledge-row .el-button {
    justify-self: flex-start;
  }
}

@media (max-width: 620px) {
  .profile-hero,
  .profile-tabs {
    padding: 14px;
  }

  .identity {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-metrics,
  .subject-grid,
  .habit-grid {
    grid-template-columns: 1fr;
  }

  .subject-card {
    min-height: auto;
  }
}
</style>
