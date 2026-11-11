-- 成品库存：按成品编码 + 车间累计合格入库数量，确认入库时原子累加。
-- 幂等性：CREATE TABLE IF NOT EXISTS 可重复执行；uk_stock_product_workshop 保证同一编码同一车间只有一行。
-- 存量数据影响：新增表，不回填历史库存，也不影响既有表。
-- 回滚：DROP TABLE fg_finished_stock;
CREATE TABLE IF NOT EXISTS fg_finished_stock (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_code VARCHAR(32) NOT NULL COMMENT '成品编码',
    workshop     VARCHAR(32) NOT NULL COMMENT '车间，用于数据域过滤',
    qty          INT         NOT NULL DEFAULT 0 COMMENT '累计合格入库数量',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_product_workshop (product_code, workshop),
    KEY idx_stock_workshop (workshop)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品库存';
