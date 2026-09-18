<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">智造</div>
        <div class="brand-name">智造协同 · 工业APP</div>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="transparent"
        text-color="#b9c8dd"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon><span>驾驶舱</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Cpu /></el-icon><span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/maintenance">
          <el-icon><Tools /></el-icon><span>点检与维修</span>
        </el-menu-item>
        <el-menu-item index="/materials">
          <el-icon><Box /></el-icon><span>物料管理</span>
        </el-menu-item>
        <el-menu-item index="/processes">
          <el-icon><SetUp /></el-icon><span>工艺管理</span>
        </el-menu-item>
        <el-menu-item index="/plans">
          <el-icon><Calendar /></el-icon><span>计划与调度</span>
        </el-menu-item>
        <el-menu-item index="/work-orders">
          <el-icon><Tickets /></el-icon><span>生产执行</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ pageTitle }}</div>
        <div class="header-right">
          <span class="user-name">{{ displayName }}</span>
          <el-button link type="danger" @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DataBoard, Cpu, Box, SetUp, Calendar, Tickets, Tools } from '@element-plus/icons-vue';
import { getUser, clearToken } from '@/api/request';

const route = useRoute();
const router = useRouter();
const activeMenu = computed(() => route.path);
const pageTitle = computed(() => (route.meta.title as string) || '智造协同');
const displayName = computed(() => getUser()?.nickname || getUser()?.username || '');

function logout() {
  clearToken();
  router.push('/login');
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.sidebar {
  background: var(--zhizao-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.brand {
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.brand-mark {
  display: inline-block;
  background: var(--zhizao-accent2);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  border-radius: 6px;
  padding: 4px 8px;
  margin-bottom: 8px;
}
.brand-name {
  color: #eaf2fc;
  font-weight: 600;
  font-size: 15px;
}
.menu {
  border-right: none;
  flex: 1;
}
.menu :deep(.el-menu-item) {
  margin: 4px 10px;
  border-radius: 8px;
  height: 44px;
}
.menu :deep(.el-menu-item.is-active) {
  background: var(--zhizao-accent);
}
.header {
  background: #fff;
  border-bottom: 1px solid var(--zhizao-rule);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
}
.header-title {
  font-weight: 700;
  font-size: 16px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-name {
  color: var(--zhizao-ink);
  font-size: 13px;
  font-weight: 600;
}
.main {
  background: var(--zhizao-bg);
  padding: 0;
}
</style>
