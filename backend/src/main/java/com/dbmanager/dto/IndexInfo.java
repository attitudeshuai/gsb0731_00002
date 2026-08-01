package com.dbmanager.dto;

import lombok.Data;

@Data
public class IndexInfo {

    private String indexName;
    private String columnName;
    private boolean nonUnique;
    private String indexType;
    private int ordinalPosition;
}
