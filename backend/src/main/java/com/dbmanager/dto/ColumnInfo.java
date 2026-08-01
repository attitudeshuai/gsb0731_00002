package com.dbmanager.dto;

import lombok.Data;

@Data
public class ColumnInfo {

    private String name;
    private String type;
    private boolean nullable;
    private String defaultValue;
    private String comment;
    private boolean primaryKey;
    private boolean autoIncrement;
    private int ordinalPosition;
}
