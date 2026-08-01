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
@Table(name = "saved_queries")
public class SavedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "sql_text", columnDefinition = "LONGTEXT", nullable = false)
    private String sqlText;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String tags;
}
