<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索编码/名称/类型" clearable style="width: 240px" @keyup.enter="load" />
        <el-select v-model="query.kitStatus" placeholder="齐套状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in kitStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openCreate">＋ 新增物料</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="物料编码" width="130">
          <template #default="{ row }"><span class="cell-code">{{ row.matCode }}</span></template>
        </el-table-column>
        <el-table-column prop="matName" label="物料名称" min-width="140" />
        <el-table-column prop="matType" label="物料类型" width="100" />
        <el-table-column prop="purchasePrice" label="采购价格" width="110" />
        <el-table-column prop="productionDate" label="生产日期" width="120" />
        <el-table-column prop="stockQty" label="库存" width="90" />
        <el-table-column prop="safeStock" label="安全库存" width="90" />
        <el-table-column label="齐套状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.kitStatus" :map="kitStatusMap" /></template>
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

    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '编辑物料' : '新增物料'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="物料名称" required><el-input v-model="form.matName" /></el-form-item>
        <el-form-item label="物料类型" required>
          <el-select v-model="form.matType" style="width: 100%">
            <el-option v-for="t in matTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购价格"><el-input-number v-model="form.purchasePrice" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="生产日期"><el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="库存数量" required><el-input-number v-model="form.stockQty" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="安全库存" required><el-input-number v-model="form.safeStock" :min="0" style="width: 100%" /></el-form-item>
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
import { materialApi, type Material } from '@/api';
import { kitStatusMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';
import { isAdmin } from '@/api/request';

const matTypes = ['原材料', '外购件', '辅料', '半成品', '成品'];
const loading = ref(false);
const rows = ref<Material[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', kitStatus: '' });

const dialog = reactive({ visible: false, isEdit: false });
const form = reactive<Material>({ matName: '', matType: '原材料', purchasePrice: undefined, productionDate: '', stockQty: 0, safeStock: 0 });

async function load() {
  loading.value = true;
  try {
    const res = await materialApi.list(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.keyword = '';
  query.kitStatus = '';
  query.pageNum = 1;
  load();
}

function openCreate() {
  dialog.isEdit = false;
  Object.assign(form, { id: undefined, matName: '', matType: '原材料', purchasePrice: undefined, productionDate: '', stockQty: 0, safeStock: 0 });
  dialog.visible = true;
}

function openEdit(row: Material) {
  dialog.isEdit = true;
  Object.assign(form, row);
  dialog.visible = true;
}

async function save() {
  if (!form.matName) {
    ElMessage.warning('请填写物料名称');
    return;
  }
  if (dialog.isEdit) {
    await materialApi.update(form.id!, form);
    ElMessage.success('已更新');
  } else {
    await materialApi.create(form);
    ElMessage.success('已新增');
  }
  dialog.visible = false;
  load();
}

async function remove(row: Material) {
  await ElMessageBox.confirm(`确定删除物料 ${row.matCode} 吗？`, '提示', { type: 'warning' });
  await materialApi.remove(row.id!);
  ElMessage.success('已删除');
  load();
}

onMounted(load);
</script>
