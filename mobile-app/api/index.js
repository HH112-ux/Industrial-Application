import { request } from '../utils/request.js';

export const authApi = {
  login: (data) => request({ url: '/auth/login', method: 'POST', data })
};

export const userApi = {
  changePassword: (oldPassword, newPassword) =>
    request({ url: '/users/me/password', method: 'POST', data: { oldPassword, newPassword } })
};

export const workOrderApi = {
  list: (params) => request({ url: '/work-orders', method: 'GET', data: params }),
  get: (id) => request({ url: `/work-orders/${id}`, method: 'GET' }),
  start: (id) => request({ url: `/work-orders/${id}/start`, method: 'POST' }),
  report: (id, data) => request({ url: `/work-orders/${id}/report`, method: 'POST', data }),
  qc: (id, data) => request({ url: `/work-orders/${id}/qc`, method: 'POST', data })
};

export const scheduleApi = {
  entries: (workOrderId) => request({ url: `/schedules/work-orders/${workOrderId}`, method: 'GET' })
};

export const workOrderStatusMap = {
  PENDING: { label: '待开工', color: '#94a3b8' },
  IN_PRODUCTION: { label: '生产中', color: '#1d5fd6' },
  PENDING_QC: { label: '待质检', color: '#e8871a' },
  FINISHED: { label: '已完工', color: '#16a34a' }
};
