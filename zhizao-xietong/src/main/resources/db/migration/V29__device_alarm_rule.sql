-- 设备报警规则：阈值越界（THRESHOLD）与设备离线（OFFLINE）两类规则。
-- 幂等性：CREATE TABLE IF NOT EXISTS 可重复执行；仅新增表，不改动历史迁移。
-- 存量数据影响：新表为空，不预置规则，不影响既有设备与遥测数据。
-- 回滚：DROP TABLE IF EXISTS eq_alarm_rule;
CREATE TABLE IF NOT EXISTS eq_alarm_rule
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id         BIGINT      NOT NULL COMMENT 'eq_device.id',
    metric            VARCHAR(32) NOT NULL DEFAULT '' COMMENT '阈值指标：temperature/rpm/current；离线规则为空串',
    rule_type         VARCHAR(16) NOT NULL COMMENT 'THRESHOLD/OFFLINE',
    upper_limit       DOUBLE               DEFAULT NULL COMMENT '上限（触发条件为大于该值）',
    lower_limit       DOUBLE               DEFAULT NULL COMMENT '下限（触发条件为小于该值）',
    consecutive_times INT         NOT NULL DEFAULT 3 COMMENT '连续越界多少次才触发',
    current_streak    INT         NOT NULL DEFAULT 0 COMMENT '当前连续越界次数，由判定逻辑维护',
    enabled           TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_device_type_metric (device_id, rule_type, metric),
    KEY idx_rule_type_enabled (rule_type, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备报警规则';
