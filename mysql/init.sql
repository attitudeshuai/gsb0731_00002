-- ============================================================
-- DB Manager - Metadata Database Initialization
-- All datetime fields stored in UTC
-- ============================================================

CREATE DATABASE IF NOT EXISTS `db_manager`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `db_manager`;

-- ------------------------------------------------------------
-- users
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`    DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `username`      VARCHAR(100) NOT NULL,
    `password_hash` VARCHAR(255) DEFAULT NULL,
    `email`         VARCHAR(200) DEFAULT NULL,
    `display_name`  VARCHAR(100) DEFAULT NULL,
    `role`          VARCHAR(50)  NOT NULL DEFAULT 'USER',
    `enabled`       BIT(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- connection_groups
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `connection_groups` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `name`       VARCHAR(100) NOT NULL,
    `parent_id`  BIGINT       DEFAULT NULL,
    `user_id`    BIGINT       DEFAULT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    `color`      VARCHAR(20)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_conn_groups_parent` (`parent_id`),
    KEY `idx_conn_groups_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- connections
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `connections` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`          DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`          DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `name`                VARCHAR(100) NOT NULL,
    `host`                VARCHAR(255) NOT NULL,
    `port`                INT          NOT NULL,
    `username`            VARCHAR(100) NOT NULL,
    `password_encrypted`  TEXT         NOT NULL,
    `database_name`       VARCHAR(100) DEFAULT NULL,
    `group_id`            BIGINT       DEFAULT NULL,
    `user_id`             BIGINT       DEFAULT NULL,
    `type`                VARCHAR(20)  NOT NULL DEFAULT 'MYSQL',
    `color`               VARCHAR(20)  DEFAULT NULL,
    `last_connected_at`   DATETIME(6)  DEFAULT NULL,
    `remark`              VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_connections_group` (`group_id`),
    KEY `idx_connections_user` (`user_id`),
    KEY `idx_connections_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- query_folders
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `query_folders` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at` DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `name`       VARCHAR(100) NOT NULL,
    `parent_id`  BIGINT       DEFAULT NULL,
    `user_id`    BIGINT       DEFAULT NULL,
    `sort_order` INT          NOT NULL DEFAULT 0,
    `icon`       VARCHAR(50)  DEFAULT NULL,
    `color`      VARCHAR(20)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_query_folders_parent` (`parent_id`),
    KEY `idx_query_folders_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- saved_queries
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `saved_queries` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`    DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `name`          VARCHAR(200) NOT NULL,
    `sql_text`      LONGTEXT     NOT NULL,
    `folder_id`     BIGINT       DEFAULT NULL,
    `connection_id` BIGINT       DEFAULT NULL,
    `user_id`       BIGINT       DEFAULT NULL,
    `description`   VARCHAR(500) DEFAULT NULL,
    `tags`          VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_saved_queries_folder` (`folder_id`),
    KEY `idx_saved_queries_conn` (`connection_id`),
    KEY `idx_saved_queries_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- query_history
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `query_history` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `created_at`       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `connection_id`    BIGINT      NOT NULL,
    `sql_text`         LONGTEXT    NOT NULL,
    `execution_time_ms` BIGINT      DEFAULT NULL,
    `row_count`        INT         DEFAULT NULL,
    `success`          BIT(1)      NOT NULL DEFAULT b'1',
    `error_message`    TEXT        DEFAULT NULL,
    `executed_at`      DATETIME(6) DEFAULT NULL,
    `user_id`          BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_qh_conn` (`connection_id`),
    KEY `idx_qh_user` (`user_id`),
    KEY `idx_qh_executed` (`executed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- export_logs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `export_logs` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `created_at`    DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`    DATETIME(6)  DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `connection_id` BIGINT       DEFAULT NULL,
    `table_name`    VARCHAR(200) DEFAULT NULL,
    `query_sql`     LONGTEXT     DEFAULT NULL,
    `execution_id`  VARCHAR(64)  DEFAULT NULL,
    `export_format` VARCHAR(20)  DEFAULT NULL,
    `file_name`     VARCHAR(255) DEFAULT NULL,
    `file_size`     BIGINT       DEFAULT NULL,
    `row_count`     INT          DEFAULT NULL,
    `status`        VARCHAR(50)  DEFAULT NULL,
    `error_message` TEXT         DEFAULT NULL,
    `user_id`       BIGINT       DEFAULT NULL,
    `completed_at`  DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_export_logs_exec` (`execution_id`),
    KEY `idx_export_logs_conn` (`connection_id`),
    KEY `idx_export_logs_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
