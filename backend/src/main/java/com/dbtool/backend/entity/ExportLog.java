package com.dbtool.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "export_logs")
public class ExportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id")
    private Long connectionId;

    /** table | query */
    @Column(name = "source_type", length = 32, nullable = false)
    private String sourceType;

    @Column(name = "source_ref", length = 512)
    private String sourceRef;

    /** csv | json | sql */
    @Column(length = 16, nullable = false)
    private String format;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(nullable = false, length = 32)
    private String status = "success";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
