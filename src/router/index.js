import { createRouter, createWebHashHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

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
      path: '/profile',
      name: 'profile',
      component: () => import('../views/UserProfileView.vue'),
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

export default router
