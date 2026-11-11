import request from './request';

export interface PageResult<T> {
  total: number;
  records: T[];
}

export interface Device {
  id?: number;
  deviceNo?: string;
  deviceName: string;
  deviceType: string;
  manufacturer?: string;
  model: string;
  serialNo?: string;
  status?: string;
  workshop?: string;
}

export interface Material {
  id?: number;
  matCode?: string;
  matName: string;
  matType: string;
  purchasePrice?: number;
  productionDate?: string;
  stockQty: number;
  safeStock: number;
  kitStatus?: string;
}

export interface Process {
  id?: number;
  processNo?: string;
  version?: number;
  processName: string;
  processType: string;
  owner: string;
  approvalStatus?: string;
  deviceMatReq?: string;
}

export interface Plan {
  id?: number;
  planOrderNo?: string;
  productCode: string;
  productName: string;
  planQty: number;
  planStart: string;
  planEnd: string;
  priority: string;
  batch?: string;
  workshop?: string;
  requiredDeviceId?: number;
  estHours?: number;
  status?: string;
}

export interface WorkOrder {
  id?: number;
  orderNo?: string;
  planId?: number;
  productCode: string;
  productName: string;
  qty: number;
  qtyDone?: number;
  qtyRework?: number;
  batch: string;
  workshop: string;
  deviceId?: number;
  processId?: number;
  status?: string;
}

export interface ReportRequest {
  qty: number;
  qualifiedQty?: number;
  defectQty?: number;
  deviceId?: number;
  idempotencyKey?: string;
}

export interface WorkOrderHistory {
  id: number;
  orderId: number;
  fromStatus?: string;
  toStatus: string;
  operator?: string;
  remark?: string;
  createdAt: string;
}

export interface QcRequest {
  result: string;
  defectDesc?: string;
}

export const deviceApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<Device>>('/devices', { params }),
  create: (data: Device) => request.post('/devices', data),
  update: (id: number, data: Device) => request.put(`/devices/${id}`, data),
  remove: (id: number) => request.delete(`/devices/${id}`),
  changeStatus: (id: number, status: string) => request.post(`/devices/${id}/status`, null, { params: { status } })
};

export const materialApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<Material>>('/materials', { params }),
  create: (data: Material) => request.post('/materials', data),
  update: (id: number, data: Material) => request.put(`/materials/${id}`, data),
  remove: (id: number) => request.delete(`/materials/${id}`)
};

export const processApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<Process>>('/processes', { params }),
  create: (data: Process) => request.post('/processes', data),
  update: (id: number, data: Process) => request.put(`/processes/${id}`, data),
  remove: (id: number) => request.delete(`/processes/${id}`),
  submit: (id: number) => request.post(`/processes/${id}/submit`),
  approve: (id: number) => request.post(`/processes/${id}/approve`),
  reject: (id: number) => request.post(`/processes/${id}/reject`),
  createVersion: (id: number) => request.post<any, Process>(`/processes/${id}/versions`)
};

export interface ProcessOperation {
  id?: number;
  processId?: number;
  seq: number;
  operationName: string;
  deviceType?: string;
  estHours?: number;
}

export const processOperationApi = {
  list: (processId: number) => request.get<any, ProcessOperation[]>(`/processes/${processId}/operations`),
  create: (processId: number, data: ProcessOperation) => request.post(`/processes/${processId}/operations`, data),
  update: (processId: number, id: number, data: ProcessOperation) =>
    request.put(`/processes/${processId}/operations/${id}`, data),
  remove: (processId: number, id: number) => request.delete(`/processes/${processId}/operations/${id}`)
};

export interface DispatchRequest {
  processId: number;
  deviceId?: number;
  workshop?: string;
}

