package com.dbmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "query_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Lob
    @Column(name = "sql_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String sqlText;

    @Column(nullable = false, length = 16)
    private String status = "success";

    @Column(name = "affected_rows", nullable = false)
    private Long affectedRows = 0L;

    @Column(name = "elapsed_ms", nullable = false)
    private Long elapsedMs = 0L;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @PrePersist
    public void prePersist() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
        if (status == null) {
            status = "success";
        }
        if (affectedRows == null) {
            affectedRows = 0L;
        }
        if (elapsedMs == null) {
            elapsedMs = 0L;
        }
    }
}
