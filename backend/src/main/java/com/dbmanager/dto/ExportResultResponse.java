package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportResultResponse {

    private Integer rowCount;
    private Long fileSize;
    private String status;
    private String fileName;
    private String errorMessage;
}
