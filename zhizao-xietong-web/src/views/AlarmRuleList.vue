<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="deviceFilter" placeholder="按设备筛选" clearable filterable style="width: 240px">
          <el-option v-for="d in devices" :key="d.id" :label="d.label" :value="d.id!" />
        </el-select>
        <el-button @click="deviceFilter = undefined">重置</el-button>
        <div class="spacer" />
        <el-button type="primary" @click="openCreate">新增规则</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="设备" min-width="180">
          <template #default="{ row }">
            <div>{{ deviceName(row.deviceId) }}</div>
            <div class="cell-sub">{{ deviceNo(row.deviceId) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><StatusTag :value="row.ruleType" :map="alarmRuleTypeMap" /></template>
        </el-table-column>
        <el-table-column label="指标" width="100">
          <template #default="{ row }">
            {{ row.ruleType === 'OFFLINE' ? '—' : (alarmMetricMap[row.metric || '']?.label || row.metric) }}
          </template>
        </el-table-column>
        <el-table-column label="阈值区间" min-width="200">
          <template #default="{ row }">
            <span v-if="row.ruleType === 'OFFLINE'">服务端配置（默认 90 秒未上报）</span>
            <span v-else>{{ limitText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="连续次数" width="100">
          <template #default="{ row }">{{ row.ruleType === 'OFFLINE' ? '—' : row.consecutiveTimes }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggle(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无报警规则" />

      <el-dialog v-model="dialogVisible" :title="form.id ? '编辑报警规则' : '新增报警规则'" width="520px" destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
          <el-form-item label="规则类型" prop="ruleType">
            <el-radio-group v-model="form.ruleType" @change="onTypeChange">
              <el-radio value="THRESHOLD">阈值越界</el-radio>
              <el-radio value="OFFLINE">设备离线</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="设备" prop="deviceId">
            <el-select v-model="form.deviceId" placeholder="请选择设备" filterable style="width: 100%">
              <el-option v-for="d in devices" :key="d.id" :label="d.label" :value="d.id!" />
            </el-select>
          </el-form-item>
          <template v-if="form.ruleType === 'THRESHOLD'">
            <el-form-item label="指标" prop="metric">
              <el-select v-model="form.metric" placeholder="请选择指标" style="width: 100%">
                <el-option v-for="(v, k) in alarmMetricMap" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item label="上限">
              <el-input-number v-model="form.upperLimit" :precision="2" :controls="false" placeholder="大于该值触发" style="width: 100%" />
            </el-form-item>
            <el-form-item label="下限">
              <el-input-number v-model="form.lowerLimit" :precision="2" :controls="false" placeholder="小于该值触发" style="width: 100%" />
            </el-form-item>
            <el-form-item label="连续次数" prop="consecutiveTimes">
              <el-input-number v-model="form.consecutiveTimes" :min="1" :max="100" />
            </el-form-item>
          </template>
          <el-form-item v-else label="阈值说明">
            <el-alert type="info" :closable="false" show-icon
              title="离线阈值由服务端配置（默认 90 秒未上报），页面不可修改；同一设备只允许一条离线规则。" />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { alarmRuleApi, deviceApi, type AlarmRule } from '@/api';
import { alarmMetricMap, alarmRuleTypeMap } from '@/utils/dict';
import StatusTag from '@/components/StatusTag.vue';

const loading = ref(false);
const saving = ref(false);
const allRules = ref<AlarmRule[]>([]);
const devices = ref<Array<{ id?: number; name: string; no: string; label: string }>>([]);
const deviceFilter = ref<number | undefined>();
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<AlarmRule>(emptyForm());

const rows = computed(() => (deviceFilter.value == null
  ? allRules.value
  : allRules.value.filter((r) => r.deviceId === deviceFilter.value)));

const formRules: FormRules = {
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  metric: [{ required: true, message: '请选择指标', trigger: 'change' }],
  consecutiveTimes: [{ required: true, message: '请输入连续次数', trigger: 'blur' }]
};

function emptyForm(): AlarmRule {
  return {
    ruleType: 'THRESHOLD',
    deviceId: undefined,
    metric: 'temperature',
    upperLimit: null,
    lowerLimit: null,
    consecutiveTimes: 3,
    enabled: true
  };
}

function deviceName(id?: number) {
  return devices.value.find((d) => d.id === id)?.name || (id == null ? '—' : `设备#${id}`);
}

function deviceNo(id?: number) {
  return devices.value.find((d) => d.id === id)?.no || '';
}

function limitText(row: AlarmRule) {
  const parts: string[] = [];
  if (row.upperLimit != null) {
    parts.push(`上限 ${row.upperLimit}`);
  }
  if (row.lowerLimit != null) {
    parts.push(`下限 ${row.lowerLimit}`);
  }
  return parts.length ? parts.join('，') : '—';
}

function ruleLabel(row: AlarmRule) {
  const type = alarmRuleTypeMap[row.ruleType || '']?.label || row.ruleType;
  const metric = row.metric ? alarmMetricMap[row.metric]?.label || row.metric : '';
  return metric ? `${type} · ${metric}` : `${type}`;
}

async function load() {
  loading.value = true;
  try {
    allRules.value = await alarmRuleApi.list();
  } finally {
    loading.value = false;
  }
}

async function loadDevices() {
  const res = await deviceApi.list({ pageNum: 1, pageSize: 500 });
  devices.value = res.records
    .filter((d) => d.id != null)
    .map((d) => ({
      id: d.id,
      name: d.deviceName,
      no: d.deviceNo || '',
      label: `${d.deviceName}（${d.deviceNo}）`
    }));
}

function onTypeChange() {
  const base = emptyForm();
  Object.assign(form, { ...base, id: form.id, deviceId: form.deviceId, enabled: form.enabled, ruleType: form.ruleType });
}

function openCreate() {
  Object.assign(form, emptyForm());
  dialogVisible.value = true;
}

function openEdit(row: AlarmRule) {
  Object.assign(form, {
    id: row.id,
    deviceId: row.deviceId,
    ruleType: row.ruleType,
    metric: row.metric || 'temperature',
    upperLimit: row.upperLimit ?? null,
    lowerLimit: row.lowerLimit ?? null,
    consecutiveTimes: row.consecutiveTimes ?? 3,
    enabled: row.enabled !== false
  });
  dialogVisible.value = true;
}

async function submit() {
  if (!formRef.value) {
    return;
  }
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  const payload: AlarmRule = { ...form };
  if (payload.ruleType === 'OFFLINE') {
    payload.metric = '';
    payload.upperLimit = null;
    payload.lowerLimit = null;
    payload.consecutiveTimes = 3;
  } else if (payload.upperLimit == null && payload.lowerLimit == null) {
    ElMessage.warning('阈值规则至少需要配置上限或下限');
    return;
  } else if (payload.upperLimit != null && payload.lowerLimit != null && payload.upperLimit <= payload.lowerLimit) {
    ElMessage.warning('上限必须大于下限');
    return;
  }
  const duplicated = allRules.value.find((r) => r.id !== payload.id
    && r.deviceId === payload.deviceId
    && r.ruleType === payload.ruleType
    && (payload.ruleType === 'OFFLINE' || r.metric === payload.metric));
  if (duplicated) {
    ElMessage.warning(duplicated.enabled
      ? '该设备该指标已有启用规则，请直接编辑原规则'
      : '该设备该指标已有停用规则，请编辑原规则后启用');
    return;
  }
  saving.value = true;
  try {
    if (payload.id) {
      await alarmRuleApi.update(payload.id, payload);
    } else {
      await alarmRuleApi.create(payload);
    }
    ElMessage.success('已保存');
    dialogVisible.value = false;
    await load();
  } catch {
    // 冲突等错误由请求拦截器提示，保留弹窗便于修改
  } finally {
    saving.value = false;
  }
}

async function toggle(row: AlarmRule) {
  if (row.id == null) {
    return;
  }
  const next = !row.enabled;
  if (!next) {
    try {
      await ElMessageBox.confirm(
        `确认停用「${deviceName(row.deviceId)} · ${ruleLabel(row)}」？停用后不再触发新报警。`,
        '停用确认',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  try {
    await alarmRuleApi.update(row.id, { ...row, enabled: next });
    ElMessage.success(next ? '已启用' : '已停用');
    await load();
  } catch {
    // 错误由请求拦截器提示
  }
}

onMounted(() => {
  load();
  loadDevices();
});
</script>

<style scoped>
.cell-sub {
  color: var(--zhizao-muted, #8a94a6);
  font-size: 12px;
}
</style>
