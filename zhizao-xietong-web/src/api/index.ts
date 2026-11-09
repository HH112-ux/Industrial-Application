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
  planId: number;
  operationSeq?: number;
  operationName?: string;
  deviceType?: string;
  deviceId?: number;
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
    request.get<any, ScheduleEntry[]>(`/schedules/work-orders/${workOrderId}`)
};

export const workOrderApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<WorkOrder>>('/work-orders', { params }),
  start: (id: number) => request.post(`/work-orders/${id}/start`),
  report: (id: number, data: ReportRequest) => request.post(`/work-orders/${id}/report`, data),
  qc: (id: number, data: QcRequest) => request.post(`/work-orders/${id}/qc`, data),
  history: (id: number) => request.get<any, WorkOrderHistory[]>(`/work-orders/${id}/history`)
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
}

export interface DeviceTelemetry {
  deviceNo: string;
  deviceName: string;
  status: string;
  temperature: number;
  rpm: number;
  current: number;
  oee: number;
  timestamp: string;
  source?: string;
}

export const authApi = {
  login: (data: LoginRequest) => request.post<any, LoginResponse>('/auth/login', data)
};

export interface DeviceTelemetryPoint {
  reportedAt: string;
  temperature?: number;
  rpm?: number;
  current?: number;
  status?: string;
  source?: string;
}

export const deviceDataApi = {
  telemetry: (id: number) => request.get<any, DeviceTelemetry>(`/devices/${id}/telemetry`),
  history: (id: number, params: { from: string; to: string; limit?: number }) =>
    request.get<any, DeviceTelemetryPoint[]>(`/devices/${id}/telemetry/history`, { params })
};

export interface UserAccount {
  id: number;
  username: string;
  nickname?: string;
  role: string;
  enabled: boolean;
  workshopScope?: string | null;
  createdAt?: string;
}

export const userApi = {
  list: (params: Record<string, any>) => request.get<any, PageResult<UserAccount>>('/users', { params }),
  changeStatus: (id: number, enabled: boolean) =>
    request.post(`/users/${id}/status`, null, { params: { enabled } }),
  resetPassword: (id: number, newPassword: string) =>
    request.post(`/users/${id}/password`, { newPassword }),
  assignWorkshop: (id: number, workshopScope: string) =>
    request.post(`/users/${id}/workshop`, { workshopScope })
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
