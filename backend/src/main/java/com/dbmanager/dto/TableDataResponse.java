package com.dbmanager.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TableDataResponse {

    private List<ColumnInfo> columns;
    private List<Map<String, Object>> rows;
    private long total;
    private int page;
    private int size;
}
