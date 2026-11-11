<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">智造</div>
        <div class="brand-title">智造协同 · 工业APP</div>
        <div class="brand-sub">面向离散制造车间的一体化生产管理</div>
      </div>
      <el-form :model="form" @keyup.enter="login">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="login">
          登 录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { User, Lock } from '@element-plus/icons-vue';
import { authApi } from '@/api';
import { setToken, setUser, setMustChangePassword } from '@/api/request';

const router = useRouter();
const loading = ref(false);
const form = reactive({ username: '', password: '' });

async function login() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    const res = await authApi.login(form);
    setToken(res.token);
    setUser({ username: res.username, nickname: res.nickname, role: res.role });
    if (res.mustChangePassword) {
      setMustChangePassword(true);
      ElMessage.warning('请先修改初始密码');
      router.push('/change-password');
      return;
    }
    setMustChangePassword(false);
    ElMessage.success('登录成功');
    router.push('/dashboard');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #0f1f38 0%, #1c3a63 55%, #2b6cb0 120%);
}
.login-card {
  width: 380px;
  background: #fff;
  border-radius: 14px;
  padding: 36px 34px 28px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
}
.brand {
  text-align: center;
  margin-bottom: 26px;
}
.brand-mark {
  display: inline-block;
  background: var(--zhizao-accent2);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  border-radius: 6px;
  padding: 4px 10px;
  margin-bottom: 12px;
}
.brand-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--zhizao-ink);
}
.brand-sub {
  font-size: 12px;
  color: var(--zhizao-muted);
  margin-top: 4px;
}
</style>
