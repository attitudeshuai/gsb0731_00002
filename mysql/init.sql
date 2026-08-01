-- Metadata database schema for DBTool.
-- Runs automatically via docker-entrypoint-initdb.d on first container start.
-- All timestamps are stored in UTC.

CREATE DATABASE IF NOT EXISTS dbtool
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE dbtool;

CREATE TABLE IF NOT EXISTS users (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  username     VARCHAR(64)  NOT NULL UNIQUE,
  display_name VARCHAR(128),
  created_at   DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS connection_groups (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(128) NOT NULL,
  sort_order INT DEFAULT 0,
  created_at DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS connections (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  name               VARCHAR(128) NOT NULL,
  group_id           BIGINT,
  host               VARCHAR(255) NOT NULL,
  port               INT NOT NULL DEFAULT 3306,
  username           VARCHAR(128) NOT NULL,
  password_encrypted VARCHAR(512),
  database_name      VARCHAR(128),
  db_type            VARCHAR(32)  NOT NULL DEFAULT 'mysql',
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL,
  CONSTRAINT fk_conn_group FOREIGN KEY (group_id)
    REFERENCES connection_groups (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_connections_group ON connections (group_id);

CREATE TABLE IF NOT EXISTS query_history (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  connection_id BIGINT,
  sql_text      TEXT NOT NULL,
  success       TINYINT(1) NOT NULL DEFAULT 1,
  error_message TEXT,
  rows_affected INT,
  duration_ms   BIGINT,
  executed_at   DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_history_conn ON query_history (connection_id, executed_at);

CREATE TABLE IF NOT EXISTS query_folders (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(128) NOT NULL,
  sort_order INT DEFAULT 0,
  created_at DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS saved_queries (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(128) NOT NULL,
  folder_id     BIGINT,
  connection_id BIGINT,
  sql_text      TEXT NOT NULL,
  description   TEXT,
  created_at    DATETIME(6) NOT NULL,
  updated_at    DATETIME(6) NOT NULL,
  CONSTRAINT fk_saved_folder FOREIGN KEY (folder_id)
    REFERENCES query_folders (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_saved_folder ON saved_queries (folder_id);

CREATE TABLE IF NOT EXISTS export_logs (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  connection_id BIGINT,
  source_type   VARCHAR(32)  NOT NULL,
  source_ref    VARCHAR(512),
  format        VARCHAR(16)  NOT NULL,
  row_count     BIGINT,
  status        VARCHAR(32)  NOT NULL DEFAULT 'success',
  created_at    DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- seed a default user and connection group
INSERT INTO users (username, display_name, created_at)
VALUES ('admin', 'Administrator', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO connection_groups (name, sort_order, created_at)
VALUES ('Default', 0, UTC_TIMESTAMP(6));
