<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminResourceApi } from '@/api/admin'
import {
  RESOURCE_TYPES,
  SUBJECTS,
  formatDateTime,
  formatFileSize,
  pageList,
  pageTotal,
  resourceTypeLabel,
  statusMeta
} from '@/utils/format'

const formRef = ref()
const uploadRef = ref()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const page = ref({})

const query = reactive({
  subject: '',
  resourceType: '',
  knowledgePoint: '',
  pageNum: 1,
  pageSize: 10
})

const form = reactive(emptyForm())

const rules = {
  resourceName: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  resourceType: [{ required: true, message: '请选择资源类型', trigger: 'change' }]
}

function emptyForm() {
  return {
    resourceId: null,
    resourceName: '',
    resourceType: 1,
    subject: '',
    knowledgePoint: '',
    textbookVersion: '',
    fileUrl: '',
    status: 1,
    file: null,
    fileName: ''
  }
}

function cleanParams() {
  return {
    subject: query.subject || undefined,
    resourceType: query.resourceType === '' ? undefined : query.resourceType,
    knowledgePoint: query.knowledgePoint || undefined,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }
}

async function loadResources() {
  loading.value = true
  try {
    page.value = await adminResourceApi.list(cleanParams())
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadResources()
}

function resetQuery() {
  Object.assign(query, { subject: '', resourceType: '', knowledgePoint: '', pageNum: 1, pageSize: 10 })
  loadResources()
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  nextTick(() => uploadRef.value?.clearFiles())
}

function openEdit(row) {
  Object.assign(form, emptyForm(), row, { file: null, fileName: '' })
  dialogVisible.value = true
  nextTick(() => uploadRef.value?.clearFiles())
}

function handleFileChange(file) {
  form.file = file.raw
  form.fileName = file.name
}

function handleFileRemove() {
  form.file = null
  form.fileName = ''
}

async function submitForm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      resourceName: form.resourceName,
      resourceType: form.resourceType,
      subject: form.subject,
      knowledgePoint: form.knowledgePoint,
      textbookVersion: form.textbookVersion,
      fileUrl: form.fileUrl,
      status: form.status
    }

    if (form.resourceId) {
      await adminResourceApi.update(form.resourceId, { ...payload, resourceId: form.resourceId })
      ElMessage.success('资源已更新')
    } else {
      const created = await adminResourceApi.create({ ...payload, file: form.file })
      if (created?.resourceId && (payload.textbookVersion || payload.fileUrl || payload.status !== 1)) {
        await adminResourceApi.update(created.resourceId, { ...created, ...payload })
      }
      ElMessage.success('资源已新增')
    }
    dialogVisible.value = false
    loadResources()
  } finally {
    submitting.value = false
  }
}

async function changeStatus(row) {
  try {
    await adminResourceApi.updateStatus(row.resourceId, row.status)
    ElMessage.success('状态已更新')
  } catch {
    loadResources()
  }
}

async function removeResource(row) {
  try {
    await ElMessageBox.confirm(`确认删除资源「${row.resourceName}」？`, '删除资源', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await adminResourceApi.remove(row.resourceId)
    ElMessage.success('资源已删除')
    loadResources()
  } catch {
    // 用户取消或接口已由拦截器提示。
  }
}

onMounted(loadResources)
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-title">
      <div>
        <h1>资源管理</h1>
        <p>列表复用 /learning-resources，新增、修改、状态和删除接入 /admin/learning-resources。</p>
      </div>
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新增资源</el-button>
    </div>

    <section class="admin-panel">
      <el-form class="admin-toolbar" :model="query" inline @submit.prevent>
        <el-form-item label="学科">
          <el-select v-model="query.subject" placeholder="全部" clearable class="admin-filter">
            <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.resourceType" placeholder="全部" clearable class="admin-filter">
            <el-option
              v-for="(label, value) in RESOURCE_TYPES"
              :key="value"
              :label="label"
              :value="Number(value)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model.trim="query.knowledgePoint" placeholder="知识点关键词" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
          <el-button :icon="'RefreshLeft'" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="admin-panel">
      <el-table v-loading="loading" :data="pageList(page)">
        <el-table-column prop="resourceId" label="ID" width="80" />
        <el-table-column prop="resourceName" label="资源名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="subject" label="学科" width="90" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column prop="knowledgePoint" label="知识点" min-width="140" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              active-text="正常"
              inactive-text="停用"
              inline-prompt
              @change="changeStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeResource(row)">删除</el-button>
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
          @size-change="loadResources"
          @current-change="loadResources"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.resourceId ? '编辑资源' : '新增资源'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="资源名称" prop="resourceName">
          <el-input v-model.trim="form.resourceName" placeholder="请输入资源名称" />
        </el-form-item>
        <el-form-item label="资源类型" prop="resourceType">
          <el-select v-model="form.resourceType" class="admin-full">
            <el-option
              v-for="(label, value) in RESOURCE_TYPES"
              :key="value"
              :label="label"
              :value="Number(value)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学科">
          <el-select v-model="form.subject" class="admin-full" clearable>
            <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model.trim="form.knowledgePoint" placeholder="如：二次函数" />
        </el-form-item>
        <el-form-item label="教材版本">
          <el-input v-model.trim="form.textbookVersion" placeholder="如：人教版" />
        </el-form-item>
        <el-form-item label="文件地址">
          <el-input v-model.trim="form.fileUrl" placeholder="可填写外部资源链接" />
        </el-form-item>
        <el-form-item v-if="!form.resourceId" label="上传文件">
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button :icon="'Upload'">选择文件</el-button>
            <template #tip>
              <span class="admin-upload-tip">当前后端保存文件元数据，真实文件存储后续扩展。</span>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">{{ statusMeta(1).text }}</el-radio-button>
            <el-radio-button :label="0">{{ statusMeta(0).text }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
