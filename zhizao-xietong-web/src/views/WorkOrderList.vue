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
        <el-table-column prop="batch" label="批次" width="90" />
        <el-table-column prop="workshop" label="车间" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :value="row.status" :map="workOrderStatusMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="start(row)">开工</el-button>
            <el-button v-if="row.status === 'IN_PRODUCTION'" link type="primary" @click="openReport(row)">报工</el-button>
            <el-button v-if="row.status === 'PENDING_QC'" link type="warning" @click="openQc(row)">质检</el-button>
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
        <el-form-item label="操作工"><el-input v-model="reportForm.operator" placeholder="如 zhangsan" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReport">提交报工</el-button>
      </template>
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
        <el-form-item label="质检员"><el-input v-model="qcForm.inspector" placeholder="如 lisi" /></el-form-item>
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
import { workOrderApi, type WorkOrder } from '@/api';
import { workOrderStatusMap } from '@/utils/dict';
import { getUser } from '@/api/request';
import StatusTag from '@/components/StatusTag.vue';

const loading = ref(false);
const rows = ref<WorkOrder[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

const reportDialog = reactive({ visible: false, orderId: undefined as number | undefined });
const reportForm = reactive({ qty: 1, qualifiedQty: 0, defectQty: 0, operator: '' });

const qcDialog = reactive({ visible: false, orderId: undefined as number | undefined });
const qcForm = reactive({ result: 'PASS', defectDesc: '', inspector: '' });

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
  const operator = getUser()?.username || 'system';
  await workOrderApi.start(row.id!, operator);
  ElMessage.success('已开工');
  load();
}

function openReport(row: WorkOrder) {
  reportDialog.orderId = row.id;
  Object.assign(reportForm, { qty: row.qty, qualifiedQty: row.qty, defectQty: 0, operator: '' });
  reportDialog.visible = true;
}

async function submitReport() {
  if (!reportForm.qty) {
    ElMessage.warning('请填写报工数量');
    return;
  }
  await workOrderApi.report(reportDialog.orderId!, reportForm);
  ElMessage.success('已报工');
  reportDialog.visible = false;
  load();
}

function openQc(row: WorkOrder) {
  qcDialog.orderId = row.id;
  Object.assign(qcForm, { result: 'PASS', defectDesc: '', inspector: '' });
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
