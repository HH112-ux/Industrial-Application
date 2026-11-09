<template>
  <div class="page">
    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-num mono">{{ k.value }}</div>
        <div class="kpi-label">{{ k.label }}</div>
      </div>
    </div>

    <div class="page-card">
      <div class="card-title">生产工单状态分布</div>
      <div v-if="chartEmpty" class="chart-empty">暂无工单数据</div>
      <div v-show="!chartEmpty" ref="chartRef" style="height: 340px"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, reactive } from 'vue';
import * as echarts from 'echarts';
import { deviceApi, materialApi, processApi, planApi, workOrderApi } from '@/api';
import { workOrderStatusMap } from '@/utils/dict';

const kpis = reactive([
  { label: '设备总数', value: 0 },
  { label: '物料总数', value: 0 },
  { label: '工艺总数', value: 0 },
  { label: '计划总数', value: 0 },
  { label: '工单总数', value: 0 }
]);

const chartRef = ref<HTMLElement>();
const chartEmpty = ref(false);
let chart: echarts.ECharts | null = null;

async function loadCounts() {
  const [d, m, p, pl, w] = await Promise.all([
    deviceApi.list({ pageNum: 1, pageSize: 1 }),
    materialApi.list({ pageNum: 1, pageSize: 1 }),
    processApi.list({ pageNum: 1, pageSize: 1 }),
    planApi.list({ pageNum: 1, pageSize: 1 }),
    workOrderApi.list({ pageNum: 1, pageSize: 1 })
  ]);
  kpis[0].value = d.total;
  kpis[1].value = m.total;
  kpis[2].value = p.total;
  kpis[3].value = pl.total;
  kpis[4].value = w.total;
}

async function loadChart() {
  const counts = await workOrderApi.statusCounts();
  const data = counts
    .filter((c) => c.count > 0)
    .map((c) => ({ name: workOrderStatusMap[c.status]?.label || c.status, value: c.count }));
  chartEmpty.value = data.length === 0;
  if (!data.length) {
    chart?.dispose();
    chart = null;
    return;
  }

  chart = echarts.init(chartRef.value!);
  chart.setOption({
    color: ['#1d5fd6', '#e8871a', '#16a34a', '#94a3b8'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}: {c}' },
        data
      }
    ]
  });
}

function onResize() {
  chart?.resize();
}

onMounted(() => {
  loadCounts().catch(() => ElMessage.error('看板统计加载失败'));
  loadChart().catch(() => ElMessage.error('工单状态分布加载失败'));
  window.addEventListener('resize', onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  chart?.dispose();
});
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.kpi-card {
  background: #fff;
  border: 1px solid var(--zhizao-rule);
  border-radius: 10px;
  padding: 18px 20px;
  border-top: 3px solid var(--zhizao-accent);
}
.kpi-num {
  font-size: 30px;
  font-weight: 800;
  color: var(--zhizao-accent);
  line-height: 1.1;
}
.kpi-label {
  font-size: 13px;
  color: var(--zhizao-muted);
  margin-top: 4px;
}
.card-title {
  font-weight: 700;
  font-size: 15px;
  margin-bottom: 8px;
}
.chart-empty {
  height: 340px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--zhizao-muted);
  font-size: 14px;
}
</style>
