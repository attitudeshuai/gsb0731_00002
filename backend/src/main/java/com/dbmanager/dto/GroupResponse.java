package com.dbmanager.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class GroupResponse {

    private Long id;
    private String name;
    private Long parentId;
    private Long userId;
    private int sortOrder;
    private String color;
    private Instant createdAt;
    private Instant updatedAt;
}
