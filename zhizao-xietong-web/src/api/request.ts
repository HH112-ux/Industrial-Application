import axios from 'axios';

const TOKEN_KEY = 'zhizao_token';

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('zhizao_user');
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
    if (error?.response?.status === 401) {
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
