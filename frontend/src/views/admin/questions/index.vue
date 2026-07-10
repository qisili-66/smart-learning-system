<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminQuestionApi } from '@/api/admin'
import {
  DIFFICULTY,
  QUESTION_TYPES,
  SUBJECTS,
  difficultyLabel,
  formatDateTime,
  pageList,
  pageTotal,
  questionTypeLabel
} from '@/utils/format'

const formRef = ref()
const importRef = ref()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const page = ref({})

const query = reactive({
  subject: '',
  difficulty: '',
  questionType: '',
  pageNum: 1,
  pageSize: 10
})

const form = reactive(emptyQuestion())

const rules = {
  subject: [{ required: true, message: '请选择学科', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  questionText: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
}

function emptyQuestion() {
  return {
    questionId: null,
    subject: '',
    knowledgePoint: '',
    difficulty: 1,
    questionType: 1,
    questionText: '',
    options: '',
    answer: '',
    analysis: '',
    scoringPoints: ''
  }
}

function cleanParams() {
  return {
    subject: query.subject || undefined,
    difficulty: query.difficulty === '' ? undefined : query.difficulty,
    questionType: query.questionType === '' ? undefined : query.questionType,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }
}

async function loadQuestions() {
  loading.value = true
  try {
    page.value = await adminQuestionApi.list(cleanParams())
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadQuestions()
}

function resetQuery() {
  Object.assign(query, { subject: '', difficulty: '', questionType: '', pageNum: 1, pageSize: 10 })
  loadQuestions()
}

function openCreate() {
  Object.assign(form, emptyQuestion())
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, emptyQuestion(), row)
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openScoring(row) {
  router.push(`/admin/questions/${row.questionId}/scoring`)
}

async function submitForm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = { ...form }
    if (form.questionId) {
      await adminQuestionApi.update(form.questionId, payload)
      ElMessage.success('题目已更新')
    } else {
      delete payload.questionId
      await adminQuestionApi.create(payload)
      ElMessage.success('题目已新增')
    }
    dialogVisible.value = false
    loadQuestions()
  } finally {
    submitting.value = false
  }
}

async function removeQuestion(row) {
  try {
    await ElMessageBox.confirm('确认删除当前题目？', '删除题目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await adminQuestionApi.remove(row.questionId)
    ElMessage.success('题目已删除')
    loadQuestions()
  } catch {
    // 用户取消或接口已由拦截器提示。
  }
}

async function handleImport(file) {
  try {
    const result = await adminQuestionApi.batchImport(file.raw)
    ElMessage.success(`导入完成：${result?.imported || 0} 条`)
    importRef.value?.clearFiles()
    loadQuestions()
  } catch {
    importRef.value?.clearFiles()
  }
}

onMounted(loadQuestions)
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-title">
      <div>
        <h1>题库管理</h1>
        <p>已接通 /admin/questions 的列表、增删改和批量导入接口。</p>
      </div>
      <div class="admin-title-actions">
        <el-upload
          ref="importRef"
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleImport"
        >
          <el-button :icon="'Upload'">批量导入</el-button>
        </el-upload>
        <el-button type="primary" :icon="'Plus'" @click="openCreate">新增题目</el-button>
      </div>
    </div>

    <section class="admin-panel">
      <el-form class="admin-toolbar" :model="query" inline @submit.prevent>
        <el-form-item label="学科">
          <el-select v-model="query.subject" placeholder="全部" clearable class="admin-filter">
            <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="query.difficulty" placeholder="全部" clearable class="admin-filter">
            <el-option v-for="(label, value) in DIFFICULTY" :key="value" :label="label" :value="Number(value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="query.questionType" placeholder="全部" clearable class="admin-filter">
            <el-option
              v-for="(label, value) in QUESTION_TYPES"
              :key="value"
              :label="label"
              :value="Number(value)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
          <el-button :icon="'RefreshLeft'" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="admin-panel">
      <el-table v-loading="loading" :data="pageList(page)">
        <el-table-column prop="questionId" label="ID" width="80" />
        <el-table-column prop="questionText" label="题干" min-width="260" show-overflow-tooltip />
        <el-table-column prop="subject" label="学科" width="90" />
        <el-table-column prop="knowledgePoint" label="知识点" min-width="130" show-overflow-tooltip />
        <el-table-column label="难度" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ difficultyLabel(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="110">
          <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="评分要点" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.scoringPoints ? 'success' : 'info'">
              {{ row.scoringPoints ? '已配置' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openScoring(row)">评分要点</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeQuestion(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="pageTotal(page)"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadQuestions"
          @current-change="loadQuestions"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.questionId ? '编辑题目' : '新增题目'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="学科" prop="subject">
              <el-select v-model="form.subject" class="admin-full">
                <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty" class="admin-full">
                <el-option v-for="(label, value) in DIFFICULTY" :key="value" :label="label" :value="Number(value)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="form.questionType" class="admin-full">
                <el-option
                  v-for="(label, value) in QUESTION_TYPES"
                  :key="value"
                  :label="label"
                  :value="Number(value)"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="知识点">
          <el-input v-model.trim="form.knowledgePoint" placeholder="请输入知识点" />
        </el-form-item>
        <el-form-item label="题干" prop="questionText">
          <el-input v-model.trim="form.questionText" type="textarea" :rows="4" placeholder="请输入题干" />
        </el-form-item>
        <el-form-item label="选项">
          <el-input
            v-model.trim="form.options"
            type="textarea"
            :rows="3"
            placeholder="选择题可用竖线分隔：A.选项一|B.选项二"
          />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model.trim="form.answer" type="textarea" :rows="2" placeholder="请输入标准答案" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model.trim="form.analysis" type="textarea" :rows="3" placeholder="请输入解析" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
