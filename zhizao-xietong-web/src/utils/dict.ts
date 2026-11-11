// 状态字典：状态值 -> 显示文案 + el-tag 类型
export interface DictItem {
  label: string;
  type: 'success' | 'info' | 'warning' | 'danger' | 'primary';
}

export const deviceStatusMap: Record<string, DictItem> = {
  STOPPED: { label: '停机', type: 'info' },
  RUNNING: { label: '运行', type: 'success' },
  MAINTENANCE: { label: '维修', type: 'warning' },
  SCRAPPED: { label: '报废', type: 'danger' }
};

export const kitStatusMap: Record<string, DictItem> = {
  SUFFICIENT: { label: '充足', type: 'success' },
  LOW: { label: '偏低', type: 'warning' },
  SHORTAGE: { label: '缺料', type: 'danger' }
};

export const approvalStatusMap: Record<string, DictItem> = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '审批中', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
};

export const planStatusMap: Record<string, DictItem> = {
  PENDING_SCHEDULE: { label: '待排产', type: 'warning' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  FINISHED: { label: '已完成', type: 'success' }
};

export const workOrderStatusMap: Record<string, DictItem> = {
  PENDING: { label: '待开工', type: 'info' },
  IN_PRODUCTION: { label: '生产中', type: 'primary' },
  PENDING_QC: { label: '待质检', type: 'warning' },
  FINISHED: { label: '已完工', type: 'success' }
};

export const priorityMap: Record<string, DictItem> = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' }
};

export const mtTypeMap: Record<string, DictItem> = {
  CHECK: { label: '点检', type: 'info' },
  REPAIR: { label: '维修', type: 'warning' }
};

export const mtResultMap: Record<string, DictItem> = {
  NORMAL: { label: '正常', type: 'success' },
  PENDING_REPAIR: { label: '待维修', type: 'warning' },
  FIXED: { label: '已修复', type: 'primary' }
};

export const alarmStatusMap: Record<string, DictItem> = {
  OPEN: { label: '未处理', type: 'danger' },
  ACKNOWLEDGED: { label: '已确认', type: 'warning' },
  RECOVERED: { label: '已恢复', type: 'success' }
};

export const alarmRuleTypeMap: Record<string, DictItem> = {
  THRESHOLD: { label: '阈值越界', type: 'danger' },
  OFFLINE: { label: '设备离线', type: 'warning' }
};

export const alarmMetricMap: Record<string, DictItem> = {
  temperature: { label: '温度', type: 'info' },
  rpm: { label: '转速', type: 'info' },
  current: { label: '电流', type: 'info' }
};
