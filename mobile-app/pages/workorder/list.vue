<template>
  <view class="page">
    <view class="filter-bar">
      <text
        v-for="s in statusOptions"
        :key="s.value"
        class="chip"
        :class="{ active: query.status === s.value }"
        @click="filter(s.value)"
      >{{ s.label }}</text>
    </view>

    <view class="list">
      <view v-for="o in orders" :key="o.id" class="card" @click="goDetail(o)">
        <view class="card-top">
          <text class="order-no">{{ o.orderNo }}</text>
          <text class="badge" :style="{ color: statusMap[o.status]?.color || '#94a3b8' }">
            {{ statusMap[o.status]?.label || o.status }}
          </text>
        </view>
        <view class="card-info">
          <text class="name">{{ o.productName }}</text>
          <text class="meta">数量 {{ o.qty }} · 批次 {{ o.batch }} · {{ o.workshop }}</text>
        </view>
      </view>
      <view v-if="!orders.length && !loading" class="empty">暂无工单</view>
    </view>
  </view>
</template>

<script>
import { workOrderApi, workOrderStatusMap } from '../../api/index.js';

export default {
  data() {
    return {
      orders: [],
      loading: false,
      statusMap: workOrderStatusMap,
      query: { pageNum: 1, pageSize: 20, status: '' },
      statusOptions: [
        { value: '', label: '全部' },
        { value: 'PENDING', label: '待开工' },
        { value: 'IN_PRODUCTION', label: '生产中' },
        { value: 'PENDING_QC', label: '待质检' },
        { value: 'FINISHED', label: '已完工' }
      ]
    };
  },
  onShow() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const res = await workOrderApi.list(this.query);
        this.orders = res.records;
      } finally {
        this.loading = false;
      }
    },
    filter(status) {
      this.query.status = status;
      this.load();
    },
    goDetail(order) {
      uni.navigateTo({ url: '/pages/workorder/detail?id=' + order.id });
    }
  }
};
</script>

<style>
.page {
  padding: 20rpx;
}
.filter-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 8rpx;
  flex-wrap: wrap;
}
.chip {
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #5b6b82;
  font-size: 26rpx;
  border: 1rpx solid #e3e8f0;
}
.chip.active {
  background: #1d5fd6;
  color: #fff;
  border-color: #1d5fd6;
}
.list {
  margin-top: 8rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid #e3e8f0;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}
.order-no {
  font-size: 30rpx;
  font-weight: 700;
  color: #1d5fd6;
}
.badge {
  font-size: 24rpx;
  font-weight: 600;
}
.card-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.name {
  font-size: 30rpx;
  color: #182538;
}
.meta {
  font-size: 24rpx;
  color: #5b6b82;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 120rpx 0;
  font-size: 28rpx;
}
</style>
