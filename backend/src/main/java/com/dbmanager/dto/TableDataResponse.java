package com.dbmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableDataResponse {
    private List<ColumnMeta> columns;
    private List<Map<String, Object>> rows;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<PrimaryKeyColumn> primaryKeys;
}
