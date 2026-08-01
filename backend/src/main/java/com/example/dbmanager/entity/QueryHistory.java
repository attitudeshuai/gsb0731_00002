package com.example.dbmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "query_history")
public class QueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "sql_text", nullable = false)
    private String sqlText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QueryStatus status = QueryStatus.SUCCESS;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @PrePersist
    void onCreate() {
        if (this.executedAt == null) {
            this.executedAt = Instant.now();
        }
    }
}
