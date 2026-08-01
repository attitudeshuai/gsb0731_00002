package com.dbmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SavedQueryRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "SQL text is required")
    private String sqlText;

    private Long folderId;
    private Long connectionId;
    private String databaseName;
    private String tags;
}
