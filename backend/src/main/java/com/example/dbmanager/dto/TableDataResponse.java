package com.example.dbmanager.dto;

import java.util.List;

public record TableDataResponse(List<ColumnMeta> columns, List<String> primaryKeys,
                                List<List<Object>> rows, long total, int page, int size) {
}
