<template>
  <view class="page">
    <view class="brand">
      <text class="brand-mark">智造</text>
      <text class="brand-title">智造协同 · 工业APP</text>
    </view>
    <view class="form">
      <input v-model="form.username" class="input" placeholder="用户名" />
      <input v-model="form.password" class="input" password placeholder="密码" />
      <button class="btn" :loading="loading" @click="login">登 录</button>
    </view>
  </view>
</template>

<script>
import { authApi } from '../../api/index.js';
import { setToken } from '../../utils/request.js';

export default {
  data() {
    return {
      form: { username: '', password: '' },
      loading: false
    };
  },
  methods: {
    async login() {
      if (!this.form.username || !this.form.password) {
        uni.showToast({ title: '请输入用户名和密码', icon: 'none' });
        return;
      }
      this.loading = true;
      try {
        const res = await authApi.login(this.form);
        setToken(res.token);
        uni.setStorageSync('zhizao_username', res.username);
        if (res.mustChangePassword) {
          uni.showToast({ title: '请先修改初始密码', icon: 'none' });
          uni.reLaunch({ url: '/pages/changepassword/changepassword' });
          return;
        }
        uni.showToast({ title: '登录成功', icon: 'success' });
        setTimeout(() => uni.reLaunch({ url: '/pages/workorder/list' }), 400);
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

<style>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #0f1f38 0%, #1c3a63 55%, #2b6cb0 120%);
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 60rpx;
}
.brand-mark {
  background: #e8871a;
  color: #fff;
  font-weight: 700;
  font-size: 30rpx;
  border-radius: 12rpx;
  padding: 8rpx 20rpx;
  margin-bottom: 20rpx;
}
.brand-title {
  color: #eaf2fc;
  font-size: 40rpx;
  font-weight: 700;
}
.form {
  width: 80%;
  background: #fff;
  border-radius: 20rpx;
  padding: 48rpx 40rpx;
}
.input {
  border: 1rpx solid #e3e8f0;
  border-radius: 12rpx;
  padding: 22rpx 24rpx;
  font-size: 30rpx;
  margin-bottom: 28rpx;
}
.btn {
  background: #1d5fd6;
  color: #fff;
  border-radius: 12rpx;
  font-size: 32rpx;
  margin-top: 12rpx;
}
</style>
