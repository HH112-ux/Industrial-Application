<template>
  <view class="page">
    <view class="card info">
      <view class="row"><text class="k">工单号</text><text class="v">{{ order.orderNo }}</text></view>
      <view class="row"><text class="k">产品</text><text class="v">{{ order.productName }}</text></view>
      <view class="row"><text class="k">数量</text><text class="v">{{ order.qty }}</text></view>
      <view class="row"><text class="k">批次</text><text class="v">{{ order.batch }}</text></view>
      <view class="row"><text class="k">车间</text><text class="v">{{ order.workshop }}</text></view>
      <view class="row">
        <text class="k">状态</text>
        <text class="v" :style="{ color: statusMap[order.status]?.color || '#182538' }">
          {{ statusMap[order.status]?.label || order.status }}
        </text>
      </view>
    </view>

    <view class="card progress">
      <view class="progress-head">
        <text class="k">报工进度</text>
        <text class="v">{{ progressPercent }}%</text>
      </view>
      <view class="progress-bar">
        <view class="progress-fill" :style="{ width: progressPercent + '%' }" />
      </view>
      <text class="progress-text">累计 {{ order.qtyDone || 0 }} / 计划 {{ order.qty }}</text>
    </view>

    <view class="card schedule">
      <view class="schedule-head">
        <text class="k">排程落点</text>
        <text v-if="summaryText" class="summary">{{ summaryText }}</text>
      </view>
      <view v-if="scheduleLoading" class="schedule-empty">加载中…</view>
      <view v-else-if="!schedules.length" class="schedule-empty">该工单尚未排程：可能未运行排产，或无法排入</view>
      <view v-else>
        <view v-for="item in schedules" :key="item.id" class="schedule-item">
          <view class="schedule-top">
            <text class="queue">{{ queueLabel(item) }}</text>
            <text class="op">{{ operationLabel(item) }}</text>
          </view>
          <view class="schedule-row">
            <text class="k">设备</text>
            <text class="v">{{ deviceLabel(item) }}</text>
          </view>
          <view class="schedule-row">
            <text class="k">占用时间</text>
            <text class="v">{{ formatWindow(item.startTime, item.endTime) }}</text>
          </view>
          <view class="schedule-row">
            <text class="k">工时</text>
            <view class="v">
              <text>{{ hoursText(item) }}</text>
              <text v-if="isEstimated(item)" class="estimate-tag">估算</text>
            </view>
          </view>
          <text v-if="item.message" class="schedule-note">{{ item.message }}</text>
        </view>
      </view>
    </view>

    <view class="actions">
      <button v-if="order.status === 'PENDING'" class="btn" @click="start">开工</button>
      <button v-if="order.status === 'IN_PRODUCTION'" class="btn" :disabled="remainingQty <= 0" @click="openReport">报工</button>
      <text v-if="order.status === 'IN_PRODUCTION' && remainingQty <= 0" class="action-hint">已报满，无可报数量</text>
      <button v-if="order.status === 'PENDING_QC'" class="btn" @click="qcVisible = true">质检</button>
      <button v-if="order.status === 'FINISHED'" class="btn done" disabled>已完工</button>
    </view>

    <view v-if="reportVisible" class="mask" @click="reportVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">报工</text>
        <text class="hint">累计 {{ order.qtyDone || 0 }} / 计划 {{ order.qty }}，剩余 {{ remainingQty }}；合格 + 不良必须等于报工数量</text>
        <input v-model="reportForm.qty" type="number" class="input" placeholder="报工数量" />
        <input v-model="reportForm.qualifiedQty" type="number" class="input" placeholder="合格数量" />
        <input v-model="reportForm.defectQty" type="number" class="input" placeholder="不良数量" />
        <button class="btn" @click="submitReport">提交报工</button>
      </view>
    </view>

    <view v-if="qcVisible" class="mask" @click="qcVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">质检</text>
        <view class="radio-group">
          <text class="radio" :class="{ active: qcForm.result === 'PASS' }" @click="qcForm.result = 'PASS'">合格</text>
          <text class="radio" :class="{ active: qcForm.result === 'FAIL' }" @click="qcForm.result = 'FAIL'">不合格</text>
        </view>
        <input v-model="qcForm.defectDesc" class="input" placeholder="不良描述" />
        <button class="btn" @click="submitQc">提交质检</button>
      </view>
    </view>
  </view>
