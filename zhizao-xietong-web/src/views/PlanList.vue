<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索计划号/产品" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in planStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button v-if="isAdmin()" @click="runSchedule">重排待排工单</el-button>
        <el-button v-if="isAdmin()" type="primary" @click="openCreate">＋ 新建计划</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="计划订单号" width="150">
          <template #default="{ row }"><span class="cell-code">{{ row.planOrderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="productCode" label="产品编号" width="110" />
        <el-table-column prop="productName" label="产品名称" min-width="130" />
        <el-table-column prop="planQty" label="计划数量" width="90" />
        <el-table-column prop="planStart" label="开始" width="110" />
        <el-table-column prop="planEnd" label="完成" width="110" />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }"><StatusTag :value="row.priority" :map="priorityMap" /></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :value="row.status" :map="planStatusMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin()" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="isAdmin() && row.status === 'PENDING_SCHEDULE'" link type="success" @click="dispatch(row)">下达工单</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑计划' : '新建计划'" width="540px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="产品编号" required><el-input v-model="form.productCode" /></el-form-item>
        <el-form-item label="产品名称" required><el-input v-model="form.productName" /></el-form-item>
        <el-form-item label="计划数量" required><el-input-number v-model="form.planQty" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="计划开始" required><el-date-picker v-model="form.planStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="计划完成" required><el-date-picker v-model="form.planEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="(v, k) in priorityMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产批次"><el-input v-model="form.batch" /></el-form-item>
        <el-form-item label="预计工时"><el-input-number v-model="form.estHours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dispatchDialog.visible" title="下达工单" width="500px">
      <el-form label-width="100px">
        <el-form-item label="计划订单号">
          <span class="cell-code">{{ dispatchDialog.plan?.planOrderNo }}</span>
        </el-form-item>
        <el-form-item label="生产工艺" required>
          <el-select v-model="dispatchDialog.processId" placeholder="选择已发布工艺" style="width: 100%">
            <el-option v-for="p in publishedProcesses" :key="p.id"
              :label="`${p.processNo} / v${p.version} ${p.processName}`" :value="p.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="车间">
          <el-input v-model="dispatchDialog.workshop" placeholder="缺省一车间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmDispatch">确定下达</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { planApi, processApi, scheduleApi, type Plan, type Process } from '@/api';
import { planStatusMap, priorityMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';
import { isAdmin } from '@/api/request';

const loading = ref(false);
const rows = ref<Plan[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

const dialog = reactive({ visible: false, isEdit: false });
const form = reactive<Plan>({ productCode: '', productName: '', planQty: 1, planStart: '', planEnd: '', priority: 'MEDIUM', batch: '', estHours: undefined });
const dispatchDialog = reactive<{ visible: boolean; plan: Plan | null; processId?: number; workshop: string }>({
  visible: false, plan: null, processId: undefined, workshop: '一车间'
});
const publishedProcesses = ref<Process[]>([]);

async function load() {
  loading.value = true;
  try {
    const res = await planApi.list(query);
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
  Object.assign(form, { id: undefined, productCode: '', productName: '', planQty: 1, planStart: '', planEnd: '', priority: 'MEDIUM', batch: '', estHours: undefined });
  dialog.visible = true;
}

function openEdit(row: Plan) {
  dialog.isEdit = true;
  Object.assign(form, row);
  dialog.visible = true;
}

async function save() {
  if (!form.productCode || !form.productName || !form.planStart || !form.planEnd) {
    ElMessage.warning('请填写完整的产品与计划日期');
    return;
  }
  if (dialog.isEdit) {
    await planApi.update(form.id!, form);
    ElMessage.success('已更新');
  } else {
    await planApi.create(form);
    ElMessage.success('已新建');
  }
  dialog.visible = false;
  load();
}

async function dispatch(row: Plan) {
  dispatchDialog.plan = row;
  dispatchDialog.processId = undefined;
  dispatchDialog.workshop = '一车间';
  dispatchDialog.visible = true;
  const res = await processApi.list({ pageNum: 1, pageSize: 100, approvalStatus: 'PUBLISHED' });
  publishedProcesses.value = res.records;
  if (!res.records.length) {
    ElMessage.warning('暂无已发布工艺，请先在工艺管理完成审批');
  }
}

async function confirmDispatch() {
  if (!dispatchDialog.processId) {
    ElMessage.warning('请选择已发布工艺');
    return;
  }
  await planApi.dispatch(dispatchDialog.plan!.id!, {
    processId: dispatchDialog.processId,
    workshop: dispatchDialog.workshop || undefined
  });
  ElMessage.success('已下达工单');
  dispatchDialog.visible = false;
  load();
}

async function runSchedule() {
  const result = await scheduleApi.run();
  const estimatedCount = result.scheduled.filter((entry) => entry.hoursSource === 'ESTIMATED').length;
  const estimateHint = estimatedCount
    ? `，其中 ${estimatedCount} 道工序按估算工时排程（非标准工时）`
    : '';
  if (result.unscheduledWorkOrders === 0) {
    ElMessage.success(`排程完成：${result.scheduledWorkOrders} 张工单已排入${estimateHint}`);
    return;
  }
  const detail = result.unscheduled
    .map((entry) => `工单 ${entry.workOrderId}｜${entry.conflictCode}：${entry.message}`)
    .join('；');
  await ElMessageBox.alert(
    `已排入 ${result.scheduledWorkOrders} 张${estimateHint}，无法排入 ${result.unscheduledWorkOrders} 张。${detail}`,
    '排程结果',
    { type: 'warning' }
  );
}

async function remove(row: Plan) {
  await ElMessageBox.confirm(`确定删除计划 ${row.planOrderNo} 吗？`, '提示', { type: 'warning' });
  await planApi.remove(row.id!);
  ElMessage.success('已删除');
  load();
}

onMounted(load);
</script>
