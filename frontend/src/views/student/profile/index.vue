<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { profileApi } from '@/api/student'
import { useAuthStore } from '@/store/auth'
import { GRADES, SUBJECTS, asPercent, formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const activeTab = ref('overview')
const loading = ref(false)
const saving = ref(false)
const profile = ref({})
const weakPoints = ref([])

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

const ability = computed(() => asPercent(profile.value.abilityScore, 70))
const mastery = computed(() => asPercent(profile.value.knowledgeMastery, 64))

async function loadProfile() {
  loading.value = true
  try {
    const [profileData, weakData] = await Promise.all([
      profileApi.my().catch(() => ({})),
      profileApi.weakPoints({ limit: 8 }).catch(() => [])
    ])
    profile.value = profileData || {}
    weakPoints.value = Array.isArray(weakData) ? weakData : []
    profileForm.preference = profile.value.preference || ''
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
    profile.value = await profileApi.updateMy({
      preference: profileForm.preference,
      customWeakPoints: points
    })
    weakPoints.value = points
    ElMessage.success('画像修正已保存')
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="page" v-loading="loading">
    <section class="profile-hero panel">
      <div class="identity">
        <el-avatar :size="72">{{ auth.displayName.slice(0, 1) }}</el-avatar>
        <div>
          <h1>{{ auth.displayName }}</h1>
          <p>{{ auth.user?.grade || '未设置学段' }} · {{ auth.user?.subject || '全科' }}</p>
        </div>
      </div>
      <div class="score-ring">
        <el-progress type="dashboard" :percentage="ability" />
        <span>综合能力</span>
      </div>
      <div class="tag-cloud">
        <el-tag v-for="point in weakPoints.slice(0, 6)" :key="point" type="warning">{{ point }}</el-tag>
        <el-tag v-if="!weakPoints.length" type="info">暂无薄弱标签</el-tag>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="panel profile-tabs">
      <el-tab-pane label="能力总览" name="overview">
        <div class="three-col panel-body">
          <div class="ability-card">
            <span>学习能力</span>
            <el-progress :percentage="ability" />
          </div>
          <div class="ability-card">
            <span>知识掌握</span>
            <el-progress :percentage="mastery" status="success" />
          </div>
          <div class="ability-card">
            <span>学习习惯</span>
            <el-progress :percentage="profile.studyHabit ? 76 : 48" status="warning" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="知识掌握" name="knowledge">
        <div class="panel-body list-stack">
          <div v-for="(point, index) in weakPoints" :key="point" class="knowledge-row">
            <div>
              <strong>{{ point }}</strong>
              <span>建议优先复习</span>
            </div>
            <el-progress :percentage="Math.max(18, 72 - index * 8)" />
            <el-tag type="warning">薄弱</el-tag>
          </div>
          <el-empty v-if="!weakPoints.length" description="暂无知识点数据" />
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
            <span>更新时间</span>
            <strong>{{ formatDateTime(profile.updateTime) }}</strong>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="画像修正" name="edit">
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
                placeholder="用逗号分隔多个知识点"
              />
            </el-form-item>
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
  grid-template-columns: minmax(240px, 1fr) 180px minmax(220px, 1fr);
  align-items: center;
  gap: 24px;
  padding: 24px;
}

.identity {
  display: flex;
  align-items: center;
  gap: 16px;
}

.identity h1 {
  margin: 0 0 6px;
  font-size: 24px;
  letter-spacing: 0;
}

.identity p {
  margin: 0;
  color: var(--muted);
}

.score-ring {
  text-align: center;
}

.score-ring span {
  display: block;
  margin-top: -8px;
  color: var(--muted);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.profile-tabs {
  padding: 0 18px 18px;
}

.ability-card,
.habit-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #ffffff;
}

.ability-card span,
.habit-card span {
  display: block;
  margin-bottom: 12px;
  color: var(--muted);
}

.habit-card strong {
  font-size: 20px;
}

.knowledge-row {
  display: grid;
  grid-template-columns: minmax(150px, 240px) 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
}

.knowledge-row span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
}

.habit-grid,
.edit-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.edit-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.edit-grid h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.full {
  width: 100%;
}

@media (max-width: 920px) {
  .profile-hero,
  .habit-grid,
  .edit-grid {
    grid-template-columns: 1fr;
  }

  .tag-cloud {
    justify-content: flex-start;
  }

  .knowledge-row {
    grid-template-columns: 1fr;
  }
}
</style>
