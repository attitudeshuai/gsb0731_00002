package com.dbtool.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Target-database connection configuration. The password is stored AES-encrypted
 * in {@code password_encrypted} and is never persisted in plaintext.
 */
@Getter
@Setter
@Entity
@Table(name = "connections")
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port = 3306;

    @Column(nullable = false, length = 128)
    private String username;

    /** AES-encrypted password payload. */
    @Column(name = "password_encrypted", length = 512)
    private String passwordEncrypted;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    /** Reserved for future multi-engine support; currently "mysql". */
    @Column(name = "db_type", length = 32, nullable = false)
    private String dbType = "mysql";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
