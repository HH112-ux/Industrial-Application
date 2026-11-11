<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索用户名/昵称" clearable style="width: 240px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-switch v-model="query.ssoOnly" active-text="仅看未转双通道" @change="reloadFirst" />
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新增账号</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="用户名" width="210">
          <template #default="{ row }">
            <span class="cell-code">{{ row.username }}</span>
            <el-tag size="small" :type="sourceTagType(row.authSource)" style="margin-left: 6px">
              {{ sourceText(row.authSource) }}
            </el-tag>
            <el-tag v-if="row.mustChangePassword" size="small" type="danger" style="margin-left: 6px">待改密</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="姓名" width="110" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="车间数据域" min-width="200">
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
        <el-table-column label="操作" width="430" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRole(row)">分配角色</el-button>
            <el-button v-if="row.role !== 'ADMIN'" link type="primary" @click="openAssign(row)">分配车间</el-button>
            <el-tooltip :disabled="!isSelf(row)" content="不能禁用当前登录账号" placement="top">
              <span class="action-wrap">
                <el-button link :type="row.enabled ? 'danger' : 'success'" :disabled="isSelf(row)" @click="toggleStatus(row)">
                  {{ row.enabled ? '禁用' : '启用' }}
                </el-button>
              </span>
            </el-tooltip>
            <el-button link type="warning" @click="openReset(row)">{{ row.authSource === 'SSO' ? '开通本地密码' : '重置密码' }}</el-button>
            <el-tooltip :disabled="!isSelf(row)" content="不能删除当前登录账号" placement="top">
              <span class="action-wrap">
                <el-button link type="danger" :disabled="isSelf(row)" @click="removeUser(row)">删除</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load" />
      </div>
    </div>

    <el-dialog v-model="createDialog.visible" title="新增账号" width="500px">
      <div class="dialog-hint">
        新建账号为本地账号，首次登录必须修改初始密码。管理员不受车间数据域限制，无需指定车间。
      </div>
      <el-form label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="createDialog.username" maxlength="32" placeholder="3-32 位，字母开头，可含数字 . _ -" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="createDialog.nickname" maxlength="32" placeholder="用于列表与审计展示" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="createDialog.role" style="width: 100%" @change="onCreateRoleChange">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
            <el-option label="车间员工" value="WORKER" />
          </el-select>
        </el-form-item>
        <el-form-item label="车间">
          <el-select v-model="createDialog.workshopScope" filterable allow-create clearable default-first-option
            :disabled="createDialog.role === 'ADMIN'" placeholder="可手填，如：一车间" style="width: 100%">
            <el-option v-for="item in workshopOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createDialog.password" type="password" show-password maxlength="64"
            placeholder="留空由系统生成，仅展示一次" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialog.visible" title="账号已创建" width="500px" :close-on-click-modal="false">
      <div class="dialog-hint">
        初始密码只显示这一次，请立即转交。关闭后无法再次查看，只能通过「重置密码」重新设置。
      </div>
      <el-form label-width="90px">
        <el-form-item label="用户名">
          <span class="cell-code">{{ resultDialog.username }}</span>
        </el-form-item>
        <el-form-item label="初始密码">
          <template v-if="resultDialog.password">
            <span class="cell-code">{{ resultDialog.password }}</span>
            <el-button link type="primary" style="margin-left: 12px" @click="copyInitialPassword">复制</el-button>
          </template>
          <span v-else class="scope-muted">使用你填写的初始密码，系统不再显示</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="resultDialog.visible = false">我已转交</el-button>
      </template>
    </el-dialog>

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
          <el-select v-model="assignDialog.workshopScope" filterable allow-create clearable default-first-option
            placeholder="可手填，如：一车间" style="width: 100%">
            <el-option v-for="item in workshopOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetDialog.visible" :title="resetTitle" width="460px">
      <div class="dialog-hint">{{ resetHint }}</div>
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span class="cell-code">{{ resetDialog.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="resetDialog.newPassword" type="password" show-password
            placeholder="8-64 位，且不能包含用户名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReset">{{ resetIsSso ? '开通并设置' : '确定' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { deviceApi, planApi, userApi, type UserAccount } from '@/api';
import { getUser } from '@/api/request';

const USERNAME_PATTERN = /^[A-Za-z][A-Za-z0-9._-]{2,31}$/;

const loading = ref(false);
const rows = ref<UserAccount[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', ssoOnly: false });
const workshopOptions = ref<string[]>([]);
const currentUsername = getUser()?.username || '';

const createDialog = reactive({
  visible: false, submitting: false, username: '', nickname: '', role: 'WORKER', workshopScope: '', password: ''
});
const resultDialog = reactive({ visible: false, username: '', password: '' });
const assignDialog = reactive({ visible: false, id: 0, username: '', workshopScope: '' });
const roleDialog = reactive({ visible: false, id: 0, username: '', role: 'USER' });
const resetDialog = reactive({ visible: false, id: 0, username: '', newPassword: '', authSource: 'LOCAL' });

const resetIsSso = computed(() => resetDialog.authSource === 'SSO');
const resetTitle = computed(() => (resetIsSso.value ? '开通本地密码' : '重置密码'));
const resetHint = computed(() => {
  if (resetIsSso.value) {
    return '该账号目前仅支持企业微信扫码登录。设置本地密码后将转为双通道，扫码与密码两种方式都能登录；所有旧令牌立即失效，下次登录必须先修改密码。';
  }
  if (resetDialog.authSource === 'BOTH') {
    return '该账号已支持扫码与密码双通道。重置后密码通道使用新密码，扫码通道不受影响；所有旧令牌立即失效，下次登录必须先修改密码。';
  }
  return '重置后该用户下次登录必须先修改密码，所有旧令牌立即失效，需要用新密码重新登录。';
});

function sourceText(source?: string): string {
  if (source === 'SSO') return '企微';
  if (source === 'BOTH') return '双通道';
  return '本地';
}

function sourceTagType(source?: string): 'info' | 'warning' | 'success' {
  if (source === 'SSO') return 'warning';
  if (source === 'BOTH') return 'success';
  return 'info';
}

function isSelf(row: UserAccount): boolean {
  return !!currentUsername && row.username === currentUsername;
}

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

async function loadWorkshopOptions() {
  try {
    const [devices, plans] = await Promise.all([
      deviceApi.list({ pageNum: 1, pageSize: 200 }),
      planApi.list({ pageNum: 1, pageSize: 200 })
    ]);
    const values = new Set<string>();
    devices.records.forEach((item) => item.workshop && values.add(item.workshop));
    plans.records.forEach((item) => item.workshop && values.add(item.workshop));
    workshopOptions.value = Array.from(values).sort();
  } catch {
    workshopOptions.value = [];
  }
}

function reloadFirst() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.keyword = '';
  query.ssoOnly = false;
  query.pageNum = 1;
  load();
}

function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}

function openCreate() {
  createDialog.username = '';
  createDialog.nickname = '';
  createDialog.role = 'WORKER';
  createDialog.workshopScope = '';
  createDialog.password = '';
  createDialog.visible = true;
}

function onCreateRoleChange(role: string) {
  if (role === 'ADMIN') {
    createDialog.workshopScope = '';
  }
}

async function submitCreate() {
  const username = createDialog.username.trim();
  const nickname = createDialog.nickname.trim();
  const workshop = createDialog.workshopScope.trim();
  if (!USERNAME_PATTERN.test(username)) {
    ElMessage.warning('用户名需为 3-32 位、字母开头，只能包含字母、数字、点、下划线或短横线');
    return;
  }
  if (!nickname || nickname.length > 32) {
    ElMessage.warning('姓名不能为空且不能超过 32 位');
    return;
  }
  if (workshop.length > 32) {
    ElMessage.warning('车间名称不能超过 32 位');
    return;
  }
  if (createDialog.password && (createDialog.password.length < 8 || createDialog.password.length > 64)) {
    ElMessage.warning('初始密码需为 8-64 位');
    return;
  }
  createDialog.submitting = true;
  try {
    const res = await userApi.create({
      username,
      nickname,
      role: createDialog.role,
      workshopScope: createDialog.role === 'ADMIN' ? undefined : workshop || undefined,
      password: createDialog.password || undefined
    });
    createDialog.visible = false;
    resultDialog.username = res.user?.username || username;
    resultDialog.password = res.initialPassword || '';
    resultDialog.visible = true;
    load();
  } finally {
    createDialog.submitting = false;
  }
}

async function copyInitialPassword() {
  try {
    await navigator.clipboard.writeText(resultDialog.password);
    ElMessage.success('已复制初始密码');
  } catch {
    ElMessage.warning('复制失败，请手动选中后复制');
  }
}

async function toggleStatus(row: UserAccount) {
  const action = row.enabled ? '禁用' : '启用';
  await ElMessageBox.confirm(`确定${action}用户 ${row.username} 吗？该用户旧令牌将立即失效。`, '提示', { type: 'warning' });
  await userApi.changeStatus(row.id, !row.enabled);
  ElMessage.success(`已${action}`);
  load();
}

async function removeUser(row: UserAccount) {
  await ElMessageBox.confirm(
    `确定删除账号 ${row.username} 吗？账号会保留在历史记录中，用户名不可复用。`,
    '删除账号',
    { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
  );
  await userApi.delete(row.id);
  ElMessage.success('账号已删除');
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
  resetDialog.authSource = row.authSource || 'LOCAL';
  resetDialog.newPassword = '';
  resetDialog.visible = true;
}

async function submitReset() {
  if (resetDialog.newPassword.length < 8 || resetDialog.newPassword.length > 64) {
    ElMessage.warning('新密码需为 8-64 位');
    return;
  }
  await userApi.resetPassword(resetDialog.id, resetDialog.newPassword);
  ElMessage.success(resetIsSso.value ? '已开通本地密码，账号转为双通道' : '密码已重置');
  resetDialog.visible = false;
  load();
}

onMounted(() => {
  load();
  loadWorkshopOptions();
});
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
.action-wrap {
  display: inline-block;
}
</style>
