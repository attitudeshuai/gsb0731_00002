package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableMeta {
    private String name;
    private String type;
    private String comment;
    private Long estimatedRows;
}
