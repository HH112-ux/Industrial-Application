---
intent: 移动端（uni-app）工单详情只读展示排程落点，与 Web 端能力对等
success_criteria: 详情页可看到设备、占用起止、队列位次；无排程时有明确空态；后端与排产算法零改动
risk_level: low
auto_approve: true
branch: feat/mobile-schedule-view
worktree: false
---

# 移动端工单排程落点（只读）

## 范围

- 数据来源：`GET /api/schedules/work-orders/{id}`（PR #34）
- 对照实现：Web `WorkOrderList.vue` 的「排程」弹窗（PR #35）
- 只做展示：不改后端、不改排产算法、不碰报工逻辑

## 测试策略

移动端没有测试框架，Vue 模板也不在 Node 中渲染。把「字段到文案」的纯逻辑抽到
`mobile-app/utils/schedule.mjs`，用 Node 自带 `node --test` 覆盖；UI 接线用 H5 构建、
浏览器实渲染（mock 接口）和代码审查兜底。不引入 Vitest，避免为一个只读展示扩张工具链。

## 步骤

- [ ] **Step 1: 写失败测试**
action: 新建 `mobile-app/utils/schedule.test.mjs` 覆盖时间、设备、工时、位次、汇总；在 `package.json` 增加 `test` 脚本；先确认失败
loop: false
verify: cd mobile-app && npm test
gate: auto

- [ ] **Step 2: 实现展示映射**
action: 新建 `mobile-app/utils/schedule.mjs`，实现 Step 1 覆盖的纯函数
loop: until 测试全绿
max_iterations: 3
verify: cd mobile-app && npm test
gate: auto

- [ ] **Step 3: 接入接口与页面**
action: `mobile-app/api/index.js` 增加 `scheduleApi.entries`；`detail.vue` 增加加载态、空态与明细卡片
loop: false
verify: cd mobile-app && npm run build:h5
gate: auto

- [ ] **Step 4: CI 覆盖移动端**
action: `.github/workflows/ci.yml` 增加 mobile job：`npm ci`、`npm test`、`npm run build:h5`
loop: false
verify: 推送后 `Mobile tests and build` 为绿
gate: auto

- [ ] **Step 5: 完成前验证**
action: 后端回归 + 移动端测试/构建，保留真实输出
loop: false
verify:
  - type: shell
    command: cd zhizao-xietong && mvn -B test
  - type: shell
    command: cd mobile-app && npm test && npm run build:h5
gate: auto

- [ ] **Step 6: 代码审查**
action: 用 `code-review-report-plugin` 审查本分支相对 main 的改动，阻断项修完再交付
loop: false
verify: 审查报告列出问题与处置结论
gate: auto

- [ ] **Step 7: 交付**
action: 推 `feat/mobile-schedule-view`，创建单个 PR（≤400 行），正文写回归面与用例数变化
loop: false
verify: `gh pr checks` 全绿
gate: human

## 跳过项

- **brainstorming / docs/designs**：需求边界已给定且为低风险只读展示，无架构取舍。
- **setup-project 适配文件**：README、ENGINEERING-DESIGN、CI、hooks 已承载真实约束，再生成会形成第二套事实来源；当前只使用 Trae 会话。
- **多阶段 initiative 脚手架**：单切片，不适用。
- **hotl 自带 code-review / pr-reviewing**：与现有代码审查插件标准冲突，只用后者。