</template>

<script>
import { workOrderApi, scheduleApi, workOrderStatusMap } from '../../api/index.js';
import {
  formatWindow,
  deviceLabel,
  hoursText,
  isEstimated,
  queueLabel,
  operationLabel,
  scheduleSummary
} from '../../utils/schedule.mjs';

export default {
  data() {
    return {
      id: null,
      order: {},
      statusMap: workOrderStatusMap,
      schedules: [],
      scheduleLoading: false,
      reportVisible: false,
      qcVisible: false,
      reportForm: { qty: 0, qualifiedQty: 0, defectQty: 0, idempotencyKey: '' },
      qcForm: { result: 'PASS', defectDesc: '' }
    };
  },
  computed: {
    // 报工累计口径在返工场景会超过计划数量，进度条封顶 100%，绝对数另行展示。
    progressPercent() {
      const planned = Number(this.order.qty) || 0;
      const done = Number(this.order.qtyDone) || 0;
      if (planned <= 0) {
        return done > 0 ? 100 : 0;
      }
      return Math.min(100, Math.round((done / planned) * 100));
    },
    remainingQty() {
      // 质检失败后释放可返工额度，剩余量按「计划 + 可返工」计算。
      const limit = (this.order.qty || 0) + (this.order.qtyRework || 0);
      return Math.max(limit - (this.order.qtyDone || 0), 0);
    },
    summaryText() {
      return scheduleSummary(this.schedules);
    }
  },
  onLoad(options) {
    this.id = options.id;
    this.load();
  },
  methods: {
    formatWindow,
    deviceLabel,
    hoursText,
    isEstimated,
    queueLabel,
    operationLabel,
    async load() {
      this.order = await workOrderApi.get(this.id);
      this.reportForm.qty = this.remainingQty;
      this.reportForm.qualifiedQty = this.remainingQty;
      this.reportForm.defectQty = 0;
      this.loadSchedule();
    },
    async loadSchedule() {
      this.scheduleLoading = true;
      try {
        this.schedules = await scheduleApi.entries(this.id);
      } catch (e) {
        // 请求层已提示错误，排程区块退回空态，不影响工单主体操作。
        this.schedules = [];
      } finally {
        this.scheduleLoading = false;
      }
    },
    openReport() {
      if (this.remainingQty <= 0) {
        uni.showToast({ title: '已报满，无可报数量', icon: 'none' });
        return;
      }
      if (!this.reportForm.idempotencyKey) {
        const cacheKey = `report-idem-${this.id}`;
        const saved = uni.getStorageSync(cacheKey);
        this.reportForm.idempotencyKey = saved
          || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
        uni.setStorageSync(cacheKey, this.reportForm.idempotencyKey);
      }
      this.reportVisible = true;
    },
    async start() {
      await workOrderApi.start(this.id);
      uni.showToast({ title: '已开工', icon: 'success' });
      this.load();
    },
    async submitReport() {
      const qty = Number(this.reportForm.qty);
      const qualified = Number(this.reportForm.qualifiedQty);
      const defect = Number(this.reportForm.defectQty);
      if (!qty || qty <= 0) {
        uni.showToast({ title: '报工数量必须大于 0', icon: 'none' });
        return;
      }
      if (qty > this.remainingQty) {
        uni.showToast({ title: `最多可报 ${this.remainingQty} 件`, icon: 'none' });
        return;
      }
      if (qualified < 0 || defect < 0) {
        uni.showToast({ title: '数量不能为负', icon: 'none' });
        return;
      }
      if (qualified + defect !== qty) {
        uni.showToast({ title: '合格 + 不良必须等于报工数量', icon: 'none' });
        return;
      }
      await workOrderApi.report(this.id, {
        qty,
        qualifiedQty: qualified,
        defectQty: defect,
        idempotencyKey: this.reportForm.idempotencyKey
      });
      uni.showToast({ title: '已报工', icon: 'success' });
      uni.removeStorageSync(`report-idem-${this.id}`);
      this.reportForm.idempotencyKey = '';
      this.reportVisible = false;
      this.load();
    },
    async submitQc() {
      await workOrderApi.qc(this.id, this.qcForm);
      uni.showToast({ title: '质检完成', icon: 'success' });
      this.qcVisible = false;
      this.load();
    }
  }
};
</script>

