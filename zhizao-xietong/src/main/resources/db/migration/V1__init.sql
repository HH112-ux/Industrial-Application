-- 设备表
CREATE TABLE IF NOT EXISTS eq_device (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_no     VARCHAR(32)  NOT NULL COMMENT '设备编号 EQ-年月-流水',
    device_name   VARCHAR(64)  NOT NULL COMMENT '设备名称',
    device_type   VARCHAR(32)  NOT NULL COMMENT '设备类型',
    manufacturer  VARCHAR(64)  DEFAULT NULL COMMENT '制造商',
    model         VARCHAR(32)  NOT NULL COMMENT '型号',
    serial_no     VARCHAR(64)  DEFAULT NULL COMMENT '序列号',
    status        VARCHAR(16)  NOT NULL DEFAULT 'STOPPED' COMMENT '状态 STOPPED/RUNNING/MAINTENANCE/SCRAPPED',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_no (device_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 物料表
CREATE TABLE IF NOT EXISTS mt_material (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    mat_code       VARCHAR(32)  NOT NULL COMMENT '物料编码 MT-流水',
    mat_name       VARCHAR(64)  NOT NULL COMMENT '物料名称',
    mat_type       VARCHAR(32)  NOT NULL COMMENT '物料类型',
    purchase_price DECIMAL(10,2) DEFAULT NULL COMMENT '采购价格',
    production_date DATE        DEFAULT NULL COMMENT '生产日期',
    stock_qty      INT          NOT NULL DEFAULT 0 COMMENT '库存数量',
    safe_stock     INT          NOT NULL DEFAULT 0 COMMENT '安全库存',
    kit_status     VARCHAR(16)  DEFAULT NULL COMMENT '齐套状态 SUFFICIENT/LOW/SHORTAGE',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mat_code (mat_code),
    KEY idx_kit_status (kit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料表';

-- 工艺表
CREATE TABLE IF NOT EXISTS pr_process (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_no      VARCHAR(32)  NOT NULL COMMENT '工艺编号 PR-流水',
    process_name    VARCHAR(64)  NOT NULL COMMENT '工艺名称',
    process_type    VARCHAR(32)  NOT NULL COMMENT '工艺类型',
    owner           VARCHAR(32)  NOT NULL COMMENT '责任人',
    approval_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态 DRAFT/PENDING/PUBLISHED/REJECTED',
    device_mat_req  VARCHAR(255) DEFAULT NULL COMMENT '设备材料需求（描述）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_no (process_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺表';

-- 生产计划表
CREATE TABLE IF NOT EXISTS po_plan (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    plan_order_no     VARCHAR(32)  NOT NULL COMMENT '计划订单号 PO-流水',
    product_code      VARCHAR(32)  NOT NULL COMMENT '产品编号',
    product_name      VARCHAR(64)  NOT NULL COMMENT '产品名称',
    plan_qty          INT          NOT NULL COMMENT '计划数量',
    plan_start        DATE         NOT NULL COMMENT '计划开始日期',
    plan_end          DATE         NOT NULL COMMENT '计划完成日期',
    priority          VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级 HIGH/MEDIUM/LOW',
    batch             VARCHAR(32)  DEFAULT NULL COMMENT '生产批次',
    required_device_id BIGINT      DEFAULT NULL COMMENT '所需设备 ID',
    est_hours         DECIMAL(10,2) DEFAULT NULL COMMENT '预计工时',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING_SCHEDULE' COMMENT '状态 PENDING_SCHEDULE/IN_PROGRESS/FINISHED',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_order_no (plan_order_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产计划表';

-- 生产工单表
CREATE TABLE IF NOT EXISTS wo_work_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)  NOT NULL COMMENT '工单号 WO-流水',
    plan_id      BIGINT       NOT NULL COMMENT '来源计划 ID',
    product_code VARCHAR(32)  NOT NULL COMMENT '产品编号',
    product_name VARCHAR(64)  NOT NULL COMMENT '产品名称',
    qty          INT          NOT NULL COMMENT '数量',
    batch        VARCHAR(32)  NOT NULL COMMENT '批次',
    workshop     VARCHAR(32)  NOT NULL COMMENT '车间',
    device_id    BIGINT       DEFAULT NULL COMMENT '设备 ID',
    process_id   BIGINT       DEFAULT NULL COMMENT '工艺 ID',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/IN_PRODUCTION/PENDING_QC/FINISHED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_plan_id (plan_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产工单表';

-- 报工记录表
CREATE TABLE IF NOT EXISTS wo_report (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    report_no     VARCHAR(32)  NOT NULL COMMENT '报工编号 RP-流水',
    order_id      BIGINT       NOT NULL COMMENT '工单 ID',
    report_time   DATETIME     NOT NULL COMMENT '报工时间',
    qty           INT          NOT NULL COMMENT '报工数量',
    device_id     BIGINT       DEFAULT NULL COMMENT '设备 ID',
    operator      VARCHAR(32)  DEFAULT NULL COMMENT '操作工',
    qualified_qty INT          DEFAULT NULL COMMENT '合格数量',
    defect_qty    INT          DEFAULT NULL COMMENT '不良数量',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_no (report_no),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报工记录表';

-- 质检记录表
CREATE TABLE IF NOT EXISTS wo_quality (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    quality_no  VARCHAR(32)  NOT NULL COMMENT '质检编号 QC-流水',
    order_id    BIGINT       NOT NULL COMMENT '工单 ID',
    inspector   VARCHAR(32)  DEFAULT NULL COMMENT '质检员',
    result      VARCHAR(16)  NOT NULL COMMENT '结论 PASS/FAIL',
    defect_desc VARCHAR(255) DEFAULT NULL COMMENT '不良描述',
    inspect_time DATETIME    NOT NULL COMMENT '质检时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_no (quality_no),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表';

-- 点检/维修记录表
CREATE TABLE IF NOT EXISTS eq_maintenance (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    mt_no         VARCHAR(32)  NOT NULL COMMENT '记录编号',
    device_id     BIGINT       NOT NULL COMMENT '设备 ID',
    mt_type       VARCHAR(16)  NOT NULL DEFAULT 'CHECK' COMMENT '类型 CHECK/REPAIR',
    operator      VARCHAR(32)  DEFAULT NULL COMMENT '记录人',
    mt_time       DATETIME     NOT NULL COMMENT '记录时间',
    description   VARCHAR(255) DEFAULT NULL COMMENT '描述',
    result_status VARCHAR(16)  DEFAULT 'NORMAL' COMMENT '处理结果 NORMAL/PENDING_REPAIR/FIXED',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mt_no (mt_no),
    KEY idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点检/维修记录表';

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(32)  NOT NULL COMMENT '用户名',
    password   VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    nickname   VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
    role       VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 业务编号序列表
CREATE TABLE IF NOT EXISTS sys_sequence (
    seq_name      VARCHAR(64) NOT NULL COMMENT '序列名（如 EQ-202609）',
    current_value BIGINT      NOT NULL DEFAULT 0 COMMENT '当前值',
    PRIMARY KEY (seq_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编号序列表';

-- 工艺物料清单（BOM）表
CREATE TABLE IF NOT EXISTS pr_bom (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    process_id   BIGINT      NOT NULL COMMENT '工艺 ID',
    mat_code     VARCHAR(32) NOT NULL COMMENT '物料编码',
    required_qty INT         NOT NULL DEFAULT 1 COMMENT '单件用量',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_process_id (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺物料清单(BOM)表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(32)   DEFAULT NULL COMMENT '操作人',
    method     VARCHAR(16)   DEFAULT NULL COMMENT 'HTTP 方法',
    uri        VARCHAR(255)  DEFAULT NULL COMMENT '请求路径',
    operation  VARCHAR(255)  DEFAULT NULL COMMENT '操作（类.方法）',
    params     VARCHAR(1000) DEFAULT NULL COMMENT '参数摘要',
    ip         VARCHAR(64)   DEFAULT NULL COMMENT '客户端 IP',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';
