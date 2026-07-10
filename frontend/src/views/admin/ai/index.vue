<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminAiApi } from '@/api/admin'

const loading = ref(false)
const savingRules = ref(false)
const savingRecommend = ref(false)
const models = ref([])

const qaRules = reactive({
  maxAnswerLength: 800,
  enableStepGuide: true,
  strictExamMode: true,
  sensitivePolicy: '遇到疑似作业原题时先提示思路，再由学生确认是否需要完整解答'
})

const recommendConfig = reactive({
  strategy: 'weak-point-first',
  dailyLimit: 5,
  weakPointWeight: 70,
  resourceFreshnessWeight: 30
})

async function loadModels() {
  loading.value = true
  try {
    const data = await adminAiApi.models()
    models.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

async function saveQaRules() {
  savingRules.value = true
  try {
    await adminAiApi.updateQaRules({ ...qaRules })
    ElMessage.success('答疑规则已保存')
  } finally {
    savingRules.value = false
  }
}

async function saveRecommendConfig() {
  savingRecommend.value = true
  try {
    await adminAiApi.updateRecommendConfig({ ...recommendConfig })
    ElMessage.success('推荐配置已保存')
  } finally {
    savingRecommend.value = false
  }
}

onMounted(loadModels)
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <div class="admin-page-title">
      <div>
        <h1>AI 配置</h1>
        <p>已接通模型列表、答疑规则配置和推荐策略配置接口。</p>
      </div>
      <el-button :icon="'Refresh'" @click="loadModels">刷新模型</el-button>
    </div>

    <section class="admin-panel">
      <div class="admin-panel-head">
        <h2>模型列表</h2>
        <el-tag>{{ models.length }} 个模型</el-tag>
      </div>
      <el-table :data="models" empty-text="暂无模型">
        <el-table-column prop="modelName" label="模型名称" min-width="160" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">
            <el-tag type="success">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="admin-two-col">
      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>答疑规则</h2>
        </div>
        <el-form :model="qaRules" label-width="126px">
          <el-form-item label="答案长度上限">
            <el-input-number v-model="qaRules.maxAnswerLength" :min="100" :max="3000" :step="100" />
          </el-form-item>
          <el-form-item label="分步引导">
            <el-switch v-model="qaRules.enableStepGuide" />
          </el-form-item>
          <el-form-item label="考试模式约束">
            <el-switch v-model="qaRules.strictExamMode" />
          </el-form-item>
          <el-form-item label="策略说明">
            <el-input v-model="qaRules.sensitivePolicy" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingRules" @click="saveQaRules">保存规则</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="admin-panel">
        <div class="admin-panel-head">
          <h2>推荐配置</h2>
        </div>
        <el-form :model="recommendConfig" label-width="126px">
          <el-form-item label="推荐策略">
            <el-select v-model="recommendConfig.strategy" class="admin-full">
              <el-option label="薄弱点优先" value="weak-point-first" />
              <el-option label="测评结果优先" value="assessment-first" />
              <el-option label="学习计划优先" value="plan-first" />
            </el-select>
          </el-form-item>
          <el-form-item label="每日推荐数">
            <el-input-number v-model="recommendConfig.dailyLimit" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="薄弱点权重">
            <el-slider v-model="recommendConfig.weakPointWeight" :min="0" :max="100" />
          </el-form-item>
          <el-form-item label="资源新鲜度">
            <el-slider v-model="recommendConfig.resourceFreshnessWeight" :min="0" :max="100" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingRecommend" @click="saveRecommendConfig">保存配置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </section>
  </div>
</template>
