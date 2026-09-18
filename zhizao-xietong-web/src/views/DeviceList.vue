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
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue';
import { deviceApi, deviceDataApi, type Device, type DeviceTelemetry } from '@/api';
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

async function loadTelemetry() {
  if (!telemetryDialog.device?.id) return;
  telemetry.value = await deviceDataApi.telemetry(telemetryDialog.device.id);
}

function openTelemetry(row: Device) {
  telemetryDialog.device = row;
  telemetryDialog.visible = true;
  telemetry.value = null;
  loadTelemetry();
  telemetryTimer = window.setInterval(loadTelemetry, 3000);
}

function closeTelemetry() {
  telemetryDialog.visible = false;
  if (telemetryTimer) {
    window.clearInterval(telemetryTimer);
    telemetryTimer = undefined;
  }
}

onBeforeUnmount(() => {
  if (telemetryTimer) window.clearInterval(telemetryTimer);
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
</style>
