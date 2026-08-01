package com.dbmanager.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class FolderResponse {

    private Long id;
    private String name;
    private Long parentId;
    private Long userId;
    private int sortOrder;
    private String icon;
    private String color;
    private Instant createdAt;
    private Instant updatedAt;
}
