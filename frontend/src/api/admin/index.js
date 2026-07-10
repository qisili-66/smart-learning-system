import http from '../request'

export const adminUserApi = {
  list: (params) => http.get('/admin/users', { params }),
  detail: (userId) => http.get(`/admin/users/${userId}`),
  updateStatus: (userId, status) => http.put(`/admin/users/${userId}/status`, { status }),
  resetPassword: (userId) => http.put(`/admin/users/${userId}/reset-password`)
}

export const adminResourceApi = {
  list: (params) => http.get('/admin/learning-resources', { params }),
  categories: () => http.get('/learning-resources/categories'),
  create: (data) => {
    const form = new FormData()
    Object.entries(data || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        form.append(key, value)
      }
    })
    return http.post('/admin/learning-resources', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  update: (resourceId, data) => http.put(`/admin/learning-resources/${resourceId}`, data),
  updateStatus: (resourceId, status) =>
    http.put(`/admin/learning-resources/${resourceId}/status`, { status }),
  remove: (resourceId) => http.delete(`/admin/learning-resources/${resourceId}`)
}

export const adminQuestionApi = {
  list: (params) => http.get('/admin/questions', { params }),
  detail: (questionId) => http.get(`/admin/questions/${questionId}`),
  create: (data) => http.post('/admin/questions', data),
  update: (questionId, data) => http.put(`/admin/questions/${questionId}`, data),
  remove: (questionId) => http.delete(`/admin/questions/${questionId}`),
  batchImport: (file) => {
    const form = new FormData()
    if (file) form.append('file', file)
    return http.post('/admin/questions/batch-import', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export const adminSystemApi = {
  status: () => http.get('/admin/system/status'),
  logs: (params) => http.get('/admin/system/logs', { params }),
  faults: (params) => http.get('/admin/system/faults', { params }),
  backup: () => http.post('/admin/system/backup')
}

export const adminAiApi = {
  models: () => http.get('/admin/ai/models'),
  updateQaRules: (data) => http.put('/admin/ai/qa-rules', data),
  updateRecommendConfig: (data) => http.put('/admin/ai/recommend-config', data)
}
