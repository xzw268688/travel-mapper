import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAuthGuard } from './guard'
import { useAuthStore } from '../stores/auth'
import { getStoredToken } from '../utils/authStorage'

// 路由级懒加载：公开页不会下载地图、编辑器、后台或海报代码。
const LoginView = () => import('../views/LoginView.vue')
const RegisterView = () => import('../views/RegisterView.vue')
const ResetPasswordView = () => import('../views/ResetPasswordView.vue')
const AdminLoginView = () => import('../views/AdminLoginView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const MapView = () => import('../views/MapView.vue')
const PlanView = () => import('../views/PlanView.vue')
const ItineraryEditView = () => import('../views/ItineraryEditView.vue')
const TripDetailView = () => import('../views/TripDetailView.vue')
const NotesView = () => import('../views/NotesView.vue')
const NoteDetailView = () => import('../views/NoteDetailView.vue')
const ShareView = () => import('../views/ShareView.vue')
const PosterView = () => import('../views/PosterView.vue')
const ExpensesView = () => import('../views/ExpensesView.vue')
const AdminRatesView = () => import('../views/AdminRatesView.vue')
const AdminNotesView = () => import('../views/AdminNotesView.vue')
const AdminFeedbackView = () => import('../views/AdminFeedbackView.vue')
const AdminReportsView = () => import('../views/AdminReportsView.vue')
const AdminSettingsView = () => import('../views/AdminSettingsView.vue')
const AdminLogsView = () => import('../views/AdminLogsView.vue')
const AdminAnnouncementsView = () => import('../views/AdminAnnouncementsView.vue')
const AdminMonitorView = () => import('../views/AdminMonitorView.vue')
const AdminUsersView = () => import('../views/AdminUsersView.vue')
const AdminCardsView = () => import('../views/AdminCardsView.vue')
const AdminCitiesView = () => import('../views/AdminCitiesView.vue')
const AdminLinksView = () => import('../views/AdminLinksView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { bare: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true, bare: true } },
    { path: '/forgot-password', redirect: '/reset-password' },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordView, meta: { public: true, bare: true } },
    { path: '/admin/login', name: 'admin-login', component: AdminLoginView, meta: { public: true, bare: true } },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/', name: 'map', component: MapView, meta: { hideBack: true } },
    { path: '/plan', name: 'plan', component: PlanView },
    { path: '/itineraries/:id', name: 'itinerary-edit', component: ItineraryEditView },
    { path: '/trips/:id', name: 'trip-detail', component: TripDetailView },
        { path: '/notes', name: 'notes', component: NotesView },
    { path: '/notes/:id', name: 'note-detail', component: NoteDetailView },
    { path: '/poster/:id?', name: 'poster', component: PosterView },
    { path: '/expenses', name: 'expenses', component: ExpensesView },
    { path: '/admin/rates', name: 'admin-rates', component: AdminRatesView, meta: { admin: true } },
    { path: '/admin/notes', name: 'admin-notes', component: AdminNotesView, meta: { admin: true } },
    { path: '/admin/feedback', name: 'admin-feedback', component: AdminFeedbackView, meta: { admin: true } },
    { path: '/admin/reports', name: 'admin-reports', component: AdminReportsView, meta: { admin: true } },
    { path: '/admin/logs', name: 'admin-logs', component: AdminLogsView, meta: { admin: true } },
    { path: '/admin/announcements', name: 'admin-announcements', component: AdminAnnouncementsView, meta: { admin: true } },
    { path: '/admin/monitor/:section?', name: 'admin-monitor', component: AdminMonitorView, meta: { admin: true } },
    {
      path: '/admin/audit',
      redirect: { path: '/admin/logs', query: { tab: 'operations' } }
    },
    { path: '/admin/settings', name: 'admin-settings', component: AdminSettingsView, meta: { admin: true } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { admin: true } },
    { path: '/admin/cards', name: 'admin-cards', component: AdminCardsView, meta: { admin: true } },
    { path: '/admin/cities', name: 'admin-cities', component: AdminCitiesView, meta: { admin: true } },
    { path: '/admin/official-links', name: 'admin-links', component: AdminLinksView, meta: { admin: true } },
    { path: '/share/:token', name: 'share', component: ShareView, meta: { public: true, hideBack: true } }
  ]
})

router.beforeEach(
  createAuthGuard({
    getToken: getStoredToken,
    getAuthStore: useAuthStore,
    notify: (message) => ElMessage.warning(message)
  })
)

export default router
