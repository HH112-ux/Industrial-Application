<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" :clearable="false"
          range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
        <el-button type="primary" @click="load">查询</el-button>
        <span class="hint">仅展示所选时段内有排程的设备，最长 31 天</span>
      </div>

      <div v-loading="loading" class="board">
        <div v-if="!rows.length" class="empty">所选时段内没有排程占用</div>
        <template v-else>
          <div class="axis">
            <div class="device-col">设备</div>
            <div class="track axis-track">
              <span v-for="tick in ticks" :key="tick.key" class="tick" :style="{ left: tick.left + '%' }">
                {{ tick.label }}
              </span>
            </div>
          </div>
          <div v-for="row in rows" :key="row.deviceId" class="row">
            <div class="device-col">
              <div class="device-no">{{ row.deviceNo || `#${row.deviceId}` }}</div>
              <div class="device-name">
                {{ row.deviceName || row.deviceType || '' }}<span v-if="row.workshop"> · {{ row.workshop }}</span>
              </div>
            </div>
            <div class="track">
              <div v-for="bar in row.bars" :key="bar.key" class="bar"
                :class="{ estimated: bar.entry.hoursSource === 'ESTIMATED', occupied: bar.entry.occupied }"
                :style="{ left: bar.left + '%', width: bar.width + '%' }" :title="barTitle(bar.entry)">
                <template v-if="!bar.entry.occupied">
                  {{ bar.entry.orderNo || `工单 ${bar.entry.workOrderId}` }}
                </template>
              </div>
            </div>
          </div>
          <div class="legend">
            <span><i class="dot" />标准工时</span>
            <span><i class="dot estimated" />估算工时</span>
            <span><i class="dot occupied" />其他车间占用（仅显示占用时段，不含工单信息）</span>
            <span class="legend-hint">时间轴按 {{ range[0] }} 至 {{ range[1] }} 全时段展开</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { scheduleApi, type ScheduleEntry } from '@/api';

interface Bar {
  entry: ScheduleEntry;
  /** 占用块没有排程主键，用设备与时间窗组合出稳定 key */
  key: string;
  left: number;
  width: number;
}

interface DeviceRow {
  deviceId: number;
  deviceNo?: string;
  deviceName?: string;
  deviceType?: string;
  workshop?: string;
  bars: Bar[];
}

const DAY_MS = 86400000;

const loading = ref(false);
const range = ref<[string, string]>(defaultRange());
const entries = ref<ScheduleEntry[]>([]);

function isoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function defaultRange(): [string, string] {
  const start = new Date();
  return [isoDate(start), isoDate(new Date(start.getTime() + 6 * DAY_MS))];
}

const fromMs = computed(() => new Date(`${range.value[0]}T00:00:00`).getTime());
const toMs = computed(() => new Date(`${range.value[1]}T00:00:00`).getTime() + DAY_MS);
const spanMs = computed(() => Math.max(toMs.value - fromMs.value, 1));

// 按设备分组并把占用区间换算成百分比；跨出所选时段的区段裁剪到边界，不伪造时间。
const rows = computed<DeviceRow[]>(() => {
  const grouped = new Map<number, DeviceRow>();
  for (const entry of entries.value) {
    if (entry.deviceId == null || !entry.startTime || !entry.endTime) {
      continue;
    }
    const start = Math.max(new Date(entry.startTime).getTime(), fromMs.value);
    const end = Math.min(new Date(entry.endTime).getTime(), toMs.value);
    if (end <= start) {
      continue;
    }
    let row = grouped.get(entry.deviceId);
    if (!row) {
      row = {
        deviceId: entry.deviceId,
        deviceNo: entry.deviceNo,
        deviceName: entry.deviceName,
        deviceType: entry.deviceType,
        workshop: entry.deviceWorkshop,
        bars: []
      };
      grouped.set(entry.deviceId, row);
    }
    row.bars.push({
      entry,
      key: entry.occupied ? `occupied-${entry.deviceId}-${entry.startTime}-${entry.endTime}` : `${entry.id}`,
      left: ((start - fromMs.value) / spanMs.value) * 100,
      width: Math.max(((end - start) / spanMs.value) * 100, 0.4)
    });
  }
  return [...grouped.values()].sort((a, b) => (a.deviceNo || '').localeCompare(b.deviceNo || ''));
});

