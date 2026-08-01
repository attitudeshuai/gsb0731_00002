package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnMeta {
    private String name;
    private String type;
    private String typeName;
    private Integer precision;
    private Integer scale;
    private Boolean nullable;
    private Boolean primaryKey;
    private String defaultValue;
    private String comment;
    private Boolean autoIncrement;
}
