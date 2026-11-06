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
        <el-table-column label="版本" width="70">
          <template #default="{ row }">V{{ row.version ?? 1 }}</template>
        </el-table-column>
        <el-table-column prop="processName" label="工艺名称" min-width="150" />
        <el-table-column prop="processType" label="工艺类型" width="100" />
        <el-table-column prop="owner" label="责任人" width="100" />
        <el-table-column prop="deviceMatReq" label="设备材料需求" min-width="160" />
        <el-table-column label="审批状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.approvalStatus" :map="approvalStatusMap" /></template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin() && row.approvalStatus !== 'PUBLISHED'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="isAdmin()" link type="info" @click="openDetail(row)">工序/BOM</el-button>
            <el-button v-if="isAdmin()" link type="info" @click="createVersion(row)">新建版本</el-button>
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

    <el-dialog v-model="detail.visible" :title="`工序/BOM - ${detail.processNo} V${detail.version}`" width="760px">
      <div v-if="!isEditable" class="detail-hint">已发布版本只读，如需调整请新建版本</div>

      <div class="section-title">工序路线</div>
      <div v-if="isEditable" class="detail-add">
        <el-input-number v-model="opForm.seq" :min="1" controls-position="right" style="width: 100px" />
        <el-input v-model="opForm.operationName" placeholder="工序名称" style="width: 180px" />
        <el-input v-model="opForm.deviceType" placeholder="设备类型" style="width: 150px" />
        <el-input-number v-model="opForm.estHours" :min="0" :step="0.1" :precision="2" controls-position="right" style="width: 130px" />
        <el-button v-if="!opForm.editingId" type="primary" @click="addOperation">添加</el-button>
        <template v-else>
          <el-button type="success" @click="saveOperation">保存</el-button>
          <el-button @click="resetOpForm">取消</el-button>
        </template>
      </div>
      <el-table v-if="operations.length" :data="operations" border size="small">
        <el-table-column prop="seq" label="顺序" width="70" />
        <el-table-column prop="operationName" label="工序名称" min-width="160" />
        <el-table-column prop="deviceType" label="设备类型" width="140" />
        <el-table-column prop="estHours" label="单件工时(h)" width="110" />
        <el-table-column v-if="isEditable" label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="editOperation(row)">编辑</el-button>
            <el-button link type="danger" @click="removeOperation(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="detail-empty">暂无工序，请添加</div>

      <div class="section-title">物料清单（BOM）</div>
      <div v-if="isEditable" class="detail-add">
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
        <el-table-column v-if="isEditable" label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="editBomItem(row)">编辑</el-button>
            <el-button link type="danger" @click="removeBomItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="detail-empty">暂无 BOM 项，请添加</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  processApi, processOperationApi, bomApi, materialApi,
  type Process, type ProcessOperation, type BomItem
} from '@/api';
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

async function createVersion(row: Process) {
  await ElMessageBox.confirm(`将复制 V${row.version ?? 1} 的工序与 BOM 生成下一版草稿，继续？`, '新建版本', { type: 'info' });
  const created = await processApi.createVersion(row.id!);
  ElMessage.success(`已新建 V${created.version} 草稿`);
  await load();
}

onMounted(load);

const detail = reactive({
  visible: false,
  processId: null as number | null,
  processNo: '',
  version: 1,
  approvalStatus: ''
});
const isEditable = computed(() => detail.approvalStatus !== 'PUBLISHED');
const operations = ref<ProcessOperation[]>([]);
const bomItems = ref<BomItem[]>([]);
const materials = ref<{ matCode: string; matName: string }[]>([]);
const opForm = reactive({ seq: 1, operationName: '', deviceType: '', estHours: 0, editingId: null as number | null });
const bomForm = reactive({ matCode: '', requiredQty: 1, editingId: null as number | null });

function matNameOf(code: string) {
  return materials.value.find((m) => m.matCode === code)?.matName || code;
}

async function openDetail(row: Process) {
  detail.processId = row.id!;
  detail.processNo = row.processNo || '';
  detail.version = row.version ?? 1;
  detail.approvalStatus = row.approvalStatus || '';
  detail.visible = true;
  resetOpForm();
  resetBomForm();
  await Promise.all([loadOperations(), loadBomItems(), loadMaterials()]);
}

async function loadOperations() {
  operations.value = await processOperationApi.list(detail.processId!);
}

async function loadMaterials() {
  const res = await materialApi.list({ pageNum: 1, pageSize: 200 });
  materials.value = res.records.map((m) => ({ matCode: m.matCode!, matName: m.matName }));
}

async function loadBomItems() {
  bomItems.value = await bomApi.list(detail.processId!);
}

function resetOpForm() {
  const maxSeq = operations.value.reduce((max, op) => Math.max(max, op.seq), 0);
  Object.assign(opForm, { seq: maxSeq + 1, operationName: '', deviceType: '', estHours: 0, editingId: null });
}

async function addOperation() {
  if (!opForm.operationName) {
    ElMessage.warning('请填写工序名称');
    return;
  }
  await processOperationApi.create(detail.processId!, {
    seq: opForm.seq, operationName: opForm.operationName,
    deviceType: opForm.deviceType || undefined, estHours: opForm.estHours
  });
  ElMessage.success('已添加工序');
  resetOpForm();
  loadOperations();
}

function editOperation(row: ProcessOperation) {
  Object.assign(opForm, {
    seq: row.seq, operationName: row.operationName,
    deviceType: row.deviceType || '', estHours: row.estHours ?? 0, editingId: row.id!
  });
}

async function saveOperation() {
  await processOperationApi.update(detail.processId!, opForm.editingId!, {
    seq: opForm.seq, operationName: opForm.operationName,
    deviceType: opForm.deviceType || undefined, estHours: opForm.estHours
  });
  ElMessage.success('已保存');
  resetOpForm();
  loadOperations();
}

async function removeOperation(row: ProcessOperation) {
  await ElMessageBox.confirm(`确定删除工序「${row.operationName}」吗？`, '提示', { type: 'warning' });
  await processOperationApi.remove(detail.processId!, row.id!);
  ElMessage.success('已删除');
  loadOperations();
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
  await bomApi.create(detail.processId!, { matCode: bomForm.matCode, requiredQty: bomForm.requiredQty });
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
  await bomApi.update(detail.processId!, bomForm.editingId!, { matCode: bomForm.matCode, requiredQty: bomForm.requiredQty });
  ElMessage.success('已保存');
  resetBomForm();
  loadBomItems();
}

async function removeBomItem(row: BomItem) {
  await ElMessageBox.confirm(`确定删除物料 ${row.matCode} 吗？`, '提示', { type: 'warning' });
  await bomApi.remove(detail.processId!, row.id!);
  ElMessage.success('已删除');
  loadBomItems();
}
</script>

<style scoped>
.detail-hint {
  font-size: 12px;
  color: #e6a23c;
  margin-bottom: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 16px 0 10px;
}
.section-title:first-of-type {
  margin-top: 0;
}
.detail-add {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.detail-empty {
  text-align: center;
  color: var(--zhizao-muted);
  padding: 14px 0;
  font-size: 13px;
}
</style>
