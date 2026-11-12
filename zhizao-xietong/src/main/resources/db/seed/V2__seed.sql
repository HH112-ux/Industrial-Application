-- 演示种子数据
INSERT INTO eq_device (device_no, device_name, device_type, manufacturer, model, serial_no, status) VALUES
('EQ-202609-0001', '数控车床 CNC-01', '加工设备', '沈阳机床', 'CK6150', 'SN-8801', 'RUNNING'),
('EQ-202609-0002', '立式加工中心 MC-02', '加工设备', '大连机床', 'VMC850', 'SN-8802', 'RUNNING'),
('EQ-202609-0003', '激光切割机 LC-03', '切割设备', '大族激光', 'G3015F', 'SN-8803', 'STOPPED');

INSERT INTO mt_material (mat_code, mat_name, mat_type, purchase_price, stock_qty, safe_stock, kit_status) VALUES
('MT-001', '45# 圆钢 Φ50', '原材料', 12.50, 320, 100, 'SUFFICIENT'),
('MT-002', '铝合金板 6061', '原材料', 28.00, 260, 100, 'SUFFICIENT'),
('MT-003', '轴承 6204', '外购件', 15.00, 300, 50, 'SUFFICIENT'),
('MT-004', '切削液', '辅料', 9.00, 18, 30, 'LOW');

INSERT INTO pr_process (process_no, process_name, process_type, owner, approval_status, device_mat_req) VALUES
('PR-001', '轴类件车削工艺', '机加工', '张伟', 'PUBLISHED', 'MT-001,MT-003'),
('PR-002', '箱体铣削工艺', '机加工', '李娜', 'DRAFT', 'MT-002');

INSERT INTO po_plan (plan_order_no, product_code, product_name, plan_qty, plan_start, plan_end, priority, batch, required_device_id, est_hours, status) VALUES
('PO-202609-0001', 'P-001', '传动轴 A', 200, '2026-09-20', '2026-09-25', 'HIGH', 'B01', 1, 40.00, 'PENDING_SCHEDULE');

-- 工艺 PR-001 的物料清单（单件用量）
INSERT INTO pr_bom (process_id, mat_code, required_qty) VALUES
(1, 'MT-001', 1),
(1, 'MT-003', 1);
