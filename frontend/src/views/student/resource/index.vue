<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { learningResourceApi, studyPlanApi } from '@/api/student'
import {
  RESOURCE_TYPES,
  SUBJECTS,
  formatDateTime,
  formatFileSize,
  pageList,
  pageTotal,
  resourceTypeLabel
} from '@/utils/format'
import { isPlaceholderResourceUrl, resolveResourceUrl, smartEduSubjectUrl } from '@/utils/resourceLinks'

const route = useRoute()
const loading = ref(false)
const recommendLoading = ref(false)
const detailVisible = ref(false)
const page = ref({ list: [], total: 0 })
const recommended = ref([])
const currentResource = ref(null)

const query = reactive({
  keyword: '',
  subject: '',
  resourceType: '',
  knowledgePoint: '',
  pageNum: 1,
  pageSize: 9
})

const resources = computed(() => pageList(page.value))
const total = computed(() => pageTotal(page.value))
const typeCount = computed(() => {
  const counts = {}
  resources.value.forEach((item) => {
    counts[item.resourceType] = (counts[item.resourceType] || 0) + 1
  })
  return counts
})

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
    if (query.keyword.trim()) {
      page.value = await learningResourceApi.search({
        keyword: query.keyword.trim(),
        subject: query.subject || undefined,
        resourceType: query.resourceType === '' ? undefined : query.resourceType,
        pageNum: query.pageNum,
        pageSize: query.pageSize
      })
    } else {
      page.value = await learningResourceApi.list(cleanParams())
    }
  } finally {
    loading.value = false
  }
}

async function applyRouteQuery() {
  const resourceId = Number(route.query.resourceId || 0)
  Object.assign(query, {
    subject: route.query.subject?.toString() || query.subject,
    knowledgePoint: route.query.knowledgePoint?.toString() || query.knowledgePoint,
    pageNum: 1
  })
  await loadResources()
  if (resourceId) {
    try {
      currentResource.value = await learningResourceApi.detail(resourceId)
      detailVisible.value = true
    } catch {
      ElMessage.warning('未找到计划关联的资源，已为你展示资源列表')
    }
  }
}

