package com.dbmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Data
@Entity
@Table(name = "export_logs")
public class ExportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "table_name", length = 200)
    private String tableName;

    @Column(name = "query_sql", columnDefinition = "LONGTEXT")
    private String querySql;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(name = "export_format", length = 20)
    private String exportFormat;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(length = 50)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "completed_at")
    private Instant completedAt;
}
