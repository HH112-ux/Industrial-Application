import axios from 'axios';

const TOKEN_KEY = 'zhizao_token';
const MUST_CHANGE_KEY = 'zhizao_must_change';

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('zhizao_user');
  localStorage.removeItem(MUST_CHANGE_KEY);
}

export function mustChangePassword(): boolean {
  return localStorage.getItem(MUST_CHANGE_KEY) === '1';
}

export function setMustChangePassword(value: boolean) {
  if (value) {
    localStorage.setItem(MUST_CHANGE_KEY, '1');
  } else {
    localStorage.removeItem(MUST_CHANGE_KEY);
  }
}

export function getUser(): { username: string; nickname: string; role: string } | null {
  const raw = localStorage.getItem('zhizao_user');
  return raw ? JSON.parse(raw) : null;
}

export function setUser(user: { username: string; nickname: string; role: string }) {
  localStorage.setItem('zhizao_user', JSON.stringify(user));
}

export function isAdmin(): boolean {
  return getUser()?.role === 'ADMIN';
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
});

request.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res && typeof res.code === 'number') {
      if (res.code !== 0) {
        ElMessage.error(res.message || '请求失败');
        return Promise.reject(new Error(res.message || '请求失败'));
      }
      return res.data;
    }
    return res;
  },
  (error) => {
    const status = error?.response?.status;
    const code = error?.response?.data?.code;
    if (status === 403 && code === 40301) {
      setMustChangePassword(true);
      ElMessage.warning('请先修改初始密码');
      if (window.location.pathname !== '/change-password') {
        window.location.href = '/change-password';
      }
    } else if (status === 401) {
      clearToken();
      ElMessage.error('登录已过期，请重新登录');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    } else {
      ElMessage.error(error?.response?.data?.message || error.message || '网络错误');
    }
    return Promise.reject(error);
  }
);

export default request;