<style>
.page {
  padding: 24rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  border: 1rpx solid #e3e8f0;
}
.info .row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx dashed #e3e8f0;
}
.info .k {
  color: #5b6b82;
  font-size: 28rpx;
}
.info .v {
  color: #182538;
  font-size: 28rpx;
  font-weight: 600;
}
.progress {
  margin-top: 24rpx;
}
.progress-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}
.progress-head .k {
  color: #5b6b82;
  font-size: 28rpx;
}
.progress-head .v {
  color: #1d5fd6;
  font-size: 28rpx;
  font-weight: 700;
}
.progress-bar {
  height: 14rpx;
  border-radius: 999rpx;
  background: #e3e8f0;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: #1d5fd6;
}
.progress-text {
  display: block;
  margin-top: 10rpx;
  color: #5b6b82;
  font-size: 24rpx;
}
.schedule {
  margin-top: 24rpx;
}
.schedule-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}
.schedule-head .k {
  color: #5b6b82;
  font-size: 28rpx;
}
.summary {
  color: #1d5fd6;
  font-size: 24rpx;
  font-weight: 600;
}
.schedule-empty {
  text-align: center;
  color: #94a3b8;
  font-size: 26rpx;
  padding: 24rpx 0;
}
.schedule-item {
  padding: 20rpx 0;
  border-top: 1rpx dashed #e3e8f0;
}
.schedule-item:first-child {
  padding-top: 6rpx;
  border-top: none;
}
.schedule-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}
.queue {
  color: #1d5fd6;
  font-size: 26rpx;
  font-weight: 700;
}
.op {
  color: #182538;
  font-size: 28rpx;
}
.schedule-row {
  display: flex;
  justify-content: space-between;
  padding: 6rpx 0;
}
.schedule-row .k {
  color: #5b6b82;
  font-size: 26rpx;
}
.schedule-row .v {
  color: #182538;
  font-size: 26rpx;
  font-weight: 600;
  text-align: right;
}
.schedule-note {
  display: block;
  margin-top: 8rpx;
  color: #5b6b82;
  font-size: 24rpx;
  line-height: 1.6;
}
.estimate-tag {
  color: #e8871a;
  font-size: 24rpx;
  margin-left: 8rpx;
}
.actions {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.btn {
  background: #1d5fd6;
  color: #fff;
  border-radius: 12rpx;
  font-size: 32rpx;
}
.btn.done {
  background: #e2e8f0;
  color: #94a3b8;
}
.action-hint {
  color: #94a3b8;
  font-size: 26rpx;
  text-align: center;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 40rpx 32rpx 48rpx;
}
.sheet-title {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 28rpx;
}
.hint {
  display: block;
  color: #5b6b82;
  font-size: 24rpx;
  line-height: 1.6;
  margin-bottom: 20rpx;
}
.input {
  border: 1rpx solid #e3e8f0;
  border-radius: 12rpx;
  padding: 22rpx 24rpx;
  font-size: 30rpx;
  margin-bottom: 24rpx;
}
.radio-group {
  display: flex;
  gap: 24rpx;
  margin-bottom: 24rpx;
}
.radio {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  border: 1rpx solid #e3e8f0;
  border-radius: 12rpx;
  color: #5b6b82;
}
.radio.active {
  border-color: #1d5fd6;
  color: #1d5fd6;
  background: #eef4fd;
  font-weight: 600;
}
</style>
