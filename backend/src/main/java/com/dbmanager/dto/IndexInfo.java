package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexInfo {
    private String indexName;
    private String columnName;
    private Boolean nonUnique;
    private String indexType;
    private Integer seqInIndex;
}
