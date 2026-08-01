package com.dbmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "export_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "table_name", length = 128)
    private String tableName;

    @Column(nullable = false, length = 16)
    private String format;

    @Column(name = "row_count", nullable = false)
    private Long rowCount = 0L;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(nullable = false, length = 16)
    private String status = "success";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "success";
        }
        if (rowCount == null) {
            rowCount = 0L;
        }
    }
}
