package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SavedQueryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "SQL text is required")
    private String sqlText;

    private Long folderId;

    private Long connectionId;

    private String description;

    private String tags;
}
