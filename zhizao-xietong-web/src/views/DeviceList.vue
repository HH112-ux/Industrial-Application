<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索编号/名称/制造商/型号" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in deviceStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button v-if="isAdmin()" type="primary" @click="openCreate">＋ 新增设备</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="设备编号" width="150">
          <template #default="{ row }"><span class="cell-code">{{ row.deviceNo }}</span></template>
        </el-table-column>
        <el-table-column prop="deviceName" label="设备名称" min-width="140" />
        <el-table-column prop="deviceType" label="设备类型" width="110" />
        <el-table-column prop="manufacturer" label="制造商" width="120" />
        <el-table-column prop="model" label="型号" width="110" />
        <el-table-column prop="serialNo" label="序列号" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :value="row.status" :map="deviceStatusMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="openTelemetry(row)">实时数据</el-button>
            <el-button v-if="isAdmin()" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-dropdown v-if="isAdmin()" trigger="click" @command="(c: string) => onStatus(row, c)">
              <el-button link type="warning">状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="(v, k) in deviceStatusMap" :key="k" :command="k">{{ v.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-if="isAdmin()" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="telemetryDialog.visible" :title="`设备详情 - ${telemetryDialog.device?.deviceName || ''}`"
      width="92%" style="max-width: 760px" @closed="closeTelemetry">
      <div v-if="telemetry" class="telemetry">
        <div class="t-item"><span>设备状态</span><b>{{ deviceStatusMap[telemetry.status]?.label || telemetry.status }}</b></div>
        <div class="t-item"><span>数据来源</span><b>{{ telemetry.source || '-' }}</b></div>
        <div class="t-item"><span>温度</span><b>{{ telemetry.temperature }} ℃</b></div>
        <div class="t-item"><span>转速</span><b>{{ telemetry.rpm }} rpm</b></div>
        <div class="t-item"><span>电流</span><b>{{ telemetry.current }} A</b></div>
        <div class="t-item"><span>OEE</span><b>{{ telemetry.oee }}%</b></div>
      </div>
      <div v-else class="telemetry-empty">暂无实时数据</div>

      <div class="history-head">
        <span class="history-title">遥测历史</span>
        <el-radio-group v-model="historyRange" size="small" @change="loadHistory">
          <el-radio-button value="1h">近 1 小时</el-radio-button>
          <el-radio-button value="24h">近 24 小时</el-radio-button>
          <el-radio-button value="7d">近 7 天</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="historyLoading" class="telemetry-empty">加载中…</div>
      <div v-else-if="!historyPoints.length" class="telemetry-empty">
        所选时间范围内没有遥测记录（设备未上报，或已超出 90 天保留期）
      </div>
      <div v-show="historyPoints.length" ref="historyChartRef" class="history-chart"></div>
    </el-dialog>

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑设备' : '新增设备'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="设备名称" required><el-input v-model="form.deviceName" /></el-form-item>
        <el-form-item label="设备类型" required>
          <el-select v-model="form.deviceType" style="width: 100%">
            <el-option v-for="t in deviceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="制造商"><el-input v-model="form.manufacturer" /></el-form-item>
        <el-form-item label="型号" required><el-input v-model="form.model" /></el-form-item>
        <el-form-item label="序列号"><el-input v-model="form.serialNo" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(v, k) in deviceStatusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onBeforeUnmount, reactive, ref } from 'vue';
import * as echarts from 'echarts';
import { deviceApi, deviceDataApi, type Device, type DeviceTelemetry, type DeviceTelemetryPoint } from '@/api';
import { deviceStatusMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';
import { isAdmin } from '@/api/request';

const deviceTypes = ['加工设备', '切割设备', '检测设备', '自动化设备', '其他'];
const loading = ref(false);
const rows = ref<Device[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

const dialog = reactive({ visible: false, isEdit: false });
const form = reactive<Device>({ deviceName: '', deviceType: '加工设备', manufacturer: '', model: '', serialNo: '', status: 'STOPPED' });

async function load() {
  loading.value = true;
  try {
    const res = await deviceApi.list(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.keyword = '';
  query.status = '';
  query.pageNum = 1;
  load();
}

function openCreate() {
  dialog.isEdit = false;
  Object.assign(form, { id: undefined, deviceName: '', deviceType: '加工设备', manufacturer: '', model: '', serialNo: '', status: 'STOPPED' });
  dialog.visible = true;
}

function openEdit(row: Device) {
  dialog.isEdit = true;
  Object.assign(form, row);
  dialog.visible = true;
}

async function save() {
  if (!form.deviceName || !form.model) {
    ElMessage.warning('请填写设备名称和型号');
    return;
  }
  if (dialog.isEdit) {
    await deviceApi.update(form.id!, form);
    ElMessage.success('已更新');
  } else {
    await deviceApi.create(form);
    ElMessage.success('已新增');
  }
  dialog.visible = false;
  load();
}

async function onStatus(row: Device, status: string) {
  await deviceApi.changeStatus(row.id!, status);
  ElMessage.success('状态已更新');
  load();
}

async function remove(row: Device) {
  await ElMessageBox.confirm(`确定删除设备 ${row.deviceNo} 吗？`, '提示', { type: 'warning' });
  await deviceApi.remove(row.id!);
  ElMessage.success('已删除');
  load();
}

onMounted(load);

const telemetryDialog = reactive({ visible: false, device: null as Device | null });
const telemetry = ref<DeviceTelemetry | null>(null);
let telemetryTimer: number | undefined;

const historyRange = ref<'1h' | '24h' | '7d'>('24h');
const historyPoints = ref<DeviceTelemetryPoint[]>([]);
const historyLoading = ref(false);
const historyChartRef = ref<HTMLElement>();
let historyChart: echarts.ECharts | null = null;

async function loadTelemetry() {
  if (!telemetryDialog.device?.id) return;
  telemetry.value = await deviceDataApi.telemetry(telemetryDialog.device.id);
}

function localIso(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`
    + `T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`;
}

function rangeStart(range: '1h' | '24h' | '7d'): Date {
  const start = new Date();
  if (range === '1h') {
    start.setHours(start.getHours() - 1);
  } else if (range === '24h') {
    start.setHours(start.getHours() - 24);
  } else {
    start.setDate(start.getDate() - 7);
  }
  return start;
}

async function loadHistory() {
  const id = telemetryDialog.device?.id;
  if (!id) return;
  historyLoading.value = true;
  try {
    historyPoints.value = await deviceDataApi.history(id, {
      from: localIso(rangeStart(historyRange.value)),
      to: localIso(new Date()),
      limit: 1000
    });
    await nextTick();
    renderHistory();
  } finally {
    historyLoading.value = false;
  }
}

function renderHistory() {
  if (!historyPoints.value.length) {
    historyChart?.dispose();
    historyChart = null;
    return;
  }
  if (!historyChartRef.value) return;
  if (!historyChart) historyChart = echarts.init(historyChartRef.value);
  historyChart.setOption({
    color: ['#e8871a', '#1d5fd6'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['温度(℃)', '转速(rpm)'] },
    grid: { left: 48, right: 56, top: 36, bottom: 32 },
    xAxis: { type: 'time' },
    yAxis: [
      { type: 'value', name: '℃', scale: true },
      { type: 'value', name: 'rpm', scale: true }
    ],
    series: [
      {
        name: '温度(℃)', type: 'line', showSymbol: false, smooth: true,
        data: historyPoints.value.map((p) => [p.reportedAt, p.temperature ?? null])
      },
      {
        name: '转速(rpm)', type: 'line', yAxisIndex: 1, showSymbol: false, smooth: true,
        data: historyPoints.value.map((p) => [p.reportedAt, p.rpm ?? null])
      }
    ]
  }, true);
}

function openTelemetry(row: Device) {
  telemetryDialog.device = row;
  telemetryDialog.visible = true;
  telemetry.value = null;
  historyPoints.value = [];
  historyChart?.dispose();
  historyChart = null;
  loadTelemetry();
  telemetryTimer = window.setInterval(loadTelemetry, 3000);
  nextTick(loadHistory);
}

function closeTelemetry() {
  telemetryDialog.visible = false;
  if (telemetryTimer) {
    window.clearInterval(telemetryTimer);
    telemetryTimer = undefined;
  }
  historyChart?.dispose();
  historyChart = null;
}

function onResize() {
  historyChart?.resize();
}

window.addEventListener('resize', onResize);

onBeforeUnmount(() => {
  if (telemetryTimer) window.clearInterval(telemetryTimer);
  window.removeEventListener('resize', onResize);
  historyChart?.dispose();
});
</script>

<style scoped>
.telemetry {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 20px;
}
.t-item {
  display: flex;
  justify-content: space-between;
  border-bottom: 1px dashed var(--zhizao-rule);
  padding-bottom: 6px;
}
.t-item span {
  color: var(--zhizao-muted);
  font-size: 13px;
}
.t-item b {
  font-size: 14px;
}
.telemetry-empty {
  text-align: center;
  color: var(--zhizao-muted);
  padding: 20px;
}
.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin: 18px 0 10px;
}
.history-title {
  font-weight: 700;
  font-size: 14px;
}
.history-chart {
  width: 100%;
  height: 260px;
}
</style>
