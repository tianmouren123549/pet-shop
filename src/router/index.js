import { createRouter, createWebHashHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { LAST_VISITED_ROUTE_KEY } from '../utils/authStorage.js'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('../views/ProductListView.vue'),
    },
    {
      path: '/product/:id',
      name: 'product-detail',
      component: () => import('../views/ProductDetailView.vue'),
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('../views/CartView.vue'),
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('../views/OrdersView.vue'),
    },
    {
      path: '/support',
      name: 'support',
      component: () => import('../views/SupportChatView.vue'),
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('../views/NotificationsView.vue'),
    },
    {
      path: '/merchant-contact',
      name: 'merchant-contact',
      component: () => import('../views/MerchantContactView.vue'),
    },
    {
      path: '/contact-admin',
      name: 'contact-admin',
      component: () => import('../views/ContactAdminView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/UserProfileView.vue'),
    },
    {
      path: '/ai',
      name: 'user-ai',
      component: () => import('../views/UserAiView.vue'),
    },
    {
      path: '/order/:id',
      name: 'order-detail',
      component: () => import('../views/OrderDetailView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/admin-login',
      name: 'admin-login',
      component: () => import('../views/AdminLoginView.vue'),
    },
    {
      path: '/admin',
      name: 'admin-dashboard',
      component: () => import('../views/admin/AdminDashboardView.vue'),
    },
    {
      path: '/admin/products',
      name: 'admin-products',
      component: () => import('../views/admin/AdminProductsView.vue'),
    },
    {
      path: '/admin/inventory',
      name: 'admin-inventory',
      component: () => import('../views/admin/AdminInventoryView.vue'),
    },
    {
      path: '/admin/accounts',
      name: 'admin-accounts',
      component: () => import('../views/admin/AdminAccountsView.vue'),
    },
    {
      path: '/admin/orders',
      name: 'admin-orders',
      component: () => import('../views/admin/AdminOrdersView.vue'),
    },
    {
      path: '/admin/orders/:id',
      name: 'admin-order-detail',
      component: () => import('../views/admin/AdminOrderDetailView.vue'),
    },
    {
      path: '/admin/support',
      name: 'admin-support',
      component: () => import('../views/admin/AdminSupportView.vue'),
    },
    {
      path: '/admin/audit-logs',
      name: 'admin-audit-logs',
      component: () => import('../views/admin/AdminAuditLogsView.vue'),
    },
    {
      path: '/merchant',
      name: 'merchant-dashboard',
      component: () => import('../views/merchant/MerchantDashboardView.vue'),
    },
    {
      path: '/merchant/products',
      name: 'merchant-products',
      component: () => import('../views/merchant/MerchantProductsView.vue'),
    },
    {
      path: '/merchant/orders/:orderId',
      name: 'merchant-order-detail',
      component: () => import('../views/merchant/MerchantOrderDetailView.vue'),
    },
    {
      path: '/merchant/orders',
      name: 'merchant-orders',
      component: () => import('../views/merchant/MerchantOrdersView.vue'),
    },
    {
      path: '/merchant/support',
      name: 'merchant-support',
      component: () => import('../views/merchant/MerchantSupportView.vue'),
    },
    {
      path: '/merchant/contact-admin',
      name: 'merchant-contact-admin',
      component: () => import('../views/merchant/MerchantContactAdminView.vue'),
    },
    {
      path: '/merchant/notifications',
      name: 'merchant-notifications',
      component: () => import('../views/merchant/MerchantNotificationsView.vue'),
    },
    {
      path: '/merchant/profile',
      name: 'merchant-profile',
      component: () => import('../views/merchant/MerchantProfileView.vue'),
    },
    {
      path: '/merchant/product/create',
      name: 'merchant-product-create',
      component: () => import('../views/merchant/MerchantProductCreateView.vue'),
    },
    {
      path: '/merchant/product/:id/edit',
      name: 'merchant-product-edit',
      component: () => import('../views/merchant/MerchantProductContentEditView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
    },
  ],
})

/**
 * 已登录时记住最后一次访问路径；冷启动打开站点根路径时再回到该页（避免重启前后端后总是落在首页）。
 */
router.afterEach((to) => {
  try {
    const token = localStorage.getItem('accessToken')
    const role = localStorage.getItem('role')
    if (!token || !role) return
    if (to.path === '/login' || to.path === '/admin-login') return
    localStorage.setItem(LAST_VISITED_ROUTE_KEY, to.fullPath)
  } catch {
    /* ignore */
  }
})

router.isReady().then(() => {
  try {
    const token = localStorage.getItem('accessToken')
    const role = localStorage.getItem('role')
    const saved = localStorage.getItem(LAST_VISITED_ROUTE_KEY)
    if (!token || !role || !saved) return
    if (router.currentRoute.value.path !== '/') return
    router.replace(saved)
  } catch {
    /* ignore */
  }
})

export default router
