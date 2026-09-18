<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索编号/名称/责任人" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.approvalStatus" placeholder="审批状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in approvalStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button v-if="isAdmin()" type="primary" @click="openCreate">＋ 新增工艺</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="工艺编号" width="130">
          <template #default="{ row }"><span class="cell-code">{{ row.processNo }}</span></template>
        </el-table-column>
        <el-table-column prop="processName" label="工艺名称" min-width="150" />
        <el-table-column prop="processType" label="工艺类型" width="100" />
        <el-table-column prop="owner" label="责任人" width="100" />
        <el-table-column prop="deviceMatReq" label="设备材料需求" min-width="160" />
        <el-table-column label="审批状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.approvalStatus" :map="approvalStatusMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin()" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="isAdmin()" link type="info" @click="openBom(row)">BOM</el-button>
            <el-button v-if="isAdmin() && row.approvalStatus === 'DRAFT'" link type="warning" @click="submit(row)">提交</el-button>
            <el-button v-if="isAdmin() && row.approvalStatus === 'PENDING'" link type="success" @click="approve(row)">通过</el-button>
            <el-button v-if="isAdmin() && row.approvalStatus === 'PENDING'" link type="danger" @click="reject(row)">驳回</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑工艺' : '新增工艺'" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="工艺名称" required><el-input v-model="form.processName" /></el-form-item>
        <el-form-item label="工艺类型" required>
          <el-select v-model="form.processType" style="width: 100%">
            <el-option v-for="t in processTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人" required><el-input v-model="form.owner" /></el-form-item>
        <el-form-item label="设备材料需求"><el-input v-model="form.deviceMatReq" placeholder="设备与材料需求描述" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bomDialog.visible" :title="`BOM - ${bomDialog.processName}`" width="640px">
      <div class="bom-toolbar">
        <span class="bom-label">版本：</span>
        <el-select v-model="bomVersion" style="width: 120px" @change="onVersionChange">
          <el-option v-for="v in bomVersions" :key="v" :label="`V${v}`" :value="v" />
        </el-select>
        <el-button v-if="isAdmin()" type="primary" plain @click="newBomVersion">＋ 新建版本</el-button>
        <span v-if="!isLatestVersion" class="bom-hint">历史版本只读</span>
      </div>

      <div v-if="isLatestVersion" class="bom-add">
        <el-select v-model="bomForm.matCode" filterable placeholder="选择物料" style="width: 240px">
          <el-option v-for="m in materials" :key="m.matCode" :label="`${m.matCode} · ${m.matName}`" :value="m.matCode" />
        </el-select>
        <el-input-number v-model="bomForm.requiredQty" :min="1" />
        <el-button v-if="!bomForm.editingId" type="primary" @click="addBomItem">添加</el-button>
        <template v-else>
          <el-button type="success" @click="saveBomEdit">保存</el-button>
          <el-button @click="cancelBomEdit">取消</el-button>
        </template>
      </div>

      <el-table v-if="bomItems.length" :data="bomItems" border size="small">
        <el-table-column prop="matCode" label="物料编码" width="130" />
        <el-table-column label="物料名称" min-width="140">
          <template #default="{ row }">{{ matNameOf(row.matCode) }}</template>
        </el-table-column>
        <el-table-column prop="requiredQty" label="单件用量" width="100" />
        <el-table-column v-if="isLatestVersion" label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="editBomItem(row)">编辑</el-button>
            <el-button link type="danger" @click="removeBomItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="bom-empty">暂无 BOM 项，请添加</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { processApi, bomApi, materialApi, type Process, type BomItem } from '@/api';
import { approvalStatusMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';
import { isAdmin } from '@/api/request';

const processTypes = ['机加工', '下料', '装配', '检测', '其他'];
const loading = ref(false);
const rows = ref<Process[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', approvalStatus: '' });

const dialog = reactive({ visible: false, isEdit: false });
const form = reactive<Process>({ processName: '', processType: '机加工', owner: '', deviceMatReq: '' });

async function load() {
  loading.value = true;
  try {
    const res = await processApi.list(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.keyword = '';
  query.approvalStatus = '';
  query.pageNum = 1;
  load();
}

function openCreate() {
  dialog.isEdit = false;
  Object.assign(form, { id: undefined, processName: '', processType: '机加工', owner: '', deviceMatReq: '' });
  dialog.visible = true;
}

function openEdit(row: Process) {
  dialog.isEdit = true;
  Object.assign(form, row);
  dialog.visible = true;
}

async function save() {
  if (!form.processName || !form.owner) {
    ElMessage.warning('请填写工艺名称和责任人');
    return;
  }
  if (dialog.isEdit) {
    await processApi.update(form.id!, form);
    ElMessage.success('已更新');
  } else {
    await processApi.create(form);
    ElMessage.success('已新增');
  }
  dialog.visible = false;
  load();
}

async function submit(row: Process) {
  await processApi.submit(row.id!);
  ElMessage.success('已提交审批');
  load();
}

async function approve(row: Process) {
  await processApi.approve(row.id!);
  ElMessage.success('已发布');
  load();
}

async function reject(row: Process) {
  await processApi.reject(row.id!);
  ElMessage.success('已驳回');
  load();
}

async function remove(row: Process) {
  await ElMessageBox.confirm(`确定删除工艺 ${row.processNo} 吗？`, '提示', { type: 'warning' });
  await processApi.remove(row.id!);
  ElMessage.success('已删除');
  load();
}

onMounted(load);

const bomDialog = reactive({ visible: false, processId: null as number | null, processName: '' });
const bomItems = ref<BomItem[]>([]);
const materials = ref<{ matCode: string; matName: string }[]>([]);
const bomForm = reactive({ matCode: '', requiredQty: 1, editingId: null as number | null });
const bomVersion = ref<number>(1);
const bomVersions = ref<number[]>([]);
const isLatestVersion = computed(() => bomVersions.value.length === 0 || bomVersion.value === bomVersions.value[0]);

function matNameOf(code: string) {
  return materials.value.find((m) => m.matCode === code)?.matName || code;
}

async function openBom(row: Process) {
  bomDialog.processId = row.id!;
  bomDialog.processName = row.processName;
  bomDialog.visible = true;
  resetBomForm();
  await Promise.all([loadBomVersions(), loadMaterials()]);
}

async function loadBomVersions() {
  bomVersions.value = await bomApi.versions(bomDialog.processId!);
  bomVersion.value = bomVersions.value.length ? bomVersions.value[0] : 1;
  await loadBomItems();
}

async function newBomVersion() {
  await ElMessageBox.confirm('将复制当前最新版本的 BOM 项生成新版本，继续？', '新建版本', { type: 'info' });
  const v = await bomApi.newVersion(bomDialog.processId!);
  ElMessage.success(`已新建版本 V${v}`);
  await loadBomVersions();
}

function onVersionChange() {
  resetBomForm();
  loadBomItems();
}

async function loadMaterials() {
  const res = await materialApi.list({ pageNum: 1, pageSize: 200 });
  materials.value = res.records.map((m) => ({ matCode: m.matCode!, matName: m.matName }));
}

async function loadBomItems() {
  bomItems.value = await bomApi.list(bomDialog.processId!);
}

function resetBomForm() {
  bomForm.matCode = '';
  bomForm.requiredQty = 1;
  bomForm.editingId = null;
}

async function addBomItem() {
  if (!bomForm.matCode) {
    ElMessage.warning('请选择物料');
    return;
  }
  await bomApi.create(bomDialog.processId!, { matCode: bomForm.matCode, requiredQty: bomForm.requiredQty });
  ElMessage.success('已添加');
  resetBomForm();
  loadBomItems();
}

function editBomItem(row: BomItem) {
  bomForm.matCode = row.matCode;
  bomForm.requiredQty = row.requiredQty;
  bomForm.editingId = row.id!;
}

function cancelBomEdit() {
  resetBomForm();
}

async function saveBomEdit() {
  if (!bomForm.matCode) {
    ElMessage.warning('请选择物料');
    return;
  }
  await bomApi.update(bomDialog.processId!, bomForm.editingId!, { matCode: bomForm.matCode, requiredQty: bomForm.requiredQty });
  ElMessage.success('已保存');
  resetBomForm();
  loadBomItems();
}

async function removeBomItem(row: BomItem) {
  await ElMessageBox.confirm(`确定删除物料 ${row.matCode} 吗？`, '提示', { type: 'warning' });
  await bomApi.remove(bomDialog.processId!, row.id!);
  ElMessage.success('已删除');
  loadBomItems();
}
</script>

<style scoped>
.bom-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.bom-label {
  font-size: 13px;
  color: var(--zhizao-muted);
}
.bom-hint {
  font-size: 12px;
  color: #e6a23c;
}
.bom-add {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.bom-empty {
  text-align: center;
  color: var(--zhizao-muted);
  padding: 14px 0;
  font-size: 13px;
}
</style>
