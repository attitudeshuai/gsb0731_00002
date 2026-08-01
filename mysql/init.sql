-- 元数据库初始化脚本（由 docker-entrypoint-initdb.d 自动执行）
CREATE DATABASE IF NOT EXISTS dbmanager_meta
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE dbmanager_meta;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128),
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS connection_groups (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS connections (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    group_id        BIGINT       NULL,
    host            VARCHAR(255) NOT NULL,
    port            INT          NOT NULL DEFAULT 3306,
    username        VARCHAR(128) NOT NULL,
    password_enc    VARCHAR(1024) NULL COMMENT 'AES 加密后的密码',
    database_name   VARCHAR(128) NULL,
    query_timeout_seconds INT  NOT NULL DEFAULT 60 COMMENT '查询超时时间（秒）',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_conn_group FOREIGN KEY (group_id) REFERENCES connection_groups (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS query_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT       NULL,
    database_name VARCHAR(128) NULL,
    sql_text      TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    row_count     INT          NULL,
    duration_ms   BIGINT       NULL,
    error_message TEXT         NULL,
    executed_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_history_conn (connection_id),
    INDEX idx_history_time (executed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS query_folders (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS saved_queries (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    folder_id  BIGINT       NULL,
    name       VARCHAR(128) NOT NULL,
    sql_text   TEXT         NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sq_folder FOREIGN KEY (folder_id) REFERENCES query_folders (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS export_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT       NULL,
    database_name VARCHAR(128) NULL,
    table_name    VARCHAR(128) NULL,
    format        VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED/FAILED/CANCELLED',
    error_message TEXT         NULL,
    row_count     INT          NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 默认用户（无登录流程，占位）
INSERT INTO users (username, password_hash, display_name)
SELECT 'admin', '', 'Administrator'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
