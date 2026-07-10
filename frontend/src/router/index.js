import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import StudentShell from '@/views/student/layout/index.vue'
import AdminShell from '@/views/admin/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/student/login/index.vue'),
    meta: { guest: true }
  },
  {
    path: '/admin',
    component: AdminShell,
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'dashboard',
        name: 'adminDashboard',
        component: () => import('@/views/admin/dashboard/index.vue'),
        meta: { title: '后台首页', affix: true }
      },
      {
        path: 'users',
        name: 'adminUsers',
        component: () => import('@/views/admin/users/index.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'resources',
        name: 'adminResources',
        component: () => import('@/views/admin/resources/index.vue'),
        meta: { title: '资源管理' }
      },
      {
        path: 'questions',
        name: 'adminQuestions',
        component: () => import('@/views/admin/questions/index.vue'),
        meta: { title: '题库管理' }
      },
      {
        path: 'questions/:questionId/scoring',
        name: 'adminQuestionScoring',
        component: () => import('@/views/admin/questions/scoring.vue'),
        meta: { title: '评分要点配置' }
      },
      {
        path: 'ai',
        name: 'adminAi',
        component: () => import('@/views/admin/ai/index.vue'),
        meta: { title: 'AI 配置' }
      },
      {
        path: 'system',
        name: 'adminSystem',
        component: () => import('@/views/admin/system/index.vue'),
        meta: { title: '系统运维' }
      }
    ]
  },
  {
    path: '/',
    component: StudentShell,
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/student/home/index.vue'),
        meta: { title: '学习首页' }
      },
      {
        path: 'ai',
        name: 'ai',
        component: () => import('@/views/student/qa/index.vue'),
        meta: { title: '智能答疑' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/student/profile/index.vue'),
        meta: { title: '学情画像' }
      },
      {
        path: 'wrong-questions',
        name: 'wrongQuestions',
        component: () => import('@/views/student/wrong/index.vue'),
        meta: { title: '错题本' }
      },
      {
        path: 'wrong-questions/list',
        name: 'wrongQuestionList',
        component: () => import('@/views/student/wrong/list.vue'),
        meta: { title: '错题列表' }
      },
      {
        path: 'wrong-questions/books/:assessmentId',
        name: 'wrongQuestionBook',
        component: () => import('@/views/student/wrong/book.vue'),
        meta: { title: '测评错题册' }
      },
      {
        path: 'wrong-questions/:wrongId',
        name: 'wrongQuestionDetail',
        component: () => import('@/views/student/wrong/detail.vue'),
        meta: { title: '错题详情' }
      },
      {
        path: 'study-plans',
        name: 'studyPlans',
        component: () => import('@/views/student/task/index.vue'),
        meta: { title: '学习方案' }
      },
      {
        path: 'assessments',
        name: 'assessments',
        component: () => import('@/views/student/exam/index.vue'),
        meta: { title: '测评中心' }
      },
      {
        path: 'assessments/:assessmentId/take',
        name: 'assessmentTake',
        component: () => import('@/views/student/exam/take.vue'),
        meta: { title: '测评答题' }
      },
      {
        path: 'assessments/:assessmentId/report',
        name: 'assessmentReport',
        component: () => import('@/views/student/exam/report.vue'),
        meta: { title: '测评报告' }
      },
      {
        path: 'assessments/:assessmentId/trend',
        name: 'assessmentTrend',
        component: () => import('@/views/student/exam/trend.vue'),
        meta: { title: '成绩趋势' }
      },
      {
        path: 'resources',
        name: 'resources',
        component: () => import('@/views/student/resource/index.vue'),
        meta: { title: '资源库' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (auth.token && !auth.user) {
    await auth.fetchUser().catch(() => auth.setToken(''))
  }

  if (to.meta.requiresAuth && !auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && auth.user?.role !== 2) {
    return '/dashboard'
  }

  if (to.meta.guest && auth.token) {
    return to.query.redirect?.toString() || (auth.user?.role === 2 ? '/admin/dashboard' : '/dashboard')
  }

  return true
})

export default router
