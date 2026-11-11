<template>
  <view class="page">
    <view class="card">
      <text class="title">修改初始密码</text>
      <text class="sub">为保障账号安全，请先设置新密码后继续使用。</text>
      <input v-model="form.oldPassword" class="input" password placeholder="原密码" />
      <input v-model="form.newPassword" class="input" password placeholder="新密码（至少 6 位）" />
      <button class="btn" :loading="loading" @click="submit">确认修改</button>
    </view>
  </view>
</template>

<script>
import { authApi, userApi } from '../../api/index.js';
import { setToken } from '../../utils/request.js';

export default {
  data() {
    return { form: { oldPassword: '', newPassword: '' }, loading: false };
  },
  methods: {
    async submit() {
      if (!this.form.oldPassword || this.form.newPassword.length < 6) {
        uni.showToast({ title: '请填写原密码，新密码至少 6 位', icon: 'none' });
        return;
      }
      const username = uni.getStorageSync('zhizao_username');
      this.loading = true;
      try {
        await userApi.changePassword(this.form.oldPassword, this.form.newPassword);
        // 改密后旧令牌失效，用新口令换一枚新令牌直接进入工单列表。
        const res = await authApi.login({ username, password: this.form.newPassword });
        setToken(res.token);
        uni.showToast({ title: '密码已更新', icon: 'success' });
        setTimeout(() => uni.reLaunch({ url: '/pages/workorder/list' }), 400);
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

<style>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(160deg, #0f1f38 0%, #1c3a63 55%, #2b6cb0 120%); }
.card { width: 80%; background: #fff; border-radius: 20rpx; padding: 48rpx 40rpx; display: flex; flex-direction: column; }
.title { font-size: 38rpx; font-weight: 700; color: #0f1f38; text-align: center; }
.sub { font-size: 26rpx; color: #64748b; text-align: center; margin: 16rpx 0 36rpx; }
.input { border: 1rpx solid #e3e8f0; border-radius: 12rpx; padding: 22rpx 24rpx; font-size: 30rpx; margin-bottom: 28rpx; }
.btn { background: #1d5fd6; color: #fff; border-radius: 12rpx; font-size: 32rpx; margin-top: 12rpx; }
</style>
