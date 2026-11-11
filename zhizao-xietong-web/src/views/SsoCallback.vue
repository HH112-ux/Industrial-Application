<template>
  <div class="callback-wrap">
    <div class="callback-card">
      <el-icon class="callback-icon" :size="28"><Loading /></el-icon>
      <div class="callback-title">正在完成企业微信登录</div>
      <div class="callback-sub">请稍候，不要关闭页面</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Loading } from '@element-plus/icons-vue';
import { authApi } from '@/api';
import { setToken, setUser, setMustChangePassword } from '@/api/request';

const route = useRoute();
const router = useRouter();

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : '';
  if (!code) {
    ElMessage.error('未获取到企业微信授权码，请重新扫码');
    router.replace('/login');
    return;
  }
  try {
    const res = await authApi.ssoLogin(code);
    setToken(res.token);
    setUser({ username: res.username, nickname: res.nickname, role: res.role });
    setMustChangePassword(!!res.mustChangePassword);
    router.replace('/dashboard');
  } catch {
    router.replace('/login');
  }
});
</script>

<style scoped>
.callback-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #0f1f38 0%, #1c3a63 55%, #2b6cb0 120%);
}
.callback-card {
  width: 320px;
  background: #fff;
  border-radius: 14px;
  padding: 32px 28px;
  text-align: center;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
}
.callback-icon {
  color: var(--zhizao-accent2);
  animation: spin 1.2s linear infinite;
}
.callback-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--zhizao-ink);
}
.callback-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--zhizao-muted);
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
