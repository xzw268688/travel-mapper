import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import VerifyEmailView from '../views/VerifyEmailView.vue'
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import ResetPasswordView from '../views/ResetPasswordView.vue'
import ProfileView from '../views/ProfileView.vue'
import MapView from '../views/MapView.vue'
import PlanView from '../views/PlanView.vue'
import ItineraryEditView from '../views/ItineraryEditView.vue'
import PolishView from '../views/PolishView.vue'
import NotesView from '../views/NotesView.vue'
import NoteDetailView from '../views/NoteDetailView.vue'
import ShareView from '../views/ShareView.vue'
import PosterView from '../views/PosterView.vue'
import ExpensesView from '../views/ExpensesView.vue'
import AdminRatesView from '../views/AdminRatesView.vue'
import AdminNotesView from '../views/AdminNotesView.vue'
import AdminSettingsView from '../views/AdminSettingsView.vue'
import AdminUsersView from '../views/AdminUsersView.vue'
import AdminCardsView from '../views/AdminCardsView.vue'
import AdminCitiesView from '../views/AdminCitiesView.vue'
import AdminLinksView from '../views/AdminLinksView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/verify-email', name: 'verify-email', component: VerifyEmailView, meta: { public: true } },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordView, meta: { public: true } },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/', name: 'map', component: MapView },
    { path: '/plan', name: 'plan', component: PlanView },
    { path: '/itineraries/:id', name: 'itinerary-edit', component: ItineraryEditView },
    { path: '/polish', name: 'polish', component: PolishView },
    { path: '/notes', name: 'notes', component: NotesView },
    { path: '/notes/:id', name: 'note-detail', component: NoteDetailView },
    { path: '/poster/:id?', name: 'poster', component: PosterView },
    { path: '/expenses', name: 'expenses', component: ExpensesView },
    { path: '/admin/rates', name: 'admin-rates', component: AdminRatesView, meta: { admin: true } },
    { path: '/admin/notes', name: 'admin-notes', component: AdminNotesView, meta: { admin: true } },
    { path: '/admin/settings', name: 'admin-settings', component: AdminSettingsView, meta: { admin: true } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { admin: true } },
    { path: '/admin/cards', name: 'admin-cards', component: AdminCardsView, meta: { admin: true } },
    { path: '/admin/cities', name: 'admin-cities', component: AdminCitiesView, meta: { admin: true } },
    { path: '/admin/official-links', name: 'admin-links', component: AdminLinksView, meta: { admin: true } },
    { path: '/share/:token', name: 'share', component: ShareView, meta: { public: true } }
  ]
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  const token = localStorage.getItem('tm_token')
  if (to.name !== 'login' && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'map' }
  }
  if (to.meta.admin && token) {
    const auth = useAuthStore()
    if (!auth.user) {
      await auth.loadProfile()
    }
    if (auth.user?.role !== 'ADMIN') {
      ElMessage.warning('需要管理员权限')
      return { name: 'map' }
    }
  }
  return true
})

export default router
