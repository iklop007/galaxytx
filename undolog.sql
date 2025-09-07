CREATE TABLE undo_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    xid VARCHAR(128) NOT NULL COMMENT '全局事务ID',
    branch_id BIGINT NOT NULL COMMENT '分支事务ID',
    table_name VARCHAR(64) NOT NULL COMMENT '表名',
    before_image TEXT COMMENT '前镜像数据',
    after_image TEXT COMMENT '后镜像数据',
    sql_text TEXT COMMENT '原始SQL',
    parameters TEXT COMMENT 'SQL参数',
    log_status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-补偿中，2-已补偿',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_xid_branch_id (xid, branch_id),
    INDEX idx_create_time (create_time)
) COMMENT='Undo Log表';