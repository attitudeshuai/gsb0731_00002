package com.dbmanager.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class SavedQueryResponse {

    private Long id;
    private String name;
    private String sqlText;
    private Long folderId;
    private Long connectionId;
    private Long userId;
    private String description;
    private String tags;
    private Instant createdAt;
    private Instant updatedAt;
}
