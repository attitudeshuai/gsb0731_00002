-- ============================================================
--  Database visualization management tool - Meta database
--  All datetime fields stored in UTC.
-- ============================================================

CREATE DATABASE IF NOT EXISTS `dbmanager`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `dbmanager`;

-- ------------------------------------------------------------
--  users
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `username`   VARCHAR(64)  NOT NULL,
  `password`   VARCHAR(255) NOT NULL,
  `email`      VARCHAR(128) DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  connection_groups
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `connection_groups` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(128) NOT NULL,
  `parent_id`  BIGINT       DEFAULT NULL,
  `sort_order` INT          NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_groups_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  connections
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `connections` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(128) NOT NULL,
  `host`        VARCHAR(255) NOT NULL,
  `port`        INT          NOT NULL DEFAULT 3306,
  `username`    VARCHAR(128) NOT NULL,
  `password`    VARCHAR(512) NOT NULL COMMENT 'AES encrypted',
  `database_name` VARCHAR(128) DEFAULT NULL,
  `group_id`    BIGINT       DEFAULT NULL,
  `color`       VARCHAR(16)  DEFAULT NULL,
  `remark`      VARCHAR(512) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_connections_group` (`group_id`),
  CONSTRAINT `fk_connections_group` FOREIGN KEY (`group_id`)
    REFERENCES `connection_groups` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  query_folders
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `query_folders` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(128) NOT NULL,
  `parent_id`  BIGINT       DEFAULT NULL,
  `sort_order` INT          NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_qfolders_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  saved_queries
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `saved_queries` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(255) NOT NULL,
  `sql_text`    MEDIUMTEXT   NOT NULL,
  `folder_id`   BIGINT       DEFAULT NULL,
  `connection_id` BIGINT     DEFAULT NULL,
  `database_name` VARCHAR(128) DEFAULT NULL,
  `tags`        VARCHAR(512) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_saved_folder` (`folder_id`),
  KEY `idx_saved_conn` (`connection_id`),
  CONSTRAINT `fk_saved_folder` FOREIGN KEY (`folder_id`)
    REFERENCES `query_folders` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  query_history
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `query_history` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `connection_id` BIGINT       DEFAULT NULL,
  `database_name` VARCHAR(128) DEFAULT NULL,
  `sql_text`      MEDIUMTEXT   NOT NULL,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'success',
  `affected_rows` BIGINT       NOT NULL DEFAULT 0,
  `elapsed_ms`    BIGINT       NOT NULL DEFAULT 0,
  `error_message` TEXT         DEFAULT NULL,
  `executed_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_history_conn` (`connection_id`),
  KEY `idx_history_executed` (`executed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
--  export_logs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `export_logs` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `connection_id` BIGINT       DEFAULT NULL,
  `database_name` VARCHAR(128) DEFAULT NULL,
  `table_name`    VARCHAR(128) DEFAULT NULL,
  `format`        VARCHAR(16)  NOT NULL,
  `row_count`     BIGINT       NOT NULL DEFAULT 0,
  `file_name`     VARCHAR(255) DEFAULT NULL,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'success',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_export_conn` (`connection_id`),
  KEY `idx_export_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
