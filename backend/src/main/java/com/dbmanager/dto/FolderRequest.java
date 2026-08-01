package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FolderRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private Long parentId;

    private int sortOrder;

    private String icon;

    private String color;
}
