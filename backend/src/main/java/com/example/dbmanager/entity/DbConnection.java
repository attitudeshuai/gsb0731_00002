package com.example.dbmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 目标数据库连接配置。密码以 AES 加密形式存于 password_enc。
 */
@Getter
@Setter
@Entity
@Table(name = "connections")
public class DbConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port = 3306;

    @Column(nullable = false, length = 128)
    private String username = "";

    @Column(name = "password_enc", length = 1024)
    private String passwordEnc;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "query_timeout_seconds", nullable = false)
    private Integer queryTimeoutSeconds = 60;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
