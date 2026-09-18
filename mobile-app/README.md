# 智造协同 · 移动端（uni-app）

面向车间现场操作工的移动端 App，聚焦「生产执行」：登录、工单列表、工单详情与开工/报工/质检。

技术栈：uni-app（Vue3 语法），支持 H5 / App（Android/iOS）/ 微信小程序。

## 运行方式

推荐用 **HBuilderX**（官方 IDE）打开本目录运行：

1. 下载安装 [HBuilderX](https://www.dcloud.io/hbuilderx.html)。
2. 菜单「文件 → 导入 → 从本地目录导入」，选择本 `mobile-app` 目录。
3. 点击工具栏「运行 → 运行到浏览器」（H5），或「运行 → 运行到手机或模拟器」（App）。

## 后端地址

默认请求 `http://localhost:8080/api`（见 `utils/request.js` 的 `BASE_URL`）。

- H5 运行时后端已开启 CORS，可直接跨域访问。
- 真机/模拟器运行时，请把 `BASE_URL` 改为你电脑的局域网 IP（如 `http://192.168.x.x:8080/api`），并确保手机与电脑在同一网络。

## 登录账号

默认 `admin / admin123`（由后端启动时自动初始化）。

## 目录结构

```
mobile-app/
├── App.vue               # 应用入口组件
├── main.js               # 入口（createSSRApp）
├── manifest.json         # 应用配置
├── pages.json            # 页面路由与导航栏
├── utils/request.js      # uni.request 封装（token）
├── api/index.js          # 接口封装
└── pages/
    ├── login/login.vue       # 登录页
    └── workorder/
        ├── list.vue          # 工单列表
        └── detail.vue        # 工单详情 + 开工/报工/质检
```