export interface ScheduleEntry {
  id?: number;
  workOrderId: number;
  orderNo?: string;
  planId: number;
  productCode?: string;
  productName?: string;
  workshop?: string;
  operationSeq?: number;
  operationName?: string;
  deviceType?: string;
  deviceId?: number;
  deviceNo?: string;
  deviceName?: string;
  deviceWorkshop?: string;
  startTime?: string;
  endTime?: string;
  durationHours?: number;
  estimated?: number;
  hoursSource?: 'STANDARD' | 'ESTIMATED';
  queueRank: number;
  rankBasis: string;
  status: string;
  conflictCode?: string;
  message?: string;
  /** 占用块：该时段被其他车间工单占用，除设备与起止时间外不携带任何工单内容 */
  occupied?: boolean;
}

export interface ScheduleRunResult {
  runAt: string;
  scheduledWorkOrders: number;
  unscheduledWorkOrders: number;
  scheduled: ScheduleEntry[];
  unscheduled: ScheduleEntry[];
}

export const planApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<Plan>>('/plans', { params }),
  create: (data: Plan) => request.post('/plans', data),
  update: (id: number, data: Plan) => request.put(`/plans/${id}`, data),
  remove: (id: number) => request.delete(`/plans/${id}`),
  dispatch: (id: number, data: DispatchRequest) => request.post(`/plans/${id}/dispatch`, data)
};

export const scheduleApi = {
  run: () => request.post<any, ScheduleRunResult>('/schedules/run'),
  entries: (workOrderId: number) =>
    request.get<any, ScheduleEntry[]>(`/schedules/work-orders/${workOrderId}`),
  deviceTimeline: (from: string, to: string) =>
    request.get<any, ScheduleEntry[]>('/schedules/devices', { params: { from, to } })
};

export const workOrderApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<WorkOrder>>('/work-orders', { params }),
  start: (id: number) => request.post(`/work-orders/${id}/start`),
  report: (id: number, data: ReportRequest) => request.post(`/work-orders/${id}/report`, data),
  qc: (id: number, data: QcRequest) => request.post(`/work-orders/${id}/qc`, data),
  history: (id: number) => request.get<any, WorkOrderHistory[]>(`/work-orders/${id}/history`),
  statusCounts: () => request.get<any, WorkOrderStatusCount[]>('/work-orders/stats/status')
};

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  nickname: string;
  role: string;
  mustChangePassword: boolean;
}

export interface DeviceTelemetry {
  deviceNo: string;
  deviceName: string;
  status: string;
  temperature: number | null;
  rpm: number | null;
  current: number | null;
  oee: number | null;
  timestamp: string | null;
  source?: string;
}

export interface SsoStatus {
  enabled: boolean;
}

export const authApi = {
  login: (data: LoginRequest) => request.post<any, LoginResponse>('/auth/login', data),
  ssoStatus: () => request.get<any, SsoStatus>('/auth/sso/config'),
  ssoLogin: (code: string, state: string) => request.post<any, LoginResponse>('/auth/sso/wecom/login', { code, state }),
  logout: () => request.post('/auth/logout')
};

export interface DeviceTelemetryPoint {
  reportedAt: string;
  temperature?: number;
  rpm?: number;
  current?: number;
  status?: string;
  source?: string;
}

export interface TelemetryHistory {
  points: DeviceTelemetryPoint[];
  total: number;
  truncated: boolean;
}

export const deviceDataApi = {
  telemetry: (id: number) => request.get<any, DeviceTelemetry>(`/devices/${id}/telemetry`),
  history: (id: number, params: { from: string; to: string; limit?: number }) =>
    request.get<any, TelemetryHistory>(`/devices/${id}/telemetry/history`, { params })
};

export interface WorkOrderStatusCount {
  status: string;
  count: number;
}

export interface UserAccount {
  id: number;
  username: string;
  nickname?: string;
  role: string;
  enabled: boolean;
  workshopScope?: string | null;
  authSource?: string;
  departmentIds?: string | null;
  createdAt?: string;
}

