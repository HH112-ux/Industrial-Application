<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索编号/描述/记录人" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.mtType" placeholder="类型" clearable style="width: 140px">
          <el-option v-for="(v, k) in mtTypeMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openCreate">＋ 新增记录</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="记录编号" width="150">
          <template #default="{ row }"><span class="cell-code">{{ row.mtNo }}</span></template>
        </el-table-column>
        <el-table-column label="设备" min-width="130">
          <template #default="{ row }">{{ deviceNameOf(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }"><StatusTag :value="row.mtType" :map="mtTypeMap" /></template>
        </el-table-column>
        <el-table-column prop="operator" label="记录人" width="100" />
        <el-table-column prop="mtTime" label="记录时间" width="160" />
        <el-table-column prop="description" label="描述" min-width="140" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }"><StatusTag :value="row.resultStatus" :map="mtResultMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin()" link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑记录' : '新增记录'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="设备" required>
          <el-select v-model="form.deviceId" filterable placeholder="选择设备" style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceNo} · ${d.deviceName}`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.mtType" style="width: 100%">
            <el-option v-for="(v, k) in mtTypeMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录人"><el-input v-model="form.operator" /></el-form-item>
        <el-form-item label="记录时间"><el-date-picker v-model="form.mtTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="结果">
          <el-select v-model="form.resultStatus" style="width: 100%">
            <el-option v-for="(v, k) in mtResultMap" :key="k" :label="v.label" :value="k" />
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
import { onMounted, reactive, ref } from 'vue';
import { maintenanceApi, deviceApi, type MaintenanceRecord } from '@/api';
import { mtTypeMap, mtResultMap } from '@/utils/dict';
import { isAdmin } from '@/api/request';
import StatusTag from '@/components/StatusTag.vue';

const loading = ref(false);
const rows = ref<MaintenanceRecord[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', mtType: '' });

const devices = ref<{ id: number; deviceNo: string; deviceName: string }[]>([]);

const dialog = reactive({ visible: false, isEdit: false });
const form = reactive<MaintenanceRecord>({ deviceId: undefined as any, mtType: 'CHECK', operator: '', mtTime: '', description: '', resultStatus: 'NORMAL' });

function deviceNameOf(id: number) {
  return devices.value.find((d) => d.id === id)?.deviceName || `#${id}`;
}

async function load() {
  loading.value = true;
  try {
    const res = await maintenanceApi.list(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

async function loadDevices() {
  const res = await deviceApi.list({ pageNum: 1, pageSize: 200 });
  devices.value = res.records.map((d) => ({ id: d.id!, deviceNo: d.deviceNo!, deviceName: d.deviceName }));
}

function reset() {
  query.keyword = '';
  query.mtType = '';
  query.pageNum = 1;
  load();
}

function openCreate() {
  dialog.isEdit = false;
  Object.assign(form, { id: undefined, deviceId: undefined, mtType: 'CHECK', operator: '', mtTime: '', description: '', resultStatus: 'NORMAL' });
  dialog.visible = true;
}

function openEdit(row: MaintenanceRecord) {
  dialog.isEdit = true;
  Object.assign(form, row);
  dialog.visible = true;
}

async function save() {
  if (!form.deviceId) {
    ElMessage.warning('请选择设备');
    return;
  }
  if (dialog.isEdit) {
    await maintenanceApi.update(form.id!, form);
    ElMessage.success('已更新');
  } else {
    await maintenanceApi.create(form);
    ElMessage.success('已新增');
  }
  dialog.visible = false;
  load();
}

async function remove(row: MaintenanceRecord) {
  await ElMessageBox.confirm(`确定删除记录 ${row.mtNo} 吗？`, '提示', { type: 'warning' });
  await maintenanceApi.remove(row.id!);
  ElMessage.success('已删除');
  load();
}

onMounted(() => {
  load();
  loadDevices();
});
</script>
