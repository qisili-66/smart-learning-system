export const SUBJECTS = ['\u8bed\u6587', '\u6570\u5b66', '\u82f1\u8bed', '\u7269\u7406', '\u5316\u5b66', '\u751f\u7269', '\u5386\u53f2', '\u5730\u7406', '\u653f\u6cbb']

export const GRADES = ['\u5c0f\u5b66', '\u521d\u4e00', '\u521d\u4e8c', '\u521d\u4e09', '\u9ad8\u4e00', '\u9ad8\u4e8c', '\u9ad8\u4e09', '\u5927\u5b66']

export const RESOURCE_TYPES = {
  1: '\u5fae\u8bfe',
  2: '\u8bfe\u4ef6',
  3: '\u771f\u9898',
  4: '\u601d\u7ef4\u5bfc\u56fe',
  5: '\u8003\u70b9\u624b\u518c'
}

export const WRONG_REASON = {
  1: '\u8ba1\u7b97\u5931\u8bef',
  2: '\u6982\u5ff5\u6df7\u6dc6',
  3: '\u5ba1\u9898\u9519\u8bef',
  4: '\u601d\u8def\u9519\u8bef',
  5: '\u5176\u4ed6'
}

export const PLAN_STATUS = {
  1: { text: '\u8fdb\u884c\u4e2d', type: 'primary' },
  2: { text: '\u5df2\u5b8c\u6210', type: 'success' },
  3: { text: '\u5df2\u7ec8\u6b62', type: 'info' }
}

export const ASSESSMENT_TYPES = {
  1: '\u5355\u5143\u6d4b\u8bc4',
  2: '\u4e13\u9879\u6d4b\u8bc4',
  3: '\u6a21\u62df\u6d4b\u8bd5'
}

export const DIFFICULTY = {
  1: '\u57fa\u7840',
  2: '\u8fdb\u9636',
  3: '\u63d0\u5347'
}

export const QUESTION_TYPES = {
  1: '\u5355\u9009\u9898',
  2: '\u591a\u9009\u9898',
  3: '\u586b\u7a7a\u9898',
  4: '\u89e3\u7b54\u9898'
}

export const SCORE_STATUS = {
  1: { text: '\u81ea\u52a8\u8bc4\u5206', type: 'success' },
  2: { text: '\u5f85\u4eba\u5de5\u590d\u6838', type: 'warning' },
  3: { text: '\u4eba\u5de5\u590d\u6838\u5b8c\u6210', type: 'primary' }
}

export function roleLabel(role) {
  return Number(role) === 2 ? '\u7ba1\u7406\u5458' : '\u5b66\u751f'
}

export function statusMeta(status) {
  return Number(status) === 1
    ? { text: '\u6b63\u5e38', type: 'success' }
    : { text: '\u505c\u7528', type: 'info' }
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
  return PLAN_STATUS[status] || { text: '\u672a\u77e5', type: 'info' }
}

export function resourceTypeLabel(type) {
  return RESOURCE_TYPES[type] || '\u8d44\u6599'
}

export function wrongReasonLabel(reason) {
  return WRONG_REASON[reason] || '\u672a\u5206\u7c7b'
}

export function difficultyLabel(difficulty) {
  return DIFFICULTY[difficulty] || '\u57fa\u7840'
}

export function questionTypeLabel(type) {
  return QUESTION_TYPES[type] || '\u9898\u76ee'
}

export function scoreStatusMeta(status) {
  return SCORE_STATUS[status] || { text: '\u672a\u8bc4\u5206', type: 'info' }
}

export function formatFileSize(size) {
  const bytes = Number(size || 0)
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
