<template>
  <div class="pwd-wrap">
    <div class="pwd-card">
      <div class="pwd-title">修改初始密码</div>
      <div class="pwd-sub">为保障账号安全，请先设置新密码后继续使用。</div>
      <el-form @keyup.enter="submit">
        <el-form-item><el-input v-model="form.oldPassword" type="password" placeholder="原密码" size="large" show-password /></el-form-item>
        <el-form-item><el-input v-model="form.newPassword" type="password" placeholder="新密码（8-64 位，且不能包含用户名）" size="large" show-password /></el-form-item>
        <el-form-item><el-input v-model="form.confirm" type="password" placeholder="确认新密码" size="large" show-password /></el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
          确认修改
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { authApi, userApi } from '@/api';
import { clearToken, getUser, setMustChangePassword, setToken, setUser } from '@/api/request';

const router = useRouter();
const loading = ref(false);
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' });

async function submit() {
  if (!form.oldPassword || form.newPassword.length < 8 || form.newPassword.length > 64) {
    ElMessage.warning('请填写原密码，新密码需为 8-64 位');
    return;
  }
  if (form.newPassword !== form.confirm) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  const username = getUser()?.username;
  if (!username) {
    clearToken();
    router.replace('/login');
    return;
  }
  loading.value = true;
  try {
    await userApi.changePassword(form.oldPassword, form.newPassword);
    // 改密会递增令牌版本使旧令牌失效，这里用新口令换一枚新令牌直接进入主界面。
    const res = await authApi.login({ username, password: form.newPassword });
    setToken(res.token);
    setUser({ username: res.username, nickname: res.nickname, role: res.role });
    setMustChangePassword(false);
    ElMessage.success('密码已更新');
    router.replace('/dashboard');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.pwd-wrap { height: 100%; display: flex; align-items: center; justify-content: center; background: linear-gradient(160deg, #0f1f38 0%, #1c3a63 55%, #2b6cb0 120%); }
.pwd-card { width: 380px; background: #fff; border-radius: 14px; padding: 32px 34px 26px; box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28); }
.pwd-title { font-size: 20px; font-weight: 800; color: var(--zhizao-ink); text-align: center; }
.pwd-sub { font-size: 13px; color: var(--zhizao-muted); text-align: center; margin: 8px 0 20px; }
</style>
