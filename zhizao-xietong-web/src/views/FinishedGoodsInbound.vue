<template>
  <div class="page">
    <div class="page-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="待入库" name="inbound">
          <div class="toolbar">
            <el-input v-model="inboundQuery.keyword" placeholder="搜索产品编码/批次/车间" clearable
              style="width: 240px" @keyup.enter="loadInbounds" />
            <el-select v-model="inboundQuery.status" placeholder="状态" clearable style="width: 140px">
              <el-option v-for="(v, k) in inboundStatusMap" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-button type="primary" @click="searchInbounds">查询</el-button>
            <el-button @click="resetInbounds">重置</el-button>
          </div>

          <el-table :data="inboundRows" v-loading="inboundLoading" border stripe>
            <el-table-column label="产品编码" min-width="140">
              <template #default="{ row }"><span class="cell-code">{{ row.productCode }}</span></template>
            </el-table-column>
            <el-table-column prop="qty" label="入库数量" width="100" />
            <el-table-column prop="batch" label="批次" width="130" />
            <el-table-column prop="workshop" label="车间" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><StatusTag :value="row.status" :map="inboundStatusMap" /></template>
            </el-table-column>
            <el-table-column label="经手人" width="110">
              <template #default="{ row }">{{ row.operator || '—' }}</template>
            </el-table-column>
            <el-table-column label="入库时间" width="170">
              <template #default="{ row }">{{ formatTime(row.inboundTime) || '—' }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.cancelReason || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button link type="success" @click="openConfirm(row)">确认入库</el-button>
                  <el-button link type="danger" @click="openCancel(row)">取消</el-button>
                </template>
                <span v-else class="done-text">已处理</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination background layout="total, sizes, prev, pager, next" :total="inboundTotal"
              v-model:current-page="inboundQuery.pageNum" v-model:page-size="inboundQuery.pageSize"
              :page-sizes="[10, 20, 50]" @current-change="loadInbounds" @size-change="searchInbounds" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="成品库存" name="stock">
          <div class="toolbar">
            <el-input v-model="stockQuery.keyword" placeholder="搜索产品编码/车间" clearable
              style="width: 240px" @keyup.enter="loadStocks" />
            <el-button type="primary" @click="searchStocks">查询</el-button>
            <el-button @click="resetStocks">重置</el-button>
          </div>

          <el-table :data="stockRows" v-loading="stockLoading" border stripe>
            <el-table-column label="产品编码" min-width="160">
              <template #default="{ row }"><span class="cell-code">{{ row.productCode }}</span></template>
            </el-table-column>
            <el-table-column prop="workshop" label="车间" width="120" />
            <el-table-column prop="qty" label="库存数量" width="120" />
            <el-table-column label="最近更新" width="180">
              <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) || '—' }}</template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination background layout="total, sizes, prev, pager, next" :total="stockTotal"
              v-model:current-page="stockQuery.pageNum" v-model:page-size="stockQuery.pageSize"
              :page-sizes="[10, 20, 50]" @current-change="loadStocks" @size-change="searchStocks" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="confirmDialog.visible" title="确认入库" width="440px">
      <el-form label-width="90px">
        <el-form-item label="产品编码">{{ confirmDialog.productCode }}</el-form-item>
        <el-form-item label="入库数量">{{ confirmDialog.qty }}</el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="confirmDialog.batch" maxlength="64" show-word-limit
            placeholder="留空沿用原批次" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="confirmDialog.submitting" @click="submitConfirm">确认入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelDialog.visible" title="取消入库单" width="440px">
      <el-form label-width="90px">
        <el-form-item label="产品编码">{{ cancelDialog.productCode }}</el-form-item>
        <el-form-item label="取消原因" required>
          <el-input v-model="cancelDialog.reason" type="textarea" :rows="3" maxlength="255" show-word-limit
            placeholder="取消原因将记录在单据上" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelDialog.visible = false">返回</el-button>
        <el-button type="danger" :loading="cancelDialog.submitting" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { finishedGoodsApi, type FinishedGoodsInbound, type FinishedGoodsStock } from '@/api';
import { inboundStatusMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';

const activeTab = ref('inbound');

const inboundLoading = ref(false);
const inboundRows = ref<FinishedGoodsInbound[]>([]);
const inboundTotal = ref(0);
const inboundQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' });

const stockLoading = ref(false);
const stockRows = ref<FinishedGoodsStock[]>([]);
const stockTotal = ref(0);
const stockQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '' });
const stockLoaded = ref(false);

const confirmDialog = reactive({
  visible: false,
  submitting: false,
  id: 0,
  productCode: '',
  qty: 0,
  batch: ''
});

const cancelDialog = reactive({
  visible: false,
  submitting: false,
  id: 0,
  productCode: '',
  reason: ''
});

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}

async function loadInbounds() {
  inboundLoading.value = true;
  try {
    const res = await finishedGoodsApi.inbounds(inboundQuery);
    inboundRows.value = res.records;
    inboundTotal.value = res.total;
  } finally {
    inboundLoading.value = false;
  }
}

function searchInbounds() {
  inboundQuery.pageNum = 1;
  loadInbounds();
}

function resetInbounds() {
  inboundQuery.keyword = '';
  inboundQuery.status = '';
  searchInbounds();
}

async function loadStocks() {
  stockLoading.value = true;
  try {
    const res = await finishedGoodsApi.stocks(stockQuery);
    stockRows.value = res.records;
    stockTotal.value = res.total;
    stockLoaded.value = true;
  } finally {
    stockLoading.value = false;
  }
}

function searchStocks() {
  stockQuery.pageNum = 1;
  loadStocks();
}

function resetStocks() {
  stockQuery.keyword = '';
  searchStocks();
}

// 库存只在首次切到该页签时加载，避免每次切换都发请求；确认入库后置脏强制刷新。
watch(activeTab, (tab) => {
  if (tab === 'stock' && !stockLoaded.value) {
    loadStocks();
  }
});

function openConfirm(row: FinishedGoodsInbound) {
  Object.assign(confirmDialog, {
    visible: true,
    submitting: false,
    id: row.id,
    productCode: row.productCode,
    qty: row.qty,
    batch: row.batch || ''
  });
}

async function submitConfirm() {
  if (confirmDialog.submitting) {
    return;
  }
  confirmDialog.submitting = true;
  try {
    await finishedGoodsApi.confirm(confirmDialog.id, confirmDialog.batch.trim() || undefined);
    ElMessage.success('已确认入库');
    confirmDialog.visible = false;
    stockLoaded.value = false;
    loadInbounds();
  } finally {
    confirmDialog.submitting = false;
  }
}

function openCancel(row: FinishedGoodsInbound) {
  Object.assign(cancelDialog, {
    visible: true,
    submitting: false,
    id: row.id,
    productCode: row.productCode,
    reason: ''
  });
}

async function submitCancel() {
  if (cancelDialog.submitting) {
    return;
  }
  const reason = cancelDialog.reason.trim();
  if (!reason) {
    ElMessage.warning('请填写取消原因');
    return;
  }
  cancelDialog.submitting = true;
  try {
    await finishedGoodsApi.cancel(cancelDialog.id, reason);
    ElMessage.success('入库单已取消');
    cancelDialog.visible = false;
    loadInbounds();
  } finally {
    cancelDialog.submitting = false;
  }
}

onMounted(loadInbounds);
</script>

<style scoped>
.done-text {
  color: var(--zhizao-muted);
  font-size: 12px;
}
</style>
