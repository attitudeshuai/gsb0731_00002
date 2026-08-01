package com.dbmanager.dto;

import lombok.Data;

@Data
public class TableInfo {

    private String name;
    private String type;
    private String schema;
    private String comment;
    private long rowCountEstimate;
}
