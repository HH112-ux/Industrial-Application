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

    <view class="actions">
      <button v-if="order.status === 'PENDING'" class="btn" @click="start">开工</button>
      <button v-if="order.status === 'IN_PRODUCTION'" class="btn" @click="reportVisible = true">报工</button>
      <button v-if="order.status === 'PENDING_QC'" class="btn" @click="qcVisible = true">质检</button>
      <button v-if="order.status === 'FINISHED'" class="btn done" disabled>已完工</button>
    </view>

    <view v-if="reportVisible" class="mask" @click="reportVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">报工</text>
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
import { workOrderApi, workOrderStatusMap } from '../../api/index.js';

export default {
  data() {
    return {
      id: null,
      order: {},
      statusMap: workOrderStatusMap,
      reportVisible: false,
      qcVisible: false,
      reportForm: { qty: 0, qualifiedQty: 0, defectQty: 0, operator: '' },
      qcForm: { result: 'PASS', defectDesc: '', inspector: '' }
    };
  },
  onLoad(options) {
    this.id = options.id;
    this.load();
  },
  methods: {
    async load() {
      this.order = await workOrderApi.get(this.id);
      this.reportForm.qty = this.order.qty || 0;
    },
    async start() {
      await workOrderApi.start(this.id);
      uni.showToast({ title: '已开工', icon: 'success' });
      this.load();
    },
    async submitReport() {
      if (!this.reportForm.qty) {
        uni.showToast({ title: '请填写报工数量', icon: 'none' });
        return;
      }
      await workOrderApi.report(this.id, this.reportForm);
      uni.showToast({ title: '已报工', icon: 'success' });
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
