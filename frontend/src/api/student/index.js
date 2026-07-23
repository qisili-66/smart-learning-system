import http from '../request'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  logout: () => http.post('/auth/logout')
}

export const userApi = {
  info: () => http.get('/users/info'),
  updateInfo: (data) => http.put('/users/info', data),
  changePassword: (data) => http.put('/users/password', data)
}

export const profileApi = {
  my: () => http.get('/user-profiles/my'),
  updateMy: (data) => http.put('/user-profiles/my', data),
  refresh: () => http.post('/user-profiles/refresh'),
  weakPoints: (params) => http.get('/user-profiles/weak-points', { params })
}

export const wrongQuestionApi = {
  list: (params) => http.get('/wrong-questions', { params }),
  detail: (wrongId) => http.get(`/wrong-questions/${wrongId}`),
  collect: (data) => http.post('/wrong-questions/collect', data),
  batchCollect: (data) => http.post('/wrong-questions/batch-collect', data),
  markMastered: (wrongId, isMastered) =>
    http.put(`/wrong-questions/${wrongId}/mastered`, { isMastered }),
  updateReviewPlan: (wrongId, data) => http.put(`/wrong-questions/${wrongId}/review-plan`, data),
  statistics: (params) => http.get('/wrong-questions/statistics', { params }),
  similar: (wrongId, params) => http.get(`/wrong-questions/${wrongId}/similar`, { params }),
  export: (params) => http.get('/wrong-questions/export', { params }),
  remove: (wrongId) => http.delete(`/wrong-questions/${wrongId}`),
  clear: (params) => http.delete('/wrong-questions', { params }),
  downloadExport: (downloadUrl) => {
    const path = String(downloadUrl || '').replace(/^\/api/, '')
    return http.get(path, { responseType: 'blob' })
  }
}

export const studyPlanApi = {
  create: (data) => http.post('/study-plans', data),
  list: (params) => http.get('/study-plans', { params }),
  detail: (planId) => http.get(`/study-plans/${planId}`),
  path: (planId) => http.get(`/study-plans/${planId}/path`),
  update: (planId, data) => http.put(`/study-plans/${planId}`, data),
  remove: (planId) => http.delete(`/study-plans/${planId}`),
  dailyTasks: (params) => http.get('/study-plans/daily-tasks', { params }),
  finishTask: (taskId, data = {}) => http.put(`/study-plans/tasks/${taskId}/finish`, data),
  recommendedResources: (params) => http.get('/study-plans/recommended-resources', { params }),
  createTarget: (data) => http.post('/study-plans/targets', data),
  adjust: (planId) => http.post(`/study-plans/${planId}/adjustments`)
}

export const learningResourceApi = {
  list: (params) => http.get('/learning-resources', { params }),
  detail: (resourceId) => http.get(`/learning-resources/${resourceId}`),
  search: (params) => http.get('/learning-resources/search', { params }),
  categories: () => http.get('/learning-resources/categories')
}

export const qaApi = {
  text: (data) => http.post('/qa/text', data),
  conversations: (params) => http.get('/qa/conversations', { params }),
  conversationDetail: (conversationId) => http.get(`/qa/conversations/${conversationId}`),
  deleteConversation: (conversationId) => http.delete(`/qa/conversations/${conversationId}`),
  evaluation: (params) => http.get('/qa/evaluation', { params }),
  image: (file, data = {}) => {
    const form = new FormData()
    form.append('file', file)
    if (data.conversationId) form.append('conversationId', data.conversationId)
    if (data.subject) form.append('subject', data.subject)
    if (data.confirmAnswer !== undefined) form.append('confirmAnswer', data.confirmAnswer)
    return http.post('/qa/image', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000
    })
  },
  voice: (file, data = {}) => {
    const form = new FormData()
    form.append('file', file)
    if (data.conversationId) form.append('conversationId', data.conversationId)
    if (data.subject) form.append('subject', data.subject)
    if (data.recognizedText) form.append('recognizedText', data.recognizedText)
    if (data.correctedText) form.append('correctedText', data.correctedText)
    if (data.confirmAnswer !== undefined) form.append('confirmAnswer', data.confirmAnswer)
    return http.post('/qa/voice', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000
    })
  },
  downloadAudio: (audioUrl) => {
    const path = String(audioUrl || '').replace(/^\/api/, '')
    return http.get(path, { responseType: 'blob' })
  }
}

export const studyRecordApi = {
  create: (data) => http.post('/study-records', data),
  durationStatistics: (params) => http.get('/study-records/duration-statistics', { params }),
  progressReport: (params) => http.get('/study-records/progress-report', { params }),
  reminders: () => http.get('/study-records/reminders')
}

export const assessmentApi = {
  create: (data) => http.post('/assessments', data),
  detail: (assessmentId) => http.get(`/assessments/${assessmentId}`),
  submit: (assessmentId, data) => http.post(`/assessments/${assessmentId}/submit`, data),
  reviewAnswer: (assessmentId, answerId, data) =>
    http.put(`/assessments/${assessmentId}/answers/${answerId}/review`, data),
  report: (assessmentId) => http.get(`/assessments/${assessmentId}/report`),
  trend: (assessmentId) => http.get(`/assessments/${assessmentId}/trend`),
  history: (params) => http.get('/assessments/history', { params }),
  remove: (assessmentId) => http.delete(`/assessments/${assessmentId}`),
  clear: (params) => http.delete('/assessments', { params })
}

export const personalDataApi = {
  overview: () => http.get('/personal-data/overview'),
  export: () => http.get('/personal-data/export'),
  clear: (data) => http.delete('/personal-data/clear', { data }),
  exportLogs: () => http.get('/personal-data/export-logs'),
  clearLogs: () => http.get('/personal-data/clear-logs')
}
