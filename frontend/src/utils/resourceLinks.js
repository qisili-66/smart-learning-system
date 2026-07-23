const SMARTEDU_SEARCH_BASE = 'https://basic.smartedu.cn/search?keyword='

const RESOURCE_TYPE_KEYWORDS = {
  1: '微课',
  2: '课件',
  3: '练习',
  4: '思维导图',
  5: '考点手册'
}

const PLACEHOLDER_HOSTS = new Set([
  'example.com',
  'www.example.com',
  'localhost',
  '127.0.0.1'
])

export function isPlaceholderResourceUrl(url) {
  const text = String(url || '').trim()
  if (!text) return false
  try {
    return PLACEHOLDER_HOSTS.has(new URL(text).hostname.toLowerCase())
  } catch {
    return false
  }
}

function firstKnowledgePoint(value) {
  return String(value || '')
    .split(/[、,，;；|/\s]+/)
    .map((item) => item.trim())
    .find(Boolean) || ''
}

function normalizedText(value) {
  return String(value || '').replace(/\s+/g, '')
}

function decodedSearchKeyword(url) {
  try {
    return new URL(url).searchParams.get('keyword') || ''
  } catch {
    return ''
  }
}

export function smartEduSubjectKeyword(subject) {
  const cleanSubject = String(subject || '').trim()
  return cleanSubject ? `初中${cleanSubject}` : '初中学习资源'
}

export function smartEduResourceKeyword(resource) {
  const subjectKeyword = smartEduSubjectKeyword(resource?.subject)
  const point = firstKnowledgePoint(resource?.knowledgePoint)
  const typeKeyword = RESOURCE_TYPE_KEYWORDS[Number(resource?.resourceType)] || ''
  return [subjectKeyword, point, typeKeyword].filter(Boolean).join('')
}

export function smartEduSubjectUrl(subject) {
  return `${SMARTEDU_SEARCH_BASE}${encodeURIComponent(smartEduSubjectKeyword(subject))}`
}

export function smartEduResourceUrl(resource) {
  return `${SMARTEDU_SEARCH_BASE}${encodeURIComponent(smartEduResourceKeyword(resource))}`
}

export function isGenericSmartEduUrl(url, resource = {}) {
  const text = String(url || '').trim()
  if (!text) return true
  if (!text.startsWith('https://basic.smartedu.cn')) return false
  if (!text.includes('/search?keyword=')) return true
  const keyword = normalizedText(decodedSearchKeyword(text))
  const point = normalizedText(firstKnowledgePoint(resource?.knowledgePoint))
  if (point && !keyword.includes(point)) return true
  return keyword === normalizedText(smartEduSubjectKeyword(resource?.subject))
}

export function resolveResourceUrl(resource) {
  const fileUrl = String(resource?.fileUrl || '').trim()
  if (isPlaceholderResourceUrl(fileUrl)) {
    return ''
  }
  if (isGenericSmartEduUrl(fileUrl, resource)) {
    return smartEduResourceUrl(resource)
  }
  return fileUrl
}
