<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索工单号/产品/车间" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in workOrderStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="工单号" width="150">
          <template #default="{ row }"><span class="cell-code">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="130" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column label="报工进度" width="170">
          <template #default="{ row }">
            <el-progress :percentage="progressPercent(row)" :stroke-width="8" />
            <span class="progress-text">累计 {{ row.qtyDone || 0 }} / 计划 {{ row.qty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batch" label="批次" width="90" />
        <el-table-column prop="workshop" label="车间" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :value="row.status" :map="workOrderStatusMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="start(row)">开工</el-button>
            <el-tooltip v-if="row.status === 'IN_PRODUCTION' && remainingQty(row) <= 0"
              content="已报满，无可报数量" placement="top">
              <span><el-button link type="primary" disabled>报工</el-button></span>
            </el-tooltip>
            <el-button v-else-if="row.status === 'IN_PRODUCTION'" link type="primary" @click="openReport(row)">报工</el-button>
            <el-button v-if="row.status === 'PENDING_QC'" link type="warning" @click="openQc(row)">质检</el-button>
            <el-button link type="primary" @click="openSchedule(row)">排程</el-button>
            <el-button link type="info" @click="openHistory(row)">流转记录</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="reportDialog.visible" title="报工" width="460px">
      <el-form :model="reportForm" label-width="90px">
        <el-form-item label="报工数量" required><el-input-number v-model="reportForm.qty" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="合格数量"><el-input-number v-model="reportForm.qualifiedQty" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="不良数量"><el-input-number v-model="reportForm.defectQty" :min="0" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReport">提交报工</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyDialog.visible" title="工单流转记录" width="92%" style="max-width: 620px">
      <div class="history-order">{{ historyDialog.orderNo }}</div>
      <div v-if="historyDialog.loading" class="history-empty">加载中…</div>
      <el-timeline v-else-if="historyDialog.rows.length">
        <el-timeline-item v-for="h in historyDialog.rows" :key="h.id" :timestamp="formatTime(h.createdAt)" placement="top">
          <div class="history-line">
            <StatusTag :value="h.toStatus" :map="workOrderStatusMap" />
            <span class="history-op">{{ h.operator || 'system' }}</span>
          </div>
          <div class="history-desc">
            <span v-if="h.fromStatus">
              从「{{ workOrderStatusMap[h.fromStatus]?.label || h.fromStatus }}」流转
            </span>
            <span v-if="h.remark"> · {{ h.remark }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
      <div v-else class="history-empty">暂无流转记录</div>
    </el-dialog>

    <el-dialog v-model="scheduleDialog.visible" title="排程落点" width="92%" style="max-width: 820px">
      <div class="history-order">{{ scheduleDialog.orderNo }}</div>
      <el-table :data="scheduleDialog.rows" v-loading="scheduleDialog.loading" border size="small"
        empty-text="该工单尚未排程：可能未运行排产，或无法排入">
        <el-table-column label="队列" width="76">
          <template #default="{ row }">第 {{ row.queueRank }} 位</template>
        </el-table-column>
        <el-table-column label="工序" width="140">
          <template #default="{ row }">{{ row.operationSeq }} · {{ row.operationName || '—' }}</template>
        </el-table-column>
        <el-table-column label="设备" min-width="130">
          <template #default="{ row }">{{ row.deviceName || row.deviceNo || row.deviceType || '—' }}</template>
        </el-table-column>
        <el-table-column label="占用时间" min-width="220">
          <template #default="{ row }">{{ formatWindow(row.startTime, row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="工时" width="120">
          <template #default="{ row }">
            {{ row.durationHours != null ? `${row.durationHours} 小时` : '—' }}
            <span v-if="row.hoursSource === 'ESTIMATED'" class="estimate-tag">估算</span>
          </template>
        </el-table-column>
        <el-table-column label="排产依据" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.status === 'UNSCHEDULED' ? row.conflictCode : row.rankBasis }}</template>
        </el-table-column>
        <el-table-column prop="message" label="落位说明" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-dialog v-model="qcDialog.visible" title="质检" width="460px">
      <el-form :model="qcForm" label-width="90px">
        <el-form-item label="质检结论" required>
          <el-radio-group v-model="qcForm.result">
            <el-radio value="PASS">合格</el-radio>
            <el-radio value="FAIL">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="不良描述"><el-input v-model="qcForm.defectDesc" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitQc">提交质检</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { scheduleApi, workOrderApi, type ScheduleEntry, type WorkOrder, type WorkOrderHistory } from '@/api';
import { workOrderStatusMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';

const loading = ref(false);
const rows = ref<WorkOrder[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

const reportDialog = reactive({ visible: false, orderId: undefined as number | undefined });
const reportForm = reactive({
  qty: 1, qualifiedQty: 0, defectQty: 0,
  idempotencyKey: ''
});

const qcDialog = reactive({ visible: false, orderId: undefined as number | undefined });
const qcForm = reactive({ result: 'PASS', defectDesc: '' });

const historyDialog = reactive({
  visible: false,
  orderNo: '',
  loading: false,
  rows: [] as WorkOrderHistory[]
});

const scheduleDialog = reactive({
  visible: false,
  orderNo: '',
  loading: false,
  rows: [] as ScheduleEntry[]
});

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}

function formatWindow(start?: string, end?: string): string {
  return start && end ? `${formatTime(start)} ~ ${formatTime(end)}` : '未落位';
}

function newIdempotencyKey(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

// 报工累计口径在返工场景会超过计划数量，进度条封顶 100%，绝对数另行展示。
function progressPercent(row: WorkOrder): number {
  const planned = Number(row.qty) || 0;
  const done = Number(row.qtyDone) || 0;
  if (planned <= 0) {
    return done > 0 ? 100 : 0;
  }
  return Math.min(100, Math.round((done / planned) * 100));
}

async function load() {
  loading.value = true;
  try {
    const res = await workOrderApi.list(query);
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

async function start(row: WorkOrder) {
  await workOrderApi.start(row.id!);
  ElMessage.success('已开工');
  load();
}

function remainingQty(row: WorkOrder): number {
  // 质检失败会释放返工额度，可报上限按「计划 + 可返工 - 已报」计算。
  const limit = (Number(row.qty) || 0) + (Number(row.qtyRework) || 0);
  return Math.max(limit - (Number(row.qtyDone) || 0), 0);
}

function openReport(row: WorkOrder) {
  const remaining = remainingQty(row);
  if (remaining <= 0) {
    ElMessage.warning('已报满，无可报数量');
    return;
  }
  reportDialog.orderId = row.id;
  Object.assign(reportForm, {
    qty: remaining,
    qualifiedQty: remaining,
    defectQty: 0,
    idempotencyKey: newIdempotencyKey()
  });
  reportDialog.visible = true;
}

async function submitReport() {
  const qty = Number(reportForm.qty);
  const qualified = Number(reportForm.qualifiedQty);
  const defect = Number(reportForm.defectQty);
  if (!qty || qty <= 0) {
    ElMessage.warning('报工数量必须大于 0');
    return;
  }
  if (qualified < 0 || defect < 0) {
    ElMessage.warning('合格数量与不良数量不能为负');
    return;
  }
  if (qualified + defect !== qty) {
    ElMessage.warning('合格数量与不良数量之和必须等于报工数量');
    return;
  }
  await workOrderApi.report(reportDialog.orderId!, {
    qty,
    qualifiedQty: qualified,
    defectQty: defect,
    idempotencyKey: reportForm.idempotencyKey
  });
  ElMessage.success('已报工');
  reportDialog.visible = false;
  load();
}

async function openHistory(row: WorkOrder) {
  historyDialog.visible = true;
  historyDialog.orderNo = row.orderNo || '';
  historyDialog.loading = true;
  historyDialog.rows = [];
  try {
    historyDialog.rows = await workOrderApi.history(row.id!);
  } finally {
    historyDialog.loading = false;
  }
}

async function openSchedule(row: WorkOrder) {
  scheduleDialog.visible = true;
  scheduleDialog.orderNo = `${row.orderNo || ''} · ${row.productName || ''}`;
  scheduleDialog.loading = true;
  scheduleDialog.rows = [];
  try {
    scheduleDialog.rows = await scheduleApi.entries(row.id!);
  } finally {
    scheduleDialog.loading = false;
  }
}

function openQc(row: WorkOrder) {
  qcDialog.orderId = row.id;
  Object.assign(qcForm, { result: 'PASS', defectDesc: '' });
  qcDialog.visible = true;
}

async function submitQc() {
  await workOrderApi.qc(qcDialog.orderId!, qcForm);
  ElMessage.success('质检完成');
  qcDialog.visible = false;
  load();
}

onMounted(load);
</script>

<style scoped>
.progress-text {
  display: block;
  margin-top: 4px;
  color: var(--zhizao-muted);
  font-size: 12px;
}
.history-order {
  color: var(--zhizao-muted);
  font-size: 13px;
  margin-bottom: 12px;
}
.history-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
.history-op {
  color: var(--zhizao-ink);
  font-size: 13px;
  font-weight: 600;
}
.history-desc {
  color: var(--zhizao-muted);
  font-size: 13px;
  margin-top: 4px;
}
.history-empty {
  text-align: center;
  color: var(--zhizao-muted);
  padding: 24px 0;
}
.estimate-tag {
  color: var(--zhizao-accent2);
  font-size: 12px;
  margin-left: 4px;
}
</style>
