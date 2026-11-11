import { createRouter, createWebHistory } from 'vue-router';
import Layout from '@/layout/Layout.vue';
import { getToken, isAdmin, mustChangePassword } from '@/api/request';

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
      path: '/change-password',
      name: 'ChangePassword',
      component: () => import('@/views/ChangePassword.vue'),
      meta: { title: '修改密码' }
    },
    {
      path: '/sso/callback',
      name: 'SsoCallback',
      component: () => import('@/views/SsoCallback.vue'),
      meta: { title: '企业微信登录' }
    },
    {
      path: '/',
      component: Layout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '驾驶舱' } },
        { path: 'devices', name: 'Devices', component: () => import('@/views/DeviceList.vue'), meta: { title: '设备管理' } },
        { path: 'alarms', name: 'Alarms', component: () => import('@/views/AlarmCenter.vue'), meta: { title: '报警中心' } },
        { path: 'alarm-rules', name: 'AlarmRules', component: () => import('@/views/AlarmRuleList.vue'), meta: { title: '报警规则', admin: true } },
        { path: 'maintenance', name: 'Maintenance', component: () => import('@/views/MaintenanceList.vue'), meta: { title: '点检与维修' } },
        { path: 'materials', name: 'Materials', component: () => import('@/views/MaterialList.vue'), meta: { title: '物料管理' } },
        { path: 'processes', name: 'Processes', component: () => import('@/views/ProcessList.vue'), meta: { title: '工艺管理' } },
        { path: 'plans', name: 'Plans', component: () => import('@/views/PlanList.vue'), meta: { title: '计划与调度' } },
        { path: 'schedule-board', name: 'ScheduleBoard', component: () => import('@/views/ScheduleBoard.vue'), meta: { title: '排产看板' } },
        { path: 'work-orders', name: 'WorkOrders', component: () => import('@/views/WorkOrderList.vue'), meta: { title: '生产执行' } },
        { path: 'finished-goods', name: 'FinishedGoods', component: () => import('@/views/FinishedGoodsInbound.vue'), meta: { title: '成品入库' } },
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
  if (to.path === '/sso/callback') {
    next();
  } else if (!token) {
    next(to.path === '/login' ? undefined : '/login');
  } else if (to.path !== '/change-password' && mustChangePassword()) {
    next('/change-password');
  } else if (to.path === '/login') {
    next('/dashboard');
  } else if (to.meta.admin && !isAdmin()) {
    next('/dashboard');
  } else {
    next();
  }
});

export default router;
