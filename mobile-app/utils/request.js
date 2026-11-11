const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export function getToken() {
  return uni.getStorageSync('zhizao_token') || '';
}

export function setToken(token) {
  uni.setStorageSync('zhizao_token', token);
}

export function clearToken() {
  uni.removeStorageSync('zhizao_token');
}

export function request(options) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        Authorization: getToken() ? 'Bearer ' + getToken() : ''
      },
      success: (res) => {
        const body = res.data;
        if (res.statusCode === 200 && body && body.code === 0) {
          resolve(body.data);
        } else if (res.statusCode === 401) {
          clearToken();
          uni.reLaunch({ url: '/pages/login/login' });
          reject(new Error('未登录'));
        } else if (res.statusCode === 403 && body && body.code === 40301) {
          uni.showToast({ title: '请先修改初始密码', icon: 'none' });
          uni.reLaunch({ url: '/pages/changepassword/changepassword' });
          reject(new Error('请先修改初始密码'));
        } else {
          const msg = (body && body.message) || '请求失败';
          uni.showToast({ title: msg, icon: 'none' });
          reject(new Error(msg));
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' });
        reject(err);
      }
    });
  });
}
