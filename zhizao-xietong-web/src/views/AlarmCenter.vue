<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px" @change="reload">
          <el-option v-for="(v, k) in alarmStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-tag :type="unread > 0 ? 'danger' : 'success'" effect="light">未读 {{ unread }}</el-tag>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="设备" min-width="160">
          <template #default="{ row }">
            <div>{{ deviceName(row.deviceId) }}</div>
            <div class="cell-sub">{{ deviceNo(row.deviceId) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><StatusTag :value="row.ruleType" :map="alarmRuleTypeMap" /></template>
        </el-table-column>
        <el-table-column label="指标" width="100">
          <template #default="{ row }">{{ row.metric || '—' }}</template>
        </el-table-column>
        <el-table-column label="触发值" width="100">
          <template #default="{ row }">{{ row.triggerValue == null ? '—' : row.triggerValue }}</template>
        </el-table-column>
        <el-table-column prop="thresholdSnapshot" label="阈值快照" min-width="190" show-overflow-tooltip />
        <el-table-column label="开始时间" width="165">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="165">
          <template #default="{ row }">{{ fmtTime(row.recoverTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }"><StatusTag :value="row.status" :map="alarmStatusMap" /></template>
        </el-table-column>
        <el-table-column label="确认人" width="110">
          <template #default="{ row }">{{ row.ackUser || '—' }}</template>
        </el-table-column>
        <el-table-column label="确认时间" width="165">
          <template #default="{ row }">{{ fmtTime(row.ackTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'OPEN'" @click="acknowledge(row)">确认</el-button>
            <el-button link type="warning" :disabled="row.status === 'RECOVERED'" @click="recover(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { alarmApi, deviceApi, type AlarmRecord } from '@/api';
import { alarmRuleTypeMap, alarmStatusMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';

const loading = ref(false);
const rows = ref<AlarmRecord[]>([]);
const total = ref(0);
const unread = ref(0);
const devices = ref<Record<number, { name: string; no: string }>>({});
const query = reactive({ pageNum: 1, pageSize: 10, status: '' });

let listSeq = 0;

function deviceName(id?: number) {
  return id == null ? '—' : devices.value[id]?.name || `设备#${id}`;
}
function deviceNo(id?: number) {
  return id == null ? '' : devices.value[id]?.no || '';
}
function fmtTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
}

async function load() {
  const seq = ++listSeq;
  loading.value = true;
  try {
    const res = await alarmApi.list({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined
    });
    if (seq !== listSeq) {
      return;
    }
    rows.value = res.records;
    total.value = res.total;
  } finally {
    if (seq === listSeq) {
      loading.value = false;
    }
  }
}

function reload() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.status = '';
  query.pageNum = 1;
  load();
}

async function loadUnread() {
  unread.value = await alarmApi.unreadCount();
}

async function loadDevices() {
  const res = await deviceApi.list({ pageNum: 1, pageSize: 500 });
  const map: Record<number, { name: string; no: string }> = {};
  res.records.forEach((d) => {
    if (d.id != null) {
      map[d.id] = { name: d.deviceName, no: d.deviceNo || '' };
    }
  });
  devices.value = map;
}

async function acknowledge(row: AlarmRecord) {
  if (row.id == null) {
    return;
  }
  try {
    await alarmApi.acknowledge(row.id);
    ElMessage.success('已确认');
  } catch {
    // 并发冲突等错误由请求拦截器提示，重新加载拿到最新状态
  }
  await Promise.all([load(), loadUnread()]);
}

async function recover(row: AlarmRecord) {
  if (row.id == null) {
    return;
  }
  try {
    await alarmApi.recover(row.id);
    ElMessage.success('已恢复');
  } catch {
    // 并发冲突等错误由请求拦截器提示，重新加载拿到最新状态
  }
  await Promise.all([load(), loadUnread()]);
}

onMounted(() => {
  load();
  loadUnread();
  loadDevices();
});
</script>

<style scoped>
.cell-sub {
  color: var(--zhizao-muted, #8a94a6);
  font-size: 12px;
}
</style>
