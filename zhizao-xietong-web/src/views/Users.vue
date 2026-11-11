<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索用户名/昵称" clearable style="width: 240px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="用户名" width="180">
          <template #default="{ row }">
            <span class="cell-code">{{ row.username }}</span>
            <el-tag v-if="row.authSource === 'SSO'" size="small" type="warning" style="margin-left: 6px">企微</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="130" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="车间数据域" min-width="220">
          <template #default="{ row }">
            <span v-if="row.role === 'ADMIN'" class="scope-muted">不受限（管理员）</span>
            <el-tag v-else-if="row.workshopScope" type="success" size="small">{{ row.workshopScope }}</el-tag>
            <el-tag v-else type="warning" size="small">未分配 · 看不到车间数据</el-tag>
            <div v-if="row.authSource === 'SSO' && row.departmentIds" class="scope-muted">
              企微部门 {{ row.departmentIds }} · 仅建议，需管理员确认
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRole(row)">分配角色</el-button>
            <el-button v-if="row.role !== 'ADMIN'" link type="primary" @click="openAssign(row)">分配车间</el-button>
            <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="warning" @click="openReset(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="roleDialog.visible" title="分配角色" width="460px">
      <div class="dialog-hint">
        角色变更会写入审计，该用户旧令牌立即失效、需要重新登录。WORKER 与 USER 均为非管理员。
      </div>
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span class="cell-code">{{ roleDialog.username }}</span>
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="roleDialog.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
            <el-option label="车间员工" value="WORKER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignDialog.visible" title="分配车间数据域" width="460px">
      <div class="dialog-hint">
        未分配车间的用户看不到任何设备、计划与工单。分配后该用户下一次请求即按新车间过滤，无需重新登录。
      </div>
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span class="cell-code">{{ assignDialog.username }}</span>
        </el-form-item>
        <el-form-item label="车间" required>
          <el-input v-model="assignDialog.workshopScope" placeholder="如：一车间" maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog.visible" title="重置密码" width="460px">
      <div class="dialog-hint">
        重置后该用户下次登录必须先修改密码，所有旧令牌立即失效，需要用新密码重新登录。
      </div>
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span class="cell-code">{{ resetDialog.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="resetDialog.newPassword" type="password" show-password placeholder="8-64 位，且不能包含用户名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { userApi, type UserAccount } from '@/api';

const loading = ref(false);
const rows = ref<UserAccount[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' });

const assignDialog = reactive({ visible: false, id: 0, username: '', workshopScope: '' });
const roleDialog = reactive({ visible: false, id: 0, username: '', role: 'USER' });
const resetDialog = reactive({ visible: false, id: 0, username: '', newPassword: '' });

function roleLabel(role: string): string {
  if (role === 'ADMIN') return '管理员';
  if (role === 'WORKER') return '车间员工';
  return '普通用户';
}

async function load() {
  loading.value = true;
  try {
    const res = await userApi.list(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.keyword = '';
  query.pageNum = 1;
  load();
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}

async function toggleStatus(row: UserAccount) {
  const action = row.enabled ? '禁用' : '启用';
  await ElMessageBox.confirm(`确定${action}用户 ${row.username} 吗？`, '提示', { type: 'warning' });
  await userApi.changeStatus(row.id, !row.enabled);
  ElMessage.success(`已${action}`);
  load();
}

function openAssign(row: UserAccount) {
  assignDialog.id = row.id;
  assignDialog.username = row.username;
  assignDialog.workshopScope = row.workshopScope || '';
  assignDialog.visible = true;
}

async function submitAssign() {
  const scope = assignDialog.workshopScope.trim();
  await ElMessageBox.confirm(
    scope
      ? `将 ${assignDialog.username} 的车间数据域改为「${scope}」？`
      : `撤销 ${assignDialog.username} 的车间分配？该用户将看不到任何车间数据。`,
    '确认分配',
    { type: 'warning' }
  );
  await userApi.assignWorkshop(assignDialog.id, scope);
  ElMessage.success(scope ? '已分配车间' : '已撤销车间分配，该用户将看不到车间数据');
  assignDialog.visible = false;
  load();
}

function openRole(row: UserAccount) {
  roleDialog.id = row.id;
  roleDialog.username = row.username;
  roleDialog.role = row.role;
  roleDialog.visible = true;
}

async function submitRole() {
  await ElMessageBox.confirm(
    `将 ${roleDialog.username} 的角色调整为「${roleLabel(roleDialog.role)}」？该用户需要重新登录。`,
    '确认调整角色',
    { type: 'warning' }
  );
  await userApi.assignRole(roleDialog.id, roleDialog.role);
  ElMessage.success('角色已调整');
  roleDialog.visible = false;
  load();
}

function openReset(row: UserAccount) {
  resetDialog.id = row.id;
  resetDialog.username = row.username;
  resetDialog.newPassword = '';
  resetDialog.visible = true;
}

async function submitReset() {
  if (resetDialog.newPassword.length < 8 || resetDialog.newPassword.length > 64) {
    ElMessage.warning('新密码需为 8-64 位');
    return;
  }
  await userApi.resetPassword(resetDialog.id, resetDialog.newPassword);
  ElMessage.success('密码已重置');
  resetDialog.visible = false;
}

onMounted(load);
</script>

<style scoped>
.scope-muted {
  color: var(--zhizao-muted);
  font-size: 13px;
}
.dialog-hint {
  background: #fff7e6;
  color: #8a5a00;
  border: 1px solid #ffe0a3;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  margin-bottom: 14px;
}
</style>