async function loadRecommended() {
  recommendLoading.value = true
  try {
    const data = await studyPlanApi.recommendedResources({ limit: 6 }).catch(() => ({ resources: [] }))
    recommended.value = Array.isArray(data?.resources) ? data.resources : []
  } finally {
    recommendLoading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadResources()
}

function resetQuery() {
  Object.assign(query, {
    keyword: '',
    subject: '',
    resourceType: '',
    knowledgePoint: '',
    pageNum: 1,
    pageSize: 9
  })
  loadResources()
}

async function openDetail(resource) {
  currentResource.value = await learningResourceApi.detail(resource.resourceId)
  detailVisible.value = true
}

function openResource(resource = currentResource.value) {
  const url = resolveResourceUrl(resource)
  if (!url) {
    ElMessage.info(isPlaceholderResourceUrl(resource?.fileUrl) ? '当前资源地址是开发占位链接，已拦截打开' : '当前资源暂无文件地址，可在后台资源管理中补充')
    return
  }
  window.open(url, '_blank', 'noopener')
}

function applyRecommend(item) {
  Object.assign(query, {
    keyword: '',
    subject: item.subject || '',
    resourceType: item.resourceType || '',
    knowledgePoint: item.knowledgePoint || '',
    pageNum: 1
  })
  loadResources()
}

onMounted(() => {
  applyRouteQuery()
  loadRecommended()
})

watch(
  () => route.query,
  () => {
    applyRouteQuery()
  }
)
</script>

<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h1>学习资源库</h1>
        <p>按学科、资源类型和知识点检索微课、课件、真题、思维导图和考点手册。</p>
      </div>
      <el-button :icon="'Refresh'" @click="loadResources">刷新</el-button>
    </div>

    <section class="resource-summary">
      <div class="summary-main panel">
        <span>当前命中资源</span>
        <strong>{{ total }}</strong>
        <p>资源查看会自动写入学习画像，用于后续推荐。</p>
      </div>
      <div v-for="(label, value) in RESOURCE_TYPES" :key="value" class="summary-item panel">
        <span>{{ label }}</span>
        <strong>{{ typeCount[value] || 0 }}</strong>
      </div>
    </section>

    <section class="panel panel-body">
      <div class="toolbar resource-toolbar">
        <div class="subject-shortcuts" aria-label="初中学科资源快捷入口">
          <a
            v-for="subject in SUBJECTS"
            :key="subject"
            :href="smartEduSubjectUrl(subject)"
            target="_blank"
            rel="noopener noreferrer"
          >
            初中{{ subject }}
          </a>
        </div>
        <el-input
          v-model.trim="query.keyword"
          clearable
          class="keyword-input"
          placeholder="搜索资源名称、知识点、教材版本"
          :prefix-icon="'Search'"
          @keyup.enter="search"
        />
        <el-select v-model="query.subject" clearable placeholder="学科" @change="search">
          <el-option v-for="subject in SUBJECTS" :key="subject" :label="subject" :value="subject" />
        </el-select>
        <el-select v-model="query.resourceType" clearable placeholder="类型" @change="search">
          <el-option
            v-for="(label, value) in RESOURCE_TYPES"
            :key="value"
            :label="label"
            :value="Number(value)"
          />
        </el-select>
        <el-input
          v-model.trim="query.knowledgePoint"
          clearable
          class="point-input"
          placeholder="知识点"
          @keyup.enter="search"
        />
        <el-button type="primary" :icon="'Search'" @click="search">查询</el-button>
        <el-button :icon="'RefreshLeft'" @click="resetQuery">重置</el-button>
      </div>
    </section>

    <section class="two-col">
      <div v-loading="loading" class="resource-grid">
        <article v-for="item in resources" :key="item.resourceId" class="resource-card panel lift-card">
          <div class="resource-card-head">
            <el-tag>{{ resourceTypeLabel(item.resourceType) }}</el-tag>
            <span>{{ formatFileSize(item.fileSize) }}</span>
          </div>
          <h2>{{ item.resourceName || '未命名资源' }}</h2>
          <p>{{ item.knowledgePoint || '未标注知识点' }}</p>
          <div class="tag-row">
            <el-tag type="info">{{ item.subject || '全科' }}</el-tag>
            <el-tag type="info">{{ item.textbookVersion || '通用版本' }}</el-tag>
          </div>
          <div class="resource-meta">
            <span><el-icon><Clock /></el-icon>{{ formatDateTime(item.updateTime || item.createTime) }}</span>
          </div>
          <div class="resource-actions">
            <el-button type="primary" plain :icon="'View'" @click="openDetail(item)">详情</el-button>
            <el-button :icon="'Link'" @click="openResource(item)">打开具体资源</el-button>
          </div>
        </article>

        <el-empty v-if="!loading && !resources.length" class="full-empty" description="暂无匹配资源">
          <el-button type="primary" @click="resetQuery">查看全部资源</el-button>
        </el-empty>
      </div>

      <aside class="recommend-panel panel panel-body" v-loading="recommendLoading">
        <div class="panel-head">
          <h2>画像推荐</h2>
          <el-button text :icon="'Refresh'" @click="loadRecommended">刷新</el-button>
        </div>
        <div class="recommend-list">
          <button
            v-for="item in recommended"
            :key="`${item.resourceId}-${item.knowledgePoint}`"
            class="recommend-item"
            type="button"
            @click="applyRecommend(item)"
          >
            <span>{{ item.resourceName }}</span>
            <small>{{ item.knowledgePoint || '薄弱知识点' }} · {{ resourceTypeLabel(item.resourceType) }}</small>
          </button>
          <p v-if="!recommended.length" class="empty-copy">暂无推荐资源，先在后台补充资源或刷新画像。</p>
        </div>
      </aside>
    </section>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[9, 18, 36]"
        :total="total"
        @change="loadResources"
      />
    </div>

    <el-drawer v-model="detailVisible" title="资源详情" size="520px">
      <div v-if="currentResource" class="resource-detail">
        <h2>{{ currentResource.resourceName || '学习资源' }}</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="学科">{{ currentResource.subject || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            {{ resourceTypeLabel(currentResource.resourceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="知识点">{{ currentResource.knowledgePoint || '-' }}</el-descriptions-item>
          <el-descriptions-item label="教材版本">{{ currentResource.textbookVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatFileSize(currentResource.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="具体链接">{{ resolveResourceUrl(currentResource) }}</el-descriptions-item>
        </el-descriptions>
        <div class="drawer-actions">
          <el-button type="primary" :icon="'Link'" @click="openResource()">打开具体资源</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.resource-summary {
  display: grid;
  grid-template-columns: minmax(220px, 1.3fr) repeat(5, minmax(110px, 1fr));
  gap: 12px;
}

.summary-main,
.summary-item {
  padding: 16px;
}

.summary-main span,
.summary-item span,
.resource-meta,
.resource-card p,
.resource-card-head span {
  color: var(--muted);
}

.summary-main strong,
.summary-item strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  line-height: 1.1;
}

.summary-main p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.keyword-input {
  width: min(360px, 100%);
}

.point-input,
.resource-toolbar .el-select {
  width: 150px;
}

.resource-toolbar {
  align-items: stretch;
}

.subject-shortcuts {
  display: flex;
  flex: 1 0 100%;
  flex-wrap: wrap;
  gap: 8px;
}

.subject-shortcuts a {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--text);
  background: var(--primary-soft);
  font-size: 13px;
}

.subject-shortcuts a:hover {
  border-color: var(--primary);
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.resource-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.resource-card-head,
.resource-actions,
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.resource-card h2 {
  margin: 0;
  min-height: 48px;
  font-size: 18px;
  line-height: 1.35;
}

.resource-card p {
  margin: 0;
}

.resource-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.resource-actions {
  justify-content: flex-start;
  margin-top: auto;
}

.recommend-panel {
  align-self: start;
}

.panel-head {
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  font-size: 17px;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recommend-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--text);
  background: var(--panel);
  text-align: left;
  cursor: pointer;
}

.recommend-item:hover {
  background: var(--primary-soft);
}

.recommend-item span,
.recommend-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-item small {
  color: var(--muted);
}

.full-empty {
  grid-column: 1 / -1;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.resource-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.resource-detail h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.4;
}

.drawer-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 1180px) {
  .resource-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .resource-grid,
  .resource-summary {
    grid-template-columns: 1fr;
  }

  .resource-toolbar > * {
    width: 100%;
  }

  .point-input,
  .resource-toolbar .el-select,
  .keyword-input {
    width: 100%;
  }
}
</style>