export const userApi = {
  me: () => request.get<any, UserAccount>('/users/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    request.post('/users/me/password', { oldPassword, newPassword }),
  list: (params: Record<string, any>) => request.get<any, PageResult<UserAccount>>('/users', { params }),
  changeStatus: (id: number, enabled: boolean) =>
    request.post(`/users/${id}/status`, null, { params: { enabled } }),
  resetPassword: (id: number, newPassword: string) =>
    request.post(`/users/${id}/password`, { newPassword }),
  assignWorkshop: (id: number, workshopScope: string) =>
    request.post(`/users/${id}/workshop`, { workshopScope }),
  assignRole: (id: number, role: string) =>
    request.post(`/users/${id}/role`, { role })
};

export interface BomItem {
  id?: number;
  processId?: number;
  matCode: string;
  requiredQty: number;
}

export const bomApi = {
  list: (processId: number) => request.get<any, BomItem[]>(`/processes/${processId}/bom`),
  create: (processId: number, data: { matCode: string; requiredQty: number }) => request.post(`/processes/${processId}/bom`, data),
  update: (processId: number, id: number, data: { matCode: string; requiredQty: number }) => request.put(`/processes/${processId}/bom/${id}`, data),
  remove: (processId: number, id: number) => request.delete(`/processes/${processId}/bom/${id}`)
};

export interface MaintenanceRecord {
  id?: number;
  mtNo?: string;
  deviceId: number;
  mtType: string;
  operator?: string;
  mtTime?: string;
  description?: string;
  resultStatus?: string;
}

export const maintenanceApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<MaintenanceRecord>>('/maintenance', { params }),
  create: (data: MaintenanceRecord) => request.post('/maintenance', data),
  update: (id: number, data: MaintenanceRecord) => request.put(`/maintenance/${id}`, data),
  remove: (id: number) => request.delete(`/maintenance/${id}`)
};

export interface FinishedGoodsInbound {
  id: number;
  workOrderId: number;
  productCode: string;
  qty: number;
  batch?: string;
  workshop?: string;
  status: string;
  operator?: string;
  inboundTime?: string;
  cancelReason?: string;
  createdAt?: string;
}

export interface FinishedGoodsStock {
  id: number;
  productCode: string;
  workshop?: string;
  qty: number;
  createdAt?: string;
  updatedAt?: string;
}

export const finishedGoodsApi = {
  inbounds: (params: Record<string, any>) =>
    request.get<any, PageResult<FinishedGoodsInbound>>('/finished-goods/inbounds', { params }),
  confirm: (id: number, batch?: string) =>
    request.post<any, FinishedGoodsInbound>(`/finished-goods/inbounds/${id}/confirm`, { batch }),
  cancel: (id: number, reason: string) =>
    request.post<any, FinishedGoodsInbound>(`/finished-goods/inbounds/${id}/cancel`, { reason }),
  stocks: (params: Record<string, any>) =>
    request.get<any, PageResult<FinishedGoodsStock>>('/finished-goods/stocks', { params })
};

export interface AlarmRecord {
  id?: number;
  deviceId?: number;
  metric?: string;
  ruleType?: string;
  triggerValue?: number | null;
  thresholdSnapshot?: string;
  startTime?: string;
  recoverTime?: string | null;
  status?: string;
  ackUser?: string | null;
  ackTime?: string | null;
  activeFlag?: number | null;
}

export const alarmApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<AlarmRecord>>('/alarm-records', { params }),
  unreadCount: () => request.get<any, number>('/alarm-records/unread-count'),
  acknowledge: (id: number) => request.post<any, AlarmRecord>(`/alarm-records/${id}/acknowledge`),
  recover: (id: number) => request.post<any, AlarmRecord>(`/alarm-records/${id}/recover`)
};

export interface AlarmRule {
  id?: number;
  deviceId?: number;
  metric?: string;
  ruleType?: string;
  upperLimit?: number | null;
  lowerLimit?: number | null;
  consecutiveTimes?: number;
  currentStreak?: number;
  enabled?: boolean;
}

export const alarmRuleApi = {
  list: (deviceId?: number) => request.get<any, AlarmRule[]>('/alarm-rules', { params: { deviceId } }),
  create: (data: AlarmRule) => request.post<any, AlarmRule>('/alarm-rules', data),
  update: (id: number, data: AlarmRule) => request.put<any, AlarmRule>(`/alarm-rules/${id}`, data)
};
