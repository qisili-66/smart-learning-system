<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminUserApi } from '@/api/admin'
import { formatDateTime, pageList, pageTotal, roleLabel, statusMeta } from '@/utils/format'

const loading = ref(false)
const page = ref({})
const detailVisible = ref(false)
const currentUser = ref({})

const query = reactive({
  username: '',
  role: '',
  status: '',
  pageNum: 1,
  pageSize: 10
})

function cleanParams() {
  return {
    username: query.username || undefined,
    role: query.role === '' ? undefined : query.role,
    status: query.status === '' ? undefined : query.status,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }
}

async function loadUsers() {
  loading.value = true
  try {
    page.value = await adminUserApi.list(cleanParams())
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadUsers()
}

function resetQuery() {
  Object.assign(query, { username: '', role: '', status: '', pageNum: 1, pageSize: 10 })
  loadUsers()
}

async function openDetail(row) {
  currentUser.value = await adminUserApi.detail(row.userId)
  detailVisible.value = true
}

async function changeStatus(row) {
  try {
    await adminUserApi.updateStatus(row.userId, row.status)
    ElMessage.success('状态已更新')
  } catch {
    loadUsers()
  }
}

async function resetPassword(row) {
  try {
    await ElMessageBox.confirm(`确认将 ${row.username} 的密码重置为 123456？`, '重置密码', {
      type: 'warning',
      confirmButtonText: '重置',
      cancelButtonText: '取消'
    })
    await adminUserApi.resetPassword(row.userId)
    ElMessage.success('密码已重置为 123456')
  } catch {
    // 用户取消或接口已由拦截器提示。
  }
}

onMounted(loadUsers)
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-title">
      <div>
        <h1>用户管理</h1>
        <p>已接通 /admin/users 列表、详情、状态修改和密码重置接口。</p>
      </div>
    </div>

    <section class="admin-panel">
      <el-form class="admin-toolbar" :model="query" inline @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model.trim="query.username" placeholder="请输入账号" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" placeholder="全部" clearable class="admin-filter">
            <el-option label="学生" :value="1" />
            <el-option label="管理员" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable class="admin-filter">
            <el-option label="正常" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
          <el-button :icon="'RefreshLeft'" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="admin-panel">
      <el-table v-loading="loading" :data="pageList(page)" @row-dblclick="openDetail">
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="110" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="grade" label="学段" width="100" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 2 ? 'warning' : 'primary'">{{ roleLabel(row.role) }}</el-tag>
          </template>
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
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
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
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="用户详情" size="360px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户 ID">{{ currentUser.userId }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ currentUser.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ currentUser.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleLabel(currentUser.role) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(currentUser.status).type">{{ statusMeta(currentUser.status).text }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="学段">{{ currentUser.grade || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学科">{{ currentUser.subject || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ currentUser.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(currentUser.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>
