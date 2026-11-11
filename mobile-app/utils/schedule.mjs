/**
 * 工单排程落点的展示映射：把接口原始字段转成移动端可读文案。
 * 保持纯函数，不依赖 uni-app 运行时，便于用 node --test 直接覆盖。
 */

/** 把 ISO 时间转成展示用日期时间；空值返回空串。 */
export function formatTime(value) {
  if (value == null || value === '') {
    return '';
  }
  return String(value).replace('T', ' ').slice(0, 19);
}

/** 拼接占用时间窗；任一端缺失都视为未落位。 */
export function formatWindow(start, end) {
  if (!start || !end) {
    return '未落位';
  }
  return `${formatTime(start)} ~ ${formatTime(end)}`;
}

/** 设备展示名：设备名 > 设备编号 > 设备类型 > 占位符。 */
export function deviceLabel(entry = {}) {
  return entry.deviceName || entry.deviceNo || entry.deviceType || '—';
}

/** 工时文案；0 小时是有效值，缺失才显示占位符。 */
export function hoursText(entry = {}) {
  const hours = entry.durationHours;
  if (hours == null || hours === '') {
    return '—';
  }
  return `${hours} 小时`;
}

/** 工时是否来自估算，用于单独标注。 */
export function isEstimated(entry = {}) {
  return entry.hoursSource === 'ESTIMATED';
}

/** 队列位次文案。 */
export function queueLabel(entry = {}) {
  const rank = entry.queueRank;
  if (rank == null || rank === '') {
    return '—';
  }
  return `第 ${rank} 位`;
}

/** 工序文案；整单冲突没有具体工序时明确说明。 */
export function operationLabel(entry = {}) {
  const seq = entry.operationSeq;
  if (seq == null || seq === '') {
    return '工序未定';
  }
  return `${seq} · ${entry.operationName || '—'}`;
}

/** 排程汇总：区分空结果、全部排入、部分与全部无法排入。 */
export function scheduleSummary(entries = []) {
  if (!entries.length) {
    return '';
  }
  const scheduled = entries.filter((entry) => entry.status !== 'UNSCHEDULED').length;
  const unscheduled = entries.length - scheduled;
  if (!unscheduled) {
    return `已排入 ${scheduled} 道工序`;
  }
  if (!scheduled) {
    return `无法排入 ${unscheduled} 道工序`;
  }
  return `已排入 ${scheduled} 道 · 无法排入 ${unscheduled} 道`;
}
