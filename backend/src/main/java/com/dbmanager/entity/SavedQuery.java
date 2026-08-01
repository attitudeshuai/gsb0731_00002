package com.dbmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "saved_queries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedQuery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "sql_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String sqlText;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(length = 512)
    private String tags;
}
