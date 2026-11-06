import { createRouter, createWebHistory } from 'vue-router';
import Layout from '@/layout/Layout.vue';
import { getToken, isAdmin } from '@/api/request';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: Layout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '驾驶舱' } },
        { path: 'devices', name: 'Devices', component: () => import('@/views/DeviceList.vue'), meta: { title: '设备管理' } },
        { path: 'maintenance', name: 'Maintenance', component: () => import('@/views/MaintenanceList.vue'), meta: { title: '点检与维修' } },
        { path: 'materials', name: 'Materials', component: () => import('@/views/MaterialList.vue'), meta: { title: '物料管理' } },
        { path: 'processes', name: 'Processes', component: () => import('@/views/ProcessList.vue'), meta: { title: '工艺管理' } },
        { path: 'plans', name: 'Plans', component: () => import('@/views/PlanList.vue'), meta: { title: '计划与调度' } },
        { path: 'work-orders', name: 'WorkOrders', component: () => import('@/views/WorkOrderList.vue'), meta: { title: '生产执行' } },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/Users.vue'),
          meta: { title: '用户管理', admin: true }
        }
      ]
    }
  ]
});

router.beforeEach((to, _from, next) => {
  const token = getToken();
  if (to.path !== '/login' && !token) {
    next('/login');
  } else if (to.path === '/login' && token) {
    next('/dashboard');
  } else if (to.meta.admin && !isAdmin()) {
    next('/dashboard');
  } else {
    next();
  }
});

export default router;
