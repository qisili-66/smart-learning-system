export const SUBJECTS = ['语文', '数学', '英语', '物理', '化学', '生物', '历史', '地理', '道德与法治']

export const GRADES = ['小学', '初一', '初二', '初三', '高一', '高二', '高三', '大学']

export const RESOURCE_TYPES = {
  1: '微课',
  2: '课件',
  3: '真题',
  4: '思维导图',
  5: '考点手册'
}

export const WRONG_REASON = {
  1: '计算失误',
  2: '概念混淆',
  3: '审题错误',
  4: '思路错误',
  5: '其他'
}

export const PLAN_STATUS = {
  1: { text: '进行中', type: 'primary' },
  2: { text: '已完成', type: 'success' },
  3: { text: '已终止', type: 'info' }
}

export const ASSESSMENT_TYPES = {
  1: '单元测评',
  2: '专项测评',
  3: '模拟测试'
}

export const DIFFICULTY = {
  1: '基础',
  2: '进阶',
  3: '提升'
}

export const QUESTION_TYPES = {
  1: '单选题',
  2: '多选题',
  3: '填空题',
  4: '解答题'
}

export const SCORE_STATUS = {
  1: { text: '自动评分', type: 'success' },
  2: { text: '待人工复核', type: 'warning' },
  3: { text: '人工复核完成', type: 'primary' }
}

export function roleLabel(role) {
  return Number(role) === 2 ? '管理员' : '学生'
}

export function statusMeta(status) {
  return Number(status) === 1
    ? { text: '正常', type: 'success' }
    : { text: '停用', type: 'info' }
}

export function pageList(page) {
  return Array.isArray(page?.list) ? page.list : []
}

export function pageTotal(page) {
  return Number(page?.total || 0)
}

export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

export function asPercent(value, fallback = 0) {
  const num = Number(value ?? fallback)
  if (Number.isNaN(num)) return fallback
  return Math.max(0, Math.min(100, Math.round(num)))
}

export function planStatusMeta(status) {
  return PLAN_STATUS[status] || { text: '未知', type: 'info' }
}

export function resourceTypeLabel(type) {
  return RESOURCE_TYPES[type] || '资料'
}

export function wrongReasonLabel(reason) {
  return WRONG_REASON[reason] || '未分类'
}

export function difficultyLabel(difficulty) {
  return DIFFICULTY[difficulty] || '基础'
}

export function questionTypeLabel(type) {
  return QUESTION_TYPES[type] || '题目'
}

export function scoreStatusMeta(status) {
  return SCORE_STATUS[status] || { text: '未评分', type: 'info' }
}

export function formatFileSize(size) {
  const bytes = Number(size || 0)
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