const ticks = computed(() => {
  const days = Math.round(spanMs.value / DAY_MS);
  const step = days <= 7 ? 1 : days <= 14 ? 2 : 5;
  const result: { key: number; label: string; left: number }[] = [];
  for (let index = 0; index < days; index += step) {
    const date = new Date(fromMs.value + index * DAY_MS);
    result.push({
      key: index,
      label: `${date.getMonth() + 1}-${date.getDate()}`,
      left: (index / days) * 100
    });
  }
  return result;
});

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
}

function barTitle(entry: ScheduleEntry): string {
  if (entry.occupied) {
    return `该时段已被其他车间占用｜${formatTime(entry.startTime)} ~ ${formatTime(entry.endTime)}`;
  }
  const device = entry.deviceName || entry.deviceNo || entry.deviceType || '—';
  const deviceLabel = entry.deviceWorkshop ? `${device}（${entry.deviceWorkshop}）` : device;
  const source = entry.hoursSource === 'ESTIMATED' ? '估算工时' : '标准工时';
  return `${entry.orderNo || `工单 ${entry.workOrderId}`}｜${entry.productName || ''}`
    + `｜工序 ${entry.operationSeq} ${entry.operationName || ''}｜${deviceLabel}`
    + `｜${formatTime(entry.startTime)} ~ ${formatTime(entry.endTime)}`
    + `｜${entry.durationHours ?? '—'} 小时（${source}）｜队列第 ${entry.queueRank} 位：${entry.rankBasis}`;
}

async function load() {
  loading.value = true;
  try {
    entries.value = await scheduleApi.deviceTimeline(range.value[0], range.value[1]);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.hint {
  color: var(--zhizao-muted);
  font-size: 13px;
}
.board {
  min-height: 120px;
}
.empty {
  text-align: center;
  color: var(--zhizao-muted);
  padding: 40px 0;
}
.axis {
  display: flex;
  align-items: flex-end;
  border-bottom: 1px solid var(--zhizao-rule);
  padding-bottom: 6px;
}
.device-col {
  width: 150px;
  flex: none;
  color: var(--zhizao-muted);
  font-size: 13px;
}
.track {
  flex: 1;
  position: relative;
  min-width: 0;
}
.axis-track {
  height: 20px;
}
.tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  color: var(--zhizao-muted);
  font-size: 12px;
  white-space: nowrap;
}
.tick:first-child {
  transform: none;
}
.row {
  display: flex;
  align-items: stretch;
  border-bottom: 1px solid var(--zhizao-rule);
}
.row .device-col {
  padding: 8px 8px 8px 0;
}
.row .track {
  height: 46px;
}
.device-no {
  font-weight: 600;
  font-size: 13px;
  color: var(--zhizao-ink);
}
.device-name {
  color: var(--zhizao-muted);
  font-size: 12px;
  margin-top: 2px;
}
.bar {
  position: absolute;
  top: 9px;
  height: 28px;
  line-height: 28px;
  background: var(--zhizao-accent);
  color: #fff;
  font-size: 12px;
  border-radius: 4px;
  padding: 0 6px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  box-sizing: border-box;
}
.bar.estimated {
  background: var(--zhizao-accent2);
}
/* 占用块：斜纹灰块，与空闲底色和本车间工单色块都能明显区分 */
.bar.occupied {
  background: repeating-linear-gradient(45deg, #d9dde3 0 6px, #c4c9d1 6px 12px);
  border: 1px solid #b4bac3;
  cursor: help;
}
.legend {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 12px;
  color: var(--zhizao-muted);
  font-size: 12px;
}
.legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: var(--zhizao-accent);
  margin-right: 6px;
}
.legend .dot.estimated {
  background: var(--zhizao-accent2);
}
.legend .dot.occupied {
  background: repeating-linear-gradient(45deg, #d9dde3 0 4px, #c4c9d1 4px 8px);
  border: 1px solid #b4bac3;
}
.legend-hint {
  margin-left: auto;
}
</style>
