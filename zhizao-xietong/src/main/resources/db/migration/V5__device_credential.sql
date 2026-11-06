-- 设备凭证：设备上报使用 key_id + HMAC-SHA256，不复用用户 JWT
CREATE TABLE IF NOT EXISTS eq_device_credential (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_id         BIGINT       NOT NULL COMMENT '设备主键，一设备一凭证',
    key_id            VARCHAR(32)  NOT NULL COMMENT '凭证标识，随请求头传递',
    secret_ciphertext VARCHAR(256) NOT NULL COMMENT '设备密钥密文，AES-GCM 加密存储，不落明文',
    enabled           TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态 1 启用 0 已吊销',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    last_used_at      DATETIME     DEFAULT NULL COMMENT '最近一次通过校验的时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    UNIQUE KEY uk_key_id (key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备凭证表';
