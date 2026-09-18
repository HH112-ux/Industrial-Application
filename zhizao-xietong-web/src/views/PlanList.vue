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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { planApi, type Plan } from '@/api';
import { planStatusMap, priorityMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';
import { isAdmin } from '@/api/request';

const loading = ref(false);
const rows = ref<Plan[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

const dialog = reactive({ visible: false, isEdit: false });
const form = reactive<Plan>({ productCode: '', productName: '', planQty: 1, planStart: '', planEnd: '', priority: 'MEDIUM', batch: '', estHours: undefined });

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
  await ElMessageBox.confirm(`确定下达计划 ${row.planOrderNo} 并生成生产工单吗？`, '提示', { type: 'warning' });
  await planApi.dispatch(row.id!);
  ElMessage.success('已下达工单');
  load();
}

async function remove(row: Plan) {
  await ElMessageBox.confirm(`确定删除计划 ${row.planOrderNo} 吗？`, '提示', { type: 'warning' });
  await planApi.remove(row.id!);
  ElMessage.success('已删除');
  load();
}

onMounted(load);
</script>
